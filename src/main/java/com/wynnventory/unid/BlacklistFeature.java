package com.wynnventory.unid;

import com.mojang.blaze3d.platform.InputConstants;
import com.wynntils.core.components.Models;
import com.wynntils.mc.event.InventoryKeyPressEvent;
import com.wynntils.mc.event.ItemTooltipRenderEvent;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.models.ingredients.type.IngredientInfo;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.IngredientItem;
import com.wynntils.screens.base.TextboxScreen;
import com.wynntils.utils.mc.McUtils;
import com.wynnventory.feature.input.Keybinds;
import com.wynnventory.util.ItemStackUtils;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.TextColor;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import org.lwjgl.glfw.GLFW;

/**
 * Blacklisted gear and ingredients are never highlighted, add nothing to box EV and are crossed out in box tooltips;
 * favorites are always highlighted and shown bold in their color. The blacklist and favorite keys work on the hovered
 * item, or on a box, on the possible item marked with the arrow keys.
 */
public class BlacklistFeature {
    public static final KeyMapping TOGGLE_KEY = key("toggleBlacklist", GLFW.GLFW_KEY_B);
    public static final KeyMapping FAVORITE_KEY = key("toggleFavorite", GLFW.GLFW_KEY_V);
    public static final KeyMapping MARK_PREVIOUS_KEY = key("markPrevious", GLFW.GLFW_KEY_UP);
    public static final KeyMapping MARK_NEXT_KEY = key("markNext", GLFW.GLFW_KEY_DOWN);

    private static final int UNCOLORED = 0x555555;

    // The possible gear the last box tooltip listed, in tooltip order, and which of them is marked.
    private List<String> shownNames = List.of();
    private String markedName;

    private static KeyMapping key(String name, int defaultKey) {
        return new KeyMapping(
                "key.wynnventory_unid." + name, InputConstants.Type.KEYSYM, defaultKey, Keybinds.ROOT_CATEGORY);
    }

    @SubscribeEvent
    public void onInventoryKeyPress(InventoryKeyPressEvent event) {
        Slot slot = event.getHoveredSlot();
        if (slot == null) return;
        // Keys typed into a Wynntils search box are text.
        if (McUtils.screen() instanceof TextboxScreen screen && screen.getFocusedTextInput() != null) return;

        KeyEvent key = event.getKeyEvent();
        if (ItemStackUtils.getWynnItem(slot.getItem()) instanceof GearBoxItem) {
            if (markedName == null) return;

            int step = MARK_NEXT_KEY.matches(key) ? 1 : MARK_PREVIOUS_KEY.matches(key) ? -1 : 0;
            if (step != 0) {
                int index = shownNames.indexOf(markedName) + step;
                markedName = shownNames.get(Math.floorMod(index, shownNames.size()));
            } else if (!toggleList(key, markedName)) {
                return;
            }
            event.setCanceled(true);
            return;
        }

        String name = itemName(slot.getItem());
        if (name != null && toggleList(key, name)) event.setCanceled(true);
    }

    /** Toggles the item on the list the key belongs to; false if it's neither list's key. */
    private static boolean toggleList(KeyEvent key, String name) {
        UnidSettings settings = UnidSettings.get();
        String message;
        if (TOGGLE_KEY.matches(key)) {
            boolean blacklisted = !settings.removeFromBlacklist(name);
            if (blacklisted) settings.addToBlacklist(name);
            message = blacklisted ? "Blacklisted " + name : "Removed " + name + " from blacklist";
        } else if (FAVORITE_KEY.matches(key)) {
            boolean favorite = !settings.removeFromFavorites(name);
            if (favorite) settings.addToFavorites(name);
            message = favorite ? "Favorited " + name : "Removed " + name + " from favorites";
        } else {
            return false;
        }

        UnidSettings.save();
        McUtils.mc().gui.setOverlayMessage(Component.literal(message), false);
        return true;
    }

    // LOWEST runs after Wynntils' item guess has listed the box's possible gear.
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public void onTooltip(ItemTooltipRenderEvent.Pre event) {
        if (!(ItemStackUtils.getWynnItem(event.getItemStack()) instanceof GearBoxItem box)) return;

        Set<String> possible =
                Models.Gear.getPossibleGears(box).stream().map(GearInfo::name).collect(Collectors.toSet());
        shownNames = event.getTooltips().stream()
                .flatMap(line -> line.toFlatList().stream())
                .map(Component::getString)
                .filter(possible::contains)
                .distinct()
                .toList();
        // Another box starts on its first item, unless it lists the marked one too.
        if (markedName == null || !shownNames.contains(markedName)) {
            markedName = shownNames.isEmpty() ? null : shownNames.getFirst();
        }

        // Rebuilt rather than restyled in place: some lines are the item's own lore, shared with the stack.
        List<Component> lines = new ArrayList<>();
        for (Component line : event.getTooltips()) {
            lines.add(restyle(line));
        }
        event.setTooltips(lines);
    }

    static String itemName(ItemStack stack) {
        return switch (ItemStackUtils.getWynnItem(stack)) {
            case GearItem gear -> gear.getName();
            case IngredientItem ingredient -> ingredient.getName();
            case null, default -> null;
        };
    }

    /** The item's own spelling of a typed name, or empty for a typo that would never match anything. */
    static Optional<String> resolveItemName(String input) {
        return itemNames().filter(input.trim()::equalsIgnoreCase).findFirst();
    }

    /** Every name the blacklist can hold, for command suggestions. */
    static Stream<String> itemNames() {
        return Stream.concat(
                Models.Gear.getAllGearInfos().map(GearInfo::name),
                Models.Ingredient.getAllIngredientInfos().map(IngredientInfo::name));
    }

    /** Copies a tooltip line, crossing out blacklisted names, bolding favorites and marking the marked one. */
    private Component restyle(Component component) {
        MutableComponent copy = MutableComponent.create(component.getContents()).setStyle(component.getStyle());
        for (Component sibling : component.getSiblings()) {
            copy.append(restyle(sibling));
        }
        if (!(component.getContents() instanceof PlainTextContents text)) return copy;

        if (UnidSettings.get().isBlacklisted(text.text())) {
            TextColor color = component.getStyle().getColor();
            // Half brightness keeps the tier color recognisable.
            int darker = color == null ? UNCOLORED : (color.getValue() >> 1) & 0x7F7F7F;
            copy.withStyle(style -> style.withStrikethrough(true).withColor(darker));
        } else if (UnidSettings.get().isFavorite(text.text())) {
            int color = UnidSettings.get().getFavoriteLook().getColor();
            copy.withStyle(style -> style.withBold(true).withColor(color));
        }
        if (!text.text().equals(markedName)) return copy;

        // The empty parent keeps the name inheriting the line's style rather than the marker's.
        return Component.empty()
                .append(Component.literal("▶ ").withStyle(ChatFormatting.WHITE))
                .append(copy);
    }
}

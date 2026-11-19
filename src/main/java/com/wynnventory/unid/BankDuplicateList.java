package com.wynnventory.unid;

import com.wynntils.core.components.Models;
import com.wynntils.models.containers.containers.personal.PersonalStorageContainer;
import com.wynntils.models.gear.type.GearInfo;
import com.wynntils.utils.mc.McUtils;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.regex.Pattern;
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.client.screen.v1.ScreenMouseEvents;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.core.component.DataComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.ItemLore;
import org.lwjgl.glfw.GLFW;

/**
 * In any bank menu, a draggable list of the unidentified items owned more than once, most copies first. With WynnExtras
 * it counts everything WynnExtras' All Characters Mode sees plus the live inventory; without it, the open page and the
 * inventory.
 */
public final class BankDuplicateList {
    // Wynncraft's lore line on every sealed item, gear boxes included.
    private static final String SEALED = "This item's power has been sealed";
    // Wynncraft pads names with characters from its own fonts, outside the Basic Multilingual Plane or in private use.
    private static final Pattern HIDDEN = Pattern.compile("[\\x{E000}-\\x{F8FF}\\x{10000}-\\x{10FFFF}]|§.");
    // Each refresh rereads every character's saved bank from disk, off the render thread.
    private static final long REFRESH_MS = 3000;
    private static final int MARGIN = 4;
    private static final int PADDING = 4;
    private static final int LINE_HEIGHT = 10;
    private static final int TITLE_HEIGHT = LINE_HEIGHT + 2;
    private static final int BACKGROUND = 0xC0101010;
    private static final int TITLE_COLOR = 0xFFFFAA00;
    private static final int COUNT_COLOR = 0xAAAAAA;

    record Duplicate(String name, int count) {}

    private static volatile List<Duplicate> shown = List.of();
    private static volatile boolean everything;
    private static CompletableFuture<?> pending;
    private static long refreshedAt;

    // Where the list was last drawn, for dragging; null while it isn't showing.
    private static int[] bounds;
    private static boolean dragging;
    private static double grabX;
    private static double grabY;

    private BankDuplicateList() {}

    // Fabric's hooks run around the whole screen, so the list works even where WynnExtras replaces the bank's drawing.
    static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (!(screen instanceof AbstractContainerScreen<?>)) return;

            ScreenEvents.afterRender(screen)
                    .register((drawn, graphics, mouseX, mouseY, partialTick) -> render(drawn, graphics));
            ScreenMouseEvents.allowMouseClick(screen).register((clicked, event) -> !startDrag(event));
            ScreenMouseEvents.allowMouseDrag(screen).register((dragged, event, dx, dy) -> !drag(dragged, event));
            ScreenMouseEvents.allowMouseRelease(screen).register((released, event) -> !endDrag());
            ScreenEvents.remove(screen).register(removed -> bounds = null);
        });
    }

    private static void render(Screen screen, GuiGraphics graphics) {
        UnidSettings settings = UnidSettings.get();
        if (!settings.isShowBankDuplicates()
                || !(Models.Container.getCurrentContainer() instanceof PersonalStorageContainer)) {
            bounds = null;
            return;
        }

        long now = System.currentTimeMillis();
        if ((pending == null || pending.isDone()) && now - refreshedAt >= REFRESH_MS) {
            refreshedAt = now;
            refresh(screen, settings.getBankDuplicateRows());
        }
        draw(graphics, screen.width, screen.height, settings);
    }

    private static void refresh(Screen screen, int rows) {
        // Copied here, on the render thread, since the counting may finish on another.
        List<ItemStack> live = inventory();
        pending = WynnExtrasBank.everythingButCurrentInventory()
                .<CompletableFuture<?>>map(saved -> saved.thenAccept(items -> {
                    items.addAll(live);
                    publish(items, rows, true);
                }))
                .orElseGet(() -> {
                    live.addAll(openPage(screen));
                    publish(live, rows, false);
                    return CompletableFuture.completedFuture(null);
                });
    }

    private static void publish(List<ItemStack> items, int rows, boolean fromEverything) {
        Map<String, Integer> counts = new HashMap<>();
        for (ItemStack item : items) {
            String name = unidName(item);
            if (name != null) counts.merge(name, item.getCount(), Integer::sum);
        }
        everything = fromEverything;
        shown = rank(counts, rows);
    }

    private static List<ItemStack> inventory() {
        Inventory inventory = McUtils.player().getInventory();
        List<ItemStack> items = new ArrayList<>();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            items.add(inventory.getItem(i).copy());
        }
        return items;
    }

    private static List<ItemStack> openPage(Screen screen) {
        if (!(screen instanceof AbstractContainerScreen<?> container)) return List.of();

        List<ItemStack> items = new ArrayList<>();
        for (Slot slot : container.getMenu().slots) {
            if (!(slot.container instanceof Inventory)) items.add(slot.getItem().copy());
        }
        return items;
    }

    /** A sealed item's name, or null for anything else, gear boxes included. */
    private static String unidName(ItemStack item) {
        ItemLore lore = item.get(DataComponents.LORE);
        if (item.isEmpty() || lore == null) return null;
        if (lore.lines().stream().noneMatch(line -> line.getString().contains(SEALED))) return null;

        String name = cleanName(item.getHoverName().getString());
        // Gear boxes are sealed too, under a generic name like "Unidentified Chestplate".
        return name.isEmpty() || name.startsWith("Unidentified ") ? null : name;
    }

    static String cleanName(String name) {
        return HIDDEN.matcher(name).replaceAll("").strip();
    }

    /** The names counted more than once, most copies first, then by name. */
    static List<Duplicate> rank(Map<String, Integer> counts, int limit) {
        return counts.entrySet().stream()
                .filter(entry -> entry.getValue() > 1)
                .map(entry -> new Duplicate(entry.getKey(), entry.getValue()))
                .sorted(Comparator.comparingInt(Duplicate::count).reversed().thenComparing(Duplicate::name))
                .limit(limit)
                .toList();
    }

    private static void draw(GuiGraphics graphics, int screenWidth, int screenHeight, UnidSettings settings) {
        Font font = McUtils.mc().font;
        Component title =
                Component.literal(everything ? "Unid copies, all characters" : "Unid copies, page + inventory");
        List<Component> lines = lines(shown, (screenHeight - TITLE_HEIGHT - 2 * PADDING) / LINE_HEIGHT);

        int width = font.width(title);
        for (Component line : lines) {
            width = Math.max(width, font.width(line));
        }
        width += 2 * PADDING;
        int height = TITLE_HEIGHT + lines.size() * LINE_HEIGHT + 2 * PADDING;
        // Unmoved, it sits top right; moved, wherever it was dropped, kept on screen if the window shrank since.
        int left = clamp(settings.getBankListX(), screenWidth - width - MARGIN, screenWidth - width);
        int top = clamp(settings.getBankListY(), MARGIN, screenHeight - height);
        bounds = new int[] {left, top, width, height};

        // Over everything the screen drew, WynnExtras' bank overlay included.
        graphics.nextStratum();
        graphics.fill(left, top, left + width, top + height, BACKGROUND);
        graphics.drawString(font, title, left + PADDING, top + PADDING, TITLE_COLOR);
        for (int i = 0; i < lines.size(); i++) {
            graphics.drawString(
                    font, lines.get(i), left + PADDING, top + PADDING + TITLE_HEIGHT + i * LINE_HEIGHT, 0xFFFFFFFF);
        }
    }

    /** One line per duplicate, cut to what fits with a last line saying how many more there are. */
    private static List<Component> lines(List<Duplicate> duplicates, int fit) {
        if (duplicates.isEmpty()) return List.of(Component.literal("None").withColor(COUNT_COLOR));

        boolean cut = duplicates.size() > fit;
        int shownCount = cut ? Math.max(0, fit - 1) : duplicates.size();
        List<Component> lines = new ArrayList<>();
        for (Duplicate duplicate : duplicates.subList(0, shownCount)) {
            lines.add(Component.literal(duplicate.count() + "× ")
                    .withColor(COUNT_COLOR)
                    .append(Component.literal(duplicate.name()).withStyle(tierColor(duplicate.name()))));
        }
        if (cut)
            lines.add(Component.literal("…" + (duplicates.size() - shownCount) + " more")
                    .withColor(COUNT_COLOR));
        return lines;
    }

    private static int clamp(Integer saved, int fallback, int max) {
        return Math.clamp(saved == null ? fallback : saved, 0, Math.max(0, max));
    }

    private static ChatFormatting tierColor(String name) {
        GearInfo info = Models.Gear.getGearInfoFromDisplayName(name);
        return info == null ? ChatFormatting.WHITE : info.tier().getChatFormatting();
    }

    /** Whether the click grabbed the list, which keeps it from reaching the bank underneath. */
    private static boolean startDrag(MouseButtonEvent event) {
        if (bounds == null || event.button() != GLFW.GLFW_MOUSE_BUTTON_LEFT) return false;
        if (event.x() < bounds[0] || event.x() >= bounds[0] + bounds[2]) return false;
        if (event.y() < bounds[1] || event.y() >= bounds[1] + bounds[3]) return false;

        dragging = true;
        grabX = event.x() - bounds[0];
        grabY = event.y() - bounds[1];
        return true;
    }

    private static boolean drag(Screen screen, MouseButtonEvent event) {
        if (!dragging || bounds == null) return dragging;

        UnidSettings settings = UnidSettings.get();
        settings.setBankListX(
                Math.clamp((int) Math.round(event.x() - grabX), 0, Math.max(0, screen.width - bounds[2])));
        settings.setBankListY(
                Math.clamp((int) Math.round(event.y() - grabY), 0, Math.max(0, screen.height - bounds[3])));
        return true;
    }

    private static boolean endDrag() {
        if (!dragging) return false;

        dragging = false;
        UnidSettings.save();
        return true;
    }
}

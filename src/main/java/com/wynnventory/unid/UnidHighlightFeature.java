package com.wynnventory.unid;

import com.wynntils.mc.event.SlotRenderEvent;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.IngredientItem;
import com.wynntils.utils.mc.McUtils;
import com.wynnventory.api.service.TrademarketService;
import com.wynnventory.core.config.ModConfig;
import com.wynnventory.model.item.trademarket.TrademarketItemSnapshot;
import com.wynnventory.util.ItemStackUtils;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Highlights and stars unidentified gear that sells often for a high unid price, gear boxes with a high expected value
 * and ingredients with a high lowest price.
 */
public class UnidHighlightFeature {
    private static final int STAR_COLOR = 0xFFFFFF55;
    private static final float STAR_SCALE = 0.75f;

    @SubscribeEvent
    public void onSlotRender(SlotRenderEvent.Pre event) {
        if (!UnidSettings.get().isHighlightItems()) return;

        Slot slot = event.getSlot();
        if (!isHighlighted(slot.getItem())) return;

        // Same color as WynnVentory's own price highlighting.
        int color = ModConfig.getInstance().getPriceHighlightSettings().getHighlightColor();
        // Pre runs before the item renders, so the fill sits behind it like Wynntils' own slot highlights.
        event.getGuiGraphics().fill(slot.x, slot.y, slot.x + 16, slot.y + 16, 0x80000000 | color);
    }

    @SubscribeEvent
    public void onSlotRendered(SlotRenderEvent.Post event) {
        if (!UnidSettings.get().isHighlightItems()) return;

        Slot slot = event.getSlot();
        if (!isHighlighted(slot.getItem())) return;

        // Post runs after the item renders, so the star sits on top of it.
        GuiGraphics graphics = event.getGuiGraphics();
        graphics.pose().pushMatrix();
        graphics.pose().translate(slot.x, slot.y);
        graphics.pose().scale(STAR_SCALE, STAR_SCALE);
        graphics.drawString(McUtils.mc().font, "★", 0, 0, STAR_COLOR, true);
        graphics.pose().popMatrix();
    }

    static boolean isHighlighted(ItemStack stack) {
        return switch (ItemStackUtils.getWynnItem(stack)) {
            case GearBoxItem box ->
                GearBoxValue.of(box).expectedValue() >= UnidSettings.get().getBoxMinEv();
            case GearItem gear -> !UnidSettings.get().isBlacklisted(gear.getName()) && isHighValue(gear);
            case IngredientItem ingredient ->
                !UnidSettings.get().isBlacklisted(ingredient.getName()) && isValuableIngredient(stack);
            case null, default -> false;
        };
    }

    private static boolean isValuableIngredient(ItemStack stack) {
        TrademarketItemSnapshot snapshot = TrademarketItemSnapshot.resolveSnapshot(stack);
        return snapshot != null && UnidValue.isValuableIngredient(snapshot);
    }

    private static boolean isHighValue(GearItem gear) {
        if (!gear.isUnidentified()) return false;

        TrademarketItemSnapshot snapshot = TrademarketService.INSTANCE.getItem(gear.getName(), false);
        return snapshot != null && UnidValue.isHighValue(snapshot, gear.getGearTier() == GearTier.MYTHIC);
    }
}

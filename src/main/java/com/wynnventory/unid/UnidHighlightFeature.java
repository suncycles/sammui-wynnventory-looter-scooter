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
import net.minecraft.client.gui.Font;
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
    private static final int STAR_OUTLINE_COLOR = 0xFF000000;
    private static final float STAR_SCALE = 1.25f;
    // A faint fill: the frame does the job of standing out against Wynntils' rarity backgrounds.
    private static final int FILL_ALPHA = 0x4D000000;
    private static final int FRAME_WIDTH = 2;

    @SubscribeEvent
    public void onSlotRender(SlotRenderEvent.Pre event) {
        if (!UnidSettings.get().isHighlightItems()) return;

        Slot slot = event.getSlot();
        if (!isHighlighted(slot.getItem())) return;

        // Pre runs before the item renders, so the fill sits behind it like Wynntils' own slot highlights.
        event.getGuiGraphics().fill(slot.x, slot.y, slot.x + 16, slot.y + 16, FILL_ALPHA | highlightColor());
    }

    @SubscribeEvent
    public void onSlotRendered(SlotRenderEvent.Post event) {
        if (!UnidSettings.get().isHighlightItems()) return;

        Slot slot = event.getSlot();
        if (!isHighlighted(slot.getItem())) return;

        // Post runs after the item renders, so the frame and star sit on top of it.
        GuiGraphics graphics = event.getGuiGraphics();
        drawFrame(graphics, slot);

        Font font = McUtils.mc().font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(slot.x, slot.y);
        graphics.pose().scale(STAR_SCALE, STAR_SCALE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx != 0 || dy != 0) graphics.drawString(font, "★", dx, dy, STAR_OUTLINE_COLOR, false);
            }
        }
        graphics.drawString(font, "★", 0, 0, STAR_COLOR, false);
        graphics.pose().popMatrix();
    }

    // Covers the slot's own 1px border and 1px of the item area, so it never reaches the next slot.
    private static void drawFrame(GuiGraphics graphics, Slot slot) {
        int color = 0xFF000000 | highlightColor();
        int left = slot.x - 1;
        int top = slot.y - 1;
        int right = slot.x + 17;
        int bottom = slot.y + 17;
        graphics.fill(left, top, right, top + FRAME_WIDTH, color);
        graphics.fill(left, bottom - FRAME_WIDTH, right, bottom, color);
        graphics.fill(left, top + FRAME_WIDTH, left + FRAME_WIDTH, bottom - FRAME_WIDTH, color);
        graphics.fill(right - FRAME_WIDTH, top + FRAME_WIDTH, right, bottom - FRAME_WIDTH, color);
    }

    // Same color as WynnVentory's own price highlighting.
    private static int highlightColor() {
        return ModConfig.getInstance().getPriceHighlightSettings().getHighlightColor();
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

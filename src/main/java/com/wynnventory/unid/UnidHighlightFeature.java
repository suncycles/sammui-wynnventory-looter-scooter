package com.wynnventory.unid;

import com.wynntils.mc.event.SlotRenderEvent;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.models.items.items.game.GearItem;
import com.wynntils.models.items.items.game.IngredientItem;
import com.wynntils.utils.mc.McUtils;
import com.wynnventory.api.service.TrademarketService;
import com.wynnventory.model.item.trademarket.TrademarketItemSnapshot;
import com.wynnventory.unid.UnidSettings.Look;
import com.wynnventory.unid.UnidSettings.Marker;
import com.wynnventory.unid.UnidSettings.Shape;
import com.wynnventory.util.ItemStackUtils;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Highlights unidentified gear that sells often for a high unid price, gear boxes with a high expected value,
 * ingredients with a high lowest price and favorites (with boxes that can drop them), and marks blacklisted gear and
 * ingredients; each kind in its own look.
 */
public class UnidHighlightFeature {
    private static final int MARKER_OUTLINE_COLOR = 0xFF000000;
    private static final float MARKER_SCALE = 1.0f;
    // Half a pixel, thinner than drawString's whole-pixel positions allow.
    private static final float MARKER_OUTLINE_WIDTH = 0.5f;
    private static final int FAINT_ALPHA = 0x4D;
    private static final int HALF_ALPHA = 0x80;
    private static final int PULSE_MIN_ALPHA = 0x26;
    private static final int PULSE_MAX_ALPHA = 0xE6;
    private static final long PULSE_PERIOD_MS = 2200;
    private static final int DIM_COLOR = 0x8C000000;
    // The slot's own 1px border around its 16px item area; shapes stay inside it, so they never reach the next slot.
    private static final int CELL = 18;
    private static final int FRAME_WIDTH = 2;
    private static final int CORNER_LENGTH = 6;
    private static final int DASH = 3;
    private static final int DASH_GAP = 2;
    private static final int INNER_FRAME_COLOR = 0xFFFFC800;

    private enum Kind {
        FAVORITE,
        BOX,
        ITEM,
        BLACKLISTED
    }

    @SubscribeEvent
    public void onSlotRender(SlotRenderEvent.Pre event) {
        if (!UnidSettings.get().isHighlightItems()) return;

        Slot slot = event.getSlot();
        Look look = lookOf(slot.getItem());
        if (look == null) return;

        int alpha =
                switch (look.getFill()) {
                    case FAINT -> FAINT_ALPHA;
                    case HALF -> HALF_ALPHA;
                    case PULSE -> pulseAlpha();
                    case NONE, DIM -> 0;
                };
        // Pre runs before the item renders, so the fill sits behind it like Wynntils' own slot highlights.
        if (alpha > 0) {
            event.getGuiGraphics().fill(slot.x, slot.y, slot.x + 16, slot.y + 16, alpha << 24 | look.getColor());
        }
    }

    @SubscribeEvent
    public void onSlotRendered(SlotRenderEvent.Post event) {
        if (!UnidSettings.get().isHighlightItems()) return;

        Slot slot = event.getSlot();
        Look look = lookOf(slot.getItem());
        if (look == null) return;

        // Post runs after the item renders, so all of these sit on top of it.
        GuiGraphics graphics = event.getGuiGraphics();
        if (look.getFill() == UnidSettings.Fill.DIM) {
            graphics.fill(slot.x, slot.y, slot.x + 16, slot.y + 16, DIM_COLOR);
        }
        drawShape(graphics, slot.x - 1, slot.y - 1, look.getShape(), 0xFF000000 | look.getColor());
        drawMarker(graphics, slot, look.getMarker(), 0xFF000000 | look.getMarkerColor());
    }

    /** Fades from faint to strong and back once per period. */
    private static int pulseAlpha() {
        double phase = System.currentTimeMillis() % PULSE_PERIOD_MS / (double) PULSE_PERIOD_MS;
        double wave = 0.5 - 0.5 * Math.cos(2 * Math.PI * phase);
        return (int) (PULSE_MIN_ALPHA + (PULSE_MAX_ALPHA - PULSE_MIN_ALPHA) * wave);
    }

    private static void drawShape(GuiGraphics graphics, int left, int top, Shape shape, int color) {
        int right = left + CELL;
        int bottom = top + CELL;
        switch (shape) {
            case FRAME -> drawRing(graphics, left, top, right, bottom, FRAME_WIDTH, color);
            case DOUBLE_FRAME -> {
                drawRing(graphics, left, top, right, bottom, FRAME_WIDTH, color);
                drawRing(graphics, left + 3, top + 3, right - 3, bottom - 3, 1, INNER_FRAME_COLOR);
            }
            case CORNERS -> {
                for (int x : new int[] {left, right - CORNER_LENGTH}) {
                    graphics.fill(x, top, x + CORNER_LENGTH, top + FRAME_WIDTH, color);
                    graphics.fill(x, bottom - FRAME_WIDTH, x + CORNER_LENGTH, bottom, color);
                }
                for (int y : new int[] {top, bottom - CORNER_LENGTH}) {
                    graphics.fill(left, y, left + FRAME_WIDTH, y + CORNER_LENGTH, color);
                    graphics.fill(right - FRAME_WIDTH, y, right, y + CORNER_LENGTH, color);
                }
            }
            case DASHED_FRAME -> {
                for (int start = 0; start < CELL; start += DASH + DASH_GAP) {
                    int end = Math.min(start + DASH, CELL);
                    graphics.fill(left + start, top, left + end, top + FRAME_WIDTH, color);
                    graphics.fill(left + start, bottom - FRAME_WIDTH, left + end, bottom, color);
                    graphics.fill(left, top + start, left + FRAME_WIDTH, top + end, color);
                    graphics.fill(right - FRAME_WIDTH, top + start, right, top + end, color);
                }
            }
            case SLASH -> {
                // Bottom-left to top-right, a 2px step per column.
                for (int i = 0; i < CELL; i++) {
                    graphics.fill(left + i, Math.max(top, bottom - i - 2), left + i + 1, bottom - i, color);
                }
            }
            case NONE -> {}
        }
    }

    private static void drawRing(GuiGraphics graphics, int left, int top, int right, int bottom, int width, int color) {
        graphics.fill(left, top, right, top + width, color);
        graphics.fill(left, bottom - width, right, bottom, color);
        graphics.fill(left, top + width, left + width, bottom - width, color);
        graphics.fill(right - width, top + width, right, bottom - width, color);
    }

    private static void drawMarker(GuiGraphics graphics, Slot slot, Marker marker, int color) {
        if (marker == Marker.NONE) return;

        Font font = McUtils.mc().font;
        graphics.pose().pushMatrix();
        graphics.pose().translate(slot.x, slot.y);
        graphics.pose().scale(MARKER_SCALE, MARKER_SCALE);
        for (int dx = -1; dx <= 1; dx++) {
            for (int dy = -1; dy <= 1; dy++) {
                if (dx == 0 && dy == 0) continue;

                graphics.pose().pushMatrix();
                graphics.pose().translate(dx * MARKER_OUTLINE_WIDTH, dy * MARKER_OUTLINE_WIDTH);
                graphics.drawString(font, marker.glyph, 0, 0, MARKER_OUTLINE_COLOR, false);
                graphics.pose().popMatrix();
            }
        }
        graphics.drawString(font, marker.glyph, 0, 0, color, false);
        graphics.pose().popMatrix();
    }

    /** Whether the slot is highlighted as worth keeping; blacklisted items are marked, not highlighted. */
    static boolean isHighlighted(ItemStack stack) {
        Kind kind = kindOf(stack);
        return kind != null && kind != Kind.BLACKLISTED;
    }

    private static Look lookOf(ItemStack stack) {
        Kind kind = kindOf(stack);
        if (kind == null) return null;

        UnidSettings settings = UnidSettings.get();
        return switch (kind) {
            case FAVORITE -> settings.getFavoriteLook();
            case BOX -> settings.getBoxLook();
            case ITEM -> settings.getItemLook();
            case BLACKLISTED -> settings.getBlacklistLook();
        };
    }

    private static Kind kindOf(ItemStack stack) {
        UnidSettings settings = UnidSettings.get();
        return switch (ItemStackUtils.getWynnItem(stack)) {
            case GearBoxItem box -> {
                GearBoxValue value = GearBoxValue.of(box);
                if (value.canDropFavorite()) yield Kind.FAVORITE;
                yield value.expectedValue() >= settings.getBoxMinEv() ? Kind.BOX : null;
            }
            // Favorites are highlighted whatever they're worth, identified or not.
            case GearItem gear when settings.isFavorite(gear.getName()) -> Kind.FAVORITE;
            case GearItem gear
            when gear.isUnidentified() ->
                settings.isBlacklisted(gear.getName()) ? Kind.BLACKLISTED : isHighValue(gear) ? Kind.ITEM : null;
            case IngredientItem ingredient when settings.isFavorite(ingredient.getName()) -> Kind.FAVORITE;
            case IngredientItem ingredient ->
                settings.isBlacklisted(ingredient.getName())
                        ? Kind.BLACKLISTED
                        : isValuableIngredient(stack) ? Kind.ITEM : null;
            case null, default -> null;
        };
    }

    private static boolean isValuableIngredient(ItemStack stack) {
        TrademarketItemSnapshot snapshot = TrademarketItemSnapshot.resolveSnapshot(stack);
        return snapshot != null && UnidValue.isValuableIngredient(snapshot);
    }

    private static boolean isHighValue(GearItem gear) {
        TrademarketItemSnapshot snapshot = TrademarketService.INSTANCE.getItem(gear.getName(), false);
        return snapshot != null && UnidValue.isHighValue(snapshot, gear.getGearTier() == GearTier.MYTHIC);
    }
}

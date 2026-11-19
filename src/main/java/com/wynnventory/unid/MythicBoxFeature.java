package com.wynnventory.unid;

import com.wynntils.core.components.Models;
import com.wynntils.mc.event.ContainerRenderEvent;
import com.wynntils.models.containers.containers.reward.RewardContainer;
import com.wynntils.models.gear.type.GearTier;
import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynntils.utils.mc.McUtils;
import com.wynnventory.unid.UnidSettings.MythicEffect;
import com.wynnventory.unid.mixin.AbstractContainerScreenAccessor;
import com.wynnventory.util.ItemStackUtils;
import java.util.List;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.client.resources.sounds.SimpleSoundInstance;
import net.minecraft.resources.Identifier;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.inventory.Slot;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;

/**
 * Draws a mythic gear box found in a reward chest bigger, with soft rings or rays behind it, and growls like the ender
 * dragon once per chest.
 */
public class MythicBoxFeature {
    private static final int COLOR = 0xC040FF;
    private static final int LIGHT_COLOR = 0xF0C0FF;
    private static final int TEXTURE_SIZE = 128;
    private static final Identifier GLOW = texture("mythic_glow");
    private static final Identifier RING = texture("mythic_ring");
    private static final Identifier RAYS = texture("mythic_rays");
    // In GUI pixels for a box at 1x; the effect grows with the box.
    private static final float GLOW_DIAMETER = 28;
    private static final float RING_DIAMETER = 48;
    private static final float RAYS_DIAMETER = 60;
    private static final int RINGS = 3;
    private static final long RING_PERIOD_MS = 1800;
    private static final long GLOW_PERIOD_MS = 2400;
    private static final long RAYS_PERIOD_MS = 9000;
    private static final long INNER_RAYS_PERIOD_MS = 14000;

    private Screen announcedScreen;

    @SubscribeEvent
    public void onContainerRender(ContainerRenderEvent event) {
        if (!(Models.Container.getCurrentContainer() instanceof RewardContainer)) return;

        AbstractContainerScreen<?> screen = event.getScreen();
        // Only the chest's own slots: a mythic box already in the player's inventory was found earlier.
        List<Slot> mythicSlots = screen.getMenu().slots.stream()
                .filter(slot -> !(slot.container instanceof Inventory) && isMythicBox(slot.getItem()))
                .toList();
        if (mythicSlots.isEmpty()) return;

        // Every chest opens a new screen, so this growls once per chest.
        if (screen != announcedScreen) {
            announcedScreen = screen;
            growl();
        }

        UnidSettings settings = UnidSettings.get();
        GuiGraphics graphics = event.getGuiGraphics();
        AbstractContainerScreenAccessor chest = (AbstractContainerScreenAccessor) screen;
        float size = settings.getMythicSize();
        long now = System.currentTimeMillis();
        // A new stratum draws over every slot already drawn, so the effect and the bigger box can cover neighbors.
        graphics.nextStratum();
        for (Slot slot : mythicSlots) {
            int x = chest.getLeftPos() + slot.x + 8;
            int y = chest.getTopPos() + slot.y + 8;
            drawEffect(graphics, settings.getMythicEffect(), x, y, size, now);
        }
        graphics.nextStratum();
        for (Slot slot : mythicSlots) {
            drawItem(graphics, slot.getItem(), chest.getLeftPos() + slot.x + 8, chest.getTopPos() + slot.y + 8, size);
        }
    }

    /** Plays the ender dragon's idle sound, which picks one of its four growls at random, at the set volume. */
    static void growl() {
        int volume = UnidSettings.get().getMythicVolume();
        if (volume == 0) return;

        McUtils.mc()
                .getSoundManager()
                .play(SimpleSoundInstance.forUI(SoundEvents.ENDER_DRAGON_AMBIENT, 1, volume / 100f));
    }

    private static boolean isMythicBox(ItemStack stack) {
        return ItemStackUtils.getWynnItem(stack) instanceof GearBoxItem box && box.getGearTier() == GearTier.MYTHIC;
    }

    private static void drawEffect(GuiGraphics graphics, MythicEffect effect, int x, int y, float size, long now) {
        if (effect == MythicEffect.OFF) return;

        float breath = 0.5f - 0.5f * (float) Math.cos(2 * Math.PI * phase(now, GLOW_PERIOD_MS));
        drawTexture(
                graphics, GLOW, x, y, GLOW_DIAMETER * size * (0.9f + 0.15f * breath), 0, COLOR, 0.55f + 0.4f * breath);
        if (effect == MythicEffect.RINGS) {
            for (int i = 0; i < RINGS; i++) {
                float t = phase(now + i * RING_PERIOD_MS / RINGS, RING_PERIOD_MS);
                // Each ring shoots out fast, then slows and fades as it reaches full size.
                float grown = 1 - (float) Math.pow(1 - t, 3);
                drawTexture(
                        graphics, RING, x, y, RING_DIAMETER * size * (0.22f + 0.78f * grown), 0, LIGHT_COLOR, 1 - t);
            }
        } else {
            float turn = (float) (2 * Math.PI);
            float diameter = RAYS_DIAMETER * size;
            drawTexture(graphics, RAYS, x, y, diameter, turn * phase(now, RAYS_PERIOD_MS), COLOR, 0.85f);
            // A fainter, lighter set turning the other way makes the rays shimmer.
            drawTexture(
                    graphics, RAYS, x, y, diameter, -turn * phase(now, INNER_RAYS_PERIOD_MS) + 0.2f, LIGHT_COLOR, 0.5f);
        }
    }

    private static float phase(long now, long period) {
        return now % period / (float) period;
    }

    private static void drawTexture(
            GuiGraphics graphics,
            Identifier texture,
            float x,
            float y,
            float diameter,
            float radians,
            int color,
            float alpha) {
        float scale = diameter / TEXTURE_SIZE;
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().rotate(radians);
        graphics.pose().scale(scale, scale);
        int half = TEXTURE_SIZE / 2;
        graphics.blit(
                RenderPipelines.GUI_TEXTURED,
                texture,
                -half,
                -half,
                0,
                0,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                TEXTURE_SIZE,
                Math.round(alpha * 255) << 24 | color);
        graphics.pose().popMatrix();
    }

    private static void drawItem(GuiGraphics graphics, ItemStack stack, int x, int y, float size) {
        graphics.pose().pushMatrix();
        graphics.pose().translate(x, y);
        graphics.pose().scale(size, size);
        graphics.renderItem(stack, -8, -8);
        graphics.pose().popMatrix();
    }

    private static Identifier texture(String name) {
        return Identifier.fromNamespaceAndPath("wynnventory_unid", "textures/gui/" + name + ".png");
    }
}

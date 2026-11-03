package com.wynnventory.unid;

import com.wynntils.core.components.Models;
import com.wynntils.mc.event.InventoryKeyPressEvent;
import com.wynntils.models.containers.containers.reward.RewardContainer;
import com.wynntils.utils.mc.McUtils;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.gui.screens.inventory.AbstractContainerScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.entity.player.Inventory;
import net.neoforged.bus.api.SubscribeEvent;

/** Swallows the first two inventory-key presses on a reward chest that still holds a highlighted item. */
public class ChestCloseGuard {
    private static final int BLOCKED_PRESSES = 2;

    private Screen guardedScreen;
    private int blockedPresses;

    @SubscribeEvent
    public void onInventoryKeyPress(InventoryKeyPressEvent event) {
        if (!UnidSettings.get().isHighlightItems()) return;
        if (!McUtils.options().keyInventory.matches(event.getKeyEvent())) return;
        if (!(Models.Container.getCurrentContainer() instanceof RewardContainer)) return;
        if (!(McUtils.mc().screen instanceof AbstractContainerScreen<?> screen) || !holdsHighlightedItem(screen))
            return;

        // Every chest opens a new screen, so the count starts over per chest.
        if (screen != guardedScreen) {
            guardedScreen = screen;
            blockedPresses = 0;
        }
        if (blockedPresses >= BLOCKED_PRESSES) return;

        blockedPresses++;
        event.setCanceled(true);
        McUtils.playSoundUI(SoundEvents.NOTE_BLOCK_BASS.value());
        int pressesLeft = BLOCKED_PRESSES - blockedPresses + 1;
        McUtils.mc()
                .gui
                .setOverlayMessage(
                        Component.literal("Highlighted items left: press E " + pressesLeft + " more time"
                                + (pressesLeft == 1 ? "" : "s") + " to close"),
                        false);
    }

    private static boolean holdsHighlightedItem(AbstractContainerScreen<?> screen) {
        // Only the chest's own slots count; highlighted items in the player's inventory leave with them.
        return screen.getMenu().slots.stream()
                .anyMatch(slot ->
                        !(slot.container instanceof Inventory) && UnidHighlightFeature.isHighlighted(slot.getItem()));
    }
}

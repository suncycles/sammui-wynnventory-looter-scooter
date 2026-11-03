package com.wynnventory.unid.mixin;

import com.wynntils.core.WynntilsMod;
import com.wynnventory.unid.BlacklistFeature;
import com.wynnventory.unid.ChestCloseGuard;
import com.wynnventory.unid.UnidHighlightFeature;
import java.io.File;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(WynntilsMod.class)
public abstract class WynntilsModMixin {
    private WynntilsModMixin() {}

    // Wynntils creates its event bus in init, so listeners can only be registered after it returns.
    @Inject(method = "init", at = @At("RETURN"))
    private static void registerUnidHighlight(
            WynntilsMod.ModLoader loader,
            String modVersion,
            boolean isDevelopmentEnvironment,
            File modFile,
            CallbackInfo ci) {
        WynntilsMod.registerEventListener(new UnidHighlightFeature());
        WynntilsMod.registerEventListener(new ChestCloseGuard());
        WynntilsMod.registerEventListener(new BlacklistFeature());
    }
}

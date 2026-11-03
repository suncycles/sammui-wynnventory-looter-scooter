package com.wynnventory.unid.mixin;

import com.wynntils.models.items.items.game.GearBoxItem;
import com.wynnventory.feature.tooltip.price.PriceTooltipFactory;
import com.wynnventory.unid.GearBoxValue;
import com.wynnventory.util.ItemStackUtils;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(PriceTooltipFactory.class)
public abstract class PriceTooltipFactoryMixin {
    @Inject(method = "getPriceTooltip", at = @At("RETURN"), cancellable = true)
    private void addBoxValue(ItemStack stack, CallbackInfoReturnable<List<Component>> cir) {
        if (cir.getReturnValue().isEmpty() || !(ItemStackUtils.getWynnItem(stack) instanceof GearBoxItem box)) return;

        // Copied in case WynnVentory hands back an immutable list; the lines go right under the title.
        List<Component> lines = new ArrayList<>(cir.getReturnValue());
        lines.addAll(1, GearBoxValue.of(box).tooltipLines());
        cir.setReturnValue(lines);
    }
}

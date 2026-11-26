package dev.schematicstock.mixin;

import com.llamalad7.mixinextras.sugar.Local;
import dev.schematicstock.refill.RefillClient;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fi.dy.masa.litematica.util.EasyPlaceUtils")
abstract class EasyPlaceRewriteMixin {
    @Inject(method = "handleEasyPlace()Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE", target =
        "Lfi/dy/masa/litematica/util/InventoryUtils;schematicWorldPickBlock(Lnet/minecraft/world/item/ItemStack;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/Level;Lnet/minecraft/client/Minecraft;)V"),
        cancellable = true)
    private static void tomsmatic$validateRetry(CallbackInfoReturnable<InteractionResult> result,
                                               @Local(index = 7) BlockPos pos, @Local(index = 11) ItemStack item) {
        if (RefillClient.rejectRetryTarget(pos, item)) result.setReturnValue(InteractionResult.CONSUME);
    }

    @Inject(method = "handleEasyPlace()Lnet/minecraft/world/InteractionResult;", at = @At(value = "INVOKE", target =
        "Lfi/dy/masa/litematica/util/EntityUtils;getUsedHandForItem(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)Lnet/minecraft/world/InteractionHand;"),
        cancellable = true)
    private static void tomsmatic$requestMaterial(CallbackInfoReturnable<InteractionResult> result,
                                                @Local(index = 7) BlockPos pos, @Local(index = 11) ItemStack item) {
        if (RefillClient.handleMissing(Minecraft.getInstance(), pos, item)) result.setReturnValue(InteractionResult.CONSUME);
    }
}

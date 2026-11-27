package dev.schematicstock.mixin;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import dev.schematicstock.ClientScreens;
import dev.schematicstock.SchematicStock;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.network.chat.Component;
import net.minecraft.world.inventory.MenuType;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(ClientPacketListener.class)
abstract class TerminalOpenScreenMixin {
    @WrapOperation(method = "handleOpenScreen", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/client/gui/screens/MenuScreens;create(Lnet/minecraft/world/inventory/MenuType;Lnet/minecraft/client/Minecraft;ILnet/minecraft/network/chat/Component;)V"))
    private void tomsmatic$restoreMaterialsBeforeRender(MenuType<?> type, Minecraft client, int containerId,
                                                       Component title, Operation<Void> original) {
        var previousScreen = ClientScreens.current(client);
        // Initialize the terminal normally, then restore the report in the same client task.
        original.call(type, client, containerId, title);
        SchematicStock.keepMaterialsVisible(client, previousScreen);
    }
}

package dev.schematicstock.mixin;

import com.tom.storagemod.block.AbstractStorageTerminalBlock;
import com.tom.storagemod.block.entity.StorageTerminalBlockEntity;
import dev.schematicstock.refill.RefillServer;
import java.util.OptionalInt;
import net.minecraft.world.MenuProvider;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(AbstractStorageTerminalBlock.class)
abstract class WirelessOpenMixin {
    @Redirect(method = "useWithoutItem", at = @At(value = "INVOKE", target =
        "Lnet/minecraft/world/entity/player/Player;openMenu(Lnet/minecraft/world/MenuProvider;)Ljava/util/OptionalInt;"))
    private OptionalInt tomsmatic$captureAuthorizedTerminal(Player player, MenuProvider provider) {
        if (provider instanceof StorageTerminalBlockEntity storage && RefillServer.captureTerminal(player, storage)) {
            return OptionalInt.empty();
        }
        return player.openMenu(provider);
    }
}

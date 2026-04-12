package com.mikarific.eidtracker.mixins;

import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import com.mikarific.eidtracker.context.EIDTrackerContext;
import net.minecraft.network.protocol.game.ServerboundPlayerActionPacket;
import net.minecraft.server.network.ServerGamePacketListenerImpl;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerGamePacketListenerImpl.class)
public class ServerGamePacketListenerImplMixin {
    @WrapOperation(
            method = "handlePlayerAction",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket;getAction()Lnet/minecraft/network/protocol/game/ServerboundPlayerActionPacket$Action;")
    )
    private ServerboundPlayerActionPacket.Action handlePlayerAction(ServerboundPlayerActionPacket instance, Operation<ServerboundPlayerActionPacket.Action> original) {
        EIDTrackerContext.push();

        return original.call(instance);
    }

    @Inject(method = "handlePlayerAction", at = @At("RETURN"))
    private void handlePlayerAction(ServerboundPlayerActionPacket serverboundPlayerActionPacket, CallbackInfo ci) {
        EIDTrackerContext.pop();
    }
}

package com.mikarific.eidtracker.client.carpet;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

public class CarpetHandler {
    public static boolean entityIdLoggerExists = false;

    public static void register() {
        PayloadTypeRegistry.clientboundPlay().register(CarpetPayload.TYPE, CarpetPayload.STREAM_CODEC);
        ClientPlayNetworking.registerGlobalReceiver(CarpetPayload.TYPE, (payload, context) -> {
            context.responseSender().sendPacket(new ServerboundCommandSuggestionPacket(Integer.MIN_VALUE, "/log "));
        });
    }
}

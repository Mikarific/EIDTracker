package com.mikarific.eidtracker.networking;

import com.mikarific.eidtracker.EIDTracker;
import com.mikarific.eidtracker.networking.packets.EIDPayload;
import com.mikarific.eidtracker.networking.packets.HelloPayload;
import com.mikarific.eidtracker.networking.packets.SubscribePayload;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.fabricmc.loader.api.FabricLoader;
import net.fabricmc.loader.api.ModContainer;

import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class NetworkingHandler {
    public static final Set<UUID> clients = ConcurrentHashMap.newKeySet();

    public static void init() {
        PayloadTypeRegistry.serverboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(SubscribePayload.TYPE, SubscribePayload.CODEC);
        PayloadTypeRegistry.serverboundPlay().register(EIDPayload.TYPE, EIDPayload.CODEC);

        PayloadTypeRegistry.clientboundPlay().register(HelloPayload.TYPE, HelloPayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(SubscribePayload.TYPE, SubscribePayload.CODEC);
        PayloadTypeRegistry.clientboundPlay().register(EIDPayload.TYPE, EIDPayload.CODEC);

        Optional<ModContainer> optionalMod = FabricLoader.getInstance().getModContainer(EIDTracker.MOD_ID);
        ModContainer mod = optionalMod.orElse(null);

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            if (mod == null) return;
            if (!server.isDedicatedServer()) {
                clients.add(handler.player.getUUID());
                return;
            }
            sender.sendPacket(new HelloPayload(mod.getMetadata().getVersion().getFriendlyString()));
        });

        ServerPlayNetworking.registerGlobalReceiver(HelloPayload.TYPE, (payload, context) -> {
            clients.add(context.player().getUUID());
        });

        ServerPlayConnectionEvents.DISCONNECT.register((handler, server) -> {
            clients.remove(handler.player.getUUID());
        });
    }

    public static boolean hasClientMod(UUID uuid) {
        return clients.contains(uuid);
    }
}

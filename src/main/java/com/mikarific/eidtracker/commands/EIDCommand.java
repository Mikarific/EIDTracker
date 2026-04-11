package com.mikarific.eidtracker.commands;

import com.mikarific.eidtracker.interfaces.ITrackedEntity;
import com.mikarific.eidtracker.mixins.*;
import com.mikarific.eidtracker.networking.NetworkingHandler;
import com.mikarific.eidtracker.networking.packets.EIDPayload;
import com.mikarific.eidtracker.networking.packets.SubscribePayload;
import com.mikarific.eidtracker.tracker.TrackerManager;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.fabricmc.fabric.api.networking.v1.PlayerLookup;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandBuildContext;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.protocol.game.ClientboundAddEntityPacket;
import net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityDataPacket;
import net.minecraft.network.protocol.game.ClientboundTeleportEntityPacket;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.permissions.Permissions;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import net.minecraft.world.level.gamerules.GameRules;
import org.jspecify.annotations.NonNull;

import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class EIDCommand {
    public static final Set<UUID> subscribers = ConcurrentHashMap.newKeySet();

    private static void sendResponse(CommandSourceStack sourceStack, Component translatable, Component fallback) {
        sendResponse(sourceStack, translatable, fallback, true);
    }

    private static void sendResponse(CommandSourceStack sourceStack, Component translatable, Component fallback, boolean broadcastToAdmins) {
        CommandSource source = ((CommandSourceStackAccessor) sourceStack).getSource();

        if (source.acceptsSuccess() && !sourceStack.isSilent()) {
            boolean hasClientMod = sourceStack.isPlayer() && NetworkingHandler.hasClientMod(Objects.requireNonNull(sourceStack.getPlayer()).getUUID());
            source.sendSystemMessage(hasClientMod ? translatable : fallback);
        }
        if (broadcastToAdmins && source.shouldInformAdmins() && !sourceStack.isSilent()) {
            translatable = Component.translatable("chat.type.admin", sourceStack.getDisplayName(), translatable).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            fallback = Component.translatable("chat.type.admin", sourceStack.getDisplayName(), fallback).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC);
            GameRules gameRules = sourceStack.getLevel().getGameRules();
            if (gameRules.get(GameRules.SEND_COMMAND_FEEDBACK)) {
                for (ServerPlayer serverPlayer : sourceStack.getServer().getPlayerList().getPlayers()) {
                    if (serverPlayer.commandSource() != source && sourceStack.getServer().getPlayerList().isOp(serverPlayer.nameAndId())) {
                        boolean hasClientMod = NetworkingHandler.hasClientMod(serverPlayer.getUUID());
                        serverPlayer.sendSystemMessage(hasClientMod ? translatable : fallback);
                    }
                }
            }

            if (source != sourceStack.getServer() && gameRules.get(GameRules.LOG_ADMIN_COMMANDS)) {
                sourceStack.getServer().sendSystemMessage(translatable);
            }
        }
    }

    private static void sendError(CommandSourceStack sourceStack, Component translatable, Component fallback) {
        CommandSource source = ((CommandSourceStackAccessor) sourceStack).getSource();
        if (source.acceptsFailure() && !sourceStack.isSilent()) {
            boolean hasClientMod = sourceStack.isPlayer() && NetworkingHandler.hasClientMod(Objects.requireNonNull(sourceStack.getPlayer()).getUUID());
            source.sendSystemMessage(hasClientMod ? Component.empty().append(translatable).withStyle(ChatFormatting.RED) : Component.empty().append(fallback).withStyle(ChatFormatting.RED));
        }
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher, CommandBuildContext registryAccess, Commands.CommandSelection environment) {
        dispatcher.register(Commands.literal("eid")
                .requires(Commands.hasPermission(Commands.LEVEL_GAMEMASTERS))
                .then(Commands.literal("get")
                        .executes(context -> get(context.getSource()))
                )
                .then(Commands.literal("set")
                        .then(Commands.argument("value", IntegerArgumentType.integer(Integer.MIN_VALUE, Integer.MAX_VALUE))
                                .executes(context -> set(context.getSource(), IntegerArgumentType.getInteger(context, "value")))
                        )
                )
                .then(Commands.literal("fetch")
                        .executes(context -> fetch(context.getSource(), context.getSource().getEntityOrException()))
                        .then(Commands.argument("entity", EntityArgument.entities())
                                .executes(context -> fetch(context.getSource(), EntityArgument.getEntity(context, "entity")))
                        )
                )
                .then(Commands.literal("modify")
                        .executes(context -> modify(context.getSource(), context.getSource().getEntityOrException(),    
                                                                                            IntegerArgumentType.getInteger(context, "value")))
                        .then(Commands.argument("entity", EntityArgument.entities())
                                .then(Commands.argument("value", IntegerArgumentType.integer(Integer.MIN_VALUE, Integer.MAX_VALUE))
                                        .executes(context -> modify(context.getSource(), EntityArgument.getEntity(context, "entity"),
                                                IntegerArgumentType.getInteger(context, "value")))
                                )
                        )
                )
                .then(Commands.literal("increment")
                        .executes(context -> increment(context.getSource(), 1))
                        .then(Commands.argument("value", IntegerArgumentType.integer(Integer.MIN_VALUE, Integer.MAX_VALUE))
                                .executes(context -> increment(context.getSource(), IntegerArgumentType.getInteger(context, "value")))
                        )
                )
                .then(Commands.literal("decrement")
                        .executes(context -> decrement(context.getSource(), 1))
                        .then(Commands.argument("value", IntegerArgumentType.integer(Integer.MIN_VALUE, Integer.MAX_VALUE))
                                .executes(context -> decrement(context.getSource(), IntegerArgumentType.getInteger(context, "value")))
                        )
                )
                .then(Commands.literal("track")
                        .executes(context -> track(context.getSource()))
                        .then(Commands.literal("start")
                                .executes(context -> beginTrack(context.getSource()))
                        )
                        .then(Commands.literal("stop")
                                .executes(context -> endTrack(context.getSource()))
                        )
                )
        );
        if (environment.includeDedicated) {
            dispatcher.register(Commands.literal("eid")
                    .requires(source -> NetworkingHandler.clients.contains(Objects.requireNonNull(source.getPlayer()).getUUID()) && source.permissions().hasPermission(Permissions.COMMANDS_GAMEMASTER))
                    .then(Commands.literal("subscribe")
                            .executes(context -> subscribe(context.getSource()))
                    )
            );
        }
    }

    private static int get(CommandSourceStack source) {
        MutableComponent displayId = Component.literal(String.valueOf(EntityAccessor.getCurrentId().get())).withStyle(ChatFormatting.YELLOW);
        MutableComponent translatable = Component.translatable("commands.eid.get", displayId);
        MutableComponent fallback = Component.literal("Current Entity ID: ").append(displayId);
        sendResponse(source, translatable, fallback, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int fetch(CommandSourceStack source, @NonNull Entity entity) {
        MutableComponent displayEntityName = Component.literal(entity.getPlainTextName()).withStyle(ChatFormatting.AQUA);
        MutableComponent displayId = Component.literal(String.valueOf(entity.getId())).withStyle(ChatFormatting.YELLOW);
        MutableComponent translatable = Component.translatable("commands.eid.fetch", displayEntityName, displayId);
        MutableComponent fallback = Component.literal("The Entity ID of ").append(displayEntityName).append(" is ")
                .append(displayId).append(".");
        sendResponse(source, translatable, fallback);

        return Command.SINGLE_SUCCESS;
    }

    private static int set(CommandSourceStack source, int value) {
        EntityAccessor.getCurrentId().set(value);

        MutableComponent displayValue = Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
        MutableComponent translatable = Component.translatable("commands.eid.set", displayValue);
        MutableComponent fallback = Component.literal("Set the Current Entity ID to ").append(displayValue).append(".");
        sendResponse(source, translatable, fallback);

        return Command.SINGLE_SUCCESS;
    }

    private static int modify(CommandSourceStack source, @NonNull Entity entity, int value) {
        ServerLevel level = source.getLevel();
        int oldId = entity.getId();
        forceUpdateEntityId(level, entity, oldId, value);
        updateNetworkTrackerId(level, oldId, value);
        sendSyncPackets(level, entity, oldId, value);

        if (entity.getId() != value) {
            MutableComponent displayEntityName = Component.literal(entity.getPlainTextName()).withStyle(ChatFormatting.UNDERLINE);
            MutableComponent translatable = Component.translatable("commands.eid.modify.fail", displayEntityName)
                    .withStyle(ChatFormatting.RED).withStyle(ChatFormatting.BOLD);
            MutableComponent fallback = Component.literal("Failed to modify the Entity ID of ").append(displayEntityName).append(".");
            sendError(source, translatable, fallback);

            return 0;
        }

        MutableComponent displayEntityName = Component.literal(entity.getPlainTextName()).withStyle(ChatFormatting.AQUA);
        MutableComponent displayValue = Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
        MutableComponent translatable = Component.translatable("commands.eid.modify", displayEntityName, displayValue);
        MutableComponent fallback = Component.literal("Set the Entity ID of ").append(displayEntityName)
                .append(" to ").append(displayValue).append(".");
        sendResponse(source, translatable, fallback);

        return Command.SINGLE_SUCCESS;
    }

    private static int increment(CommandSourceStack source, int value) {
        EntityAccessor.getCurrentId().set(EntityAccessor.getCurrentId().get() + value);

        MutableComponent displayValue = Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
        MutableComponent displayId = Component.literal(String.valueOf(EntityAccessor.getCurrentId().get())).withStyle(ChatFormatting.YELLOW);
        MutableComponent translatable = Component.translatable("commands.eid.increment", displayValue, displayId);
        MutableComponent fallback = Component.literal("Incremented the Current Entity ID by ").append(displayValue).append(". Current Entity ID: ").append(displayId).append(".");
        sendResponse(source, translatable, fallback);

        return Command.SINGLE_SUCCESS;
    }

    private static int decrement(CommandSourceStack source, int value) {
        EntityAccessor.getCurrentId().set(EntityAccessor.getCurrentId().get() - value);

        MutableComponent displayValue = Component.literal(String.valueOf(value)).withStyle(ChatFormatting.YELLOW);
        MutableComponent displayId = Component.literal(String.valueOf(EntityAccessor.getCurrentId().get())).withStyle(ChatFormatting.YELLOW);
        MutableComponent translatable = Component.translatable("commands.eid.decrement", displayValue, displayId);
        MutableComponent fallback = Component.literal("Decremented the Current Entity ID by ").append(displayValue).append(". Current Entity ID: ").append(displayId).append(".");
        sendResponse(source, translatable, fallback);

        return Command.SINGLE_SUCCESS;
    }

    private static int subscribe(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        UUID uuid = player.getUUID();

        if(!NetworkingHandler.hasClientMod(uuid)) {
            source.sendFailure(Component.literal("You don't have EIDTracker installed!"));
            return 0;
        }

        if (subscribers.contains(uuid)) {
            subscribers.remove(uuid);
            ServerPlayNetworking.send(player, new SubscribePayload(false));
        } else {
            subscribers.add(uuid);
            ServerPlayNetworking.send(player, new SubscribePayload(true));
            ServerPlayNetworking.send(player, new EIDPayload(EntityAccessor.getCurrentId().get()));
        }

        boolean subscribed = subscribers.contains(uuid);
        MutableComponent translatable = Component.translatable("commands.eid.subscribe" + (subscribed ? ".true" : ".false"), player.getDisplayName()).withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC);
        MutableComponent fallback = player.getDisplayName().plainCopy().append(Component.literal((subscribed ? " subscribed to " : " unsubscribed from ") + "the Current Entity ID.")).withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC);
        sendResponse(source, translatable, fallback);

        return Command.SINGLE_SUCCESS;
    }

    private static int beginTrack(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();

        if (TrackerManager.isTracking(player)) {
            MutableComponent translatable = Component.translatable("commands.eid.track.start.fail");
            MutableComponent fallback = Component.literal("You're already tracking the Current Entity ID!");
            sendError(source, translatable, fallback);
            return 0;
        }

        TrackerManager.addTracker(player);

        MutableComponent translatable = Component.translatable("commands.eid.track.start").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC);
        MutableComponent fallback = Component.literal("Tracking the Current Entity ID...").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC);
        sendResponse(source, translatable, fallback, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int endTrack(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int tickCount = source.getServer().getTickCount();

        if (!TrackerManager.isTracking(player)) {
            MutableComponent translatable = Component.translatable("commands.eid.track.stop.fail");
            MutableComponent fallback = Component.literal("You're not tracking the Current Entity ID!");
            sendError(source, translatable, fallback);
            return 0;
        }

        TrackerManager.removeTracker(player, tickCount);

        MutableComponent translatable = Component.translatable("commands.eid.track.stop").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC);
        MutableComponent fallback = Component.literal("No longer tracking the Current Entity ID.").withStyle(ChatFormatting.GRAY).withStyle(ChatFormatting.ITALIC);
        sendResponse(source, translatable, fallback, false);

        return Command.SINGLE_SUCCESS;
    }

    private static int track(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        int tickCount = source.getServer().getTickCount();

        if (!TrackerManager.isTracking(player)) return beginTrack(source);

        TrackerManager.sendRate(player, tickCount);

        return Command.SINGLE_SUCCESS;
    }

    private static void forceUpdateEntityId(ServerLevel level, Entity entity, int oldId, int newId) {
        PersistentEntitySectionManager<Entity> manager = ((ServerWorldAccessor) level).getEntityManager();
        EntityLookup visibleEntityStorage = ((PersistentEntitySectionManagerAccessor<Entity>) manager).getVisibleEntityStorage();
        Int2ObjectMap<Entity> idMap = ((EntityLookupAccessor<Entity>) visibleEntityStorage).getById();

        idMap.remove(oldId);

        ((EntityAccessor) entity).setEntityId(newId);

        idMap.put(newId, entity);
    }

    private static void updateNetworkTrackerId(ServerLevel level, int oldId, int newId) {
        ChunkMap chunkMap = level.getChunkSource().chunkMap;
        Int2ObjectMap<ITrackedEntity> entityMap = ((ChunkMapAccessor) chunkMap).getEntityMap();

        ITrackedEntity tracker = entityMap.remove(oldId);
        if (tracker != null) {
            entityMap.put(newId, tracker);
        }
    }

    private static void sendSyncPackets(ServerLevel level, Entity entity, int oldId, int newId) {
        ClientboundRemoveEntitiesPacket removeEntityPacket = new ClientboundRemoveEntitiesPacket(oldId);
        ClientboundAddEntityPacket spawnEntityPacket = new ClientboundAddEntityPacket(
                newId,
                entity.getUUID(),
                entity.getX(), entity.getY(), entity.getZ(),
                entity.getXRot(), entity.getYRot(),
                entity.getType(), 0,
                entity.getDeltaMovement(),
                entity.getYHeadRot()
        );
        ClientboundTeleportEntityPacket teleportEntityPacket = ClientboundTeleportEntityPacket.teleport(
                newId,
                new PositionMoveRotation(
                        entity.position(),
                        entity.getDeltaMovement(),
                        entity.getYRot(),
                        entity.getXRot()
                ),
                Collections.emptySet(),
                entity.onGround()
        );

        for (ServerPlayer player : PlayerLookup.tracking(entity)) {
            player.connection.send(removeEntityPacket);
            player.connection.send(spawnEntityPacket);
            player.connection.send(teleportEntityPacket);

            var metadata = entity.getEntityData().getNonDefaultValues();
            if (metadata != null && !metadata.isEmpty()) {
                player.connection.send(new ClientboundSetEntityDataPacket(newId, metadata));
            }
        }
    }
}

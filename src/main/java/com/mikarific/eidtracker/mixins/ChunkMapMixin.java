package com.mikarific.eidtracker.mixins;

import com.mikarific.eidtracker.context.EIDTrackerContext;
import com.mikarific.eidtracker.interfaces.ITrackedEntity;
import com.mojang.logging.LogUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ChunkMap;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.entity.EntityLookup;
import net.minecraft.world.level.entity.PersistentEntitySectionManager;
import org.slf4j.Logger;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.concurrent.ThreadLocalRandom;

@Mixin(ChunkMap.class)
public abstract class ChunkMapMixin {
    @Unique
    private static final Logger LOGGER = LogUtils.getLogger();

    @Final
    @Shadow
    private Int2ObjectMap<ITrackedEntity> entityMap;

    @Final
    @Shadow
    ServerLevel level;

    @Inject(
            method = "addEntity(Lnet/minecraft/world/entity/Entity;)V",
            at = @At(value = "HEAD", target = "addEntity")
    )
    private void handleGracefully(Entity entity, CallbackInfo ci) {
        if (EIDTrackerContext.isPlayerAction() || !EIDTrackerContext.fixNonPlayerTriggeredCollidingEntityIds) return;

        ChunkMap self = (ChunkMap) (Object) this;
        int currentId = entity.getId();

        if (!entityMap.containsKey(currentId)) return;

        int newId = findFreeId(level.getServer(), entity);
        PersistentEntitySectionManager<Entity> entityManager = ((ServerWorldAccessor) level).getEntityManager();
        EntityLookup lookup = ((PersistentEntitySectionManagerAccessor) entityManager).getVisibleEntityStorage();

        lookup.remove(entity);
        ((EntityAccessor) entity).setEntityId(newId);
        lookup.add(entity);

        EntityAccessor.getCurrentId().set(newId);

        LOGGER.warn("Non-player triggered ID collision! Re-indexed {} in {} from {} to {}",
                entity.getType().getDescriptionId(),
                level.dimension().registry().toString(),
                currentId,
                newId
        );
    }

    @Unique
    private int findFreeId(MinecraftServer server, Entity entity) {
        int hint = EntityAccessor.getCurrentId().get();

        for (int i = 1; i <= 50; i++) {
            int probe = hint + (int) Math.pow(i, 3);

            if (!entityMap.containsKey(probe)) {
                return probe;
            }
        }

        return hint * (int) Math.log(hint);
    }
}

package com.mikarific.eidtracker.mixins;

import com.mikarific.eidtracker.interfaces.ITrackedEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(targets = "net.minecraft.server.level.ChunkMap$TrackedEntity")
public abstract class TrackedEntityMixin implements ITrackedEntity {
    
}

package com.mikarific.eidtracker.mixins;

import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import net.minecraft.world.level.entity.EntityAccess;
import net.minecraft.world.level.entity.EntityLookup;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(EntityLookup.class)
public interface EntityLookupAccessor<T extends EntityAccess> {
    @Accessor("byId")
    Int2ObjectMap<T> getById();
}

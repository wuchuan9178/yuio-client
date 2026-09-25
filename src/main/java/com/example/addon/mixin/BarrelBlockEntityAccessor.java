package com.example.addon.mixin;

import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(BarrelBlockEntity.class)
public interface BarrelBlockEntityAccessor {
    @Invoker("getHeldStacks")
    DefaultedList<ItemStack> yuio$getHeldStacks();
}

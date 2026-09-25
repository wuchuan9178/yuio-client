package com.example.addon.mixin;

import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.util.collection.DefaultedList;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(ChestBlockEntity.class)
public interface ChestBlockEntityAccessor {
    @Invoker("getHeldStacks")
    DefaultedList<ItemStack> yuio$getHeldStacks();
}

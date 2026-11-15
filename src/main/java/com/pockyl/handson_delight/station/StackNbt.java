package com.pockyl.handson_delight.station;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtOps;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;

/**
 * Item stacks as NBT. Minecraft 26.1 dropped {@code ItemStack#saveOptional}/{@code parseOptional}: the codec is the only
 * way now, in the same NBT shape, so old saves keep loading.
 */
public final class StackNbt {
    private StackNbt() {
    }

    public static CompoundTag save(ItemStack stack, Level level) {
        if (stack.isEmpty()) {
            return new CompoundTag();
        }
        return ItemStack.CODEC
                .encodeStart(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), stack)
                .result()
                .filter(tag -> tag instanceof CompoundTag)
                .map(tag -> (CompoundTag) tag)
                .orElseGet(CompoundTag::new);
    }

    public static ItemStack load(CompoundTag tag, Level level) {
        if (tag == null || tag.isEmpty()) {
            return ItemStack.EMPTY;
        }
        return ItemStack.CODEC
                .parse(level.registryAccess().createSerializationContext(NbtOps.INSTANCE), tag)
                .result()
                .orElse(ItemStack.EMPTY);
    }
}

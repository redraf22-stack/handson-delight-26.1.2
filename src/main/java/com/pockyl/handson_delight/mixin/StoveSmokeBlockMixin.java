package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.util.RandomSource;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.StoveBlock;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * A lit stove's own puffs of smoke and flame: from the station's camera they hang over the pan and read as dirt on the food (the
 * scene draws its own steam and smoke there). While the local player works at a station near the stove, the stove keeps them.
 */
@Mixin(value = StoveBlock.class, remap = false)
abstract class StoveSmokeBlockMixin {
    @Inject(method = "animateTick", at = @At("HEAD"), cancellable = true)
    private void handson$quietWhileShown(BlockState state, Level level, BlockPos pos, RandomSource random, CallbackInfo callback) {
        if (HandsOnDelightClient.isShown(pos) || HandsOnDelightClient.stationNear(pos, 6.0)) {
            callback.cancel();
        }
    }
}

package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import vectorwing.farmersdelight.common.block.entity.StoveBlockEntity;

import com.pockyl.handson_delight.client.HandsOnDelightClient;

/**
 * Farmer's Delight puffs dark smoke over every item cooking on its stove; where the stove's scene shows the grill, its own steam and
 * smoke tell how each piece is doing, and the dark puffs read as dirt on the food. While somebody works at a station beside the
 * stove (a skillet on it, say), the scene is what they watch: the stove's own puffs are stopped as well, they were the big grey
 * column of smoke seen through the camera over the pan.
 */
@Mixin(value = StoveBlockEntity.class, remap = false)
abstract class StoveSmokeMixin {
    @Inject(method = "addSmokeParticles", at = @At("HEAD"), cancellable = true)
    private void handson$quietWhileShown(CallbackInfo callback) {
        BlockPos pos = ((StoveBlockEntity) (Object) this).getBlockPos();
        if (HandsOnDelightClient.isShown(pos) || HandsOnDelightClient.stationNear(pos, WORKING_NEAR)) {
            callback.cancel();
        }
    }

    /** How near a station being worked at the stove has to be for its smoke to stay out of the picture, in blocks. */
    private static final double WORKING_NEAR = 6.0;
}

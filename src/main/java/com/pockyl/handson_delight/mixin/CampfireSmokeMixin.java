package com.pockyl.handson_delight.mixin;

import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.CampfireBlock;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.diegesis.api.client.DiegesisClient;
import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.station.SkilletStation;

/**
 * A campfire's smoke rises straight through a skillet standing on it, and from the camera of the station the big puffs cover the
 * food: the signal fire's are large and white and hide the whole pan. While the local player works at a station near the fire, the
 * fire keeps its smoke to itself (everybody else sees it as before).
 */
@Mixin(CampfireBlock.class)
abstract class CampfireSmokeMixin {
    @Inject(method = "makeParticles", at = @At("HEAD"), cancellable = true)
    private static void handson$clearOverTheSkillet(Level level, BlockPos pos, boolean signal, boolean extraSmoke, CallbackInfo callback) {
        // A signal fire's puffs are large and white and cover the whole pan: while the local player works at any station near the
        // fire (the skillet on it, the pot beside it), the fire keeps them.
        if (HandsOnDelightClient.stationNear(pos, 12.0)) {
            callback.cancel();
        }
    }
}

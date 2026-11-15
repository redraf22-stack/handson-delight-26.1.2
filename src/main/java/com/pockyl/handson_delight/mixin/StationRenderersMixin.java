package com.pockyl.handson_delight.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderDispatcher;
import net.minecraft.client.renderer.blockentity.state.BlockEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import com.pockyl.handson_delight.client.HandsOnDelightClient;
import com.pockyl.handson_delight.client.SceneDebug;

/**
 * Where a scene shows a station, the scene draws what the block entity would draw (the food on the board, the skillet's
 * contents): the block entity's own renderer would draw it a second time in the same place. It is stopped at the
 * dispatcher, after the render state was built and right before it would be submitted to the frame.
 */
@Mixin(BlockEntityRenderDispatcher.class)
abstract class StationRenderersMixin {
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void handson$drawnByScene(BlockEntityRenderState state, PoseStack poseStack, SubmitNodeCollector collector,
            CameraRenderState camera, CallbackInfo callback) {
        if (state != null && state.blockPos != null && HandsOnDelightClient.isSceneDrawn(state.blockPos)) {
            SceneDebug.once("skip " + state.blockPos, () -> "station renderer at " + state.blockPos + " stopped: the scene draws it");
            callback.cancel();
            return;
        }
        if (state != null) {
            SceneDebug.once("keep", () -> "station renderer at " + state.blockPos + " NOT stopped: station "
                    + (state.blockPos == null ? "-" : HandsOnDelightClient.stationShown(state.blockPos) + "/"
                            + HandsOnDelightClient.isSceneDrawn(state.blockPos)));
        }
    }
}

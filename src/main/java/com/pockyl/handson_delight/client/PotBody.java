package com.pockyl.handson_delight.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.blockentity.BlockEntityRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import org.joml.Matrix4f;
import vectorwing.farmersdelight.common.block.entity.CookingPotBlockEntity;

import com.pockyl.diegesis.api.client.NodePainter;
import com.pockyl.diegesis.api.client.NodeView;
import com.pockyl.diegesis.api.client.StationView;

/**
 * The pot's body, when another mod draws it with a block entity renderer instead of the block's model (Vanilla Delights' dyed
 * pots). Drawn by the world, such a body comes after the scene and paints over the soup, which writes no depth: the soup was
 * gone. While the scene is drawn that renderer is stopped at the dispatcher ({@code StationRenderersMixin}) and called here,
 * with the scene's opaque parts, before the soup. Farmer's Delight gives the pot no renderer: then nothing is drawn here.
 */
final class PotBody implements NodePainter {
    private final StationView view;
    private final NodeView node;

    PotBody(StationView view, NodeView node) {
        this.view = view;
        this.node = node;
    }

    @Override
    public void paint(PoseStack poseStack, MultiBufferSource buffers, int light) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.level == null || !(minecraft.level.getBlockEntity(view.pos()) instanceof CookingPotBlockEntity pot)) {
            return;
        }
        BlockEntityRenderer<CookingPotBlockEntity, ?> renderer = minecraft.getBlockEntityRenderDispatcher().getRenderer(pot);
        if (renderer == null) {
            return;
        }
        poseStack.pushPose();
        // Back from the node's pixels to the block, as the world draws it: the node's place undone, pixels to blocks, and the
        // scene's facing turn undone, since the renderer turns the pot by the block's facing itself.
        poseStack.mulPose(new Matrix4f(node.sceneMatrix()).invert());
        poseStack.scale(16.0F, 16.0F, 16.0F);
        poseStack.translate(0.5F, 0.0F, 0.5F);
        poseStack.mulPose(Axis.YP.rotationDegrees(yaw(view)));
        poseStack.translate(-0.5F, 0.0F, -0.5F);
        // Minecraft 26.1 has no direct BlockEntityRenderer#render: the pot's own model is drawn here as block geometry.
        net.minecraft.world.level.block.state.BlockState state = pot.getBlockState();
        net.minecraft.client.renderer.block.dispatch.BlockStateModel model =
                minecraft.getModelManager().getBlockStateModelSet().get(state);
        java.util.List<net.minecraft.client.renderer.block.dispatch.BlockStateModelPart> parts = new java.util.ArrayList<>();
        model.collectParts(net.minecraft.util.RandomSource.create(0L), parts);
        com.mojang.blaze3d.vertex.VertexConsumer consumer =
                buffers.getBuffer(net.minecraft.client.renderer.Sheets.cutoutBlockSheet());
        com.mojang.blaze3d.vertex.PoseStack.Pose pose = poseStack.last();
        com.mojang.blaze3d.vertex.QuadInstance instance = new com.mojang.blaze3d.vertex.QuadInstance();
        instance.setColor(0xFFFFFFFF);
        instance.setLightCoords(light);
        instance.setOverlayCoords(OverlayTexture.NO_OVERLAY);
        for (net.minecraft.client.renderer.block.dispatch.BlockStateModelPart part : parts) {
            for (net.minecraft.core.Direction direction : net.minecraft.core.Direction.values()) {
                for (net.minecraft.client.resources.model.geometry.BakedQuad quad : part.getQuads(direction)) {
                    consumer.putBakedQuad(pose, quad, instance);
                }
            }
            for (net.minecraft.client.resources.model.geometry.BakedQuad quad : part.getQuads(null)) {
                consumer.putBakedQuad(pose, quad, instance);
            }
        }
        poseStack.popPose();
    }

    /** The turn of a scene facing the station's way, as Diegesis puts it into the scene. */
    private static float yaw(StationView view) {
        return switch (view.facing()) {
            case EAST -> 90.0F;
            case SOUTH -> 180.0F;
            case WEST -> 270.0F;
            default -> 0.0F;
        };
    }
}

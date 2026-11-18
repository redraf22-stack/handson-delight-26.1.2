package com.pockyl.diegesis.impl.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.TreeMap;

/**
 * A frame of a scene's geometry, kept until the game draws it. Since 26.1 the level is built from render states and drawn from
 * what was submitted while they were: nothing may be drawn into the world directly (a batch drawn then is drawn before the
 * level's own, and never seen). So what the scene draws into this is kept, and handed to the frame's
 * {@link SubmitNodeCollector}, which draws it in the frame's batches, with the level's matrices - the way entities are drawn.
 *
 * <p>Vertices are kept in plain arrays, per render type, in the order they were written; nothing is allocated while a scene
 * draws them.
 */
public final class SceneSubmit implements MultiBufferSource {
    /** A vertex: x, y, z, u, v, normal x, y, z. */
    private static final int FLOATS = 8;
    /** The same vertex: colour, overlay, light. */
    private static final int INTS = 3;
    /**
     * The pose stack the geometry is submitted with. Everything in it is already where it is drawn (the level's own poses in
     * 26.1 are empty: what is drawn is placed relative to the camera), so it adds nothing.
     */
    private static final PoseStack EMPTY = new PoseStack();

    /**
     * What is being drawn, in the order it must be laid over the frame: batched by render type alone, a page turning over another
     * one comes out in pieces — all the paper first, then every well and every icon of both sheets, so the one underneath showed
     * through the one on top. A layer is one sheet: its own things, drawn before the next layer's.
     */
    private final Map<Integer, Map<RenderType, Vertices>> batches = new TreeMap<>();
    private static int layer;

    /** Draw what follows on {@code layer} (0 by default, sheets over it). */
    public static void layer(int on) {
        layer = on;
    }

    /** Empties it for a new frame. */
    public void clear() {
        batches.clear();
        layer = 0;
    }

    @Override
    public VertexConsumer getBuffer(RenderType type) {
        return batches.computeIfAbsent(layer, l -> new LinkedHashMap<>())
                .computeIfAbsent(type, t -> new Vertices()).consumer;
    }

    /**
     * Hands everything drawn this frame to the frame's batches, to be drawn at {@code order} (earlier orders first: opaque
     * things before see-through ones, the scene before what lies over it; a layer over the one before it).
     */
    public void submit(SubmitNodeCollector collector, int order) {
        if (batches.isEmpty()) {
            return;
        }
        int total = 0;
        StringBuilder types = new StringBuilder();
        for (Map.Entry<Integer, Map<RenderType, Vertices>> layer : batches.entrySet()) {
            for (Map.Entry<RenderType, Vertices> entry : layer.getValue().entrySet()) {
                Vertices vertices = entry.getValue();
                vertices.endVertex();
                if (vertices.count == 0) {
                    continue;
                }
                total += vertices.count * 4;
                types.append(' ').append(entry.getKey()).append('=').append(vertices.count);
                int at = order + layer.getKey();
                collector.order(at).submitCustomGeometry(EMPTY, entry.getKey(), (pose, consumer) -> vertices.draw(consumer));
            }
        }
        layer = 0;
        if (DIAG && total > 0) {
            long now = System.nanoTime();
            if (now - lastDiag > 5_000_000_000L) {
                lastDiag = now;
                com.pockyl.diegesis.DiegesisMod.LOGGER.warn("DIAG submit order {} verts {}:{}", order, total, types);
            }
        }
    }

    /** Whether what is submitted is logged ({@code -Ddiegesis.diag} while porting). */
    private static final boolean DIAG = Boolean.getBoolean("diegesis.diag");
    private static long lastDiag;

    /** The vertices of one render type, and the consumer that writes them. */
    private static final class Vertices {
        private final Recorder consumer = new Recorder(this);
        private float[] floats = new float[FLOATS * 32];
        private int[] ints = new int[INTS * 32];
        /** Vertices finished: each is {@code FLOATS} floats and {@code INTS} ints. */
        private int count;
        private float x;
        private float y;
        private float z;
        private float u;
        private float v;
        private float normalX;
        private float normalY = 1.0F;
        private float normalZ;
        private int color = -1;
        private int overlay;
        private int light;
        private boolean building;

        /** Starts a vertex, storing the one before it. */
        void beginVertex(float x, float y, float z) {
            endVertex();
            this.x = x;
            this.y = y;
            this.z = z;
            this.u = 0.0F;
            this.v = 0.0F;
            this.normalX = 0.0F;
            this.normalY = 1.0F;
            this.normalZ = 0.0F;
            this.color = -1;
            this.overlay = 0;
            this.light = 0;
            this.building = true;
        }

        /** Stores the vertex being built, if one is. */
        void endVertex() {
            if (!building) {
                return;
            }
            building = false;
            if (count + 1 > floats.length / FLOATS) {
                float[] wider = new float[floats.length * 2];
                System.arraycopy(floats, 0, wider, 0, count * FLOATS);
                floats = wider;
                int[] more = new int[ints.length * 2];
                System.arraycopy(ints, 0, more, 0, count * INTS);
                ints = more;
            }
            int at = count * FLOATS;
            floats[at] = x;
            floats[at + 1] = y;
            floats[at + 2] = z;
            floats[at + 3] = u;
            floats[at + 4] = v;
            floats[at + 5] = normalX;
            floats[at + 6] = normalY;
            floats[at + 7] = normalZ;
            ints[count * INTS] = color;
            ints[count * INTS + 1] = overlay;
            ints[count * INTS + 2] = light;
            count++;
        }

        void setUv(float u, float v) {
            this.u = u;
            this.v = v;
        }

        void setNormal(float x, float y, float z) {
            this.normalX = x;
            this.normalY = y;
            this.normalZ = z;
        }

        void setColor(int argb) {
            this.color = argb;
        }

        void setUv1(int u, int v) {
            this.overlay = v << 16 | u & 0xFFFF;
        }

        void setUv2(int u, int v) {
            this.light = v << 16 | u & 0xFFFF;
        }

        /** Writes the kept vertices into the batch the frame draws them in. */
        void draw(VertexConsumer consumer) {
            for (int i = 0; i < count; i++) {
                int at = i * FLOATS;
                int attrs = i * INTS;
                consumer.addVertex(floats[at], floats[at + 1], floats[at + 2], ints[attrs], floats[at + 3], floats[at + 4],
                        ints[attrs + 1], ints[attrs + 2], floats[at + 5], floats[at + 6], floats[at + 7]);
            }
        }
    }

    /** Keeps a render type's vertices as they are drawn; the only consumer a scene ever writes into. */
    private static final class Recorder implements VertexConsumer {
        private final Vertices vertices;

        Recorder(Vertices vertices) {
            this.vertices = vertices;
        }

        @Override
        public VertexConsumer addVertex(float x, float y, float z) {
            vertices.beginVertex(x, y, z);
            return this;
        }

        @Override
        public void addVertex(float x, float y, float z, int color, float u, float v, int overlay, int light, float normalX,
                float normalY, float normalZ) {
            vertices.beginVertex(x, y, z);
            vertices.setColor(color);
            vertices.setUv(u, v);
            vertices.setUv1(overlay & 0xFFFF, overlay >>> 16);
            vertices.setUv2(light & 0xFFFF, light >>> 16);
            vertices.setNormal(normalX, normalY, normalZ);
            vertices.endVertex();
        }

        @Override
        public VertexConsumer setColor(int red, int green, int blue, int alpha) {
            vertices.setColor(alpha << 24 | red << 16 | green << 8 | blue);
            return this;
        }

        @Override
        public VertexConsumer setColor(int argb) {
            vertices.setColor(argb);
            return this;
        }

        @Override
        public VertexConsumer setUv(float u, float v) {
            vertices.setUv(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv1(int u, int v) {
            vertices.setUv1(u, v);
            return this;
        }

        @Override
        public VertexConsumer setUv2(int u, int v) {
            vertices.setUv2(u, v);
            return this;
        }

        @Override
        public VertexConsumer setNormal(float x, float y, float z) {
            vertices.setNormal(x, y, z);
            return this;
        }

        @Override
        public VertexConsumer setLineWidth(float width) {
            return this;
        }
    }
}

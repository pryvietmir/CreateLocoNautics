package com.lycoris.loconautics.content.knuckle;

import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;
import org.joml.Vector3f;

public final class WallClipVertexConsumer implements VertexConsumer {
    private static final float KEEP_EPSILON = 1.0E-4F;
    private final WallClipVertexConsumer.Vert[] quad = new WallClipVertexConsumer.Vert[]{
        new WallClipVertexConsumer.Vert(), new WallClipVertexConsumer.Vert(), new WallClipVertexConsumer.Vert(), new WallClipVertexConsumer.Vert()
    };
    private final WallClipVertexConsumer.Vert[] cuts = new WallClipVertexConsumer.Vert[]{
        new WallClipVertexConsumer.Vert(), new WallClipVertexConsumer.Vert(), new WallClipVertexConsumer.Vert(), new WallClipVertexConsumer.Vert()
    };
    private final WallClipVertexConsumer.Vert[] polygon = new WallClipVertexConsumer.Vert[8];
    private final Vector3f planeNormal = new Vector3f();
    private VertexConsumer target;
    private float planeOffset;
    private int filled;

    public void begin(VertexConsumer target, Matrix4f base, float pointX, float pointY, float pointZ, float outwardX, float outwardY, float outwardZ) {
        this.target = target;
        this.filled = 0;
        base.transformDirection(outwardX, outwardY, outwardZ, this.planeNormal).normalize();
        Vector3f point = base.transformPosition(pointX, pointY, pointZ, new Vector3f());
        this.planeOffset = -this.planeNormal.dot(point);
    }

    public VertexConsumer addVertex(float x, float y, float z) {
        WallClipVertexConsumer.Vert vert = this.quad[this.filled];
        vert.x = x;
        vert.y = y;
        vert.z = z;
        return this;
    }

    public VertexConsumer setColor(int r, int g, int b, int a) {
        WallClipVertexConsumer.Vert vert = this.quad[this.filled];
        vert.r = r;
        vert.g = g;
        vert.b = b;
        vert.a = a;
        return this;
    }

    public VertexConsumer setUv(float u, float v) {
        this.quad[this.filled].u = u;
        this.quad[this.filled].v = v;
        return this;
    }

    public VertexConsumer setUv1(int u, int v) {
        this.quad[this.filled].overlayU = u;
        this.quad[this.filled].overlayV = v;
        return this;
    }

    public VertexConsumer setUv2(int u, int v) {
        this.quad[this.filled].lightU = u;
        this.quad[this.filled].lightV = v;
        return this;
    }

    public VertexConsumer setNormal(float x, float y, float z) {
        WallClipVertexConsumer.Vert vert = this.quad[this.filled];
        vert.nx = x;
        vert.ny = y;
        vert.nz = z;
        if (++this.filled == this.quad.length) {
            this.filled = 0;
            this.clipQuad();
        }

        return this;
    }

    private void clipQuad() {
        int kept = 0;

        for (WallClipVertexConsumer.Vert vert : this.quad) {
            vert.side = this.planeNormal.x * vert.x + this.planeNormal.y * vert.y + this.planeNormal.z * vert.z + this.planeOffset;
            if (vert.side <= 1.0E-4F) {
                kept++;
            }
        }

        if (kept == this.quad.length) {
            this.emit(this.quad[0], this.quad[1], this.quad[2], this.quad[3]);
        } else if (kept != 0) {
            int count = 0;
            int cut = 0;

            for (int i = 0; i < this.quad.length; i++) {
                WallClipVertexConsumer.Vert from = this.quad[i];
                WallClipVertexConsumer.Vert to = this.quad[(i + 1) % this.quad.length];
                boolean fromKept = from.side <= 1.0E-4F;
                if (fromKept) {
                    this.polygon[count++] = from;
                }

                if (fromKept != to.side <= 1.0E-4F) {
                    WallClipVertexConsumer.Vert crossing = this.cuts[cut++];
                    crossing.lerp(from, to, Mth.clamp(from.side / (from.side - to.side), 0.0F, 1.0F));
                    this.polygon[count++] = crossing;
                }
            }

            for (int i = 1; i + 1 < count; i++) {
                this.emit(this.polygon[0], this.polygon[i], this.polygon[i + 1], this.polygon[i + 1]);
            }
        }
    }

    private void emit(
        WallClipVertexConsumer.Vert first, WallClipVertexConsumer.Vert second, WallClipVertexConsumer.Vert third, WallClipVertexConsumer.Vert fourth
    ) {
        this.emitCorner(first);
        this.emitCorner(second);
        this.emitCorner(third);
        this.emitCorner(fourth);
    }

    private void emitCorner(WallClipVertexConsumer.Vert vert) {
        this.target
            .addVertex(vert.x, vert.y, vert.z)
            .setColor(vert.r, vert.g, vert.b, vert.a)
            .setUv(vert.u, vert.v)
            .setUv1(vert.overlayU, vert.overlayV)
            .setUv2(vert.lightU, vert.lightV)
            .setNormal(vert.nx, vert.ny, vert.nz);
    }

    private static final class Vert {
        float x;
        float y;
        float z;
        float u;
        float v;
        float nx;
        float ny;
        float nz;
        float side;
        int r;
        int g;
        int b;
        int a;
        int overlayU;
        int overlayV;
        int lightU;
        int lightV;

        void lerp(WallClipVertexConsumer.Vert from, WallClipVertexConsumer.Vert to, float t) {
            this.x = Mth.lerp(t, from.x, to.x);
            this.y = Mth.lerp(t, from.y, to.y);
            this.z = Mth.lerp(t, from.z, to.z);
            this.u = Mth.lerp(t, from.u, to.u);
            this.v = Mth.lerp(t, from.v, to.v);
            this.nx = Mth.lerp(t, from.nx, to.nx);
            this.ny = Mth.lerp(t, from.ny, to.ny);
            this.nz = Mth.lerp(t, from.nz, to.nz);
            this.r = Math.round(Mth.lerp(t, from.r, to.r));
            this.g = Math.round(Mth.lerp(t, from.g, to.g));
            this.b = Math.round(Mth.lerp(t, from.b, to.b));
            this.a = Math.round(Mth.lerp(t, from.a, to.a));
            WallClipVertexConsumer.Vert lit = t < 0.5F ? from : to;
            this.overlayU = lit.overlayU;
            this.overlayV = lit.overlayV;
            this.lightU = lit.lightU;
            this.lightV = lit.lightV;
        }
    }
}

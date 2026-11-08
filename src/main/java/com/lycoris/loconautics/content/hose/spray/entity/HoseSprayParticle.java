package com.lycoris.loconautics.content.hose.spray.entity;

import com.lycoris.loconautics.content.hose.spray.HoseSprayOptions;
import com.lycoris.loconautics.foundation.particle.LateParticles;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;

public class HoseSprayParticle extends TextureSheetParticle {
    private static final double JET_SPEED = 0.18;
    private static final double SUCK_BACK_OFFSET = 0.3;
    private static final float SUCK_FADE_IN = 0.35F;
    private final SpriteSet sprites;
    private final boolean jet;
    private final boolean suck;
    private final double startX;
    private final double startY;
    private final double startZ;
    private final double endX;
    private final double endY;
    private final double endZ;
    private double ctrlX;
    private double ctrlY;
    private double ctrlZ;
    private float startSize;

    HoseSprayParticle(ClientLevel level, double x, double y, double z, double xd, double yd, double zd, SpriteSet sprites, int color, boolean suck) {
        super(level, x, y, z);
        this.sprites = sprites;
        LateParticles.add(this);
        this.xd = xd;
        this.yd = yd;
        this.zd = zd;
        double speed = Math.sqrt(xd * xd + yd * yd + zd * zd);
        this.jet = !suck && speed > 0.18;
        this.friction = suck ? 1.0F : (this.jet ? 0.9F : 0.82F);
        this.gravity = suck ? 0.0F : (this.jet ? 0.35F : 0.7F);
        this.hasPhysics = !suck;
        this.suck = suck;
        double back = suck ? 0.3 : 0.0;
        this.startX = x - xd * back;
        this.startY = y - yd * back;
        this.startZ = z - zd * back;
        this.endX = x + xd;
        this.endY = y + yd;
        this.endZ = z + zd;
        if (suck) {
            this.setPos(this.startX, this.startY, this.startZ);
            this.xo = this.startX;
            this.yo = this.startY;
            this.zo = this.startZ;
        }

        if (suck) {
            this.xd = 0.0;
            this.yd = 0.0;
            this.zd = 0.0;
            double distance = Math.sqrt(xd * xd + yd * yd + zd * zd);
            this.lifetime = 6 + (int)(distance * 3.0) + this.random.nextInt(3);
            this.quadSize = 0.07F + this.random.nextFloat() * 0.16F;
            this.startSize = this.quadSize;
            this.setSprite(sprites.get(0, 9));
            double bend = distance * (0.25 + this.random.nextDouble() * 0.35);
            double rx = this.random.nextDouble() - 0.5;
            double ry = this.random.nextDouble() - 0.5;
            double rz = this.random.nextDouble() - 0.5;
            double px = ry * zd - rz * yd;
            double py = rz * xd - rx * zd;
            double pz = rx * yd - ry * xd;
            double plen = Math.sqrt(px * px + py * py + pz * pz);
            double k = plen < 1.0E-6 ? 0.0 : bend / plen;
            this.ctrlX = this.startX + (this.endX - this.startX) * 0.5 + px * k;
            this.ctrlY = this.startY + (this.endY - this.startY) * 0.5 + py * k;
            this.ctrlZ = this.startZ + (this.endZ - this.startZ) * 0.5 + pz * k;
        } else if (this.jet) {
            this.lifetime = 14 + this.random.nextInt(10);
            this.quadSize = 0.24F + (float)Math.min(0.22, speed * 0.6);
            this.setSpriteFromAge(sprites);
        } else {
            this.lifetime = 6 + this.random.nextInt(8);
            this.quadSize = 0.08F + this.random.nextFloat() * 0.1F;
            this.setSprite(sprites.get(0, 9));
        }

        float baseR = (color >> 16 & 0xFF) / 255.0F;
        float baseG = (color >> 8 & 0xFF) / 255.0F;
        float baseB = (color & 0xFF) / 255.0F;
        float k = 0.75F + this.random.nextFloat() * 0.25F;
        float foam = this.random.nextFloat() * 0.3F;
        this.setColor(Math.min(1.0F, baseR * k + foam), Math.min(1.0F, baseG * k + foam), Math.min(1.0F, baseB * k + foam));
        this.setAlpha(suck ? 0.0F : 0.85F);
    }

    public void tick() {
        if (this.suck) {
            this.tickSuck();
        } else {
            super.tick();
        }

        if (this.jet && !this.removed) {
            this.setSpriteFromAge(this.sprites);
        }

        float t = (float)this.age / this.lifetime;
        if (this.suck && t < 0.35F) {
            this.setAlpha(0.85F * t / 0.35F);
        } else if (t > 0.66F) {
            this.setAlpha(0.85F * (1.0F - (t - 0.66F) / 0.34F));
        }
    }

    private void tickSuck() {
        this.xo = this.x;
        this.yo = this.y;
        this.zo = this.z;
        if (this.age++ >= this.lifetime) {
            this.remove();
        } else {
            double t = (double)this.age / this.lifetime;
            double u = t * t;
            this.quadSize = this.startSize * (1.0F - 0.8F * (float)t);
            double a = (1.0 - u) * (1.0 - u);
            double b = 2.0 * (1.0 - u) * u;
            double c = u * u;
            this.setPos(
                a * this.startX + b * this.ctrlX + c * this.endX,
                a * this.startY + b * this.ctrlY + c * this.endY,
                a * this.startZ + b * this.ctrlZ + c * this.endZ
            );
        }
    }

    public ParticleRenderType getRenderType() {
        return ParticleRenderType.NO_RENDER;
    }

    public static class Provider implements ParticleProvider<HoseSprayOptions> {
        private final SpriteSet sprites;

        public Provider(SpriteSet sprites) {
            this.sprites = sprites;
        }

        public Particle createParticle(HoseSprayOptions type, ClientLevel level, double x, double y, double z, double xd, double yd, double zd) {
            return new HoseSprayParticle(level, x, y, z, xd, yd, zd, this.sprites, type.color(), type.suck());
        }
    }
}

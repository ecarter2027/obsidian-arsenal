package com.obsidianarsenal.client;

import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.registry.FxList.FxStyle;
import net.minecraft.client.Camera;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.client.particle.ParticleEngine;
import net.minecraft.client.particle.ParticleProvider;
import net.minecraft.client.particle.ParticleRenderType;
import net.minecraft.client.particle.SpriteSet;
import net.minecraft.client.particle.TextureSheetParticle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * Crisp, full-bright sprite particle (v27; replaces the soft GlowParticle). One hard-edged pixel sprite, picked from
 * the type's variants at spawn and kept (no flickering blur), that stays fully opaque for most of its life and then cuts
 * out quickly. By style:
 *  STREAK - turned so its tip points along its velocity on screen (shards, needles, lances fly point first), slowing down
 *  FLASH  - pops in, spins a little, shrinks away (stars, glyphs, plates)
 *  BOLT   - a few ticks of jagged lightning, at a random angle
 *  FLAME  - upright, rising
 *  FIRE   - an animated flame (frames played over its life), upright, rising and swaying
 *  WISP   - an animated curl of smoke that swells, turns and fades
 *  DRIFT  - hangs in the air and sinks slowly (cracks, debris)
 */
public class SharpParticle extends TextureSheetParticle {
    private final FxStyle style;
    private final float baseSize;
    private final SpriteSet sprites;
    private final float sway;

    protected SharpParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz, SpriteSet sprites, FxStyle style) {
        super(level, x, y, z);
        this.style = style;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.hasPhysics = false;
        this.rCol = this.gCol = this.bCol = 1f;
        this.sprites = sprites;
        this.sway = (random.nextFloat() - 0.5f) * 0.02f;
        switch (style) {
            case STREAK -> { lifetime = 8 + random.nextInt(7); friction = 0.86f; gravity = 0f; baseSize = 0.2f; }
            case FLASH -> { lifetime = 7 + random.nextInt(6); friction = 0.8f; gravity = 0f; baseSize = 0.22f; roll = random.nextFloat() * Mth.TWO_PI; }
            case BOLT -> { lifetime = 2 + random.nextInt(3); friction = 0.5f; gravity = 0f; baseSize = 0.32f; roll = random.nextFloat() * Mth.TWO_PI; }
            case FLAME -> { lifetime = 14 + random.nextInt(10); friction = 0.92f; gravity = -0.25f; baseSize = 0.3f; }
            case FIRE -> { lifetime = 12 + random.nextInt(9); friction = 0.88f; gravity = -0.35f; baseSize = 0.32f; }
            case WISP -> { lifetime = 18 + random.nextInt(12); friction = 0.9f; gravity = -0.08f; baseSize = 0.34f; roll = random.nextFloat() * Mth.TWO_PI; }
            default -> { lifetime = 18 + random.nextInt(10); friction = 0.9f; gravity = 0.05f; baseSize = 0.2f; roll = random.nextFloat() * Mth.TWO_PI; }
        }
        this.oRoll = roll;
        if (animated()) setSpriteFromAge(sprites); else setSprite(sprites.get(random.nextInt(3), 2));
        this.quadSize = baseSize * (0.8f + random.nextFloat() * 0.45f);
    }

    /** FIRE and WISP play their frames over their life (an animated flame / curl of smoke). */
    private boolean animated() { return style == FxStyle.FIRE || style == FxStyle.WISP; }

    @Override
    public void tick() {
        super.tick();
        oRoll = roll;
        if (style == FxStyle.FLASH) roll += 0.12f;
        if (style == FxStyle.WISP) roll += 0.05f;
        if (style == FxStyle.FIRE) xd += sway * Mth.sin(age * 0.7f);
        if (animated() && !removed) setSpriteFromAge(sprites);
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = (age + partialTick) / lifetime;
        return switch (style) {
            case FLASH -> quadSize * (t < 0.2f ? 0.5f + t / 0.2f * 0.6f : 1.1f - (t - 0.2f) * 0.9f);
            case STREAK -> quadSize * (1.2f - t * 0.5f);
            case FIRE -> quadSize * (0.85f + t * 0.3f);
            case WISP -> quadSize * (0.8f + t * 0.9f);
            default -> quadSize * (1f - t * 0.35f);
        };
    }

    @Override
    public void render(VertexConsumer buffer, Camera camera, float partialTick) {
        float t = (age + partialTick) / lifetime;
        // opaque, then a quick cut out (the animated ones fade in their own frames)
        this.alpha = animated() ? (t < 0.85f ? 1f : Mth.clamp(1f - (t - 0.85f) / 0.15f, 0f, 1f)) : (t < 0.7f ? 1f : Mth.clamp(1f - (t - 0.7f) / 0.3f, 0f, 1f));
        if (style == FxStyle.STREAK) {
            // turn the sprite so its tip (texture top) points along the velocity, as seen from the camera
            Vector3f v = new Vector3f((float) xd, (float) yd, (float) zd);
            if (v.lengthSquared() > 1e-8f) {
                new Quaternionf(camera.rotation()).conjugate().transform(v);
                roll = oRoll = (float) Math.atan2(-v.x(), v.y());
            }
        } else if (style == FxStyle.FLAME || style == FxStyle.FIRE) {
            roll = oRoll = 0f;
        }
        super.render(buffer, camera, partialTick);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    /** Full brightness: glows in the dark. */
    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }

    public static ParticleEngine.SpriteParticleRegistration<SimpleParticleType> provider(FxStyle style) {
        return sprites -> new ParticleProvider<>() {
            @Nullable
            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
                double amount = com.obsidianarsenal.OaConfig.PARTICLE_AMOUNT.get();
                if (amount < 1 && level.random.nextDouble() >= amount) return null;
                return new SharpParticle(level, x, y, z, dx, dy, dz, sprites, style);
            }
        };
    }
}

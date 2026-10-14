package com.obsidianarsenal.client;

import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.*;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

/**
 * Full-bright animated sprite particle: flickers through its sprite frames, shrinks and fades out.
 * Used for every non-3D effect (flames, sparks, lightning) so they glow even in darkness.
 */
public class GlowParticle extends TextureSheetParticle {
    /** Per-type look: size, lifetime, drag, buoyancy (+ rises like fire, - falls), spin. */
    public record Style(float size, int minLife, int maxLife, float friction, float buoyancy, float spin, boolean flicker) {}

    public static final Style FLAME     = new Style(0.32f, 10, 22, 0.94f, 0.012f, 0.10f, true);
    public static final Style BLACK     = new Style(0.34f, 14, 26, 0.93f, 0.010f, 0.08f, true);
    public static final Style SPARK     = new Style(0.14f,  8, 16, 0.90f, 0.000f, 0.00f, true);
    public static final Style BIG_GLOW  = new Style(0.30f, 10, 18, 0.90f, 0.004f, 0.00f, true);
    public static final Style EMBER     = new Style(0.12f, 10, 20, 0.92f, 0.006f, 0.00f, true);
    public static final Style LIGHTNING = new Style(0.26f,  3,  6, 0.60f, 0.000f, 0.00f, true);
    public static final Style WISP      = new Style(0.22f, 16, 28, 0.97f, 0.000f, 0.06f, true);

    private final SpriteSet sprites;
    private final Style style;
    private final float baseSize;

    protected GlowParticle(ClientLevel level, double x, double y, double z, double dx, double dy, double dz,
                           SpriteSet sprites, Style style) {
        super(level, x, y, z);
        this.sprites = sprites;
        this.style = style;
        this.xd = dx;
        this.yd = dy;
        this.zd = dz;
        this.friction = style.friction();
        this.gravity = -style.buoyancy() * 20f;   // Particle applies yd -= 0.04 * gravity
        this.hasPhysics = false;
        this.lifetime = style.minLife() + random.nextInt(Math.max(1, style.maxLife() - style.minLife() + 1));
        this.baseSize = style.size() * (0.75f + random.nextFloat() * 0.5f);
        this.quadSize = baseSize;
        this.roll = random.nextFloat() * Mth.TWO_PI;
        this.oRoll = roll;
        this.rCol = this.gCol = this.bCol = 1f;
        setSpriteFromAge(sprites);
    }

    @Override
    public void tick() {
        super.tick();
        if (!removed) {
            if (style.flicker()) setSprite(sprites.get(random.nextInt(4) + age, lifetime + 4));
            else setSpriteFromAge(sprites);
            oRoll = roll;
            roll += style.spin() * (random.nextBoolean() ? 1 : -1);
        }
    }

    @Override
    public float getQuadSize(float partialTick) {
        float t = (age + partialTick) / lifetime;
        float grow = t < 0.15f ? 0.6f + t / 0.15f * 0.4f : 1f;
        return baseSize * grow * (1f - t * t * 0.7f);
    }

    @Override
    public void render(com.mojang.blaze3d.vertex.VertexConsumer buffer, net.minecraft.client.Camera camera, float partialTick) {
        float t = (age + partialTick) / lifetime;
        this.alpha = Mth.clamp(1.15f - t * t, 0f, 1f);
        super.render(buffer, camera, partialTick);
    }

    @Override public ParticleRenderType getRenderType() { return ParticleRenderType.PARTICLE_SHEET_TRANSLUCENT; }

    /** Full brightness: glows in the dark. */
    @Override protected int getLightColor(float partialTick) { return 0xF000F0; }

    public static ParticleEngine.SpriteParticleRegistration<SimpleParticleType> provider(Style style) {
        return sprites -> new ParticleProvider<>() {
            @Nullable
            @Override
            public Particle createParticle(SimpleParticleType type, ClientLevel level, double x, double y, double z, double dx, double dy, double dz) {
                return new GlowParticle(level, x, y, z, dx, dy, dz, sprites, style);
            }
        };
    }
}

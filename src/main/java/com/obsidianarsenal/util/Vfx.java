package com.obsidianarsenal.util;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.registry.ModParticles;
import mod.chloeprime.aaaparticles.api.common.AAALevel;
import mod.chloeprime.aaaparticles.api.common.ParticleEmitterInfo;
import net.minecraft.core.particles.BlockParticleOption;
import net.minecraft.core.particles.ParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.Vec3;

/**
 * VFX hub. Two layers:
 *
 * 1) AAA Particles (REQUIRED) - Effekseer effects. Every spell / effect / gauntlet attack fires one of the
 *    {@link Effek} emitters below through {@link AAALevel#addParticle}. From the server this sends AAA's own
 *    S2C packet to nearby players. Effects load from assets/obsidian_arsenal/effeks/<id>.efkefc.
 *
 * 2) Sprite particles - our red hellfire / black Amaterasu / purple lightning particles plus vanilla dust and
 *    block-chunk particles, layered under the Effekseer effects for density, trails and impact debris.
 */
public final class Vfx {
    private Vfx() {}

    // ======================================================================================== AAA (Effekseer)
    /** Every Effekseer effect this mod uses. File = assets/obsidian_arsenal/effeks/<id>.efkefc */
    public enum Effek {
        // Obsidian school
        OBSIDIAN_ORB_TRAIL("obsidian_orb_trail"),
        OBSIDIAN_SPIKE_ERUPTION("obsidian_spike_eruption"),
        OBSIDIAN_STORM_VORTEX("obsidian_storm_vortex"),
        OBSIDIAN_TENTACLE_EMERGE("obsidian_tentacle_emerge"),
        PURPLE_LIGHTNING_BURST("purple_lightning_burst"),
        PURPLE_LIGHTNING_AURA("purple_lightning_aura"),
        OBSIDIAN_SHATTER("obsidian_shatter"),
        // Hellfire school (red flames)
        HELL_COLUMN("hell_column"),
        HELLFIRE_STREAM("hellfire_stream"),
        GYROSCOPE_RINGS("gyroscope_rings"),
        GYROSCOPE_DEFLECT("gyroscope_deflect"),
        AMATERASU_IGNITE("amaterasu_ignite"),
        AMATERASU_FLAMES("amaterasu_flames"),
        INDRA_CHARGE("indra_charge"),
        INDRA_TRAIL("indra_trail"),
        INDRA_EXPLOSION("indra_explosion"),
        // Plasma school
        PLASMA_BEAM_MUZZLE("plasma_beam_muzzle"),
        BLACK_HOLE_FORM("black_hole_form");

        public final String id;
        Effek(String id) { this.id = id; }
    }

    /** Range (blocks) within which players receive an effect. */
    private static final double RANGE = 96.0;

    private static ParticleEmitterInfo info(Level level, Effek e) {
        return ParticleEmitterInfo.create(level, ObsidianArsenal.id(e.id));
    }

    /** World-space effect at a position. */
    public static void effek(Level level, Effek e, Vec3 pos, float scale) {
        AAALevel.addParticle(level, RANGE, info(level, e).position(pos).scale(scale));
    }

    /** World-space effect facing a direction (Effekseer +Z = forward). */
    public static void effekDir(Level level, Effek e, Vec3 pos, Vec3 forward, float scale) {
        AAALevel.addParticle(level, RANGE, info(level, e).position(pos).rotationFromForward(forward).scale(scale));
    }

    /** Directional effect scaled non-uniformly: width on X/Y, length on Z (forward). */
    public static void effekStretched(Level level, Effek e, Vec3 pos, Vec3 forward, float width, float length) {
        AAALevel.addParticle(level, RANGE, info(level, e).position(pos).rotationFromForward(forward).scale(width, width, length));
    }

    /** Effect that follows an entity (offset is in the entity's local space). */
    public static void effekOn(Level level, Effek e, Entity entity, Vec3 offset, float scale) {
        AAALevel.addParticle(level, RANGE, info(level, e).bindOnEntity(entity).entitySpaceRelativePosition(offset).scale(scale));
    }

    /** Effect that follows an entity's head and aims where it looks (used for Flaming Judgement's stream). */
    public static void effekFromHead(Level level, Effek e, Entity entity, Vec3 offset, float scale) {
        AAALevel.addParticle(level, RANGE, info(level, e).bindOnEntity(entity).useEntityHeadSpace().entitySpaceRelativePosition(offset).scale(scale));
    }

    /** Effect riding a projectile, oriented along its velocity. */
    public static void effekOnProjectile(Level level, Effek e, Entity projectile, float scale) {
        AAALevel.addParticle(level, RANGE, info(level, e).bindOnEntity(projectile).useEntityVelocityAsRotation().scale(scale));
    }

    // ======================================================================================== sprite layer
    // All of these are full-bright GlowParticles (see client/GlowParticle).
    public static ParticleOptions hellfire()      { return ModParticles.HELLFIRE.get(); }
    public static ParticleOptions amaterasu()     { return ModParticles.AMATERASU.get(); }
    public static ParticleOptions purple()        { return ModParticles.PURPLE_FLAME.get(); }
    public static ParticleOptions purpleSpark()   { return ModParticles.PURPLE_SPARK.get(); }
    public static ParticleOptions deepPurpleSpark() { return ModParticles.DEEP_PURPLE_SPARK.get(); }
    public static ParticleOptions redSpark()      { return ModParticles.RED_SPARK.get(); }
    public static ParticleOptions lightning()     { return ModParticles.PURPLE_LIGHTNING.get(); }
    public static ParticleOptions plasmaSpark()   { return ModParticles.PLASMA_SPARK.get(); }
    public static ParticleOptions voidWisp()      { return ModParticles.VOID_WISP.get(); }

    public static ParticleOptions obsidianChunk() {
        return new BlockParticleOption(ParticleTypes.BLOCK, Blocks.OBSIDIAN.defaultBlockState());
    }

    /** One particle with velocity, from either side. */
    public static void spawn(Level level, ParticleOptions p, double x, double y, double z, double dx, double dy, double dz) {
        if (level instanceof ServerLevel sl) {
            for (var player : sl.players())   // force = true: visible up to 512 blocks, ignores the "minimal particles" option
                sl.sendParticles(player, p, true, x, y, z, 0, dx, dy, dz, 1.0);
        } else level.addParticle(p, true, x, y, z, dx, dy, dz);
    }

    public static void burst(Level level, ParticleOptions p, Vec3 c, int count, double radius, double speed) {
        var r = level.random;
        for (int i = 0; i < count; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian() * 0.6, r.nextGaussian()).normalize();
            spawn(level, p, c.x + d.x * radius * 0.3, c.y + d.y * radius * 0.3, c.z + d.z * radius * 0.3,
                    d.x * speed, d.y * speed, d.z * speed);
        }
    }

    public static void ring(Level level, ParticleOptions p, Vec3 c, double radius, int count, double outSpeed, double up) {
        for (int i = 0; i < count; i++) {
            double a = (Math.PI * 2 * i) / count;
            double cx = Math.cos(a), cz = Math.sin(a);
            spawn(level, p, c.x + cx * radius, c.y + 0.1, c.z + cz * radius, cx * outSpeed, up, cz * outSpeed);
        }
    }

    public static void line(Level level, ParticleOptions p, Vec3 a, Vec3 b, double step, double jitter) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-4) return;
        Vec3 n = d.scale(1.0 / len);
        var r = level.random;
        for (double t = 0; t <= len; t += step) {
            spawn(level, p, a.x + n.x * t + (r.nextDouble() - 0.5) * jitter,
                    a.y + n.y * t + (r.nextDouble() - 0.5) * jitter,
                    a.z + n.z * t + (r.nextDouble() - 0.5) * jitter, 0, 0.005, 0);
        }
    }

    /** Jagged, branching purple lightning bolt between two points. */
    public static void bolt(Level level, Vec3 a, Vec3 b) {
        boltInternal(level, a, b, 2);
    }

    private static void boltInternal(Level level, Vec3 a, Vec3 b, int depth) {
        var r = level.random;
        double len = a.distanceTo(b);
        int segs = Math.max(3, (int) (len * 2.5));
        double jag = Math.min(0.9, 0.18 + len * 0.12);
        Vec3 prev = a;
        for (int i = 1; i <= segs; i++) {
            Vec3 p = a.lerp(b, i / (double) segs);
            if (i != segs) p = p.add((r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag);
            line(level, lightning(), prev, p, 0.16, 0.02);
            line(level, purpleSpark(), prev, p, 0.3, 0.12);
            if (depth > 0 && r.nextInt(4) == 0) {   // side branch
                Vec3 off = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(len * 0.25);
                boltInternal(level, p, p.add(off), depth - 1);
            }
            prev = p;
        }
        spawn(level, deepPurpleSpark(), b.x, b.y, b.z, 0, 0, 0);
    }
}

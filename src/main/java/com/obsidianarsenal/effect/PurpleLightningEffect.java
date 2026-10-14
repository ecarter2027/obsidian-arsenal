package com.obsidianarsenal.effect;

import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.phys.Vec3;

/**
 * "Purple Lightning": +100% movement speed (2x). VFX: AAA aura bound to the body (refreshed every 2s), purple sparks,
 * arcs; the afterimage trail is rendered client-side in ClientForgeEvents.
 */
public class PurpleLightningEffect extends MobEffect {
    public PurpleLightningEffect() {
        super(MobEffectCategory.BENEFICIAL, 0xA040FF);
        addAttributeModifier(Attributes.MOVEMENT_SPEED, "7d1f3c52-6a0e-4f0b-9c55-0b6f0a1d5e11", 1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override public boolean isDurationEffectTick(int duration, int amplifier) { return true; }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        var level = e.level();
        if (level.isClientSide) return;   // everything below is sent to clients
        var r = level.random;
        double h = e.getBbHeight(), w = e.getBbWidth();
        Vec3 c = e.position().add(0, h * 0.5, 0);
        boolean moving = e.getDeltaMovement().horizontalDistanceSqr() > 0.003;

        if (e.tickCount % 40 == 0) Vfx.effekOn(level, Effek.PURPLE_LIGHTNING_AURA, e, Vec3.ZERO, 1.0f);

        // crackling electricity crawling over the body (every tick)
        for (int i = 0; i < 3; i++)
            Vfx.spawn(level, Vfx.lightning(), e.getX() + (r.nextDouble() - 0.5) * w * 1.3, e.getY() + r.nextDouble() * h,
                    e.getZ() + (r.nextDouble() - 0.5) * w * 1.3, 0, 0, 0);
        Vfx.spawn(level, Vfx.purpleSpark(), c.x + (r.nextDouble() - 0.5) * w * 1.6, e.getY() + r.nextDouble() * h,
                c.z + (r.nextDouble() - 0.5) * w * 1.6, (r.nextDouble() - 0.5) * 0.1, 0.03, (r.nextDouble() - 0.5) * 0.1);

        // short arcs jumping around the body (every 2 ticks)
        if (e.tickCount % 2 == 0) {
            Vec3 a = new Vec3(e.getX() + (r.nextDouble() - 0.5) * w, e.getY() + r.nextDouble() * h, e.getZ() + (r.nextDouble() - 0.5) * w);
            Vec3 b = a.add((r.nextDouble() - 0.5) * 1.6, (r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 1.6);
            Vfx.bolt(level, a, b);
        }
        // ground strikes around the runner (every 6 ticks, more often while sprinting)
        if (e.tickCount % (moving ? 3 : 6) == 0) {
            double ang = r.nextDouble() * Math.PI * 2, dist = 0.8 + r.nextDouble() * 1.4;
            Vec3 ground = new Vec3(e.getX() + Math.cos(ang) * dist, e.getY() + 0.05, e.getZ() + Math.sin(ang) * dist);
            Vfx.bolt(level, c.add(0, h * 0.2, 0), ground);
            Vfx.burst(level, Vfx.purpleSpark(), ground, 5, 0.3, 0.12);
        }
        // electric wake while moving
        if (moving) {
            Vec3 back = e.position().subtract(e.getDeltaMovement().normalize().scale(0.6));
            for (int i = 0; i < 3; i++)
                Vfx.spawn(level, i == 0 ? Vfx.deepPurpleSpark() : Vfx.purple(), back.x + (r.nextDouble() - 0.5) * w,
                        back.y + r.nextDouble() * h, back.z + (r.nextDouble() - 0.5) * w, 0, 0.01, 0);
            Vfx.spawn(level, ParticleTypes.ELECTRIC_SPARK, e.getX(), e.getY() + 0.05, e.getZ(), 0, 0.05, 0);
        }
    }
}

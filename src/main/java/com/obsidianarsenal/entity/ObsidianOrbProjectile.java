package com.obsidianarsenal.entity;

import com.obsidianarsenal.entity.ConstructEntity.Kind;
import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.Scheduler;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.ThrowableProjectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/** Spell 1 projectile: flies where the caster looks; on impact erupts a ring of animated obsidian spikes. */
public class ObsidianOrbProjectile extends ThrowableProjectile implements GeoEntity {
    public float damage = 8f;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ObsidianOrbProjectile(EntityType<? extends ThrowableProjectile> type, Level level) { super(type, level); }

    public ObsidianOrbProjectile(Level level, LivingEntity owner) { super(ModEntities.OBSIDIAN_ORB.get(), owner, level); }

    @Override protected void defineSynchedData() {}
    @Override protected float getGravity() { return 0.015f; }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) {
            var r = random;
            level().addParticle(Vfx.purpleSpark(), getX(), getY(), getZ(), 0, 0, 0);
            level().addParticle(Vfx.obsidianChunk(), getX() + (r.nextDouble() - 0.5) * 0.3, getY() + (r.nextDouble() - 0.5) * 0.3, getZ() + (r.nextDouble() - 0.5) * 0.3, 0, -0.05, 0);
        } else {
            if (tickCount == 1) Vfx.effekOnProjectile(level(), Effek.OBSIDIAN_ORB_TRAIL, this, 1.0f);
            if (tickCount > 80) { erupt(position()); discard(); }
        }
    }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) { erupt(result.getLocation()); discard(); }
    }

    private void erupt(Vec3 hit) {
        if (!(level() instanceof ServerLevel sl)) return;
        LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
        var spell = ModSpells.OBSIDIAN_SPIKE_EXPLOSION.get();
        double gy = SpellUtil.groundY(sl, hit.x, hit.y, hit.z);
        Vec3 c = new Vec3(hit.x, gy, hit.z);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 1.2f, 1.4f);
        Vfx.effek(sl, Effek.OBSIDIAN_SPIKE_ERUPTION, c, 2.0f);
        Vfx.burst(sl, Vfx.obsidianChunk(), c.add(0, 0.4, 0), 40, 1.5, 0.35);
        Vfx.ring(sl, Vfx.purpleSpark(), c, 2.0, 24, 0.25, 0.05);
        Vfx.spawn(sl, ParticleTypes.EXPLOSION, c.x, c.y + 0.3, c.z, 0, 0, 0);

        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(3.4, 2.0, 3.4), t -> SpellUtil.canHarm(owner, t)))
            SpellUtil.spellDamage(spell, this, owner, e, damage);

        float spikeDmg = damage * 0.5f;
        ConstructEntity.spawn(sl, Kind.SPIKE, owner, c, SpellUtil.randomCardinal(sl.random), spikeDmg, 0);
        for (int i = 0; i < 6; i++) {
            final int idx = i;
            Scheduler.later(sl, 2 + i, () -> {
                double a = Math.toRadians(idx * 60 + 15);
                double sx = c.x + Math.cos(a) * 2.3, sz = c.z + Math.sin(a) * 2.3;
                ConstructEntity.spawn(sl, Kind.SPIKE, owner, new Vec3(sx, SpellUtil.groundY(sl, sx, c.y + 1, sz), sz), SpellUtil.randomCardinal(sl.random), spikeDmg, 0);
            });
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation.obsidian_orb.idle"))));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

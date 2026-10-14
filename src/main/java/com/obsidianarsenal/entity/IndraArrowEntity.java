package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Indra's Arrow. The spell is an Iron's LONG cast: while the player channels, this arrow hovers in front of them
 * charging (CHARGING state). When the cast completes the spell calls {@link #launch()}; if the cast is cancelled
 * the arrow fizzles. On impact it detonates a 25 block radius sphere of destruction.
 */
public class IndraArrowEntity extends Projectile implements GeoEntity {
    public static final int RADIUS = 25;

    public float damage = 60f;
    private boolean launched = false;
    private int flightTicks = 0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public IndraArrowEntity(EntityType<? extends Projectile> type, Level level) { super(type, level); }

    public IndraArrowEntity(Level level, LivingEntity owner) {
        super(ModEntities.INDRA_ARROW.get(), level);
        setOwner(owner);
    }

    @Override protected void defineSynchedData() {}
    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    public void launch() {
        Entity owner = getOwner();
        if (owner == null) { discard(); return; }
        launched = true;
        setDeltaMovement(owner.getLookAngle().scale(3.2));
        faceVelocity();
        hurtMarked = true;
        level().playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 3.0f, 0.4f);
        level().playSound(null, blockPosition(), SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 3.0f, 0.5f);
        Vfx.effekOnProjectile(level(), Effek.INDRA_TRAIL, this, 1.5f);
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (!launched && getDeltaMovement().lengthSqr() < 1e-6) {   // charging (client sees zero velocity too)
            if (owner != null) {
                Vec3 p = owner.getEyePosition().add(owner.getLookAngle().scale(2.4)).add(0, -0.3, 0);
                setPos(p.x, p.y, p.z);
                setRot(owner.getYRot(), owner.getXRot());   // mob-style yaw/pitch (renderer expects this)
            } else if (!level().isClientSide) { discard(); return; }
            chargeFx();
            if (!level().isClientSide && tickCount > 200) discard();   // safety
            return;
        }
        launched = true;
        flightTicks++;
        Vec3 v = getDeltaMovement();
        if (!level().isClientSide) {
            HitResult hit = ProjectileUtil.getHitResultOnMoveVector(this, this::canHitEntity);
            if (hit.getType() != HitResult.Type.MISS) { onHit(hit); return; }
            if (flightTicks > 160) { detonate(position()); return; }
        }
        setPos(getX() + v.x, getY() + v.y, getZ() + v.z);
        faceVelocity();
        if (level().isClientSide) trailFx();
    }

    /** Mob-style yaw/pitch pointing along the velocity (same convention as a player's look). */
    private void faceVelocity() {
        Vec3 v = getDeltaMovement();
        if (v.lengthSqr() < 1e-6) return;
        float yaw = (float) Math.toDegrees(Math.atan2(-v.x, v.z));
        float pitch = (float) -Math.toDegrees(Math.atan2(v.y, v.horizontalDistance()));
        yRotO = getYRot();
        xRotO = getXRot();
        setYRot(yaw);
        setXRot(pitch);
    }

    private void chargeFx() {
        if (!level().isClientSide) {
            // purple lightning arcing into the arrow while it charges
            if (tickCount % 3 == 0) {
                var r = random;
                Vec3 from = position().add(new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(2.5 + r.nextDouble()));
                Vfx.bolt(level(), from, position());
            }
            return;
        }
        var r = random;
        float prog = Math.min(1f, tickCount / 40f);
        for (int i = 0; i < 3 + (int) (prog * 10); i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(1.2 + 2.2 * (1 - prog));
            level().addParticle(i % 3 == 0 ? Vfx.purpleSpark() : Vfx.purple(), true, getX() + d.x, getY() + d.y, getZ() + d.z,
                    -d.x * 0.12, -d.y * 0.12, -d.z * 0.12);
        }
        level().addParticle(Vfx.deepPurpleSpark(), true, getX(), getY(), getZ(), 0, 0, 0);
        if (tickCount % 2 == 0) level().addParticle(Vfx.lightning(), true, getX() + (r.nextDouble() - 0.5) * 0.8,
                getY() + (r.nextDouble() - 0.5) * 0.8, getZ() + (r.nextDouble() - 0.5) * 0.8, 0, 0, 0);
    }

    private void trailFx() {
        var r = random;
        Vec3 v = getDeltaMovement();
        for (int i = 0; i < 10; i++) {
            double t = r.nextDouble();
            level().addParticle(i % 4 == 0 ? Vfx.lightning() : Vfx.purple(), true, getX() - v.x * t, getY() - v.y * t, getZ() - v.z * t,
                    (r.nextDouble() - 0.5) * 0.15, (r.nextDouble() - 0.5) * 0.15, (r.nextDouble() - 0.5) * 0.15);
        }
        level().addParticle(Vfx.deepPurpleSpark(), true, getX(), getY(), getZ(), 0, 0, 0);
    }

    @Override
    protected boolean canHitEntity(Entity e) { return super.canHitEntity(e) && e != getOwner(); }

    @Override
    protected void onHit(HitResult result) {
        super.onHit(result);
        if (!level().isClientSide) detonate(result.getLocation());
    }

    private void detonate(Vec3 at) {
        if (level() instanceof ServerLevel sl) {
            LivingEntity owner = getOwner() instanceof LivingEntity le ? le : null;
            SpellUtil.sphereBlast(sl, at, RADIUS, ModSpells.INDRAS_ARROW.get(), this, owner, damage);
        }
        discard();
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(RawAnimation.begin().thenLoop("animation.indra_arrow.idle"))));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

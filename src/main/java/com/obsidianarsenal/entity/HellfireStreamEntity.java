package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Flaming Judgement's hellfire torrent. Spawned by the spell on the first Iron's continuous-cast pulse and kept
 * alive by every following pulse ({@link #keepAlive}). It sits at the caster's mouth, aims where they look, renders
 * a 3D GeckoLib flame cone (up to 10 blocks, shortened when blocked), spews glowing flames and burns what it touches.
 */
public class HellfireStreamEntity extends Entity implements GeoEntity {
    public static final double RANGE = 10.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(HellfireStreamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(HellfireStreamEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 3f;
    private int aliveUntil = 15;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public HellfireStreamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static HellfireStreamEntity spawn(ServerLevel level, LivingEntity owner, float damage) {
        HellfireStreamEntity e = new HellfireStreamEntity(ModEntities.HELLFIRE_STREAM.get(), level);
        e.entityData.set(OWNER_ID, owner.getId());
        e.damage = damage;
        e.follow(owner);
        e.setOldPosAndRot();
        level.addFreshEntity(e);
        return e;
    }

    /** Called on every continuous-cast pulse (every 10 ticks) to keep the stream burning. */
    public void keepAlive(float damage) {
        this.damage = damage;
        this.aliveUntil = tickCount + 15;
    }

    public float getLength() { return entityData.get(LENGTH); }

    @Nullable
    public LivingEntity getOwner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof LivingEntity le ? le : null;
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(LENGTH, (float) RANGE);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 128 * 128; }

    private void follow(LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 p = owner.getEyePosition().add(look.scale(0.55)).add(0, -0.3, 0);
        setPos(p.x, p.y, p.z);
        setYRot(owner.getYHeadRot());
        setXRot(owner.getXRot());
    }

    @Override
    public void tick() {
        super.tick();
        setOldPosAndRot();
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { if (!level().isClientSide) discard(); return; }
        follow(owner);

        if (level() instanceof ServerLevel sl) {
            if (tickCount > aliveUntil) { discard(); return; }
            entityData.set(LENGTH, (float) Math.max(1.0, Math.min(RANGE, SpellUtil.blockDistance(owner, RANGE + 0.6) - 0.6)));
            if (tickCount % 2 == 0) burn(sl, owner);
            if (tickCount % 8 == 0) sl.playSound(null, blockPosition(), SoundEvents.BLAZE_SHOOT, SoundSource.PLAYERS, 0.7f, 0.45f + random.nextFloat() * 0.1f);
        } else {
            clientFx(owner);
        }
    }

    private void burn(ServerLevel sl, LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        Vec3 start = position();
        double range = getLength();
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, owner.getBoundingBox().inflate(range + 1), t -> SpellUtil.canHarm(owner, t))) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(start);
            double t = c.dot(look);
            if (t < 0 || t > range + e.getBbWidth() / 2) continue;
            if (c.subtract(look.scale(t)).length() > 0.6 + t * 0.14 + e.getBbWidth() / 2) continue;
            if (SpellUtil.spellDamage(ModSpells.FLAMING_JUDGEMENT.get(), this, owner, e, damage)) e.setSecondsOnFire(10);
        }
    }

    /** Client-side glowing flames along the stream (no network cost, never "missing"). */
    private void clientFx(LivingEntity owner) {
        var r = random;
        Vec3 look = owner.getLookAngle();
        Vec3 start = position();
        double len = getLength();
        for (int i = 0; i < 14; i++) {
            double spd = 0.55 + r.nextDouble() * 0.65;
            double spread = 0.09;
            level().addParticle(i % 5 == 0 ? Vfx.redSpark() : Vfx.hellfire(), true,
                    start.x + look.x * r.nextDouble() * 0.6, start.y + look.y * r.nextDouble() * 0.6, start.z + look.z * r.nextDouble() * 0.6,
                    look.x * spd + (r.nextDouble() - 0.5) * spread, look.y * spd + (r.nextDouble() - 0.5) * spread, look.z * spd + (r.nextDouble() - 0.5) * spread);
        }
        // flames licking off where the stream ends
        Vec3 end = start.add(look.scale(len));
        for (int i = 0; i < 4; i++)
            level().addParticle(Vfx.hellfire(), true, end.x + (r.nextDouble() - 0.5) * 1.2, end.y + (r.nextDouble() - 0.5) * 1.2,
                    end.z + (r.nextDouble() - 0.5) * 1.2, (r.nextDouble() - 0.5) * 0.1, 0.06, (r.nextDouble() - 0.5) * 0.1);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(
                RawAnimation.begin().thenPlay("animation.hellfire_stream.ignite").thenLoop("animation.hellfire_stream.idle"))));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

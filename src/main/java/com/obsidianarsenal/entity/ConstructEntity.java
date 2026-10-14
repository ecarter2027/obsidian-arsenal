package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.core.particles.ParticleTypes;
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
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.function.Supplier;

/**
 * One GeckoLib-animated entity class for every summoned construct: obsidian spikes, shards, tentacles, the storm's
 * orbiting spikes, hellfire columns and the Gyroscope rings. Each kind has its own model/animation files, the Iron's
 * spell it deals damage as, and an AAA effect fired when it appears.
 */
public class ConstructEntity extends Entity implements GeoEntity {

    public enum Kind {
        //          model                  life  w     h     hitR  every start orbit  orbR  deg/t yOff  fire   shield  spell                                  spawn effek
        SPIKE      ("obsidian_spike",       50, 2.4f, 3.4f, 1.5f,  20,   5,   false, 0f,   0f,   0f,   false, false, () -> ModSpells.OBSIDIAN_SPIKE_EXPLOSION.get(), Effek.OBSIDIAN_SPIKE_ERUPTION),
        SHARD      ("obsidian_shard",       45, 1.6f, 2.6f, 1.4f,  20,   4,   false, 0f,   0f,   0f,   false, false, () -> ModSpells.OBSIDIAN_SHATTER.get(),         Effek.OBSIDIAN_SHATTER),
        TENTACLE   ("obsidian_tentacle",   100, 2.0f, 5.0f, 1.9f,  10,  10,   false, 0f,   0f,   0f,   false, false, () -> ModSpells.OBSIDIAN_TENTACLES.get(),       Effek.OBSIDIAN_TENTACLE_EMERGE),
        STORM_SPIKE("obsidian_storm_spike", 80, 1.0f, 2.0f, 1.3f,   5,   6,   true,  3.2f, 16f,  0.1f, false, false, () -> ModSpells.OBSIDIAN_STORM.get(),           null),
        COLUMN     ("hell_column",          90, 1.6f, 4.6f, 1.8f,   5,   4,   true,  3.0f, 10f,  0f,   true,  false, () -> ModSpells.COLUMNS_OF_HELL.get(),          Effek.HELL_COLUMN),
        RING       ("hell_gyroscope",      110, 5.0f, 2.6f, 0f,     0,   0,   true,  0f,   0f,   0f,   true,  true,  () -> ModSpells.GYROSCOPE.get(),                Effek.GYROSCOPE_RINGS);

        public final String model;
        public final int lifetime;
        public final float width, height, hitRadius;
        public final int hitEvery, hitStart;
        public final boolean orbit;
        public final float orbitRadius, degPerTick, yOffset;
        public final boolean fire, shield;
        public final Supplier<AbstractSpell> spell;
        @Nullable public final Effek spawnEffek;

        Kind(String model, int lifetime, float width, float height, float hitRadius, int hitEvery, int hitStart,
             boolean orbit, float orbitRadius, float degPerTick, float yOffset, boolean fire, boolean shield,
             Supplier<AbstractSpell> spell, @Nullable Effek spawnEffek) {
            this.model = model; this.lifetime = lifetime; this.width = width; this.height = height;
            this.hitRadius = hitRadius; this.hitEvery = hitEvery; this.hitStart = hitStart; this.orbit = orbit;
            this.orbitRadius = orbitRadius; this.degPerTick = degPerTick; this.yOffset = yOffset;
            this.fire = fire; this.shield = shield; this.spell = spell; this.spawnEffek = spawnEffek;
        }

        public String anim(String name) { return "animation." + model + "." + name; }

        /** How many idle animation variants this model ships with (idle, idle_1, idle_2 ...). */
        public int idleVariants() {
            return switch (this) { case TENTACLE -> 3; case SPIKE, SHARD -> 2; default -> 1; };
        }
    }

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> PHASE = SynchedEntityData.defineId(ConstructEntity.class, EntityDataSerializers.FLOAT);

    public final Kind kind;
    private float damage = 4f;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ConstructEntity(EntityType<?> type, Level level, Kind kind) {
        super(type, level);
        this.kind = kind;
        this.noPhysics = true;
    }

    public static ConstructEntity spawn(ServerLevel level, Kind kind, @Nullable LivingEntity owner, Vec3 pos,
                                        float yaw, float damage, float phaseDeg) {
        ConstructEntity e = new ConstructEntity(ModEntities.CONSTRUCTS.get(kind).get(), level, kind);
        e.damage = damage;
        if (owner != null) e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(PHASE, phaseDeg);
        e.setPos(pos.x, pos.y, pos.z);
        e.setYRot(yaw);
        e.yRotO = yaw;
        if (kind.orbit && owner != null) e.updateOrbit(owner);
        level.addFreshEntity(e);
        return e;
    }

    public boolean isOwnedBy(Entity other) { return entityData.get(OWNER_ID) == other.getId(); }

    @Nullable
    public Entity getOwner() {
        int id = entityData.get(OWNER_ID);
        return id < 0 ? null : level().getEntity(id);
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(PHASE, 0f);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}

    private void updateOrbit(Entity owner) {
        if (kind.orbitRadius <= 0f) {   // follow only (gyroscope)
            setPos(owner.getX(), owner.getY(), owner.getZ());
            return;
        }
        double a = Math.toRadians(entityData.get(PHASE) + tickCount * kind.degPerTick);
        double r = kind.orbitRadius;
        setPos(owner.getX() + Math.cos(a) * r, owner.getY() + kind.yOffset, owner.getZ() + Math.sin(a) * r);
        if (kind == Kind.STORM_SPIKE) setYRot(SpellUtil.yawFacing(-Math.sin(a), Math.cos(a)));
    }

    @Override
    public void tick() {
        super.tick();
        setOldPosAndRot();
        Entity owner = getOwner();
        if (kind.orbit) {
            if (owner == null) { if (!level().isClientSide) discard(); }
            else {
                float oldYaw = getYRot();
                updateOrbit(owner);
                this.yRotO = oldYaw;
            }
        }
        if (level() instanceof ServerLevel sl) serverTick(sl, owner);
        else clientTick();
    }

    private void serverTick(ServerLevel sl, @Nullable Entity owner) {
        if (tickCount == 1) {
            if (kind.spawnEffek != null) {
                if (kind.orbit && owner != null && kind == Kind.RING) Vfx.effekOn(sl, kind.spawnEffek, owner, new Vec3(0, 1.1, 0), 1.0f);
                else if (kind.orbit) Vfx.effekOn(sl, kind.spawnEffek, this, Vec3.ZERO, 1.0f);
                else Vfx.effekDir(sl, kind.spawnEffek, position(), Vec3.directionFromRotation(0, getYRot()), 1.0f);
            }
            sl.playSound(null, blockPosition(), kind.fire ? SoundEvents.BLAZE_SHOOT : SoundEvents.DEEPSLATE_BREAK, SoundSource.PLAYERS, kind.fire ? 1.0f : 1.4f, 0.6f);
        }
        if (kind.shield && owner != null) {
            owner.getPersistentData().putLong("OA_Gyro", sl.getGameTime() + 4);
            blockProjectiles(sl, owner);
            if (tickCount % 8 == 0) sl.playSound(null, owner.blockPosition(), SoundEvents.FIRE_AMBIENT, SoundSource.PLAYERS, 0.6f, 0.5f);
            if (tickCount % 40 == 0 && tickCount > 1) Vfx.effekOn(sl, Effek.GYROSCOPE_RINGS, owner, new Vec3(0, 1.1, 0), 1.0f);
        }
        if (kind.hitEvery > 0 && tickCount >= kind.hitStart && (tickCount - kind.hitStart) % kind.hitEvery == 0 && tickCount < kind.lifetime - 8) {
            hit(sl, owner);
        }
        if (tickCount >= kind.lifetime) discard();
    }

    private void hit(ServerLevel sl, @Nullable Entity owner) {
        double r = kind.hitRadius;
        AABB box = new AABB(getX() - r, getY() - 0.3, getZ() - r, getX() + r, getY() + kind.height, getZ() + r);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, t -> SpellUtil.canHarm(owner, t))) {
            if (SpellUtil.spellDamage(kind.spell.get(), this, owner, e, damage)) {
                if (kind.fire) e.setSecondsOnFire(8);
                if (kind == Kind.SPIKE || kind == Kind.SHARD) {
                    e.setDeltaMovement(e.getDeltaMovement().add(0, 0.45, 0));
                    e.hurtMarked = true;
                }
                Vfx.burst(sl, kind.fire ? Vfx.hellfire() : Vfx.purpleSpark(), e.position().add(0, e.getBbHeight() / 2, 0), 8, 0.6, 0.15);
            }
        }
    }

    private void blockProjectiles(ServerLevel sl, Entity owner) {
        for (Projectile p : sl.getEntitiesOfClass(Projectile.class, owner.getBoundingBox().inflate(3.4))) {
            if (p.getOwner() == owner || p instanceof IndraArrowEntity) continue;
            Vfx.effek(sl, Effek.GYROSCOPE_DEFLECT, p.position(), 1.0f);
            Vfx.burst(sl, Vfx.hellfire(), p.position(), 10, 0.4, 0.2);
            sl.playSound(null, p.blockPosition(), SoundEvents.FIRE_EXTINGUISH, SoundSource.PLAYERS, 0.8f, 1.4f);
            p.discard();
        }
    }

    // --------------------------------------------------------------------------------------------- client sprites
    private void clientTick() {
        var r = random;
        switch (kind) {
            case SPIKE, SHARD -> {
                if (tickCount <= 3) for (int i = 0; i < 6; i++)
                    level().addParticle(Vfx.obsidianChunk(), getX() + (r.nextDouble() - 0.5) * kind.width, getY() + 0.1,
                            getZ() + (r.nextDouble() - 0.5) * kind.width, (r.nextDouble() - 0.5) * 0.3, 0.25, (r.nextDouble() - 0.5) * 0.3);
                if (tickCount % 6 == 0)
                    level().addParticle(Vfx.purpleSpark(), getX() + (r.nextDouble() - 0.5), getY() + r.nextDouble() * kind.height, getZ() + (r.nextDouble() - 0.5), 0, 0.02, 0);
            }
            case TENTACLE -> {
                if (tickCount <= 3) for (int i = 0; i < 8; i++)
                    level().addParticle(Vfx.obsidianChunk(), getX() + (r.nextDouble() - 0.5) * 1.6, getY() + 0.1, getZ() + (r.nextDouble() - 0.5) * 1.6, 0, 0.25, 0);
                if (tickCount % 3 == 0)
                    level().addParticle(Vfx.purpleSpark(), getX() + (r.nextDouble() - 0.5), getY() + r.nextDouble() * 4, getZ() + (r.nextDouble() - 0.5), 0, 0.02, 0);
            }
            case STORM_SPIKE -> {
                level().addParticle(Vfx.purpleSpark(), getX(), getY() + 1.0, getZ(), 0, 0, 0);
                level().addParticle(Vfx.deepPurpleSpark(), getX() + (r.nextDouble() - 0.5) * 0.4, getY() + 0.6 + r.nextDouble(), getZ() + (r.nextDouble() - 0.5) * 0.4, 0, 0.01, 0);
            }
            case COLUMN -> {
                for (int i = 0; i < 4; i++) {
                    double h = r.nextDouble() * kind.height;
                    double a = tickCount * 0.5 + h;
                    double rad = 0.5 + 0.35 * Math.sin(h * 1.3);
                    level().addParticle(Vfx.hellfire(), getX() + Math.cos(a) * rad, getY() + h, getZ() + Math.sin(a) * rad, 0, 0.06, 0);
                }
                if (tickCount % 4 == 0) level().addParticle(ParticleTypes.LAVA, getX(), getY() + r.nextDouble() * 2, getZ(), 0, 0, 0);
            }
            case RING -> {
                // The three solid rings tumble about their X axes and together sweep a sphere around the
                // caster, so embers are shed from random points on those spheres (radius 34/39/44 px).
                double cx = getX(), cy = getY() + 18 / 16.0, cz = getZ();
                double[] radius = {34 / 16.0, 39 / 16.0, 44 / 16.0};
                for (int k = 0; k < 4; k++) {
                    double rr = radius[r.nextInt(3)];
                    Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                    level().addParticle(k == 0 ? Vfx.redSpark() : Vfx.hellfire(), true,
                            cx + d.x * rr, cy + d.y * rr, cz + d.z * rr, d.x * 0.03, d.y * 0.03 + 0.02, d.z * 0.03);
                }
            }
        }
    }

    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean displayFireAnimation() { return false; }

    // --------------------------------------------------------------------------------------------- GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        // Each construct picks its own idle variant and playback speed so a group never moves in lock-step.
        int v = Math.floorMod(getUUID().hashCode(), kind.idleVariants());
        String idle = kind.anim(v == 0 ? "idle" : "idle_" + v);
        double speed = 0.8 + (Math.floorMod(getUUID().hashCode() >> 4, 9)) * 0.05;   // 0.80 .. 1.20
        AnimationController<ConstructEntity> main = new AnimationController<>(this, "main", 0, state -> {
            ConstructEntity e = state.getAnimatable();
            if (e.tickCount >= e.kind.lifetime - 10)
                return state.setAndContinue(RawAnimation.begin().thenPlay(e.kind.anim("sink")));
            return state.setAndContinue(RawAnimation.begin().thenPlay(e.kind.anim("rise")).thenLoop(idle));
        });
        main.setAnimationSpeed(speed);
        controllers.add(main);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

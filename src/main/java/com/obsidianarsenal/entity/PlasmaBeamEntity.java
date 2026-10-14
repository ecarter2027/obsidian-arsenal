package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.level.BlockEvent;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Plasma Beam. A red 3D beam from the caster's extended right hand to whatever they are looking at.
 * The GeckoLib model is authored 3 blocks long and stretched to the beam's length, so its taper (thin at the hand,
 * full half-block width at one third of the length) always scales with the beam.
 * Kept alive by Iron's continuous-cast pulses, exactly like the Flaming Judgement stream. Instantly breaks every block along its line.
 */
public class PlasmaBeamEntity extends Entity implements GeoEntity {
    public static final double RANGE = 32.0;
    /** Length the model is authored at (blocks). */
    public static final double MODEL_LENGTH = 3.0;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(PlasmaBeamEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(PlasmaBeamEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 4f;
    private int aliveUntil = 15;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public PlasmaBeamEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static PlasmaBeamEntity spawn(ServerLevel level, LivingEntity owner, float damage) {
        PlasmaBeamEntity e = new PlasmaBeamEntity(ModEntities.PLASMA_BEAM.get(), level);
        e.entityData.set(OWNER_ID, owner.getId());
        e.damage = damage;
        e.aim(owner);
        e.setOldPosAndRot();
        level.addFreshEntity(e);
        return e;
    }

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
        entityData.define(LENGTH, 4f);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }

    /** World position of the caster's extended (right / main) hand while channeling. */
    public static Vec3 handPos(LivingEntity owner) {
        float yaw = owner.getYHeadRot() * Mth.DEG_TO_RAD;
        Vec3 look = owner.getLookAngle();
        double side = owner instanceof net.minecraft.world.entity.player.Player p && p.getMainArm() == net.minecraft.world.entity.HumanoidArm.LEFT ? -1 : 1;
        Vec3 right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw)).scale(side);
        return owner.getEyePosition().add(0, -0.42, 0).add(right.scale(0.33)).add(look.scale(0.75));
    }

    /** End point: first block the caster's crosshair hits (entities do not stop the beam). */
    public static Vec3 target(LivingEntity owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(RANGE));
        BlockHitResult hit = owner.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private void aim(LivingEntity owner) {
        Vec3 from = handPos(owner);
        Vec3 to = target(owner);
        Vec3 d = to.subtract(from);
        setPos(from.x, from.y, from.z);
        if (d.lengthSqr() > 1e-6) {
            setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));                       // mob-style yaw
            setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));   // mob-style pitch
        }
        if (!level().isClientSide) entityData.set(LENGTH, (float) Math.max(0.5, d.length()));
    }

    public Vec3 direction() {
        return Vec3.directionFromRotation(getXRot(), getYRot());
    }

    @Override
    public void tick() {
        super.tick();
        setOldPosAndRot();
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { if (!level().isClientSide) discard(); return; }
        aim(owner);

        if (level() instanceof ServerLevel sl) {
            if (tickCount > aliveUntil) { discard(); return; }
            breakLine(sl, owner);
            aim(owner);                          // re-aim now that the path is clear
            if (tickCount % 2 == 0) burn(sl, owner);
            if (tickCount % 10 == 1) sl.playSound(null, owner.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.2f, 1.8f);
        } else {
            impactFx();
        }
    }

    private void burn(ServerLevel sl, LivingEntity owner) {
        Vec3 start = position(), dir = direction();
        double len = getLength();
        AABB box = new AABB(start, start.add(dir.scale(len))).inflate(1.0);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, t -> SpellUtil.canHarm(owner, t))) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(start);
            double t = c.dot(dir);
            if (t < 0 || t > len + e.getBbWidth() / 2) continue;
            double halfWidth = 0.25 * Math.min(1.0, t / (len / 3.0));   // matches the model's taper
            if (c.subtract(dir.scale(t)).length() > halfWidth + e.getBbWidth() / 2 + 0.1) continue;
            if (SpellUtil.spellDamage(ModSpells.PLASMA_BEAM.get(), this, owner, e, damage)) e.setSecondsOnFire(3);
        }
    }

    /** Client-only: white-hot sparks and red glow where the beam lands, faint plasma along it. */
    private void impactFx() {
        var r = random;
        Vec3 start = position(), dir = direction();
        double len = getLength();
        Vec3 end = start.add(dir.scale(len));
        for (int i = 0; i < 5; i++) {
            Vec3 v = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(0.18).subtract(dir.scale(0.12));
            level().addParticle(i == 0 ? Vfx.redSpark() : Vfx.plasmaSpark(), true, end.x, end.y, end.z, v.x, v.y, v.z);
        }
        if (tickCount % 3 == 0) level().addParticle(ParticleTypes.SMOKE, end.x, end.y, end.z, 0, 0.03, 0);
        // white-hot streaks racing along the beam from the hand to the target
        for (int i = 0; i < 4; i++) {
            double t = r.nextDouble() * len * 0.8;
            double w = 0.22 * Math.min(1.0, t / (len / 3.0));
            Vec3 p = start.add(dir.scale(t)).add((r.nextDouble() - 0.5) * w, (r.nextDouble() - 0.5) * w, (r.nextDouble() - 0.5) * w);
            Vec3 v = dir.scale(1.6 + r.nextDouble());
            level().addParticle(i % 2 == 0 ? Vfx.plasmaSpark() : Vfx.redSpark(), true, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    /**
     * Instantly break every block along the caster's line of sight, out to the beam's range. The beam stops only at
     * unbreakable blocks (bedrock, barriers...) or where breaking is not allowed. Respects the mobGriefing gamerule,
     * spawn protection and protection mods (BlockEvent.BreakEvent); blocks drop as if mined.
     */
    private void breakLine(ServerLevel sl, LivingEntity owner) {
        if (!sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        Vec3 eye = owner.getEyePosition();
        Vec3 dir = owner.getLookAngle();
        BlockPos last = null;
        int broken = 0;
        for (double t = 0.5; t <= RANGE; t += 0.25) {
            BlockPos pos = BlockPos.containing(eye.add(dir.scale(t)));
            if (pos.equals(last)) continue;
            last = pos;
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() && state.getCollisionShape(sl, pos).isEmpty()) continue;
            if (state.getDestroySpeed(sl, pos) < 0) return;                       // unbreakable: the beam ends here
            if (owner instanceof Player player
                    && (!sl.mayInteract(player, pos) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(sl, pos, state, player))))
                return;                                                          // protected: the beam ends here
            sl.destroyBlock(pos, true, owner);
            if (++broken % 4 == 1) sl.sendParticles(ParticleTypes.LAVA, pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.2, 0.2, 0.2, 0);
        }
        if (broken > 0) sl.playSound(null, owner.blockPosition(), SoundEvents.LAVA_EXTINGUISH, SoundSource.BLOCKS, 0.5f, 1.8f);
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(
                RawAnimation.begin().thenPlay("animation.plasma_beam.ignite").thenLoop("animation.plasma_beam.idle"))));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

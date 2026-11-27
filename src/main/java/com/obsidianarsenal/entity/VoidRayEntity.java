package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.network.NetworkHooks;
import org.jetbrains.annotations.Nullable;

import java.util.HashSet;
import java.util.Set;

/**
 * Abyssal Ray: a wide beam of void from the caster's extended hand, styled on Herobrine's Void Beam (Void Powers): a
 * wide purple haze, a brighter purple band, a thin rim of white light and a thick black core. Everything inside its
 * radius is erased: creatures take heavy damage and are shoved along it, and blocks vanish without drops (a tunnel
 * as wide as the beam, only with mobGriefing on). Kept alive by Iron's continuous-cast pulses, like Plasma Beam.
 */
public class VoidRayEntity extends Entity {
    public static final double RANGE = 40.0;
    /** Radius that erases blocks and hits creatures (the black core plus the white rim is drawn about this wide). */
    public static final double RADIUS = 1.3;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(VoidRayEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> LENGTH = SynchedEntityData.defineId(VoidRayEntity.class, EntityDataSerializers.FLOAT);

    private float damage = 4f;
    private int aliveUntil = 15;

    public VoidRayEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    public static VoidRayEntity spawn(ServerLevel level, LivingEntity owner, float damage) {
        VoidRayEntity e = new VoidRayEntity(ModEntities.VOID_RAY.get(), level);
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
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 200 * 200; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(RANGE); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    /** End point: the first block the caster's crosshair hits (creatures do not stop the ray). */
    public static Vec3 target(LivingEntity owner) {
        Vec3 eye = owner.getEyePosition();
        Vec3 end = eye.add(owner.getLookAngle().scale(RANGE));
        BlockHitResult hit = owner.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return hit.getType() == HitResult.Type.MISS ? end : hit.getLocation();
    }

    private void aim(LivingEntity owner) {
        Vec3 from = PlasmaBeamEntity.handPos(owner);
        Vec3 d = target(owner).subtract(from);
        setPos(from.x, from.y, from.z);
        if (d.lengthSqr() > 1e-6) {
            setYRot((float) Math.toDegrees(Math.atan2(-d.x, d.z)));
            setXRot((float) -Math.toDegrees(Math.atan2(d.y, d.horizontalDistance())));
        }
        if (!level().isClientSide) entityData.set(LENGTH, (float) Math.max(0.5, d.length()));
    }

    public Vec3 direction() { return Vec3.directionFromRotation(getXRot(), getYRot()); }

    @Override
    public void tick() {
        super.tick();
        setOldPosAndRot();
        LivingEntity owner = getOwner();
        if (owner == null || !owner.isAlive()) { if (!level().isClientSide) discard(); return; }
        aim(owner);
        if (level() instanceof ServerLevel sl) {
            if (tickCount > aliveUntil) { discard(); return; }
            if (tickCount % 2 == 0) { erase(sl, owner); aim(owner); hit(sl, owner); }
            if (tickCount % 20 == 1) sl.playSound(null, owner.blockPosition(), SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.6f, 0.55f);
        } else {
            clientFx();
        }
    }

    private void hit(ServerLevel sl, LivingEntity owner) {
        Vec3 start = position(), dir = direction();
        double len = getLength();
        AABB box = new AABB(start, start.add(dir.scale(len))).inflate(RADIUS + 1);
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, box, t -> SpellUtil.canHarm(owner, t))) {
            Vec3 c = e.getBoundingBox().getCenter().subtract(start);
            double t = c.dot(dir);
            if (t < 0 || t > len + 1) continue;
            if (c.subtract(dir.scale(t)).length() > RADIUS + e.getBbWidth() / 2 + 0.2) continue;
            if (SpellUtil.spellDamage(ModSpells.ABYSSAL_RAY.get(), this, owner, e, damage)) {
                e.setDeltaMovement(e.getDeltaMovement().scale(0.5).add(dir.scale(0.35)));
                e.hurtMarked = true;
                Vfx.burst(sl, Vfx.voidWisp(), e.getBoundingBox().getCenter(), 4, 0.4, 0.2);
            }
        }
    }

    /**
     * Erases every block inside the ray's radius along the caster's line of sight (no drops, only with mobGriefing
     * on, never protected or unbreakable blocks). An unbreakable block on the centre line stops the ray there.
     */
    private void erase(ServerLevel sl, LivingEntity owner) {
        if (!com.obsidianarsenal.util.SpellUtil.mayGrief(sl)) return;
        Vec3 eye = owner.getEyePosition(), dir = owner.getLookAngle();
        Vec3 e1 = new Vec3(-dir.z, 0, dir.x);
        if (e1.lengthSqr() < 1e-4) e1 = new Vec3(1, 0, 0);
        e1 = e1.normalize();
        Vec3 e2 = dir.cross(e1).normalize();
        Set<BlockPos> done = new HashSet<>();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
        int erased = 0;
        for (double t = 1.0; t <= RANGE; t += 0.5) {
            Vec3 c = eye.add(dir.scale(t));
            BlockPos cp = BlockPos.containing(c);
            BlockState cs = sl.getBlockState(cp);
            if (!cs.isAir() && cs.getDestroySpeed(sl, cp) < 0) break;
            for (double a = -RADIUS; a <= RADIUS; a += 0.5) {
                for (double b = -RADIUS; b <= RADIUS; b += 0.5) {
                    if (a * a + b * b > RADIUS * RADIUS) continue;
                    BlockPos pos = BlockPos.containing(c.add(e1.scale(a)).add(e2.scale(b)));
                    if (!done.add(pos)) continue;
                    BlockState st = sl.getBlockState(pos);
                    if (st.isAir() || st.getDestroySpeed(sl, pos) < 0) continue;
                    if (owner instanceof Player p && !sl.mayInteract(p, pos)) continue;
                    sl.setBlock(pos, Blocks.AIR.defaultBlockState(), flags);
                    if (++erased % 6 == 1) sl.sendParticles(Vfx.voidWisp(), pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.02);
                }
            }
        }
        if (erased > 0 && tickCount % 6 == 0) sl.playSound(null, owner.blockPosition(), SoundEvents.CHORUS_FLOWER_DEATH, SoundSource.BLOCKS, 0.6f, 0.5f);
    }

    /** Client: void wisps drawn into the ray along its length, and an imploding swirl where it lands. */
    private void clientFx() {
        var r = random;
        Vec3 start = position(), dir = direction();
        double len = getLength();
        Vec3 end = start.add(dir.scale(len));
        for (int i = 0; i < 6; i++) {
            Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(1.6 + r.nextDouble());
            Vec3 p = end.add(o);
            Vec3 v = o.scale(-0.18);
            level().addParticle(i % 2 == 0 ? Vfx.deepPurpleSpark() : Vfx.voidWisp(), true, p.x, p.y, p.z, v.x, v.y, v.z);
        }
        for (int i = 0; i < 5; i++) {
            double t = 1 + r.nextDouble() * Math.max(0.1, len - 1);
            Vec3 side = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(RADIUS * 1.4);
            Vec3 p = start.add(dir.scale(t)).add(side);
            Vec3 v = side.scale(-0.08).add(dir.scale(0.6));
            level().addParticle(i % 2 == 0 ? Vfx.purpleSpark() : Vfx.voidWisp(), true, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }
}

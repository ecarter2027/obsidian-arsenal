package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
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
import net.minecraft.world.entity.ExperienceOrb;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
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
import org.joml.Vector3f;

import java.util.HashSet;
import java.util.Set;

/**
 * The Erasure orb: a black sphere with a thin glowing red outline that erases what it touches. Three ways to use it
 * (the three Erasure spells):
 *  ORB    - charges at the caster's hand, then flies dead straight (no gravity) until it hits a block or a creature and
 *           swells a few blocks wider, erasing every block and creature inside
 *  VOLLEY - a smaller orb charging in a magic circle that appeared behind the caster; it fires at the crosshair, the
 *           circle fades, and the spell opens the next circle
 *  GRASP  - held in front of the caster's outstretched hand and steered by where they look, erasing only what it
 *           passes through
 * Erased blocks vanish without drops (only with mobGriefing on; never unbreakable or protected blocks).
 */
public class ErasureOrbEntity extends Entity {
    public static final int ORB = 0, VOLLEY = 1, GRASP = 2;
    public static final int CHARGE = 0, FLY = 1, BLAST = 2, HELD = 3;
    /** Ticks the impact sphere takes to reach full size, then to fade out. */
    public static final int EXPAND = 6, FADE = 6;
    public static final double GRASP_DISTANCE = 6.5;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> MODE = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> PHASE_AT = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> CHARGE_TICKS = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Float> SIZE = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.FLOAT);
    private static final EntityDataAccessor<Float> BLAST_R = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.FLOAT);
    /** VOLLEY: where its magic circle stands and which way it faces (the circle stays put once the orb flies). */
    private static final EntityDataAccessor<Vector3f> ANCHOR = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.VECTOR3);
    private static final EntityDataAccessor<Vector3f> AIM = SynchedEntityData.defineId(ErasureOrbEntity.class, EntityDataSerializers.VECTOR3);

    private AbstractSpell spell;
    private float damage = 10f;
    private double speed = 2.2;
    private Vec3 dir = new Vec3(0, 0, 1);
    /** VOLLEY: the circle's place relative to the caster (right, up, back) while it charges. */
    private double offRight, offUp, offBack;
    private int aliveUntil = 15;
    private Runnable onFired;
    private final Set<Integer> struck = new HashSet<>();

    public ErasureOrbEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
        this.noCulling = true;
    }

    /** A new orb, charging for {@code chargeTicks} (ORB / VOLLEY) or held (GRASP). */
    public static ErasureOrbEntity create(ServerLevel level, LivingEntity owner, AbstractSpell spell, int mode, double size, double blastRadius,
                                          float damage, int chargeTicks) {
        ErasureOrbEntity e = new ErasureOrbEntity(ModEntities.ERASURE_ORB.get(), level);
        e.spell = spell;
        e.damage = damage;
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(MODE, mode);
        e.entityData.set(SIZE, (float) size);
        e.entityData.set(BLAST_R, (float) blastRadius);
        e.entityData.set(CHARGE_TICKS, Math.max(1, chargeTicks));
        e.entityData.set(PHASE, mode == GRASP ? HELD : CHARGE);
        if (mode == VOLLEY) {
            var r = level.random;
            e.offRight = (r.nextBoolean() ? 1 : -1) * (0.7 + r.nextDouble() * 2.0);
            e.offUp = 0.4 + r.nextDouble() * 2.4;
            e.offBack = 1.0 + r.nextDouble() * 1.4;
            e.speed = 2.6;
        }
        Vec3 at = e.heldPos(owner);
        e.setPos(at.x, at.y, at.z);
        e.setOldPosAndRot();
        level.addFreshEntity(e);
        return e;
    }

    public void onFired(Runnable r) { this.onFired = r; }
    public void keepAlive(float damage) { this.damage = damage; this.aliveUntil = tickCount + 15; }

    public int mode() { return entityData.get(MODE); }
    public int phase() { return entityData.get(PHASE); }
    public int phaseAt() { return entityData.get(PHASE_AT); }
    public int chargeTicks() { return entityData.get(CHARGE_TICKS); }
    public float size() { return entityData.get(SIZE); }
    public float blastRadius() { return entityData.get(BLAST_R); }
    public Vec3 anchor() { Vector3f v = entityData.get(ANCHOR); return new Vec3(v.x, v.y, v.z); }
    public Vec3 aim() { Vector3f v = entityData.get(AIM); return new Vec3(v.x, v.y, v.z); }

    @Nullable
    public LivingEntity getOwner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof LivingEntity le ? le : null;
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(MODE, ORB);
        entityData.define(PHASE, CHARGE);
        entityData.define(PHASE_AT, 0);
        entityData.define(CHARGE_TICKS, 20);
        entityData.define(SIZE, 0.7f);
        entityData.define(BLAST_R, 3f);
        entityData.define(ANCHOR, new Vector3f());
        entityData.define(AIM, new Vector3f(0, 0, 1));
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(8); }
    @Override public Packet<ClientGamePacketListener> getAddEntityPacket() { return NetworkHooks.getEntitySpawningPacket(this); }

    private void setPhase(int p) {
        entityData.set(PHASE, p);
        entityData.set(PHASE_AT, tickCount);
    }

    /** Where the orb sits while charging or held. */
    private Vec3 heldPos(LivingEntity owner) {
        Vec3 look = owner.getLookAngle();
        return switch (mode()) {
            case VOLLEY -> {
                float yaw = owner.getYRot() * ((float) Math.PI / 180f);
                Vec3 fwd = new Vec3(-Math.sin(yaw), 0, Math.cos(yaw)), right = new Vec3(-Math.cos(yaw), 0, -Math.sin(yaw));
                yield owner.position().add(0, owner.getBbHeight() * 0.75, 0).add(right.scale(offRight)).add(0, offUp, 0).subtract(fwd.scale(offBack));
            }
            case GRASP -> owner.getEyePosition().add(look.scale(GRASP_DISTANCE));
            default -> PlasmaBeamEntity.handPos(owner).add(look.scale(0.55 + size()));
        };
    }

    /** Where the caster's crosshair points: the creature or block it lands on, or far ahead. */
    private static Vec3 crosshair(LivingEntity owner) {
        LivingEntity t = SpellUtil.raycastLiving(owner, 64);
        if (t != null) return t.getBoundingBox().getCenter();
        Vec3 eye = owner.getEyePosition(), end = eye.add(owner.getLookAngle().scale(64));
        BlockHitResult b = owner.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        return b.getType() == HitResult.Type.MISS ? end : b.getLocation();
    }

    /** Launches a charging orb along {@code direction}. */
    public void fire(Vec3 direction) {
        if (phase() != CHARGE) return;
        dir = direction.normalize();
        entityData.set(ANCHOR, position().toVector3f());
        entityData.set(AIM, dir.toVector3f());
        setPhase(FLY);
        boolean big = mode() == ORB;
        level().playSound(null, getX(), getY(), getZ(), big ? SoundEvents.WARDEN_SONIC_BOOM : SoundEvents.SHULKER_SHOOT, SoundSource.PLAYERS,
                big ? 1.2f : 0.9f, big ? 1.5f : 0.55f);
        if (onFired != null) { onFired.run(); onFired = null; }
    }

    /** Swells into the erasing sphere where it is. */
    public void detonate() {
        if (phase() == BLAST) return;
        setPhase(BLAST);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.ENDERMAN_TELEPORT, SoundSource.PLAYERS, 2.5f, 0.4f);
        level().playSound(null, getX(), getY(), getZ(), SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, mode() == ORB ? 2.5f : 1.0f, 1.7f);
    }

    @Override
    public void tick() {
        super.tick();
        if (level().isClientSide) { clientFx(); return; }
        ServerLevel sl = (ServerLevel) level();
        LivingEntity owner = getOwner();
        int phase = phase(), age = tickCount - phaseAt();
        switch (phase) {
            case CHARGE -> {
                if (owner == null || !owner.isAlive() || age > chargeTicks() + 60) { discard(); return; }
                Vec3 at = heldPos(owner);
                setPos(at.x, at.y, at.z);
                if (mode() == VOLLEY) {
                    Vec3 aimDir = crosshair(owner).subtract(at).normalize();
                    entityData.set(AIM, aimDir.toVector3f());
                    entityData.set(ANCHOR, at.toVector3f());
                    if (age >= chargeTicks()) fire(aimDir);
                } else if (age % 6 == 0) {
                    sl.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_POWER_SELECT, SoundSource.PLAYERS, 0.8f, 0.5f + age * 0.04f);
                }
            }
            case FLY -> {
                Vec3 from = position(), to = from.add(dir.scale(speed));
                double s = size();
                BlockHitResult b = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
                Vec3 stop = b.getType() == HitResult.Type.MISS ? null : b.getLocation();
                AABB sweep = new AABB(from, to).inflate(s + 1);
                for (Entity e : sl.getEntities(this, sweep, e -> e instanceof LivingEntity le && SpellUtil.canHarm(owner, le))) {
                    var hit = e.getBoundingBox().inflate(s).clip(from, to);
                    if (hit.isPresent() && (stop == null || from.distanceToSqr(hit.get()) < from.distanceToSqr(stop))) stop = hit.get();
                }
                if (stop != null) { setPos(stop.x, stop.y, stop.z); detonate(); return; }
                setPos(to.x, to.y, to.z);
                if (age > 60) detonate();
            }
            case BLAST -> {
                if (age > EXPAND + FADE) { discard(); return; }
                if (age <= EXPAND) eraseSphere(sl, owner, position(), blastNow(age), true);
            }
            case HELD -> {
                if (owner == null || !owner.isAlive() || tickCount > aliveUntil) { discard(); return; }
                Vec3 want = heldPos(owner), at = position().lerp(want, 0.4);
                setPos(at.x, at.y, at.z);
                eraseSphere(sl, owner, at, size(), tickCount % 2 == 0);
                if (tickCount % 20 == 1) sl.playSound(null, at.x, at.y, at.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 1.4f, 0.5f);
            }
        }
    }

    /** The impact sphere's radius {@code age} ticks into the blast (a fast ease out). */
    public double blastNow(float age) {
        float k = Math.min(1f, Math.max(0f, age / EXPAND));
        k = 1 - (1 - k) * (1 - k) * (1 - k);
        return size() + (blastRadius() - size()) * k;
    }

    /** Erases the blocks within {@code r} of {@code c}; when {@code harm}, strikes the creatures inside too. */
    private void eraseSphere(ServerLevel sl, @Nullable LivingEntity owner, Vec3 c, double r, boolean harm) {
        if (harm) {
            AABB box = new AABB(c, c).inflate(r + 1);
            for (Entity e : sl.getEntities(this, box)) {
                if (e.getBoundingBox().getCenter().distanceTo(c) > r + e.getBbWidth() / 2) continue;
                if (e instanceof LivingEntity le) {
                    if (!SpellUtil.canHarm(owner, le)) continue;
                    if (phase() == BLAST && !struck.add(le.getId())) continue;
                    if (spell != null && SpellUtil.spellDamage(spell, this, owner, le, damage))
                        Vfx.burst(sl, Vfx.redSpark(), le.getBoundingBox().getCenter(), 10, 0.5, 0.3);
                } else if (e instanceof ItemEntity || e instanceof ExperienceOrb || e instanceof FallingBlockEntity
                        || e instanceof Projectile && !(e instanceof ErasureOrbEntity)) {
                    e.discard();
                }
            }
        }
        if (!sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        int R = (int) Math.ceil(r);
        BlockPos cb = BlockPos.containing(c);
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_SUPPRESS_DROPS;
        int erased = 0;
        for (int dx = -R; dx <= R; dx++) for (int dy = -R; dy <= R; dy++) for (int dz = -R; dz <= R; dz++) {
            m.set(cb.getX() + dx, cb.getY() + dy, cb.getZ() + dz);
            if (new Vec3(m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5).distanceToSqr(c) > r * r) continue;
            if (!sl.isInWorldBounds(m)) continue;
            BlockState st = sl.getBlockState(m);
            if (st.isAir() || st.getDestroySpeed(sl, m) < 0) continue;
            if (owner instanceof Player p && !sl.mayInteract(p, m)) continue;
            sl.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
            if (++erased % 5 == 1) sl.sendParticles(Vfx.redSpark(), m.getX() + 0.5, m.getY() + 0.5, m.getZ() + 0.5, 1, 0.3, 0.3, 0.3, 0.05);
        }
    }

    /** Client: red sparks sucked into the charging orb, a dark trail behind it in flight, a burst as it erases. */
    private void clientFx() {
        var r = random;
        int phase = phase();
        float age = tickCount - phaseAt();
        double s = size();
        Vec3 c = position();
        if (phase == CHARGE || phase == HELD) {
            int n = mode() == ORB ? 3 : 1;
            for (int i = 0; i < n; i++) {
                Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize().scale(s * 3 + r.nextDouble());
                Vec3 v = o.scale(-0.15);
                level().addParticle(i % 2 == 0 ? Vfx.redSpark() : Vfx.voidWisp(), true, c.x + o.x, c.y + o.y, c.z + o.z, v.x, v.y, v.z);
            }
        } else if (phase == FLY) {
            Vec3 d = aim();
            for (int i = 0; i < 3; i++) {
                Vec3 p = c.subtract(d.scale(r.nextDouble() * 2)).add(r.nextGaussian() * s * 0.4, r.nextGaussian() * s * 0.4, r.nextGaussian() * s * 0.4);
                level().addParticle(i == 0 ? Vfx.redSpark() : Vfx.voidWisp(), true, p.x, p.y, p.z, 0, 0, 0);
            }
        } else if (phase == BLAST && age < 1) {
            double R = blastRadius();
            for (int i = 0; i < 40 + R * 12; i++) {
                Vec3 o = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
                Vec3 v = o.scale(0.25 + R * 0.12 * r.nextDouble());
                level().addParticle(i % 3 == 0 ? Vfx.redSpark() : (i % 3 == 1 ? Vfx.voidWisp() : Vfx.plasmaSpark()), true, c.x, c.y, c.z, v.x, v.y, v.z);
            }
        }
    }
}

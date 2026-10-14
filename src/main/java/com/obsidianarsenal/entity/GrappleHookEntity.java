package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
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
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.Tags;
import org.jetbrains.annotations.Nullable;
import org.joml.Vector3f;

/**
 * Obsidian Gauntlet grappling hook (right-click, 80 blocks).
 *  FLYING : the claw shoots from the hand to the aimed point along a glowing tether
 *  PULL   : hooked a block  -> the player is reeled in to it (no fall damage)
 *           hooked a mob    -> the mob is yanked to the player (bosses: the player is reeled to them instead)
 * Right-click again or sneak to let go. The owner's own client applies the reel-in motion so it feels instant.
 */
public class GrappleHookEntity extends Entity {
    public static final double RANGE = 80.0, FLY_SPEED = 6.0;
    private static final int MAX_PULL = 70;

    public static final int FLYING = 0, PULL_SELF = 1, PULL_TARGET = 2;

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> HOOKED_ID = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Vector3f> TARGET = SynchedEntityData.defineId(GrappleHookEntity.class, EntityDataSerializers.VECTOR3);

    private boolean hitSomething;
    private int pullTicks;

    public GrappleHookEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    /** Fire from the player's hand at whatever they aim at within range. */
    public static GrappleHookEntity fire(ServerLevel level, Player owner) {
        GrappleHookEntity e = new GrappleHookEntity(ModEntities.GRAPPLE_HOOK.get(), level);
        Vec3 eye = owner.getEyePosition(), end = eye.add(owner.getLookAngle().scale(RANGE));
        BlockHitResult bh = level.clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, owner));
        Vec3 blockEnd = bh.getType() == HitResult.Type.MISS ? end : bh.getLocation();
        EntityHitResult eh = ProjectileUtil.getEntityHitResult(level, owner, eye, blockEnd,
                new AABB(eye, blockEnd).inflate(1.0), t -> t instanceof LivingEntity && t.isAlive() && !t.isSpectator() && t != owner);
        Vec3 target;
        if (eh != null) {
            target = eh.getLocation();
            e.entityData.set(HOOKED_ID, eh.getEntity().getId());
            e.hitSomething = true;
        } else {
            target = blockEnd;
            e.hitSomething = bh.getType() != HitResult.Type.MISS;
        }
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(TARGET, new Vector3f((float) target.x, (float) target.y, (float) target.z));
        Vec3 p = PlasmaBeamEntity.handPos(owner);
        e.setPos(p.x, p.y, p.z);
        level.addFreshEntity(e);
        level.playSound(null, owner.blockPosition(), SoundEvents.CROSSBOW_SHOOT, SoundSource.PLAYERS, 1.0f, 0.6f);
        level.playSound(null, owner.blockPosition(), SoundEvents.CHAIN_PLACE, SoundSource.PLAYERS, 1.0f, 0.8f);
        return e;
    }

    @Nullable public Entity getOwner() { return level().getEntity(entityData.get(OWNER_ID)); }
    @Nullable public Entity getHooked() { int id = entityData.get(HOOKED_ID); return id < 0 ? null : level().getEntity(id); }
    public int getState() { return entityData.get(STATE); }
    private Vec3 target() { Vector3f v = entityData.get(TARGET); return new Vec3(v.x, v.y, v.z); }

    /** Where the claw is anchored right now (follows a hooked mob). */
    public Vec3 anchor() {
        Entity h = getHooked();
        return h != null ? h.position().add(0, h.getBbHeight() * 0.6, 0) : target();
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(HOOKED_ID, -1);
        entityData.define(STATE, FLYING);
        entityData.define(TARGET, new Vector3f());
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 160 * 160; }
    @Override public AABB getBoundingBoxForCulling() {
        Entity o = getOwner();
        return o == null ? super.getBoundingBoxForCulling() : new AABB(position(), o.position()).inflate(1);
    }

    /** Let go (right-click again, sneak, arrival or timeout). */
    public void release() {
        Entity o = getOwner();
        if (o != null) o.fallDistance = 0;
        discard();
    }

    @Override
    public void tick() {
        super.tick();
        Entity owner = getOwner();
        if (owner == null || !owner.isAlive() || owner.level() != level()) { if (!level().isClientSide) discard(); return; }
        if (!level().isClientSide && (tickCount > 20 + RANGE / FLY_SPEED + MAX_PULL || owner.distanceToSqr(this) > 110 * 110)) { release(); return; }

        int state = getState();
        if (state == FLYING) {
            Vec3 a = anchor(), here = position(), d = a.subtract(here);
            double len = d.length();
            if (len <= FLY_SPEED) {
                setPos(a.x, a.y, a.z);
                if (!level().isClientSide) latch((ServerLevel) level(), owner);
            } else {
                Vec3 n = here.add(d.scale(FLY_SPEED / len));
                setPos(n.x, n.y, n.z);
            }
            return;
        }

        // anchored
        Vec3 a = anchor();
        setPos(a.x, a.y, a.z);
        pullTicks++;
        if (owner instanceof Player p && p.isShiftKeyDown() && pullTicks > 3) { if (!level().isClientSide) release(); return; }

        if (state == PULL_SELF) {
            Vec3 to = a.subtract(owner.position().add(0, owner.getBbHeight() * 0.5, 0));
            double dist = to.length();
            owner.fallDistance = 0;
            if (dist < 1.6 || pullTicks > MAX_PULL) {
                if (owner instanceof Player p && p.isLocalPlayer())
                    owner.setDeltaMovement(owner.getDeltaMovement().scale(0.5).add(0, 0.35, 0));   // small hop over the ledge
                if (!level().isClientSide) release();
                return;
            }
            // only the controlling client moves a player; the server keeps fall damage off
            if (owner instanceof Player p && p.isLocalPlayer())
                owner.setDeltaMovement(to.scale(1.0 / dist).scale(Math.min(2.4, 0.6 + dist * 0.12)).add(0, 0.04, 0));
        } else if (state == PULL_TARGET && !level().isClientSide) {
            Entity h = getHooked();
            if (h == null || !h.isAlive()) { release(); return; }
            Vec3 to = owner.position().add(owner.getLookAngle().scale(1.6)).subtract(h.position());
            double dist = to.length();
            if (dist < 1.5 || pullTicks > MAX_PULL) { h.setDeltaMovement(h.getDeltaMovement().scale(0.2)); release(); return; }
            h.setDeltaMovement(to.scale(1.0 / dist).scale(Math.min(2.0, 0.5 + dist * 0.12)).add(0, 0.05, 0));
            h.hurtMarked = true;
            h.fallDistance = 0;
        }
    }

    private void latch(ServerLevel sl, Entity owner) {
        Entity h = getHooked();
        if (!hitSomething && h == null) {     // aimed at the sky / beyond range: claw just retracts
            sl.playSound(null, blockPosition(), SoundEvents.CHAIN_BREAK, SoundSource.PLAYERS, 0.8f, 1.2f);
            discard();
            return;
        }
        boolean boss = h != null && h.getType().is(Tags.EntityTypes.BOSSES);
        entityData.set(STATE, h != null && !boss ? PULL_TARGET : PULL_SELF);
        sl.playSound(null, blockPosition(), SoundEvents.ANVIL_LAND, SoundSource.PLAYERS, 0.6f, 1.6f);
        sl.playSound(null, owner.blockPosition(), SoundEvents.CHAIN_HIT, SoundSource.PLAYERS, 1.0f, 0.7f);
        Vfx.burst(sl, Vfx.purpleSpark(), position(), 14, 0.3, 0.25);
    }
}

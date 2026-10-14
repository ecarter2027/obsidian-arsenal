package com.obsidianarsenal.entity;

import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import net.minecraft.core.BlockPos;
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
import net.minecraft.world.entity.item.FallingBlockEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.common.Tags;
import net.minecraftforge.event.level.BlockEvent;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

/**
 * Black Hole projectile. Everything scales with the spell tier (1..{@link #MAX_TIER}):
 *  CHARGING : gathers at the caster's hand while the Iron's cast bar fills ({@link #chargeTicks(int)} ticks), growing
 *  FLYING   : fired along the caster's aim as a small, inert seed (no pull while in flight)
 *  ANCHORED : the moment it hits a block or creature (or reaches max range) it erupts to full size and starts
 *             tearing up blocks (which shatter and are deleted at the core) and dragging in mobs until its duration runs out and it collapses.
 * Tier 10: 20 blocks wide, 40 block pull radius, 20 block rip radius, 50% execute chance at the core, 30 s.
 */
public class BlackHoleEntity extends Entity implements GeoEntity {
    public static final int MAX_TIER = 10;
    /** Visual width (lens diameter) of the model at scale 1. */
    public static final float BASE_WIDTH = 8.4f;
    public static final double SPEED = 0.7, MAX_RANGE = 48.0;
    public static final int COLLAPSE = 16, ERUPT = 20;
    private static final int FLIGHT_BUDGET = (int) (MAX_RANGE / SPEED) + 10;

    public static final int CHARGING = 0, FLYING = 1, ANCHORED = 2;

    // ---- per-tier scaling (t = 0 at tier 1, 1 at tier 10)
    private static float t(int tier) { return (Mth.clamp(tier, 1, MAX_TIER) - 1) / (float) (MAX_TIER - 1); }
    /** Full visual width in blocks: 6 -> 20. */
    public static float width(int tier) { return 6f + 14f * t(tier); }
    public static float scale(int tier) { return width(tier) / BASE_WIDTH; }
    /** Mobs / items / projectiles inside this radius get dragged in: 16 -> 40. */
    public static double pullRadius(int tier) { return 16 + 24 * t(tier); }
    /** Blocks inside this radius get torn out: 7 -> 20. */
    public static double ripRadius(int tier) { return 7 + 13 * t(tier); }
    /** Anything this close to the centre is being crushed: 1.6 -> 3.8. */
    public static double coreRadius(int tier) { return 1.6 + 2.2 * t(tier); }
    /** Chance per crush pulse (every 8 ticks) to instantly kill a non-boss: 5% -> 50%. */
    public static float executeChance(int tier) { return 0.05f * Mth.clamp(tier, 1, MAX_TIER); }
    /** Ticks the anchored hole lives: 8.4 s -> 30 s. */
    public static int duration(int tier) { return Math.round(20 * (6 + 2.4f * Mth.clamp(tier, 1, MAX_TIER))); }
    /** Charge-up at the hand: 1.6 s -> 2.5 s (bigger holes take longer to form). */
    public static int chargeTicks(int tier) { return 30 + 2 * Mth.clamp(tier, 1, MAX_TIER); }
    /** Blocks torn loose per tick while anchored: 16 -> 52. */
    private static int ripsPerTick(int tier) { return 12 + 4 * Mth.clamp(tier, 1, MAX_TIER); }
    /** Live debris cap (keeps the server sane): 240 -> 600. */
    private static int maxDebris(int tier) { return 200 + 40 * Mth.clamp(tier, 1, MAX_TIER); }

    private static final EntityDataAccessor<Integer> OWNER_ID = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> LIFETIME = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> STATE = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> TIER = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);
    private static final EntityDataAccessor<Integer> ANCHOR_AT = SynchedEntityData.defineId(BlackHoleEntity.class, EntityDataSerializers.INT);

    private float damage = 4f;
    private Vec3 launchPos = Vec3.ZERO;
    private int debris = 0;
    private int launchTick = 0;
    private int crunches = 0;
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public BlackHoleEntity(EntityType<?> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public static BlackHoleEntity spawn(ServerLevel level, LivingEntity owner, int tier, float damage) {
        BlackHoleEntity e = new BlackHoleEntity(ModEntities.BLACK_HOLE.get(), level);
        e.entityData.set(OWNER_ID, owner.getId());
        e.entityData.set(TIER, Mth.clamp(tier, 1, MAX_TIER));
        // provisional lifetime; replaced with anchor time + duration the moment it lands
        e.entityData.set(LIFETIME, chargeTicks(tier) * 3 + 40 + FLIGHT_BUDGET + duration(tier));
        e.damage = damage;
        Vec3 p = chargePoint(owner);
        e.setPos(p.x, p.y, p.z);
        level.addFreshEntity(e);
        return e;
    }

    private static Vec3 chargePoint(LivingEntity owner) {
        return PlasmaBeamEntity.handPos(owner).add(owner.getLookAngle().scale(0.9));
    }

    @Nullable
    public LivingEntity getOwner() {
        return level().getEntity(entityData.get(OWNER_ID)) instanceof LivingEntity le ? le : null;
    }

    public int getLifetime() { return entityData.get(LIFETIME); }
    public int getState() { return entityData.get(STATE); }
    public int getTier() { return entityData.get(TIER); }
    /** Model / lens scale for this hole's tier (1.0 = BASE_WIDTH blocks wide). */
    public float tierScale() { return scale(getTier()); }

    /** 0..1 growth: small seed while charging/flying, erupts to full size on impact, collapses at the end. */
    public float sizeFactor(float partialTick) {
        float t = tickCount + partialTick;
        int charge = chargeTicks(getTier());
        if (getState() == CHARGING) return 0.22f * Mth.sin(Mth.clamp(t / charge, 0f, 1f) * Mth.HALF_PI);
        if (getState() == FLYING) return 0.22f;
        float life = getLifetime();
        if (t > life - COLLAPSE) return Math.max(0f, (life - t) / COLLAPSE);
        float e = Mth.clamp((t - entityData.get(ANCHOR_AT)) / ERUPT, 0f, 1f);
        return 0.22f + 0.78f * (1 - (1 - e) * (1 - e));
    }

    @Override protected void defineSynchedData() {
        entityData.define(OWNER_ID, -1);
        entityData.define(LIFETIME, 400);
        entityData.define(STATE, CHARGING);
        entityData.define(TIER, 1);
        entityData.define(ANCHOR_AT, 0);
    }

    @Override protected void readAdditionalSaveData(CompoundTag tag) {}
    @Override protected void addAdditionalSaveData(CompoundTag tag) {}
    @Override public boolean shouldBeSaved() { return false; }
    @Override public boolean isPickable() { return false; }
    /** The model is up to 20 blocks wide; don't let the 1x1 hitbox cull it. */
    @Override public AABB getBoundingBoxForCulling() { return getBoundingBox().inflate(width(getTier()) * 0.6); }
    @Override public boolean shouldRenderAtSqrDistance(double d) { return d < 256 * 256; }

    @Override
    public void tick() {
        super.tick();
        if (level() instanceof ServerLevel sl) serverTick(sl);
        else clientTick();
    }

    // ------------------------------------------------------------------------------------------------ server
    private void serverTick(ServerLevel sl) {
        LivingEntity owner = getOwner();
        int state = getState();
        int tier = getTier();

        if (state == CHARGING) {   // held at the hand while the Iron's cast bar fills; the spell calls launch()
            if (owner == null || !owner.isAlive() || tickCount > chargeTicks(tier) * 3 + 40) { discard(); return; }
            Vec3 p = chargePoint(owner);
            setPos(p.x, p.y, p.z);
            if (tickCount % 4 == 0) sl.playSound(null, p.x, p.y, p.z, SoundEvents.PORTAL_AMBIENT, SoundSource.PLAYERS, 0.7f, 1.4f + Math.min(tickCount, 60) / 60f);
            return;
        }

        if (state == FLYING) {      // inert seed until it hits something
            fly(sl, owner);
            if (getState() == FLYING && tickCount > launchTick + FLIGHT_BUDGET) anchor(sl, position());
            return;
        }

        Vec3 c = position();
        float f = sizeFactor(0);
        double pullR = pullRadius(tier), core = coreRadius(tier) * Math.max(0.4f, f);
        if (tickCount % 30 == 0) sl.playSound(null, c.x, c.y, c.z, SoundEvents.BEACON_AMBIENT, SoundSource.PLAYERS, 3f, 0.4f);

        if (tickCount % 20 == 0)   // recount live debris (pieces can also land or time out on their own)
            debris = sl.getEntitiesOfClass(FallingBlockEntity.class, new AABB(c, c).inflate(pullR), FallingBlockEntity::isNoGravity).size();
        crunches = 0;
        pull(sl, owner, c, f, pullR, core);
        ripBlocks(sl, owner, c, f, tier);

        if (tickCount % 8 == 0) {
            float exec = executeChance(tier);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(core), t -> t != owner && t.isAlive() && SpellUtil.canHarm(owner, t))) {
                if (e.position().add(0, e.getBbHeight() * 0.5, 0).distanceTo(c) > core) continue;
                if (!e.getType().is(Tags.EntityTypes.BOSSES) && sl.random.nextFloat() < exec) {
                    e.invulnerableTime = 0;
                    e.hurt(ModSpells.BLACK_HOLE.get().getDamageSource(this, owner), Float.MAX_VALUE);
                    if (e.isAlive() && !(e instanceof Player)) e.kill();
                    Vfx.burst(sl, Vfx.voidWisp(), e.position().add(0, e.getBbHeight() * 0.5, 0), 30, 0.4, 0.3);
                    sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_HEARTBEAT, SoundSource.PLAYERS, 2f, 0.5f);
                } else {
                    SpellUtil.spellDamage(ModSpells.BLACK_HOLE.get(), this, owner, e, damage * f);
                }
            }
        }
        if (tickCount >= getLifetime()) {
            collapse(sl, owner, tier);
            discard();
        }
    }

    /** Fire the charged seed along the owner's aim (called when the Iron's cast completes). */
    public void launch() {
        if (getState() != CHARGING || !(level() instanceof ServerLevel sl)) return;
        LivingEntity owner = getOwner();
        Vec3 dir = owner != null ? owner.getLookAngle() : getDeltaMovement().normalize();
        if (owner != null) { Vec3 p = chargePoint(owner); setPos(p.x, p.y, p.z); }
        setDeltaMovement(dir.scale(SPEED));
        launchPos = position();
        launchTick = tickCount;
        entityData.set(STATE, FLYING);
        Vec3 p = position();
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.WARDEN_SONIC_CHARGE, SoundSource.PLAYERS, 1.2f, 1.4f);
        sl.playSound(null, p.x, p.y, p.z, SoundEvents.END_PORTAL_SPAWN, SoundSource.PLAYERS, 0.8f, 0.7f);
    }

    private void fly(ServerLevel sl, @Nullable LivingEntity owner) {
        Vec3 from = position(), v = getDeltaMovement(), to = from.add(v);
        BlockHitResult hit = sl.clip(new ClipContext(from, to, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, this));
        boolean hitMob = !sl.getEntitiesOfClass(LivingEntity.class, new AABB(from, to).inflate(0.8), t -> t != owner && t.isAlive() && !t.isSpectator()).isEmpty();
        if (hit.getType() == HitResult.Type.BLOCK) {
            Vec3 stop = hit.getLocation().subtract(v.normalize().scale(0.6));
            anchor(sl, stop);
        } else if (hitMob || to.distanceTo(launchPos) > MAX_RANGE) {
            anchor(sl, to);
        } else {
            setPos(to.x, to.y, to.z);
        }
    }

    private void anchor(ServerLevel sl, Vec3 at) {
        setPos(at.x, at.y, at.z);
        setDeltaMovement(Vec3.ZERO);
        entityData.set(ANCHOR_AT, tickCount);
        entityData.set(LIFETIME, tickCount + duration(getTier()));
        entityData.set(STATE, ANCHORED);
        float vol = 1.2f + 0.15f * getTier();
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, vol, 0.4f);
        sl.playSound(null, at.x, at.y, at.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, vol, 0.35f);
        Vfx.burst(sl, Vfx.voidWisp(), at, 40 + 8 * getTier(), 0.8, 0.9);
    }

    /** Gravity: everything within the pull radius is dragged in, harder the closer it is, with an orbital swirl. */
    private void pull(ServerLevel sl, @Nullable LivingEntity owner, Vec3 c, float f, double pullR, double core) {
        for (Entity e : sl.getEntities(this, new AABB(c, c).inflate(pullR), t -> !(t instanceof BlackHoleEntity) && t != owner && !t.isSpectator())) {
            if (e instanceof Player p && (p.isCreative() || p.isSpectator())) continue;
            if (e instanceof LivingEntity le && !SpellUtil.canHarm(owner, le)) continue;
            Vec3 to = c.subtract(e.position().add(0, e.getBbHeight() * 0.5, 0));
            double d = to.length();
            if (d > pullR || d < 1e-3) continue;
            Vec3 n = to.scale(1 / d);
            double k = 1 - d / pullR;
            double strength = (0.12 + 0.55 * k * k) * f;
            boolean loose = e instanceof ItemEntity || e instanceof Projectile || e instanceof FallingBlockEntity;
            if (loose) strength *= 1.8;
            Vec3 swirl = new Vec3(-n.z, 0, n.x).scale(0.08 * f);
            Vec3 v = e.getDeltaMovement().scale(0.84).add(n.scale(strength)).add(swirl);
            double cap = loose ? 2.4 : 1.6;
            if (v.lengthSqr() > cap * cap) v = v.normalize().scale(cap);
            e.setDeltaMovement(v);
            e.hurtMarked = true;
            e.fallDistance = 0;
            // swallowed
            if (loose && d < core + v.length() * 0.5) {
                if (e instanceof FallingBlockEntity fb) {
                    shatter(sl, fb);
                    debris = Math.max(0, debris - 1);
                }
                e.discard();
            }
        }
    }

    /** A block reaching the core breaks apart (block-break particles + sound, capped per tick) and is deleted - no drops. */
    private void shatter(ServerLevel sl, FallingBlockEntity fb) {
        if (crunches++ < 8) sl.levelEvent(2001, fb.blockPosition(), Block.getId(fb.getBlockState()));
    }

    /**
     * Tear blocks out of the world and send them flying into the hole as falling-block debris (consumed at the core).
     * Skips unbreakable blocks, blocks with inventories / block entities, and fluids. Respects mobGriefing,
     * spawn protection and protection mods.
     */
    private void ripBlocks(ServerLevel sl, @Nullable LivingEntity owner, Vec3 c, float f, int tier) {
        if (f < 0.4f || !sl.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        int cap = maxDebris(tier);
        if (debris >= cap) return;
        var r = sl.random;
        double radius = ripRadius(tier) * f;
        int tries = ripsPerTick(tier) * 3, ripped = 0, want = ripsPerTick(tier);
        for (int i = 0; i < tries && ripped < want && debris < cap; i++) {
            // random direction, distance weighted inward so the hole eats its way outward
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            double dist = radius * Math.sqrt(r.nextDouble());
            BlockPos pos = BlockPos.containing(c.add(d.scale(dist)));
            BlockState state = sl.getBlockState(pos);
            if (state.isAir() || !state.getFluidState().isEmpty() || state.hasBlockEntity()) continue;
            float hardness = state.getDestroySpeed(sl, pos);
            if (hardness < 0 || hardness >= 50f) continue;
            if (owner instanceof Player p && (!sl.mayInteract(p, pos) || MinecraftForge.EVENT_BUS.post(new BlockEvent.BreakEvent(sl, pos, state, p)))) continue;
            FallingBlockEntity fb = FallingBlockEntity.fall(sl, pos, state);
            fb.dropItem = false;
            fb.time = 1;
            fb.setNoGravity(true);
            fb.noPhysics = true;       // flies through terrain straight into the hole and can never land / re-place itself
            Vec3 in = c.subtract(Vec3.atCenterOf(pos)).normalize().scale(0.5);
            fb.setDeltaMovement(in.add(0, 0.1, 0));
            debris++;
            ripped++;
        }
    }

    /** Final implosion: hits everything close by, flings it outward and erases leftover debris. */
    private void collapse(ServerLevel sl, @Nullable LivingEntity owner, int tier) {
        Vec3 c = position();
        crunches = 0;
        double blast = 4 + 8 * t(tier);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 3.0f + 0.3f * tier, 0.5f);
        sl.playSound(null, c.x, c.y, c.z, SoundEvents.WARDEN_SONIC_BOOM, SoundSource.PLAYERS, 2.0f + 0.3f * tier, 0.6f);
        Vfx.burst(sl, Vfx.voidWisp(), c, 110 + 15 * tier, 1.2 * scale(tier), 1.0);
        Vfx.burst(sl, Vfx.purpleSpark(), c, 60 + 8 * tier, 0.6 * scale(tier), 0.7);
        for (FallingBlockEntity fb : sl.getEntitiesOfClass(FallingBlockEntity.class, new AABB(c, c).inflate(pullRadius(tier) + 4)))
            if (fb.isNoGravity()) { shatter(sl, fb); fb.discard(); }
        for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(c, c).inflate(blast), t -> t != owner && SpellUtil.canHarm(owner, t))) {
            double d = e.position().distanceTo(c);
            if (d > blast) continue;
            SpellUtil.spellDamage(ModSpells.BLACK_HOLE.get(), this, owner, e, damage * 2.5f * (float) (1 - d / (blast + 1)));
            e.setDeltaMovement(e.position().subtract(c).normalize().scale(1.8 + 0.1 * tier).add(0, 0.6, 0));
            e.hurtMarked = true;
        }
    }

    // ------------------------------------------------------------------------------------------------ client
    private void clientTick() {
        var r = random;
        float f = sizeFactor(0);
        if (f <= 0.02f) return;
        Vec3 c = position();
        if (getState() == FLYING && tickCount % 2 == 1) return;
        float sc = tierScale();
        int n = getState() == ANCHORED ? 6 + getTier() : 3;
        for (int i = 0; i < n; i++) {
            double ang = r.nextDouble() * Math.PI * 2;
            double rad = (getState() != ANCHORED ? 1.2 + r.nextDouble() * 1.2 : (3.0 + r.nextDouble() * 6.0) * sc) * Math.max(0.3, f);
            double y = (r.nextDouble() - 0.5) * (i < n * 2 / 3 ? 0.4 : 3.5) * Math.max(0.3, f) * (getState() == ANCHORED ? sc : 1);
            Vec3 p = c.add(Math.cos(ang) * rad, y, Math.sin(ang) * rad);
            Vec3 in = c.subtract(p).normalize();
            Vec3 tangent = new Vec3(-Math.sin(ang), 0, Math.cos(ang));
            Vec3 v = in.scale(0.12 + 0.06 * r.nextDouble()).add(tangent.scale(0.14));
            level().addParticle(i % 3 == 0 ? Vfx.purpleSpark() : Vfx.voidWisp(), true, p.x, p.y, p.z, v.x, v.y, v.z);
        }
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "main", 0, s -> s.setAndContinue(
                RawAnimation.begin().thenLoop("animation.black_hole.idle"))));
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }
}

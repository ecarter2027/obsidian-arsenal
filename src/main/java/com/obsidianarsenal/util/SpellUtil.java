package com.obsidianarsenal.util;

import com.obsidianarsenal.util.Vfx.Effek;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.damage.DamageSources;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.ProjectileUtil;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class SpellUtil {
    private SpellUtil() {}

    /** Cheap pre-filter; Iron's {@link DamageSources#applyDamage} does the real ally / friendly-fire checks. */
    public static boolean canHarm(@Nullable Entity owner, LivingEntity target) {
        if (target == owner || !target.isAlive() || target.isSpectator() || target instanceof ArmorStand) return false;
        if (target instanceof Player tp && tp.isCreative()) return false;
        if (owner != null && DamageSources.isFriendlyFireBetween(owner, target)) return false;
        return true;
    }

    /**
     * Deal spell damage the Iron's way: a SpellDamageSource of the spell's school (Obsidian or Hellfire), so the
     * caster's school spell power was already applied by the spell and the target's school resistance, spell
     * damage events, friendly-fire rules and death messages all come from Iron's Spells.
     */
    public static boolean spellDamage(AbstractSpell spell, @Nullable Entity direct, @Nullable Entity caster, LivingEntity target, float amount) {
        target.invulnerableTime = 0;
        Entity attacker = caster != null ? caster : direct;
        if (attacker == null) return target.hurt(target.damageSources().magic(), amount);
        return DamageSources.applyDamage(target, amount, spell.getDamageSource(direct != null ? direct : attacker, attacker));
    }

    public static double groundY(Level level, double x, double y, double z) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos(x, y + 1.5, z);
        for (int i = 0; i < 12; i++) {
            BlockState s = level.getBlockState(m);
            if (!s.getCollisionShape(level, m).isEmpty()) return m.getY() + s.getCollisionShape(level, m).max(Direction.Axis.Y);
            m.move(0, -1, 0);
        }
        return y;
    }

    /** A random cardinal facing (north, south, east or west) for things that erupt from the ground. */
    public static float randomCardinal(net.minecraft.util.RandomSource r) {
        return 90f * r.nextInt(4);
    }

    public static float yawFacing(double dx, double dz) {
        return (float) (Math.atan2(-dx, dz) * (180.0 / Math.PI));
    }

    @Nullable
    public static LivingEntity raycastLiving(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 look = caster.getLookAngle();
        Vec3 end = eye.add(look.scale(range));
        AABB box = caster.getBoundingBox().expandTowards(look.scale(range)).inflate(1.0);
        EntityHitResult r = ProjectileUtil.getEntityHitResult(caster, eye, end, box,
                e -> e instanceof LivingEntity && e.isAlive() && !e.isSpectator() && e != caster, range * range);
        if (r == null || !(r.getEntity() instanceof LivingEntity le)) return null;
        BlockHitResult b = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        if (b.getType() != HitResult.Type.MISS && eye.distanceToSqr(b.getLocation()) < eye.distanceToSqr(r.getLocation())) return null;
        return le;
    }

    public static double blockDistance(LivingEntity caster, double range) {
        Vec3 eye = caster.getEyePosition();
        Vec3 end = eye.add(caster.getLookAngle().scale(range));
        BlockHitResult b = caster.level().clip(new ClipContext(eye, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, caster));
        return b.getType() == HitResult.Type.MISS ? range : eye.distanceTo(b.getLocation());
    }

    // ------------------------------------------------------------------------------------------------ Indra's Arrow
    /** 25 block radius sphere of destruction (blocks removed shell by shell, one shell per tick). */
    public static void sphereBlast(ServerLevel level, Vec3 c, int radius, AbstractSpell spell, @Nullable Entity direct,
                                   @Nullable LivingEntity owner, float damage) {
        level.playSound(null, c.x, c.y, c.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, 10.0f, 0.5f);
        level.playSound(null, c.x, c.y, c.z, SoundEvents.WITHER_BREAK_BLOCK, SoundSource.PLAYERS, 6.0f, 0.6f);
        Vfx.effek(level, Effek.INDRA_EXPLOSION, c, radius / 5f);
        Vfx.spawn(level, ParticleTypes.EXPLOSION_EMITTER, c.x, c.y, c.z, 0, 0, 0);
        Vfx.burst(level, Vfx.purple(), c, 140, 2.0, 1.4);
        Vfx.burst(level, Vfx.deepPurpleSpark(), c, 60, 1.0, 0.8);
        for (int i = 0; i < 10; i++) {   // lightning fork out of the impact
            var r = level.random;
            Vec3 d = new Vec3(r.nextGaussian(), Math.abs(r.nextGaussian()), r.nextGaussian()).normalize().scale(6 + r.nextDouble() * 10);
            Vfx.bolt(level, c, c.add(d));
        }

        AABB box = new AABB(c, c).inflate(radius);
        for (LivingEntity e : level.getEntitiesOfClass(LivingEntity.class, box, t -> canHarm(owner, t))) {
            double d = e.position().distanceTo(c);
            if (d > radius + 2) continue;
            float dmg = damage * (float) (1.0 - 0.6 * Math.min(1.0, d / radius));
            if (spellDamage(spell, direct, owner, e, dmg)) {
                e.setDeltaMovement(e.position().subtract(c).normalize().scale(2.2).add(0, 0.9, 0));
                e.hurtMarked = true;
            }
        }

        if (!level.getGameRules().getBoolean(GameRules.RULE_MOBGRIEFING)) return;
        BlockPos cb = BlockPos.containing(c);
        for (int shell = 0; shell <= radius; shell++) {
            final int s = shell;
            Scheduler.later(level, s, () -> destroyShell(level, cb, c, s));
        }
    }

    private static void destroyShell(ServerLevel level, BlockPos cb, Vec3 c, int s) {
        BlockPos.MutableBlockPos m = new BlockPos.MutableBlockPos();
        int flags = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS;
        for (int dx = -s; dx <= s; dx++) for (int dy = -s; dy <= s; dy++) for (int dz = -s; dz <= s; dz++) {
            int d2 = dx * dx + dy * dy + dz * dz;
            if ((int) Math.floor(Math.sqrt(d2)) != s) continue;
            m.set(cb.getX() + dx, cb.getY() + dy, cb.getZ() + dz);
            if (!level.isInWorldBounds(m)) continue;
            BlockState st = level.getBlockState(m);
            if (st.isAir() || st.getDestroySpeed(level, m) < 0) continue;
            level.setBlock(m, Blocks.AIR.defaultBlockState(), flags);
        }
        var r = level.random;
        int n = Math.min(60, 8 + s * 3);
        for (int i = 0; i < n; i++) {
            Vec3 d = new Vec3(r.nextGaussian(), r.nextGaussian(), r.nextGaussian()).normalize();
            Vfx.spawn(level, i % 3 == 0 ? Vfx.lightning() : (i % 3 == 1 ? Vfx.purple() : Vfx.purpleSpark()),
                    c.x + d.x * s, c.y + d.y * s, c.z + d.z * s, d.x * 0.3, d.y * 0.3, d.z * 0.3);
        }
    }
}

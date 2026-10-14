package com.obsidianarsenal.combat;

import com.obsidianarsenal.item.ObsidianGauntletItem;
import com.obsidianarsenal.network.ModNetwork;
import com.obsidianarsenal.util.Scheduler;
import com.obsidianarsenal.util.SpellUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import software.bernie.geckolib.animatable.GeoItem;

import java.util.ArrayList;
import java.util.List;

/**
 * Server side of the Obsidian Gauntlet's 7-hit sequential combo.
 * Each swing: PlayerAnimator body animation (all nearby clients) + GeckoLib gauntlet animation (stretching for
 * attacks 3 and 6) + hit detection matched to the animation's reach. No particle effects by design.
 */
public final class GauntletCombat {
    private GauntletCombat() {}

    public enum Type { PUNCH, CLAW, STRETCH, SMASH }

    public record Attack(String name, Type type, int hitDelay, int duration, float reach, float width, float arcDeg,
                         float aoe, float aoeFwd, float dmgMul, float knock, float up, boolean pull, boolean leap) {}

    public static final Attack[] ATTACKS = {
            //          name                type          dly dur  reach wid   arc   aoe   fwd   dmg   kb    up     pull   leap
            new Attack("Obsidian Jab",      Type.PUNCH,   4, 10, 3.4f, 0f,   60f, 0f,   0f,   1.0f, 0.4f, 0.05f, false, false),
            new Attack("Rending Hook",      Type.CLAW,    4, 10, 3.6f, 0f,  110f, 0f,   0f,   1.0f, 0.4f, 0.05f, false, false),
            new Attack("Piercing Lance",    Type.STRETCH, 6, 15, 7.5f, 1.1f,  0f, 0f,   0f,   1.3f, 0.8f, 0.05f, false, false),
            new Attack("Hammer Slam",       Type.SMASH,   8, 16, 4.5f, 0f,    0f, 3.2f, 2.6f, 1.5f, 0.7f, 0.35f, false, false),
            new Attack("Claw Rake",         Type.CLAW,    5, 12, 4.0f, 0f,  140f, 0f,   0f,   1.2f, 0.5f, 0.1f,  false, false),
            new Attack("Seismic Reach",     Type.STRETCH, 8, 18, 9.0f, 1.5f,  0f, 0f,   0f,   1.5f, 1.2f, 0.1f,  true,  false),
            new Attack("Meteor Smash",      Type.SMASH,  12, 26, 6.0f, 0f,    0f, 5.5f, 3.0f, 2.4f, 1.3f, 0.6f,  false, true),
    };

    private static final String K_NEXT = "OA_Next", K_RESET = "OA_Reset", K_COMBO = "OA_Combo";

    public static void request(ServerPlayer p) {
        ItemStack stack = p.getMainHandItem();
        if (!(stack.getItem() instanceof ObsidianGauntletItem gauntlet) || !ObsidianGauntletItem.isCombat(stack)) return;
        CompoundTag d = p.getPersistentData();
        long now = p.level().getGameTime();
        if (now < d.getLong(K_NEXT)) return;

        int idx = now > d.getLong(K_RESET) ? 0 : d.getInt(K_COMBO) % ATTACKS.length;
        Attack a = ATTACKS[idx];
        d.putLong(K_NEXT, now + a.duration());
        d.putLong(K_RESET, now + a.duration() + 14);
        d.putInt(K_COMBO, (idx + 1) % ATTACKS.length);

        ServerLevel sl = p.serverLevel();
        ModNetwork.playAnim(p, "gauntlet_attack_" + (idx + 1));                                               // PlayerAnimator
        gauntlet.triggerAnim(p, GeoItem.getOrAssignId(stack, sl), "main", "attack_" + (idx + 1));          // GeckoLib
        sl.playSound(null, p.blockPosition(), a.type() == Type.SMASH ? SoundEvents.IRON_GOLEM_ATTACK : SoundEvents.PLAYER_ATTACK_SWEEP,
                SoundSource.PLAYERS, 1.0f, a.type() == Type.STRETCH ? 0.7f : 1.0f);

        if (a.leap()) {
            Vec3 look = p.getLookAngle();
            p.setDeltaMovement(look.x * 0.45, 0.55, look.z * 0.45);
            p.hurtMarked = true;
        }
        final int fi = idx;
        Scheduler.later(sl, a.hitDelay(), () -> perform(p, fi));
    }

    private static void perform(ServerPlayer p, int idx) {
        if (!p.isAlive() || !(p.getMainHandItem().getItem() instanceof ObsidianGauntletItem)) return;
        ServerLevel sl = p.serverLevel();
        Attack a = ATTACKS[idx];
        float dmg = (float) p.getAttributeValue(Attributes.ATTACK_DAMAGE) * a.dmgMul();
        Vec3 eye = p.getEyePosition();
        Vec3 look = p.getLookAngle();
        Vec3 flat = new Vec3(look.x, 0, look.z);
        flat = flat.lengthSqr() < 1e-4 ? new Vec3(0, 0, 1) : flat.normalize();

        List<LivingEntity> hits = new ArrayList<>();
        if (a.type() == Type.SMASH) {
            Vec3 center = p.position().add(flat.scale(a.aoeFwd()));
            center = new Vec3(center.x, SpellUtil.groundY(sl, center.x, p.getY(), center.z), center.z);
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, new AABB(center, center).inflate(a.aoe(), 2.5, a.aoe()), t -> SpellUtil.canHarm(p, t)))
                if (e.position().distanceTo(center) <= a.aoe() + e.getBbWidth() / 2) hits.add(e);
            sl.playSound(null, center.x, center.y, center.z, SoundEvents.GENERIC_EXPLODE, SoundSource.PLAYERS, idx == 6 ? 1.6f : 1.0f, idx == 6 ? 0.6f : 0.9f);
        } else {
            for (LivingEntity e : sl.getEntitiesOfClass(LivingEntity.class, p.getBoundingBox().inflate(a.reach() + 1.5), t -> SpellUtil.canHarm(p, t))) {
                Vec3 c = e.getBoundingBox().getCenter().subtract(eye);
                double dist = c.length();
                if (dist < 1e-3) continue;
                if (a.arcDeg() > 0) {
                    if (dist > a.reach() + e.getBbWidth() / 2) continue;
                    double ang = Math.toDegrees(Math.acos(Math.max(-1, Math.min(1, c.normalize().dot(look)))));
                    if (ang > a.arcDeg() / 2) continue;
                } else {
                    double t = c.dot(look);
                    if (t < 0 || t > a.reach() + e.getBbWidth() / 2) continue;
                    if (c.subtract(look.scale(t)).length() > a.width() + e.getBbWidth() / 2) continue;
                }
                hits.add(e);
            }
        }

        for (LivingEntity e : hits) {
            e.invulnerableTime = 0;
            if (e.hurt(sl.damageSources().playerAttack(p), dmg)) {
                if (a.pull()) {
                    Vec3 to = p.position().subtract(e.position()).normalize().scale(1.4);
                    e.setDeltaMovement(to.x, 0.25, to.z);
                } else {
                    e.knockback(a.knock(), -look.x, -look.z);
                    e.setDeltaMovement(e.getDeltaMovement().add(0, a.up(), 0));
                }
                e.hurtMarked = true;
            }
        }
        if (!hits.isEmpty()) p.getMainHandItem().hurtAndBreak(1, p, pl -> pl.broadcastBreakEvent(InteractionHand.MAIN_HAND));
    }
}

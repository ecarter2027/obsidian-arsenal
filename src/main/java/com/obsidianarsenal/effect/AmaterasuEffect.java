package com.obsidianarsenal.effect;

import com.obsidianarsenal.registry.ModSpells;
import com.obsidianarsenal.util.SpellUtil;
import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/**
 * Black flames. Applied with infinite duration and no curative items, so it ends only when the victim dies.
 * Damage is Iron's Hellfire-school spell damage credited to the original caster.
 */
public class AmaterasuEffect extends MobEffect {
    public static final String CASTER_TAG = "OA_AmaterasuCaster";
    public static final String POWER_TAG = "OA_AmaterasuPower";

    public AmaterasuEffect() { super(MobEffectCategory.HARMFUL, 0x050505); }

    @Override public boolean isDurationEffectTick(int duration, int amplifier) { return true; }

    @Override
    public void applyEffectTick(LivingEntity e, int amplifier) {
        if (!(e.level() instanceof ServerLevel sl)) return;
        var r = sl.random;
        double w = e.getBbWidth(), h = e.getBbHeight();
        if (e.tickCount % 30 == 0) Vfx.effekOn(sl, Effek.AMATERASU_FLAMES, e, Vec3.ZERO, Math.max(1f, (float) h * 0.6f));
        for (int i = 0; i < 2; i++)
            Vfx.spawn(sl, Vfx.amaterasu(), e.getX() + (r.nextDouble() - 0.5) * w * 1.2, e.getY() + r.nextDouble() * h,
                    e.getZ() + (r.nextDouble() - 0.5) * w * 1.2, 0, 0.06, 0);

        if (e.tickCount % 10 == 0) {
            var tag = e.getPersistentData();
            Entity caster = tag.hasUUID(CASTER_TAG) ? sl.getEntity(tag.getUUID(CASTER_TAG)) : null;
            float dmg = Math.max(2f, tag.getFloat(POWER_TAG)) + amplifier;
            SpellUtil.spellDamage(ModSpells.AMATERASU.get(), null, caster, e, dmg);
        }
    }

    public static void mark(LivingEntity target, UUID caster, float damagePerSecondHalf) {
        target.getPersistentData().putUUID(CASTER_TAG, caster);
        target.getPersistentData().putFloat(POWER_TAG, damagePerSecondHalf);
    }
}

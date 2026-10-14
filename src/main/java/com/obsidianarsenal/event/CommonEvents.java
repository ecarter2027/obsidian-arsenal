package com.obsidianarsenal.event;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianGauntletItem;
import com.obsidianarsenal.registry.ModEffects;
import com.obsidianarsenal.util.Vfx;
import com.obsidianarsenal.util.Vfx.Effek;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.entity.player.PlayerInteractEvent;
import net.minecraftforge.eventbus.api.Event;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID)
public class CommonEvents {

    private static boolean gyroActive(LivingEntity e) {
        return e.getPersistentData().getLong("OA_Gyro") > e.level().getGameTime();
    }

    /** Gyroscope: immune to all damage (except /kill and the void). */
    @SubscribeEvent
    public static void onAttack(LivingAttackEvent e) {
        LivingEntity v = e.getEntity();
        if (gyroActive(v) && !e.getSource().is(DamageTypeTags.BYPASSES_INVULNERABILITY)) {
            e.setCanceled(true);
            if (v.tickCount % 5 == 0) Vfx.effekOn(v.level(), Effek.GYROSCOPE_DEFLECT, v, new net.minecraft.world.phys.Vec3(0, 1, 0), 0.8f);
        }
    }

    /** Gyroscope: arrows / projectiles never land. */
    @SubscribeEvent
    public static void onImpact(ProjectileImpactEvent e) {
        if (e.getRayTraceResult() instanceof EntityHitResult hit && hit.getEntity() instanceof LivingEntity le && gyroActive(le)) {
            Projectile p = e.getProjectile();
            if (p.getOwner() != le) { e.setCanceled(true); p.discard(); }
        }
    }

    /** Amaterasu cannot be removed while its victim lives (milk, cleanse spells, totems, commands). */
    @SubscribeEvent
    public static void onRemove(MobEffectEvent.Remove e) {
        if (e.getEffect() == ModEffects.AMATERASU.get() && e.getEntity().isAlive()) e.setCanceled(true);
    }

    /** In combat mode the gauntlet punches instead of mining. */
    @SubscribeEvent
    public static void onLeftClickBlock(PlayerInteractEvent.LeftClickBlock e) {
        ItemStack s = e.getItemStack();
        if (s.getItem() instanceof ObsidianGauntletItem && ObsidianGauntletItem.isCombat(s)) {
            e.setUseItem(Event.Result.DENY);
            e.setCanceled(true);
        }
    }
}

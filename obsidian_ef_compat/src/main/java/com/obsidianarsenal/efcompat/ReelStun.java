package com.obsidianarsenal.efcompat;

import com.obsidianarsenal.entity.GrappleHookEntity;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.entity.EntityLeaveLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

@EventBusSubscriber(
   modid = "obsidian_ef_compat"
)
public final class ReelStun {
   private static final float STUN_SECONDS = 1.0F;
   private static final double ARRIVED = 4.0;

   private ReelStun() {
   }

   @SubscribeEvent
   public static void onHookRemoved(EntityLeaveLevelEvent event) {
      if (!event.getLevel().isClientSide() && event.getEntity() instanceof GrappleHookEntity hook) {
         if (hook.getState() == 2) {
            Entity owner = hook.getOwner();
            if (owner != null && hook.getHooked() instanceof LivingEntity mob && mob.isAlive()) {
               if (!((double)mob.distanceTo(owner) > 4.0)) {
                  LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(mob, LivingEntityPatch.class);
                  if (patch != null) {
                     patch.applyStun(StunType.HOLD, 1.0F);
                  }

                  mob.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 20, 9, false, false));
                  mob.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 20, 4, false, false));
                  mob.setDeltaMovement(mob.getDeltaMovement().multiply(0.2, 1.0, 0.2));
                  mob.hurtMarked = true;
               }
            }
         }
      }
   }
}

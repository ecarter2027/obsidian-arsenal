package com.obsidianarsenal.efcompat.client;

import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent.InteractionKeyMappingTriggered;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import yesman.epicfight.client.world.capabilites.entitypatch.player.LocalPlayerPatch;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;

@EventBusSubscriber(
   modid = "obsidian_ef_compat",
   value = {Dist.CLIENT}
)
public final class GauntletInputBridge {
   private static InteractionKeyMappingTriggered hidden;
   private static boolean hiddenSwing;

   private GauntletInputBridge() {
   }

   @SubscribeEvent(
      priority = EventPriority.HIGHEST
   )
   public static void hideFromGauntlet(InteractionKeyMappingTriggered event) {
      hidden = null;
      if (event.isAttack() && !event.isCanceled()) {
         LocalPlayer player = Minecraft.getInstance().player;
         if (player != null && ObsidianEFCompat.isCombatMode(player.getMainHandItem())) {
            LocalPlayerPatch patch = EpicFightCapabilities.getEntityPatch(player, LocalPlayerPatch.class);
            if (patch != null && patch.isEpicFightMode()) {
               hidden = event;
               hiddenSwing = event.shouldSwingHand();
               event.setCanceled(true);
            }
         }
      }
   }

   @SubscribeEvent(
      priority = EventPriority.LOWEST,
      receiveCanceled = true
   )
   public static void restore(InteractionKeyMappingTriggered event) {
      if (event == hidden) {
         hidden = null;
         event.setCanceled(false);
         event.setSwingHand(hiddenSwing);
      }
   }
}

package com.obsidianarsenal.efcompat;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingEquipmentChangeEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.RegistryObject;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.entity.ai.attribute.EpicFightAttributes;

/**
 * Crash guard. When the off-hand item changes, Epic Fight removes the old item's off-hand bonuses (attack speed,
 * armor negation, impact, max strikes) and adds the new item's with addTransientModifier, which throws
 * "Modifier is already applied on this attribute!" if one of them is still there (seen in a player crash report;
 * stale bonuses can be left by a missed change event or another mod hooking ServerPlayerPatch.updateHeldItem).
 * Before Epic Fight handles the change, this takes off any copy of the new item's bonuses that is already applied,
 * so Epic Fight's own add always succeeds. Nothing else changes: Epic Fight re-adds them right after.
 */
@EventBusSubscriber(modid = ObsidianEFCompat.MODID)
public final class OffhandAttributeGuard {
   private OffhandAttributeGuard() {
   }

   @SuppressWarnings("unchecked")
   private static final RegistryObject<Attribute>[] OFFHAND = new RegistryObject[]{
      EpicFightAttributes.OFFHAND_ATTACK_SPEED, EpicFightAttributes.OFFHAND_ARMOR_NEGATION,
      EpicFightAttributes.OFFHAND_IMPACT, EpicFightAttributes.OFFHAND_MAX_STRIKES
   };

   @SubscribeEvent(priority = EventPriority.HIGHEST)
   public static void beforeEpicFight(LivingEquipmentChangeEvent event) {
      LivingEntity entity = event.getEntity();
      if (event.getSlot() != EquipmentSlot.OFFHAND || !(entity instanceof Player) || entity.level().isClientSide) return;
      ItemStack to = event.getTo();
      if (to.isEmpty()) return;
      Set<UUID> incoming = new HashSet<>();
      try {
         to.getAttributeModifiers(EquipmentSlot.MAINHAND).values().forEach(m -> incoming.add(m.getId()));
         CapabilityItem cap = EpicFightCapabilities.getItemStackCapability(to);
         if (cap != null && !cap.isEmpty()) cap.getAllAttributeModifiers(EquipmentSlot.MAINHAND).values().forEach(m -> incoming.add(m.getId()));
      } catch (RuntimeException ignored) {
         return;                                       // never let the guard itself break an equipment change
      }
      if (incoming.isEmpty()) return;
      for (RegistryObject<Attribute> attr : OFFHAND) {
         AttributeInstance inst = entity.getAttribute(attr.get());
         if (inst == null) continue;
         for (UUID id : incoming) {
            AttributeModifier existing = inst.getModifier(id);
            if (existing != null) inst.removeModifier(id);
         }
      }
   }
}

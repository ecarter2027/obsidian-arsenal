package com.obsidianarsenal.efcompat;

import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.animation.AnimationManager;
import yesman.epicfight.api.forgeevent.WeaponCapabilityPresetRegistryEvent;

@Mod("obsidian_ef_compat")
public class ObsidianEFCompat {
   public static final String MODID = "obsidian_ef_compat";
   public static final ResourceLocation GAUNTLET_ID = new ResourceLocation("obsidian_arsenal", "obsidian_gauntlet");
   public static final ResourceLocation WEAPON_TYPE = new ResourceLocation("obsidian_ef_compat", "obsidian_gauntlet");

   public ObsidianEFCompat() {
      IEventBus modBus = FMLJavaModLoadingContext.get().getModEventBus();
      StretchNet.register();
      modBus.addListener((AnimationManager.AnimationRegistryEvent e) -> e.newBuilder("obsidian_ef_compat", GauntletAnimations::build));
      modBus.addListener(GauntletSkills::build);
      modBus.addListener((WeaponCapabilityPresetRegistryEvent e) -> e.getTypeEntry().put(WEAPON_TYPE, GauntletMoveset.PRESET));
   }

   public static boolean isGauntlet(ItemStack stack) {
      return !stack.isEmpty() && GAUNTLET_ID.equals(ForgeRegistries.ITEMS.getKey(stack.getItem()));
   }

   public static boolean isCombatMode(ItemStack stack) {
      return isGauntlet(stack) && stack.hasTag() && stack.getTag().getBoolean("Combat");
   }
}

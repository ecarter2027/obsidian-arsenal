package com.obsidianarsenal.efcompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.item.Item;
import yesman.epicfight.api.animation.LivingMotions;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.particle.HitParticleType;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.capabilities.item.WeaponCapability;
import yesman.epicfight.world.capabilities.item.CapabilityItem.Builder;
import yesman.epicfight.world.capabilities.item.CapabilityItem.Styles;
import yesman.epicfight.world.capabilities.item.CapabilityItem.WeaponCategories;

public final class GauntletMoveset {
   /** Epic Fight Dawnday's hookclaws: when that mod is installed the gauntlet fights with its moveset. */
   public static final String HOOKCLAWS = "epicfight_dd:hookclaws";

   /**
    * The gauntlet's weapon type. With Epic Fight Dawnday installed it is Dawnday's own hookclaws moveset (combos,
    * one-handed and dual styles, dash and air attacks, idle, walk, guard, parries and its skills), looked up from
    * Epic Fight's weapon type registry at the time the capability is built, so nothing of Dawnday is copied here.
    * Without Dawnday it falls back to this add-on's own claw moveset (OWN).
    */
   public static final Function<Item, Builder> PRESET = item -> {
      if (net.minecraftforge.fml.ModList.get().isLoaded("epicfight_dd")) {
         Function<Item, Builder> hookclaws = yesman.epicfight.world.capabilities.item.WeaponTypeReloadListener.get(HOOKCLAWS);
         if (hookclaws != null) {
            return hookclaws.apply(item);
         }
      }
      return GauntletMoveset.OWN.apply(item);
   };

   public static final Function<Item, Builder> OWN = item -> {
      yesman.epicfight.world.capabilities.item.WeaponCapability.Builder builder = WeaponCapability.builder()
         .category(WeaponCategories.FIST)
         .styleProvider(patch -> dualWielding(patch) ? Styles.TWO_HAND : Styles.ONE_HAND)
         .weaponCombinationPredicator(GauntletMoveset::dualWielding)
         .collider(GauntletAnimations.GAUNTLET)
         .swingSound((SoundEvent)EpicFightSounds.WHOOSH_SMALL.get())
         .hitSound((SoundEvent)EpicFightSounds.BLUNT_HIT.get())
         .hitParticle((HitParticleType)EpicFightParticles.HIT_BLUNT.get())
         .canBePlacedOffhand(true)
         .reach(1.5F)
         .newStyleCombo(Styles.ONE_HAND, withDashAndAir(GauntletAnimations.SINGLE_COMBO, GauntletAnimations.DASH, GauntletAnimations.AIR_SLASH))
         .newStyleCombo(Styles.TWO_HAND, withDashAndAir(GauntletAnimations.DUAL_COMBO, GauntletAnimations.DUAL_DASH, GauntletAnimations.DUAL_AIR_SLASH));
      builder.constructor(GauntletCapability::new);           // gives the gauntlet guard motions (right-click guards)

      for (Styles style : new Styles[]{Styles.ONE_HAND, Styles.TWO_HAND}) {
         builder.innateSkill(style, stack -> style == Styles.TWO_HAND ? GauntletSkills.TWIN_METEOR : GauntletSkills.OBSIDIAN_IMPALER)
            .livingMotionModifier(style, LivingMotions.IDLE, GauntletAnimations.IDLE)
            .livingMotionModifier(style, LivingMotions.WALK, GauntletAnimations.WALK)
            .livingMotionModifier(style, LivingMotions.CHASE, GauntletAnimations.WALK)
            .livingMotionModifier(style, LivingMotions.RUN, GauntletAnimations.RUN)
            .livingMotionModifier(style, LivingMotions.BLOCK, GauntletAnimations.GUARD);
      }

      return builder;
   };

   private GauntletMoveset() {
   }

   private static AnimationAccessor<? extends AttackAnimation>[] withDashAndAir(
      List<AnimationAccessor<? extends AttackAnimation>> combo, AnimationAccessor<? extends AttackAnimation> dash,
      AnimationAccessor<? extends AttackAnimation> air
   ) {
      List<AnimationAccessor<? extends AttackAnimation>> all = new ArrayList<>(combo);
      all.add(dash);
      all.add(air);
      return all.toArray(new AnimationAccessor[0]);
   }

   private static boolean dualWielding(LivingEntityPatch<?> patch) {
      return ObsidianEFCompat.isGauntlet(patch.getOriginal().getOffhandItem());
   }
}

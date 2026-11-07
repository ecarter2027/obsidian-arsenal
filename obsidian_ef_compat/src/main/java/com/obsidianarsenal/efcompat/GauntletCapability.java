package com.obsidianarsenal.efcompat;

import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.gameasset.Animations;
import yesman.epicfight.skill.guard.GuardSkill;
import yesman.epicfight.world.capabilities.entitypatch.player.PlayerPatch;
import yesman.epicfight.world.capabilities.item.CapabilityItem;
import yesman.epicfight.world.capabilities.item.WeaponCapability;

/**
 * The gauntlet's Epic Fight weapon capability. Epic Fight's guard skills only know swords, axes, spears and the
 * like, not fists, so this gives the gauntlet its own guard reactions: right-click guards (holding the crossed-claws
 * pose, LivingMotions.BLOCK), a blocked hit plays a sword-style guard reaction, a parry the active parry, and a
 * broken guard the common neutralized stagger.
 */
public class GauntletCapability extends WeaponCapability {
   public GauntletCapability(CapabilityItem.Builder builder) {
      super(builder);
   }

   @Override
   public AnimationAccessor<? extends StaticAnimation> getGuardMotion(GuardSkill skill, GuardSkill.BlockType type, PlayerPatch<?> patch) {
      boolean dual = this.getStyle(patch) == Styles.TWO_HAND;
      return switch (type) {
         case GUARD -> dual ? Animations.SWORD_DUAL_GUARD_HIT : Animations.SWORD_GUARD_HIT;
         case ADVANCED_GUARD -> patch.getOriginal().getRandom().nextBoolean() ? Animations.SWORD_GUARD_ACTIVE_HIT1 : Animations.SWORD_GUARD_ACTIVE_HIT2;
         case GUARD_BREAK -> Animations.BIPED_COMMON_NEUTRALIZED;
      };
   }
}

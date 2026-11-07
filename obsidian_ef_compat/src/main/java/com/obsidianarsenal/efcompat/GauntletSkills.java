package com.obsidianarsenal.efcompat;

import java.util.Set;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty;
import yesman.epicfight.api.forgeevent.SkillBuildEvent;
import yesman.epicfight.api.forgeevent.SkillBuildEvent.ModRegistryWorker;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.skill.Skill;
import yesman.epicfight.skill.dodge.DodgeSkill;
import yesman.epicfight.skill.weaponinnate.SimpleWeaponInnateSkill;
import yesman.epicfight.skill.weaponinnate.WeaponInnateSkill;
import yesman.epicfight.world.damagesource.EpicFightDamageTypeTags;

public final class GauntletSkills {
   public static Skill OBSIDIAN_IMPALER;
   public static Skill TWIN_METEOR;
   public static Skill OBSIDIAN_STRETCH;

   private GauntletSkills() {
   }

   public static void build(SkillBuildEvent event) {
      ModRegistryWorker registry = event.createRegistryWorker("obsidian_ef_compat");
      WeaponInnateSkill impaler = registry.build(
         "obsidian_impaler", SimpleWeaponInnateSkill::new, SimpleWeaponInnateSkill.createSimpleWeaponInnateBuilder().setAnimations(GauntletAnimations.IMPALER)
      );
      impaler.newProperty()
         .addProperty(AttackPhaseProperty.PARTICLE, EpicFightParticles.HIT_BLUNT)
         .addProperty(AttackPhaseProperty.SOURCE_TAG, Set.of(EpicFightDamageTypeTags.WEAPON_INNATE));
      OBSIDIAN_IMPALER = impaler;
      WeaponInnateSkill twinMeteor = registry.build(
         "twin_meteor", SimpleWeaponInnateSkill::new, SimpleWeaponInnateSkill.createSimpleWeaponInnateBuilder().setAnimations(GauntletAnimations.TWIN_METEOR)
      );
      twinMeteor.newProperty()
         .addProperty(AttackPhaseProperty.PARTICLE, EpicFightParticles.HIT_BLUNT)
         .addProperty(AttackPhaseProperty.SOURCE_TAG, Set.of(EpicFightDamageTypeTags.WEAPON_INNATE));
      TWIN_METEOR = twinMeteor;
      // no stamina, no cooldown: it can be used again straight away (StretchDodgeSkill chains out of its own dodge)
      DodgeSkill.Builder stretch = DodgeSkill.createDodgeBuilder().setAnimations(GauntletAnimations.STRETCH_DODGE);
      stretch.setResource(Skill.Resource.NONE);
      OBSIDIAN_STRETCH = registry.build("obsidian_stretch", StretchDodgeSkill::new, stretch);
   }
}

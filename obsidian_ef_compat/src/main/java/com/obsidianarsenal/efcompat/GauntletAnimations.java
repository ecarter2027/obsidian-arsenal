package com.obsidianarsenal.efcompat;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.Vec3;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.AnimationManager.AnimationAccessor;
import yesman.epicfight.api.animation.AnimationManager.AnimationBuilder;
import yesman.epicfight.api.animation.property.AnimationEvent;
import yesman.epicfight.api.animation.property.AnimationEvent.InTimeEvent;
import yesman.epicfight.api.animation.property.AnimationEvent.Side;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackAnimationProperty;
import yesman.epicfight.api.animation.property.AnimationProperty.AttackPhaseProperty;
import yesman.epicfight.api.animation.property.AnimationProperty.StaticAnimationProperty;
import yesman.epicfight.api.animation.types.AirSlashAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation;
import yesman.epicfight.api.animation.types.BasicAttackAnimation;
import yesman.epicfight.api.animation.types.DashAttackAnimation;
import yesman.epicfight.api.animation.types.DodgeAnimation;
import yesman.epicfight.api.animation.types.MovementAnimation;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.animation.types.AttackAnimation.JointColliderPair;
import yesman.epicfight.api.animation.types.AttackAnimation.Phase;
import yesman.epicfight.api.client.animation.property.ClientAnimationProperties;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.api.collider.Collider;
import yesman.epicfight.api.collider.MultiOBBCollider;
import yesman.epicfight.api.collider.OBBCollider;
import yesman.epicfight.api.utils.math.ValueModifier;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.gameasset.Armatures;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.gameasset.Animations.ReusableSources;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.damagesource.StunType;

public final class GauntletAnimations {
   public static final Collider GAUNTLET = new MultiOBBCollider(3, 0.85, 0.85, 0.85, 0.0, 0.0, 0.0);
   private static final Collider GAUNTLET_CLAW = new MultiOBBCollider(3, 1.1, 1.1, 1.1, 0.0, 0.0, 0.0);
   private static final Collider FIST = new MultiOBBCollider(3, 0.6, 0.6, 0.6, 0.0, 0.0, 0.0);
   private static final Collider FIST_WIDE = new MultiOBBCollider(3, 0.9, 0.9, 0.9, 0.0, 0.0, 0.0);
   private static final Collider KICK = new MultiOBBCollider(3, 0.85, 0.85, 0.85, 0.0, 0.0, 0.0);
   private static final Collider SLAM = new OBBCollider(2.2, 1.0, 2.2, 0.0, 0.5, -1.8);
   private static final Collider LANCE = new OBBCollider(0.75, 0.75, 3.3, 0.0, 1.25, -3.4);
   private static final Collider NO_HIT = new OBBCollider(0.01, 0.01, 0.01, 0.0, -64.0, 0.0);
   public static final float IMPALE_RANGE = 8.7F;
   public static final float IMPALE_EXTEND = 0.3F;
   public static final float IMPALE_FULL = 0.4F;
   public static final float IMPALE_RETRACT = 1.15F;
   public static final float IMPALE_GONE = 1.27F;
   public static final float IMPALE_SPIKE_LENGTH = 6.0F;
   public static final float IMPALE_DAMAGE = 60.0F;
   private static final float BASIS_SPEED = 2.0F;
   private static final float DUAL_BASIS_SPEED = 1.55F;
   private static final GauntletAnimations.Timing CROSS = new GauntletAnimations.Timing(0.1F, 0.25F, 0.35F);
   private static final GauntletAnimations.Timing HOOK = new GauntletAnimations.Timing(0.13F, 0.32F, 0.42F);
   private static final GauntletAnimations.Timing ROUNDHOUSE = new GauntletAnimations.Timing(0.15F, 0.32F, 0.45F);
   private static final GauntletAnimations.Timing UPPERCUT = new GauntletAnimations.Timing(0.17F, 0.35F, 0.47F);
   private static final GauntletAnimations.Timing CLAW_RAKE = new GauntletAnimations.Timing(0.24F, 0.5F, 0.58F);
   private static final GauntletAnimations.Timing AXE_KICK = new GauntletAnimations.Timing(0.2F, 0.38F, 0.5F);
   private static final GauntletAnimations.Timing HAMMER_SLAM = new GauntletAnimations.Timing(0.33F, 0.45F, 0.65F);
   private static final GauntletAnimations.Timing BODY_HOOK = new GauntletAnimations.Timing(0.14F, 0.3F, 0.4F);
   private static final GauntletAnimations.Timing FRONT_KICK = new GauntletAnimations.Timing(0.12F, 0.28F, 0.4F);
   private static final GauntletAnimations.Timing KNEE = new GauntletAnimations.Timing(0.15F, 0.3F, 0.42F);
   private static final GauntletAnimations.Timing SPIN_BACK_KICK = new GauntletAnimations.Timing(0.16F, 0.3F, 0.45F);
   private static final GauntletAnimations.Timing OVERHAND = new GauntletAnimations.Timing(0.18F, 0.33F, 0.45F);
   public static AnimationAccessor<StaticAnimation> IDLE;
   public static AnimationAccessor<StaticAnimation> GUARD;
   public static AnimationAccessor<MovementAnimation> WALK;
   public static AnimationAccessor<MovementAnimation> RUN;
   public static final List<AnimationAccessor<? extends AttackAnimation>> SINGLE_COMBO = new ArrayList<>();
   public static final List<AnimationAccessor<? extends AttackAnimation>> DUAL_COMBO = new ArrayList<>();
   public static AnimationAccessor<DashAttackAnimation> DASH;
   public static AnimationAccessor<DashAttackAnimation> DUAL_DASH;
   public static AnimationAccessor<AirSlashAnimation> AIR_SLASH;
   public static AnimationAccessor<AirSlashAnimation> DUAL_AIR_SLASH;
   public static AnimationAccessor<AttackAnimation> IMPALER;
   public static AnimationAccessor<AttackAnimation> TWIN_METEOR;
   public static AnimationAccessor<DodgeAnimation> STRETCH_DODGE;
   private static final ResourceLocation TRAIL_TEXTURE = new ResourceLocation("obsidian_ef_compat", "textures/particle/obsidian_trail.png");

   private GauntletAnimations() {
   }

   private static HumanoidArmature biped() {
      return Armatures.BIPED.get();
   }

   private static InTimeEvent<?> itemAnim(float time, boolean left, String name) {
      return InTimeEvent.create(time, GauntletFx.PLAY_ITEM_ANIM, Side.SERVER).params((left ? "L:" : "") + name);
   }

   /** The gauntlet's claws held out while this loop plays: claws_out re-triggered twice a loop, on both hands. */
   private static <T extends StaticAnimation> T clawsOut(T anim, float half) {
      anim.addEvents(new AnimationEvent[]{
         itemAnim(0.02F, false, "claws_out"), itemAnim(0.02F, true, "claws_out"),
         itemAnim(half, false, "claws_out"), itemAnim(half, true, "claws_out")
      });
      return anim;
   }

   private static InTimeEvent<?> groundCrack(float time, Joint joint, double radius) {
      return InTimeEvent.create(time, ReusableSources.FRACTURE_GROUND_SIMPLE, Side.CLIENT)
         .params(new Vec3f(0.0F, 0.0F, 0.0F), joint, Double.valueOf(radius), Float.valueOf(time));
   }

   private static <T extends AttackAnimation> T punch(T anim, boolean heavy) {
      anim.addProperty(AttackPhaseProperty.PARTICLE, EpicFightParticles.HIT_BLUNT);
      anim.addProperty(AttackPhaseProperty.SWING_SOUND, heavy ? (SoundEvent)EpicFightSounds.WHOOSH_BIG.get() : (SoundEvent)EpicFightSounds.WHOOSH_SMALL.get());
      anim.addProperty(AttackPhaseProperty.HIT_SOUND, heavy ? (SoundEvent)EpicFightSounds.BLUNT_HIT_HARD.get() : (SoundEvent)EpicFightSounds.BLUNT_HIT.get());
      return anim;
   }

   private static TrailInfo trail(String jointName, float start, float end) {
      boolean leg = jointName.startsWith("Leg");
      return TrailInfo.builder()
         .type((SimpleParticleType)EpicFightParticles.SWING_TRAIL.get())
         .texture(TRAIL_TEXTURE)
         .joint(jointName)
         .startPos(leg ? new Vec3(0.0, 0.05, 0.0) : new Vec3(0.0, 0.0, 0.15))
         .endPos(leg ? new Vec3(0.0, -0.5, 0.0) : new Vec3(0.0, 0.0, -0.5))
         .time(Math.max(0.0F, start), end)
         .fadeTime(0.12F)
         .r(0.68F)
         .g(0.22F)
         .b(1.0F)
         .interpolations(8)
         .lifetime(8)
         .blockLight(15)
         .skyLight(15)
         .itemSkinHand(jointName.endsWith("_L") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND)
         .create();
   }

   private static List<TrailInfo> trailsFor(Joint joint, float start, float end) {
      return joint == biped().rootJoint ? List.of(trail("Tool_R", start, end), trail("Tool_L", start, end)) : List.of(trail(joint.getName(), start, end));
   }

   private static void addTrails(StaticAnimation anim, List<TrailInfo> trails) {
      anim.addProperty(ClientAnimationProperties.TRAIL_EFFECT, trails);
   }

   private static Joint joint(String name) {
      HumanoidArmature b = biped();
      return switch (name) {
         case "Tool_L" -> b.toolL;
         case "Leg_R" -> b.legR;
         case "Leg_L" -> b.legL;
         case "Elbow_R" -> b.elbowR;
         default -> b.toolR;
      };
   }

   private static void slashEvents(StaticAnimation anim, ClawMoves.Move m) {
      for (float[] sl : m.slashes()) {
         anim.addEvents(new AnimationEvent[]{InTimeEvent.create(sl[0], GauntletFx.SLASH, Side.SERVER).params(sl)});
      }
   }

   /** One generated combo attack: one phase hitting with every joint it strikes with (claws and/or legs). */
   private static BasicAttackAnimation clawMove(AnimationAccessor<BasicAttackAnimation> a, ClawMoves.Move m, boolean dual) {
      JointColliderPair[] pairs = m.joints().stream()
         .map(j -> JointColliderPair.of(joint(j), j.startsWith("Leg") ? KICK : GAUNTLET_CLAW))
         .toArray(JointColliderPair[]::new);
      boolean kicksOnly = m.joints().stream().allMatch(j -> j.startsWith("Leg"));
      boolean leftOnly = m.joints().stream().allMatch(j -> j.endsWith("_L"));
      Phase phase = new Phase(0.0F, m.antic(), m.antic(), m.contact(), m.recovery(), Float.MAX_VALUE,
         leftOnly ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND, pairs);
      BasicAttackAnimation anim = new BasicAttackAnimation(dual ? 0.05F : 0.08F, a, Armatures.BIPED, phase);
      boolean heavy = m.stun() == StunType.LONG;
      anim.addProperty(AttackPhaseProperty.PARTICLE, kicksOnly ? EpicFightParticles.HIT_BLUNT : EpicFightParticles.HIT_BLADE);
      anim.addProperty(AttackPhaseProperty.SWING_SOUND, heavy ? (SoundEvent)EpicFightSounds.WHOOSH_BIG.get() : (SoundEvent)EpicFightSounds.WHOOSH_SHARP.get());
      anim.addProperty(AttackPhaseProperty.HIT_SOUND, kicksOnly ? (SoundEvent)EpicFightSounds.BLUNT_HIT_HARD.get() : (SoundEvent)EpicFightSounds.BLADE_HIT.get());
      anim.addProperty(AttackAnimationProperty.BASIS_ATTACK_SPEED, Float.valueOf(dual ? 1.55F : 2.0F));
      if (m.damage() != 1.0F) anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(m.damage()));
      if (m.impact() != 1.0F) anim.addProperty(AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.multiplier(m.impact()));
      if (m.extraTargets() > 0) anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder((float)m.extraTargets()));
      anim.addProperty(AttackPhaseProperty.STUN_TYPE, m.stun());
      List<TrailInfo> trails = new ArrayList<>();
      for (ClawMoves.Trail t : m.trails()) trails.add(trail(t.joint(), t.start(), t.end()));
      addTrails(anim, trails);
      for (ClawMoves.Claw c : m.claws()) anim.addEvents(new AnimationEvent[]{itemAnim(c.time(), c.left(), "claw_strike")});
      for (ClawMoves.Crack c : m.cracks()) anim.addEvents(new AnimationEvent[]{groundCrack(c.time(), joint(c.joint()), c.radius())});
      slashEvents(anim, m);
      return anim;
   }

   public static void build(AnimationBuilder builder) {
      IDLE = builder.nextAccessor("biped/gauntlet/idle", a -> clawsOut(new StaticAnimation(true, a, Armatures.BIPED), 1.2F));
      // right-click guard: both claws crossed before the face (tools/gen_claw_anims.py guard())
      GUARD = builder.nextAccessor("biped/gauntlet/guard", a -> clawsOut(new StaticAnimation(true, a, Armatures.BIPED), 1.0F));
      // Obsidian Stretch: no root motion (StretchFx moves the player); the dodge state gives the invulnerability
      STRETCH_DODGE = builder.nextAccessor("biped/gauntlet/stretch_dodge", a -> new DodgeAnimation(0.05F, a, 0.6F, 1.2F, Armatures.BIPED));
      WALK = builder.nextAccessor("biped/gauntlet/walk", a -> clawsOut(new MovementAnimation(true, a, Armatures.BIPED), 0.33F));
      RUN = builder.nextAccessor("biped/gauntlet/run", a -> clawsOut(new MovementAnimation(true, a, Armatures.BIPED), 0.23F));
      SINGLE_COMBO.clear();
      DUAL_COMBO.clear();
      // the combos (tools/gen_claw_anims.py -> ClawMoves): wide, wild rakes chained with kicks, spins, leaps and
      // X-slashes; every strike leaves a purple trail and a purple slash crescent, and snaps its claw out
      for (ClawMoves.Move m : ClawMoves.SINGLE) SINGLE_COMBO.add(builder.<BasicAttackAnimation>nextAccessor("biped/gauntlet/" + m.id(), a -> clawMove(a, m, false)));
      for (ClawMoves.Move m : ClawMoves.DUAL) DUAL_COMBO.add(builder.<BasicAttackAnimation>nextAccessor("biped/gauntlet/" + m.id(), a -> clawMove(a, m, true)));
      // sprint attacks (tools/gen_claw_anims.py): single gauntlet a driving elbow strike, dual a running shred
      DASH = builder.nextAccessor("biped/gauntlet/dash", a -> {
         ClawMoves.Move t = ClawMoves.OF.get("dash");
         DashAttackAnimation anim = punch(new DashAttackAnimation(0.1F, t.antic(), t.antic(), t.contact(), t.recovery(), FIST_WIDE, biped().elbowR, a, Armatures.BIPED), true);
         anim.addProperty(AttackAnimationProperty.BASIS_ATTACK_SPEED, Float.valueOf(1.6F));
         anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.3F));
         anim.addProperty(AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.multiplier(1.8F));
         anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(2.0F));
         anim.addProperty(AttackPhaseProperty.STUN_TYPE, StunType.LONG);
         return anim;
      });
      DUAL_DASH = builder.nextAccessor("biped/gauntlet/dual_dash", a -> {
         ClawMoves.Move t = ClawMoves.OF.get("dual_dash");
         Phase shred = new Phase(0.0F, t.antic(), t.antic(), t.contact(), t.recovery(), Float.MAX_VALUE, InteractionHand.MAIN_HAND,
            JointColliderPair.of(biped().toolR, GAUNTLET_CLAW), JointColliderPair.of(biped().toolL, GAUNTLET_CLAW));
         DashAttackAnimation anim = punch(new DashAttackAnimation(0.08F, a, Armatures.BIPED, shred), false);
         anim.addProperty(AttackAnimationProperty.BASIS_ATTACK_SPEED, Float.valueOf(1.55F));
         addTrails(anim, trailsFor(biped().rootJoint, t.antic() - 0.06F, t.contact() + 0.03F));
         anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.1F));
         anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(4.0F));
         anim.addEvents(new AnimationEvent[]{itemAnim(0.0F, false, "claw_strike"), itemAnim(0.0F, true, "claw_strike")});
         slashEvents(anim, t);
         return anim;
      });
      AIR_SLASH = builder.nextAccessor("biped/gauntlet/airslash", a -> {
         AirSlashAnimation anim = punch(new AirSlashAnimation(0.08F, 0.36F, 0.58F, 0.68F, GAUNTLET_CLAW, biped().toolR, a, Armatures.BIPED), true);
         anim.addProperty(AttackAnimationProperty.BASIS_ATTACK_SPEED, Float.valueOf(2.0F));
         addTrails(anim, List.of(trail("Tool_R", 0.05F, 0.33F), trail("Leg_R", 0.05F, 0.3F), trail("Leg_L", 0.05F, 0.3F), trail("Tool_R", 0.36F, 0.58F)));
         anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.3F));
         anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(2.0F));
         anim.addProperty(AttackPhaseProperty.STUN_TYPE, StunType.LONG);
         anim.addEvents(new AnimationEvent[]{itemAnim(0.3F, false, "attack_4"), groundCrack(0.45F, biped().toolR, 2.4)});
         return anim;
      });
      DUAL_AIR_SLASH = builder.nextAccessor(
         "biped/gauntlet/dual_airslash",
         a -> {
            Phase spin = new Phase(
               0.0F,
               0.07F,
               0.07F,
               0.5F,
               0.52F,
               0.52F,
               InteractionHand.OFF_HAND,
               JointColliderPair.of(biped().toolL, GAUNTLET_CLAW),
               JointColliderPair.of(biped().toolR, GAUNTLET_CLAW)
            );
            Phase slam = new Phase(0.52F, 0.58F, 0.58F, 0.78F, 0.88F, Float.MAX_VALUE, InteractionHand.MAIN_HAND, biped().rootJoint, SLAM);
            AirSlashAnimation anim = punch(new AirSlashAnimation(0.08F, a, Armatures.BIPED, spin, slam), true);
            anim.addProperty(AttackAnimationProperty.BASIS_ATTACK_SPEED, Float.valueOf(1.55F));
            addTrails(anim, List.of(trail("Tool_R", 0.05F, 0.5F), trail("Tool_L", 0.05F, 0.5F), trail("Tool_R", 0.58F, 0.76F), trail("Tool_L", 0.58F, 0.76F)));
            anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(0.7F), 0);
            anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(3.0F), 0);
            anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(1.5F), 1);
            anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(4.0F), 1);
            anim.addProperty(AttackPhaseProperty.STUN_TYPE, StunType.LONG, 1);
            anim.addEvents(
               new AnimationEvent[]{
                  itemAnim(0.0F, false, "attack_5"),
                  itemAnim(0.0F, true, "attack_5"),
                  itemAnim(0.5F, false, "attack_4"),
                  itemAnim(0.5F, true, "attack_4"),
                  groundCrack(0.66F, biped().toolR, 2.8)
               }
            );
            return anim;
         }
      );
      IMPALER = builder.nextAccessor("biped/gauntlet/impaler", a -> {
         Phase phase = new Phase(0.0F, 0.3F, 0.3F, 1.15F, 1.4F, Float.MAX_VALUE, InteractionHand.MAIN_HAND, biped().rootJoint, NO_HIT);
         AttackAnimation anim = new AttackAnimation(0.1F, a, Armatures.BIPED, phase);
         punch(anim, true);
         anim.addProperty(StaticAnimationProperty.PLAY_SPEED_MODIFIER, ReusableSources.CONSTANT_ONE);
         addTrails(anim, List.of(trail("Tool_R", 0.2F, 0.45F)));
         anim.addProperty(StaticAnimationProperty.POSE_MODIFIER, ReusableSources.COMBO_ATTACK_DIRECTION_MODIFIER);
         anim.addEvents(new AnimationEvent[]{itemAnim(0.0F, false, "attack_1"), InTimeEvent.create(0.4F, GauntletFx.IMPALE, Side.SERVER)});
         return anim;
      });
      TWIN_METEOR = builder.nextAccessor(
         "biped/gauntlet/twin_meteor",
         a -> {
            float[] starts = new float[]{0.0F, 0.12F, 0.24F, 0.36F, 0.48F, 0.6F};
            Phase[] phases = new Phase[7];

            for (int ixxxx = 0; ixxxx < 6; ixxxx++) {
               boolean leftx = ixxxx % 2 == 1;
               float s = starts[ixxxx];
               phases[ixxxx] = new Phase(
                  s,
                  s + 0.02F,
                  s + 0.02F,
                  s + 0.1F,
                  s + 0.12F,
                  s + 0.12F,
                  leftx ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND,
                  leftx ? biped().toolL : biped().toolR,
                  GAUNTLET
               );
            }

            phases[6] = new Phase(0.72F, 1.0F, 1.0F, 1.15F, 1.4F, Float.MAX_VALUE, InteractionHand.MAIN_HAND, biped().rootJoint, SLAM);
            AttackAnimation anim = new AttackAnimation(0.1F, a, Armatures.BIPED, phases);
            punch(anim, false);
            anim.addProperty(StaticAnimationProperty.PLAY_SPEED_MODIFIER, ReusableSources.CONSTANT_ONE);
            List<TrailInfo> trails = new ArrayList<>();

            for (int ixxx = 0; ixxx < 6; ixxx++) {
               trails.add(trail(ixxx % 2 == 1 ? "Tool_L" : "Tool_R", starts[ixxx], starts[ixxx] + 0.11F));
            }

            trails.addAll(trailsFor(biped().rootJoint, 0.95F, 1.15F));
            addTrails(anim, trails);

            for (int ixx = 0; ixx < 6; ixx++) {
               anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(0.5F), ixx);
            }

            anim.addProperty(AttackPhaseProperty.DAMAGE_MODIFIER, ValueModifier.multiplier(2.2F), 6);
            anim.addProperty(AttackPhaseProperty.IMPACT_MODIFIER, ValueModifier.multiplier(1.8F), 6);
            anim.addProperty(AttackPhaseProperty.MAX_STRIKES_MODIFIER, ValueModifier.adder(5.0F), 6);
            anim.addProperty(AttackPhaseProperty.STUN_TYPE, StunType.KNOCKDOWN, 6);
            anim.addProperty(AttackPhaseProperty.SWING_SOUND, (SoundEvent)EpicFightSounds.WHOOSH_BIG.get(), 6);
            anim.addProperty(AttackPhaseProperty.HIT_SOUND, (SoundEvent)EpicFightSounds.BLUNT_HIT_HARD.get(), 6);

            for (int ix = 0; ix < 6; ix++) {
               anim.addEvents(new AnimationEvent[]{itemAnim(starts[ix], ix % 2 == 1, "attack_1")});
            }

            anim.addEvents(new AnimationEvent[]{itemAnim(0.8F, false, "attack_4"), itemAnim(0.8F, true, "attack_4"), groundCrack(1.07F, biped().toolR, 3.5)});
            return anim;
         }
      );
   }

   static record Timing(float antic, float contact, float recovery) {
   }
}

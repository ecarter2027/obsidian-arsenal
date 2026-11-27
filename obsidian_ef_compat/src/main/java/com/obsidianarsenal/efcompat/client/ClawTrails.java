package com.obsidianarsenal.efcompat.client;

import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.particle.Particle;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.asset.AssetAccessor;
import yesman.epicfight.api.animation.types.StaticAnimation;
import yesman.epicfight.api.client.animation.property.TrailInfo;
import yesman.epicfight.client.particle.AbstractTrailParticle;
import yesman.epicfight.client.particle.AnimationTrailParticle;
import yesman.epicfight.particle.EpicFightParticles;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

import java.lang.reflect.Field;

/**
 * The Obsidian Gauntlet's swing trail, split per finger: Epic Fight draws one ribbon across the claw (the item skin's
 * trail, used by whatever moveset swings it, Dawnday's hookclaws included). This wraps Epic Fight's swing-trail
 * particle: when the trail belongs to a hand holding the gauntlet, the ribbon is replaced by four thin streaks, one
 * running down each finger to its tip, so every rake leaves four parallel claw marks. Any other weapon is untouched.
 */
@Mod.EventBusSubscriber(modid = "obsidian_ef_compat", value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ClawTrails {
   private static final int FINGERS = 4;
   private static Field OWNER, JOINT, ANIMATION, INFO;

   private ClawTrails() {}

   @SubscribeEvent(priority = EventPriority.LOWEST)
   public static void register(RegisterParticleProvidersEvent e) {
      try {
         OWNER = AbstractTrailParticle.class.getDeclaredField("owner");
         INFO = AbstractTrailParticle.class.getDeclaredField("trailInfo");
         JOINT = AnimationTrailParticle.class.getDeclaredField("joint");
         ANIMATION = AnimationTrailParticle.class.getDeclaredField("animation");
         for (Field f : new Field[]{OWNER, INFO, JOINT, ANIMATION}) f.setAccessible(true);
      } catch (ReflectiveOperationException ex) {
         return;                                      // a different Epic Fight build: keep its own trails
      }
      AnimationTrailParticle.Provider original = new AnimationTrailParticle.Provider();
      e.registerSpecial((SimpleParticleType) EpicFightParticles.SWING_TRAIL.get(),
            (type, level, x, y, z, dx, dy, dz) -> split(original.createParticle(type, level, x, y, z, dx, dy, dz), level));
   }

   @SuppressWarnings("unchecked")
   private static Particle split(Particle p, ClientLevel level) {
      if (!(p instanceof AnimationTrailParticle)) return p;
      try {
         LivingEntityPatch<?> owner = (LivingEntityPatch<?>) OWNER.get(p);
         TrailInfo info = (TrailInfo) INFO.get(p);
         Joint joint = (Joint) JOINT.get(p);
         AssetAccessor<? extends StaticAnimation> anim = (AssetAccessor<? extends StaticAnimation>) ANIMATION.get(p);
         InteractionHand hand = info.hand() != null ? info.hand() : joint.getName().endsWith("_L") ? InteractionHand.OFF_HAND : InteractionHand.MAIN_HAND;
         if (!joint.getName().startsWith("Tool") || !ObsidianEFCompat.GAUNTLET_ID.equals(ForgeRegistries.ITEMS.getKey(((LivingEntity) owner.getOriginal()).getItemInHand(hand).getItem()))) return p;
         // the ribbon runs across the fingertips: put one streak at each finger, running a short way up the finger
         Vec3 a = info.start(), b = info.end();
         Particle first = null;
         for (int i = 0; i < FINGERS; i++) {
            Vec3 tip = a.lerp(b, (i + 0.5) / FINGERS);
            float k = 0.85F + 0.1F * i;
            TrailInfo finger = info.unpackAsBuilder()
                  .startPos(tip.add(0.0, 0.17, 0.0))
                  .endPos(tip.add(0.0, -0.03, 0.0))
                  .r(info.rCol() * k).g(info.gCol() * k).b(info.bCol())
                  .create();
            Particle f = new Finger(level, owner, joint, anim, finger);
            if (first == null) first = f;
            else Minecraft.getInstance().particleEngine.add(f);
         }
         p.remove();
         return first;
      } catch (Throwable ex) {
         return p;
      }
   }

   /** Epic Fight's own animation trail, with this finger's trail info. */
   private static final class Finger extends AnimationTrailParticle {
      Finger(ClientLevel level, LivingEntityPatch<?> owner, Joint joint, AssetAccessor<? extends StaticAnimation> anim, TrailInfo info) {
         super(level, owner, joint, anim, info);
      }
   }
}

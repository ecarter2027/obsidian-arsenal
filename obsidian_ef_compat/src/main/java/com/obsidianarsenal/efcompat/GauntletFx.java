package com.obsidianarsenal.efcompat;

import net.minecraft.core.particles.DustParticleOptions;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import software.bernie.geckolib.animatable.GeoItem;
import yesman.epicfight.api.animation.property.AnimationEvent.E0;
import yesman.epicfight.api.animation.property.AnimationEvent.E1;
import yesman.epicfight.gameasset.EpicFightSounds;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;
import yesman.epicfight.world.damagesource.StunType;

public final class GauntletFx {
   public static final String LEFT_PREFIX = "L:";
   public static final ResourceKey<DamageType> IMPALE_DAMAGE = ResourceKey.create(Registries.DAMAGE_TYPE, new ResourceLocation("obsidian_ef_compat", "impale"));
   public static final E1<String> PLAY_ITEM_ANIM = (entitypatch, animation, params) -> {
      LivingEntity entity = entitypatch.getOriginal();
      String name = params.first();
      boolean left = name.startsWith("L:");
      ItemStack stack = left ? entity.getOffhandItem() : entity.getMainHandItem();
      if (entity.level() instanceof ServerLevel level && ObsidianEFCompat.isGauntlet(stack) && stack.getItem() instanceof GeoItem geo) {
         geo.triggerAnim(entity, GeoItem.getOrAssignId(stack, level), "main", left ? name.substring("L:".length()) : name);
      }
   };
   public static final E0 IMPALE = (entitypatch, animation, params) -> {
      LivingEntity user = (LivingEntity)entitypatch.getOriginal();
      if (user.level() instanceof ServerLevel level) {
         Vec3 var18 = user.getEyePosition();
         Vec3 look = user.getLookAngle();
         Vec3 end = var18.add(look.scale(8.7F));
         HitResult wall = level.clip(new ClipContext(var18, end, Block.COLLIDER, Fluid.NONE, user));
         if (wall.getType() != Type.MISS) {
            end = wall.getLocation();
         }

         Vec3 tip = end;
         DamageSource source = new DamageSource(level.registryAccess().registryOrThrow(Registries.DAMAGE_TYPE).getHolderOrThrow(IMPALE_DAMAGE), user);
         AABB sweep = user.getBoundingBox().expandTowards(end.subtract(var18)).inflate(1.0);

         for (LivingEntity target : level.getEntitiesOfClass(
            LivingEntity.class,
            sweep,
            e -> e != user && e.isAlive() && !e.isSpectator() && !user.isAlliedTo(e) && (!(e instanceof TamableAnimal pet) || pet.getOwner() != user)
         )) {
            AABB box = target.getBoundingBox().inflate(0.45);
            if (box.contains(var18) || !box.clip(var18, tip).isEmpty()) {
               target.invulnerableTime = 0;
               if (target.hurt(source, 60.0F)) {
                  LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(target, LivingEntityPatch.class);
                  if (patch != null) {
                     patch.applyStun(StunType.HOLD, 0.6F);
                  }

                  Vec3 c = target.getBoundingBox().getCenter();
                  level.sendParticles(ParticleTypes.CRIT, c.x, c.y, c.z, 14, 0.25, 0.25, 0.25, 0.4);
                  level.sendParticles(ParticleTypes.REVERSE_PORTAL, c.x, c.y, c.z, 20, 0.3, 0.3, 0.3, 0.05);
                  level.playSound(null, c.x, c.y, c.z, (SoundEvent)EpicFightSounds.EVISCERATE.get(), SoundSource.PLAYERS, 1.0F, 0.9F);
               }
            }
         }
      }
   };

   private static final org.joml.Vector3f SLASH_PURPLE = new org.joml.Vector3f(0.72F, 0.25F, 1.0F);
   private static final org.joml.Vector3f SLASH_DEEP = new org.joml.Vector3f(0.32F, 0.04F, 0.55F);

   /**
    * A purple slash crescent along the arc a strike sweeps (gen_claw_anims.py: {time, tilt, from, to, side, height,
    * radius}): in front of the attacker, turned with where they face, the same arc the claw (or shin) travels, scaled
    * out so it reads as a slash wave. Sent from the server so everyone nearby sees it.
    */
   public static final E1<float[]> SLASH = (entitypatch, animation, params) -> {
      LivingEntity user = entitypatch.getOriginal();
      if (!(user.level() instanceof ServerLevel level)) return;
      float[] sl = params.first();
      double tilt = Math.toRadians(sl[1]), a0 = sl[2], a1 = sl[3];
      int side = (int)sl[4];
      double height = sl[5], radius = sl[6];
      float yaw = (float)Math.toRadians(user.getYRot());
      Vec3 fwd = new Vec3(-Math.sin(yaw), 0.0, Math.cos(yaw));
      Vec3 right = new Vec3(-fwd.z, 0.0, fwd.x);
      Vec3 up = new Vec3(0.0, 1.0, 0.0);
      Vec3 across = right.scale(Math.cos(tilt) * side).add(up.scale(Math.sin(tilt)));
      Vec3 centre = user.position().add(0.0, height, 0.0).add(fwd.scale(0.25)).add(right.scale(0.3 * side));
      int n = Math.max(10, (int)(Math.abs(a1 - a0) / 9.0));
      for (int i = 0; i <= n; i++) {
         double u = (double)i / n;
         double a = Math.toRadians(a0 + (a1 - a0) * u);
         double thick = Math.sin(Math.PI * u);                         // a crescent: fat in the middle, thin at the tips
         Vec3 dir = fwd.scale(Math.cos(a)).add(across.scale(Math.sin(a)));
         for (int layer = 0; layer < 3; layer++) {
            double r = radius * (0.82 + 0.12 * layer * thick);
            Vec3 p = centre.add(dir.scale(r));
            if (p.y < user.getY() + 0.1) continue;
            DustParticleOptions dust = new DustParticleOptions(layer == 1 ? SLASH_PURPLE : SLASH_DEEP, (float)(0.6 + 1.1 * thick));
            level.sendParticles(dust, p.x, p.y, p.z, 1, 0.02, 0.02, 0.02, 0.0);
         }
         if (i % 3 == 0) {
            Vec3 p = centre.add(dir.scale(radius));
            level.sendParticles(ParticleTypes.REVERSE_PORTAL, p.x, p.y, p.z, 1, 0.05, 0.05, 0.05, 0.02);
         }
      }
   };

   private GauntletFx() {
   }
}

package com.obsidianarsenal.efcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.efcompat.GauntletAnimations;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.MultiBufferSource.BufferSource;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.ClipContext.Block;
import net.minecraft.world.level.ClipContext.Fluid;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.HitResult.Type;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent.Stage;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import yesman.epicfight.api.animation.AnimationPlayer;
import yesman.epicfight.api.animation.Animator;
import yesman.epicfight.api.animation.Joint;
import yesman.epicfight.api.animation.Pose;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.Vec3f;
import yesman.epicfight.model.armature.HumanoidArmature;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

@EventBusSubscriber(
   modid = "obsidian_ef_compat",
   value = {Dist.CLIENT}
)
public final class ImpalerSpikeRenderer {
   private static final ResourceLocation TEXTURE = new ResourceLocation("minecraft", "textures/block/obsidian.png");
   private static final float BASE_HALF_WIDTH = 0.17F;

   private ImpalerSpikeRenderer() {
   }

   @SubscribeEvent
   public static void onRenderLevel(RenderLevelStageEvent event) {
      if (event.getStage() == Stage.AFTER_ENTITIES) {
         Minecraft mc = Minecraft.getInstance();
         if (mc.level != null) {
            float partial = event.getPartialTick();
            Vec3 cam = event.getCamera().getPosition();
            BufferSource buffers = mc.renderBuffers().bufferSource();
            boolean drew = false;

            for (Player player : mc.level.players()) {
               if (!(player.distanceToSqr(cam) > 16384.0)) {
                  LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, LivingEntityPatch.class);
                  if (patch != null) {
                     Armature anim = patch.getArmature();
                     if (anim instanceof HumanoidArmature) {
                        HumanoidArmature biped = (HumanoidArmature)anim;
                        AnimationPlayer animx = patch.<Animator>getAnimator().getPlayerFor(null);
                        if (animx != null
                           && animx.getRealAnimation() != null
                           && GauntletAnimations.IMPALER != null
                           && GauntletAnimations.IMPALER.registryName().equals(animx.getRealAnimation().registryName())) {
                           float time = Mth.lerp(partial, animx.getPrevElapsedTime(), animx.getElapsedTime());
                           float length = spikeLength(time);
                           if (!(length <= 0.02F)) {
                              Vec3 eye = player.getEyePosition(partial);
                              Vec3 look = player.getViewVector(partial);
                              Vec3 aim = eye.add(look.scale(8.7F));
                              HitResult wall = mc.level.clip(new ClipContext(eye, aim, Block.COLLIDER, Fluid.NONE, player));
                              if (wall.getType() != Type.MISS) {
                                 aim = wall.getLocation();
                              }

                              Vec3 fist;
                              if (player == mc.player && mc.options.getCameraType().isFirstPerson()) {
                                 Vec3 right = look.cross(new Vec3(0.0, 1.0, 0.0)).normalize();
                                 fist = eye.add(right.scale(0.32)).add(0.0, -0.28, 0.0).add(look.scale(0.45));
                              } else {
                                 Vec3 pos = player.getPosition(partial);
                                 Pose pose = patch.<Animator>getAnimator().getPose(partial);
                                 OpenMatrix4f toWorld = OpenMatrix4f.createTranslation((float)pos.x, (float)pos.y, (float)pos.z)
                                    .mulBack(OpenMatrix4f.createRotatorDeg(180.0F, Vec3f.Y_AXIS).mulBack(patch.getModelMatrix(partial)));
                                 fist = jointPos(biped, pose, biped.toolR, toWorld);
                              }

                              Vec3 toAim = aim.subtract(fist);
                              double reach = toAim.length();
                              if (!(reach < 0.2)) {
                                 Vec3 dir = toAim.scale(1.0 / reach);
                                 float spike = (float)(reach * (double)length / 6.0);
                                 drawSpike(
                                    event.getPoseStack(),
                                    buffers.getBuffer(RenderType.entityCutoutNoCull(TEXTURE)),
                                    fist.subtract(dir.scale(0.15)).subtract(cam),
                                    dir,
                                    spike + 0.15F
                                 );
                                 drew = true;
                              }
                           }
                        }
                     }
                  }
               }
            }

            if (drew) {
               buffers.endBatch();
            }
         }
      }
   }

   private static float spikeLength(float t) {
      float max = 6.0F;
      if (t < 0.3F || t > 1.27F) {
         return 0.0F;
      } else if (t < 0.4F) {
         float k = (t - 0.3F) / 0.099999994F;
         return max * (1.0F - (1.0F - k) * (1.0F - k));
      } else if (t <= 1.15F) {
         return max;
      } else {
         float k = (t - 1.15F) / 0.120000005F;
         return max * (1.0F - k * k);
      }
   }

   private static Vec3 jointPos(HumanoidArmature biped, Pose pose, Joint joint, OpenMatrix4f toWorld) {
      OpenMatrix4f m = new OpenMatrix4f(biped.getBoundTransformFor(pose, joint)).mulFront(toWorld);
      return OpenMatrix4f.transform(m, Vec3.ZERO);
   }

   private static void drawSpike(PoseStack poseStack, VertexConsumer vc, Vec3 base, Vec3 dir, float length) {
      Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1.0, 0.0, 0.0) : new Vec3(0.0, 1.0, 0.0);
      Vec3 side = dir.cross(up).normalize().scale(0.17F);
      Vec3 lift = side.cross(dir).normalize().scale(0.17F);
      Vec3 tip = base.add(dir.scale((double)length));
      Vec3[] corners = new Vec3[]{base.add(side).add(lift), base.add(lift).subtract(side), base.subtract(side).subtract(lift), base.add(side).subtract(lift)};
      Matrix4f mat = poseStack.last().pose();
      Matrix3f normalMat = poseStack.last().normal();
      int light = 15728880;
      float vLen = 1.0F;

      for (int i = 0; i < 4; i++) {
         Vec3 a = corners[i];
         Vec3 b = corners[(i + 1) % 4];
         Vec3 n = b.subtract(a).cross(tip.subtract(a)).normalize();
         vertex(vc, mat, normalMat, a, 0.0F, 0.0F, n, light);
         vertex(vc, mat, normalMat, b, 1.0F, 0.0F, n, light);
         vertex(vc, mat, normalMat, tip, 0.5F, vLen, n, light);
         vertex(vc, mat, normalMat, tip, 0.5F, vLen, n, light);
      }

      Vec3 n = dir.reverse();

      for (int i = 3; i >= 0; i--) {
         vertex(vc, mat, normalMat, corners[i], (float)(i % 2), (float)(i / 2), n, light);
      }
   }

   private static void vertex(VertexConsumer vc, Matrix4f mat, Matrix3f normalMat, Vec3 p, float u, float v, Vec3 n, int light) {
      vc.vertex(mat, (float)p.x, (float)p.y, (float)p.z)
         .color(0.78F, 0.55F, 1.0F, 1.0F)
         .uv(u, v)
         .overlayCoords(OverlayTexture.NO_OVERLAY)
         .uv2(light)
         .normal(normalMat, (float)n.x, (float)n.y, (float)n.z)
         .endVertex();
   }
}

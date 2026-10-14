package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.obsidianarsenal.ObsidianArsenal;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoEntity;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** GeckoLib projectile renderer: yaw + pitch so the model (front = -Z) points where the entity faces. */
public class ProjectileGeoRenderer<T extends Entity & GeoEntity> extends GeoEntityRenderer<T> {
    private final boolean glow;

    public ProjectileGeoRenderer(EntityRendererProvider.Context ctx, String model, boolean glow) {
        super(ctx, new DefaultedEntityGeoModel<>(ObsidianArsenal.id(model)));
        this.glow = glow;
        this.shadowRadius = 0f;
    }

    @Override
    protected void applyRotations(T animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        // GeckoLib passes yaw 0 for non-living entities; use the entity's own (mob-style) yaw + pitch.
        super.applyRotations(animatable, poseStack, ageInTicks, Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot()), partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, animatable.xRotO, animatable.getXRot())));
    }

    @Override
    public RenderType getRenderType(T animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return glow ? RenderType.entityTranslucentEmissive(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    protected int getBlockLightLevel(T entity, BlockPos pos) { return 15; }
}

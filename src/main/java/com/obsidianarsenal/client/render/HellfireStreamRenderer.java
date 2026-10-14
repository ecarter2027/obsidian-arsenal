package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.entity.HellfireStreamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** The 3D flame cone: aimed with the caster's look, stretched to the stream's current length, translucent + full-bright. */
public class HellfireStreamRenderer extends GeoEntityRenderer<HellfireStreamEntity> {
    public HellfireStreamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(ObsidianArsenal.id("hellfire_stream")));
        this.shadowRadius = 0f;
    }

    @Override
    protected void applyRotations(HellfireStreamEntity e, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(e, poseStack, ageInTicks, Mth.rotLerp(partialTick, e.yRotO, e.getYRot()), partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, e.xRotO, e.getXRot())));
        // the model is authored 10 blocks long along -Z; shorten it when the stream hits a wall
        poseStack.scale(1f, 1f, (float) (e.getLength() / HellfireStreamEntity.RANGE));
    }

    @Override
    public RenderType getRenderType(HellfireStreamEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override
    protected int getBlockLightLevel(HellfireStreamEntity entity, BlockPos pos) { return 15; }
}

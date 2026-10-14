package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.entity.PlasmaBeamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Aims the beam model (front = -Z) along the beam and stretches only its length; width stays half a block. */
public class PlasmaBeamRenderer extends GeoEntityRenderer<PlasmaBeamEntity> {
    public PlasmaBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(ObsidianArsenal.id("plasma_beam")));
        this.shadowRadius = 0f;
    }

    @Override
    protected void applyRotations(PlasmaBeamEntity e, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(e, poseStack, ageInTicks, Mth.rotLerp(partialTick, e.yRotO, e.getYRot()), partialTick);
        poseStack.mulPose(Axis.XP.rotationDegrees(-Mth.lerp(partialTick, e.xRotO, e.getXRot())));
        poseStack.scale(1f, 1f, (float) (e.getLength() / PlasmaBeamEntity.MODEL_LENGTH));
    }

    @Override
    public RenderType getRenderType(PlasmaBeamEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override protected int getBlockLightLevel(PlasmaBeamEntity entity, BlockPos pos) { return 15; }
}

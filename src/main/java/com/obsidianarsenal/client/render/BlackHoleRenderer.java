package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.client.BlackHoleLensRenderer;
import software.bernie.geckolib.cache.object.BakedGeoModel;
import com.obsidianarsenal.entity.BlackHoleEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Black core + glowing purple accretion disk, scaled by the hole's grow/collapse factor. */
public class BlackHoleRenderer extends GeoEntityRenderer<BlackHoleEntity> {
    public BlackHoleRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<>(ObsidianArsenal.id("black_hole")));
        this.shadowRadius = 0f;
    }

    @Override
    protected void applyRotations(BlackHoleEntity e, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(e, poseStack, ageInTicks, 0f, partialTick);
        float s = Math.max(0.001f, e.sizeFactor(partialTick) * e.tierScale());
        poseStack.scale(s, s, s);
    }

    /** When the lens shader is running it draws the (lensed) accretion disk itself; keep only the black core. */
    @Override
    public void preRender(PoseStack poseStack, BlackHoleEntity animatable, BakedGeoModel model, MultiBufferSource bufferSource,
                          VertexConsumer buffer, boolean isReRender, float partialTick, int packedLight, int packedOverlay,
                          float red, float green, float blue, float alpha) {
        boolean lens = BlackHoleLensRenderer.lensAvailable();
        model.getBone("disk_tilt").ifPresent(b -> b.setHidden(lens));
        model.getBone("photon_ring").ifPresent(b -> b.setHidden(lens));
        super.preRender(poseStack, animatable, model, bufferSource, buffer, isReRender, partialTick, packedLight, packedOverlay, red, green, blue, alpha);
    }

    @Override
    public RenderType getRenderType(BlackHoleEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return RenderType.entityTranslucentEmissive(texture);
    }

    @Override protected int getBlockLightLevel(BlackHoleEntity entity, BlockPos pos) { return 15; }
}

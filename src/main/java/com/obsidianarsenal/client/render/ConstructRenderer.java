package com.obsidianarsenal.client.render;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.entity.ConstructEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.util.Mth;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/** Spikes / shards / tentacles / columns / gyroscope. Hellfire kinds are translucent + emissive. */
public class ConstructRenderer extends GeoEntityRenderer<ConstructEntity> {
    private final ConstructEntity.Kind kind;

    public ConstructRenderer(EntityRendererProvider.Context ctx, ConstructEntity.Kind kind) {
        super(ctx, new DefaultedEntityGeoModel<>(ObsidianArsenal.id(kind.model)));
        this.kind = kind;
        this.shadowRadius = 0f;
    }

    /**
     * GeckoLib only applies yaw for LivingEntities (non-living entities always get yaw 0), which is why every
     * construct used to face the same way. Feed it this entity's own yaw.
     */
    @Override
    protected void applyRotations(ConstructEntity animatable, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTick) {
        super.applyRotations(animatable, poseStack, ageInTicks, Mth.rotLerp(partialTick, animatable.yRotO, animatable.getYRot()), partialTick);
    }

    @Override
    public RenderType getRenderType(ConstructEntity animatable, ResourceLocation texture, @Nullable MultiBufferSource bufferSource, float partialTick) {
        return kind.fire ? RenderType.entityTranslucentEmissive(texture) : super.getRenderType(animatable, texture, bufferSource, partialTick);
    }

    @Override
    protected int getBlockLightLevel(ConstructEntity entity, BlockPos pos) {
        return kind.fire || kind == ConstructEntity.Kind.STORM_SPIKE ? 15 : super.getBlockLightLevel(entity, pos);
    }
}

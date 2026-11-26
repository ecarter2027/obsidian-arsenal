package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.entity.VoidRayEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * Abyssal Ray, in Herobrine's Void Beam style but wider: nested tubes seen from any angle. A wide soft purple haze,
 * a brighter twisting purple band and a thin white rim (additive light, no depth write), then the thick black core
 * drawn last inside them so it stays black; a purple flare where it lands. Swells in over the first few ticks.
 */
public class VoidRayRenderer extends EntityRenderer<VoidRayEntity> {
    private static final float PR = 0.72f, PG = 0.3f, PB = 1f;

    public VoidRayRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(VoidRayEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        double len = Math.max(0.6, e.getLength());
        Vec3 dir = Vec3.directionFromRotation(e.getViewXRot(pt), e.getViewYRot(pt)).normalize();
        float time = e.tickCount + pt;
        float in = Math.min(1f, time / 4f);
        float w = in * (1f + 0.06f * Mth.sin(time * 1.7f) + 0.03f * Mth.sin(time * 4.3f));
        double R = 1.45 * w;
        PoseStack.Pose p = pose.last();
        var glow = buffers.getBuffer(OaRenderTypes.glow(OaRenderTypes.SHEATH));
        // purple haze, a brighter twisting band, the white rim hugging the core
        Shapes.tube(glow, p, dir, len, R * 1.55, 1.8, time * 0.05f, -time * 0.35f, 0.17f, PR * 0.3f, PG * 0.3f, PB * 0.3f, 1);
        Shapes.tube(glow, p, dir, len, R * 1.0, 1.6, -time * 0.09f, -time * 0.45f, 0.25f, PR * 0.85f, PG * 0.85f, PB * 0.85f, 1);
        Shapes.tube(glow, p, dir, len, R * 0.52, 1.4, time * 0.2f, -time * 0.6f, 0.8f, 0.95f, 0.9f, 1f, 1);
        // flares: where it lands, and a smaller one at the hand
        var flare = buffers.getBuffer(OaRenderTypes.glow(OaRenderTypes.WHITE));
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(e.getPosition(pt));
        Vec3 end = dir.scale(len);
        Shapes.ring(flare, p, end, cam.subtract(end), 0, R * 2.4, PR, PG, PB, 0.9f, 0f);
        Shapes.ring(flare, p, end, cam.subtract(end), 0, R * 1.0, 1, 1, 1, 0.7f, 0f);
        Shapes.ring(flare, p, Vec3.ZERO, cam, 0, R * 0.9, PR, PG, PB, 0.8f, 0f);
        // the black core last, roiling (normal blending: black adds no light, it hides what is behind it)
        var core = buffers.getBuffer(RenderType.entityTranslucent(OaRenderTypes.WHITE));
        Shapes.tube(core, p, dir, len - 0.05, R * 0.42, 1.3, -time * 0.1f, time * 0.2f, 0.5f, 0.02f, 0f, 0.04f, 0.97f);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(VoidRayEntity e) { return OaRenderTypes.SHEATH; }
}

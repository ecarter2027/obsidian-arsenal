package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.entity.GrappleHookEntity;
import com.obsidianarsenal.entity.PlasmaBeamEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import software.bernie.geckolib.model.DefaultedEntityGeoModel;
import software.bernie.geckolib.renderer.GeoEntityRenderer;

/**
 * Grappling hook: a 3D obsidian claw (geo/entity/grapple_hook.geo.json, vanilla obsidian texture) pointing along the
 * line of fire, joined to the gauntlet hand by a chain drawn with the vanilla chain texture (two crossed strips,
 * one link pattern per block, lit by the world like a real chain).
 */
public class GrappleHookRenderer extends GeoEntityRenderer<GrappleHookEntity> {
    private static final ResourceLocation OBSIDIAN = new ResourceLocation("minecraft", "textures/block/obsidian.png");
    private static final ResourceLocation CHAIN = new ResourceLocation("minecraft", "textures/block/chain.png");
    /** Model length from the tip back to the eyelet, in blocks. */
    private static final double EYELET = 8.2 / 16.0;

    private Vec3 aim = new Vec3(0, 0, -1);

    public GrappleHookRenderer(EntityRendererProvider.Context ctx) {
        super(ctx, new DefaultedEntityGeoModel<GrappleHookEntity>(ObsidianArsenal.id("grapple_hook")) {
            @Override public ResourceLocation getTextureResource(GrappleHookEntity animatable) { return OBSIDIAN; }
        });
        this.shadowRadius = 0f;
    }

    @Override
    public void render(GrappleHookEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity o = e.getOwner();
        if (!(o instanceof LivingEntity owner)) return;
        Vec3 self = e.getPosition(pt);
        Vec3 hand = PlasmaBeamEntity.handPos(owner).add(owner.getPosition(pt).subtract(owner.position()));
        Vec3 out = self.subtract(hand);
        double len = out.length();
        if (len < 1e-3) return;
        aim = out.scale(1 / len);

        // chain: from the hook's eyelet back to the hand
        Vec3 start = aim.scale(-EYELET);              // relative to the entity (pose origin)
        Vec3 end = hand.subtract(self);
        drawChain(e, pose, buffers, start, end, hand, self);

        super.render(e, yaw, pt, pose, buffers, light);
    }

    /** Point the model's -Z (the spear tip) along the line of fire. */
    @Override
    protected void applyRotations(GrappleHookEntity e, PoseStack pose, float ageInTicks, float rotationYaw, float partialTick) {
        pose.mulPose(new Quaternionf().rotationTo(0f, 0f, -1f, (float) aim.x, (float) aim.y, (float) aim.z));
    }

    private void drawChain(GrappleHookEntity e, PoseStack pose, MultiBufferSource buffers, Vec3 a, Vec3 b, Vec3 handWorld, Vec3 selfWorld) {
        Vec3 d = b.subtract(a);
        double len = d.length();
        if (len < 1e-3) return;
        Vec3 dir = d.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize(), s2 = dir.cross(s1).normalize();
        double half = 1.5 / 16.0;                      // vanilla chain strips are 3 px wide
        VertexConsumer vc = buffers.getBuffer(RenderType.entityCutoutNoCull(CHAIN));
        Matrix4f m = pose.last().pose();
        Matrix3f n = pose.last().normal();
        int segs = (int) Math.ceil(len);
        for (int i = 0; i < segs; i++) {
            double t0 = i, t1 = Math.min(len, i + 1);
            Vec3 p0 = a.add(dir.scale(t0)), p1 = a.add(dir.scale(t1));
            float v1 = (float) (t1 - t0);              // one full link pattern per block; last piece cropped
            Vec3 mid = selfWorld.add(p0.add(p1).scale(0.5));
            int light = lightAt(e, mid);
            strip(vc, m, n, p0, p1, s1.scale(half), s2, 0f, 3f / 16f, v1, light);
            strip(vc, m, n, p0, p1, s2.scale(half), s1, 3f / 16f, 6f / 16f, v1, light);
        }
    }

    private static int lightAt(GrappleHookEntity e, Vec3 p) {
        BlockPos bp = BlockPos.containing(p);
        return LightTexture.pack(e.level().getBrightness(LightLayer.BLOCK, bp), e.level().getBrightness(LightLayer.SKY, bp));
    }

    private static void strip(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p0, Vec3 p1, Vec3 w, Vec3 normal,
                              float u0, float u1, float v1, int light) {
        v(vc, m, n, p0.add(w), u1, 0f, normal, light);
        v(vc, m, n, p0.subtract(w), u0, 0f, normal, light);
        v(vc, m, n, p1.subtract(w), u0, v1, normal, light);
        v(vc, m, n, p1.add(w), u1, v1, normal, light);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, float u, float vv, Vec3 normal, int light) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(255, 255, 255, 255).uv(u, vv)
                .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(light)
                .normal(n, (float) normal.x, (float) normal.y, (float) normal.z).endVertex();
    }
}

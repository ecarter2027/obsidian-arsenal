package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.entity.GrappleHookEntity;
import com.obsidianarsenal.entity.PlasmaBeamEntity;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.TextureAtlas;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix4f;

/**
 * Glowing obsidian tether from the gauntlet hand to the claw: alternating dark / bright purple links drawn as two
 * crossed ribbons, plus a four-pronged glowing claw at the anchor.
 */
public class GrappleHookRenderer extends EntityRenderer<GrappleHookEntity> {
    public GrappleHookRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(GrappleHookEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        Entity o = e.getOwner();
        if (!(o instanceof LivingEntity owner)) return;
        Vec3 self = e.getPosition(pt);
        Vec3 hand = PlasmaBeamEntity.handPos(owner).add(owner.getPosition(pt).subtract(owner.position()));
        Vec3 d = hand.subtract(self);
        double len = d.length();
        if (len < 1e-3) return;
        VertexConsumer vc = buffers.getBuffer(RenderType.lightning());
        Matrix4f m = pose.last().pose();
        Vec3 dir = d.scale(1 / len);
        Vec3 up = Math.abs(dir.y) > 0.95 ? new Vec3(1, 0, 0) : new Vec3(0, 1, 0);
        Vec3 s1 = dir.cross(up).normalize(), s2 = dir.cross(s1).normalize();
        int links = Math.max(1, (int) (len / 0.35));
        float pulse = (e.tickCount + pt) * 0.6f;
        for (int i = 0; i < links; i++) {
            double t0 = (double) i / links, t1 = (double) (i + 1) / links;
            boolean bright = ((i + (int) pulse) & 1) == 0;
            float r = bright ? 0.80f : 0.30f, g = bright ? 0.45f : 0.08f, b = bright ? 1.00f : 0.55f;
            double w = bright ? 0.06 : 0.045;
            ribbon(vc, m, d.scale(t0), d.scale(t1), s1.scale(w), r, g, b);
            ribbon(vc, m, d.scale(t0), d.scale(t1), s2.scale(w), r, g, b);
        }
        // claw: four glowing prongs splayed around the tether direction
        Vec3 back = dir.scale(-0.45);
        for (Vec3 side : new Vec3[]{s1, s1.scale(-1), s2, s2.scale(-1)}) {
            Vec3 tip = back.add(side.scale(0.28));
            ribbon(vc, m, Vec3.ZERO, tip, side.cross(dir).scale(0.05), 0.95f, 0.7f, 1f);
            ribbon(vc, m, Vec3.ZERO, tip, dir.scale(0.05), 0.95f, 0.7f, 1f);
        }
        super.render(e, yaw, pt, pose, buffers, light);
    }

    private static void ribbon(VertexConsumer vc, Matrix4f m, Vec3 a, Vec3 b, Vec3 w, float r, float g, float bl) {
        v(vc, m, a.add(w), r, g, bl);
        v(vc, m, a.subtract(w), r, g, bl);
        v(vc, m, b.subtract(w), r, g, bl);
        v(vc, m, b.add(w), r, g, bl);
    }

    private static void v(VertexConsumer vc, Matrix4f m, Vec3 p, float r, float g, float b) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, 0.9f).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(GrappleHookEntity e) { return TextureAtlas.LOCATION_BLOCKS; }
}

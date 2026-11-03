package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.client.mesh.Mesh;
import com.obsidianarsenal.entity.PlasmaBeamEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Plasma beam as a smooth capsule: a rounded, bullet-shaped nose at the casting hand that swells to the full
 * half-block width almost at once, then a straight beam to the target. Fast red/white streaks scroll along it,
 * with a faint outer glow shell.
 */
public class PlasmaBeamRenderer extends EntityRenderer<PlasmaBeamEntity> {
    private static final ResourceLocation STREAK = ObsidianArsenal.id("textures/entity/plasma_streak.png");
    private static final ResourceLocation HALO = ObsidianArsenal.id("textures/entity/plasma_halo.png");
    private static final double RADIUS = 0.25, NOSE = 0.55, TAIL = 0.2;
    private static final int SEG = 24;

    public PlasmaBeamRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(PlasmaBeamEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        double len = Math.max(0.6, e.getLength());
        Vec3 dir = Vec3.directionFromRotation(e.getViewXRot(pt), e.getViewYRot(pt)).normalize();
        float time = (e.tickCount + pt);
        PoseStack.Pose p = pose.last();
        capsule(new Mesh.Out(buffers.getBuffer(RenderType.entityTranslucentEmissive(STREAK)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 1),
                p, dir, len, RADIUS, -time * 0.9f, 0.8f);
        capsule(new Mesh.Out(buffers.getBuffer(RenderType.entityTranslucentEmissive(HALO)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 1, 1, 1, 0.45f),
                p, dir, len + 0.05, RADIUS * 1.45, -time * 0.35f, 0.25f);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    /**
     * Profile along the beam: elliptical nose of length NOSE (tip exactly at the hand), straight body, small rounded
     * tail at the target. vScroll moves the texture along the beam; vPerBlock sets how stretched the streaks are.
     */
    private static void capsule(Mesh.Out o, PoseStack.Pose pose, Vec3 dir, double len, double r, float vScroll, float vPerBlock) {
        Vec3 e1 = Mesh.perp(dir), e2 = dir.cross(e1).normalize();
        int noseRings = 10, tailRings = 4;
        double nose = Math.min(NOSE * (r / RADIUS), len * 0.45), tail = Math.min(TAIL * (r / RADIUS), len * 0.2);
        int n = noseRings + 1 + tailRings + 1;
        double[] z = new double[n], rr = new double[n];
        for (int k = 0; k <= noseRings; k++) {
            double phi = (Math.PI / 2) * k / noseRings;                 // 0 at the tip -> 90 deg at full width
            z[k] = nose * (1 - Math.cos(phi));
            rr[k] = r * Math.sin(phi);
        }
        for (int k = 0; k <= tailRings; k++) {
            double phi = (Math.PI / 2) * k / tailRings;
            z[noseRings + 1 + k] = len - tail + tail * Math.sin(phi);
            rr[noseRings + 1 + k] = r * Math.cos(phi);
        }
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        for (int k = 0; k < n - 1; k++) {
            for (int s = 0; s < SEG; s++) {
                double t0 = 2 * Math.PI * s / SEG, t1 = 2 * Math.PI * (s + 1) / SEG;
                Vec3 r0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), r1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
                Vec3 a0 = dir.scale(z[k]).add(r0.scale(rr[k])), a1 = dir.scale(z[k]).add(r1.scale(rr[k]));
                Vec3 b0 = dir.scale(z[k + 1]).add(r0.scale(rr[k + 1])), b1 = dir.scale(z[k + 1]).add(r1.scale(rr[k + 1]));
                float u0 = (float) s / SEG * 2, u1 = (float) (s + 1) / SEG * 2;
                float v0 = (float) (z[k] * vPerBlock) + vScroll, v1 = (float) (z[k + 1] * vPerBlock) + vScroll;
                vert(o, m, nm, a0, r0, u0, v0); vert(o, m, nm, b0, r0, u0, v1);
                vert(o, m, nm, b1, r1, u1, v1); vert(o, m, nm, a1, r1, u1, v0);
            }
        }
    }

    private static void vert(Mesh.Out o, Matrix4f m, Matrix3f n, Vec3 p, Vec3 nor, float u, float v) {
        o.vc().vertex(m, (float) p.x, (float) p.y, (float) p.z).color(o.r(), o.g(), o.b(), o.a()).uv(u, v)
                .overlayCoords(o.overlay()).uv2(o.light()).normal(n, (float) nor.x, (float) nor.y, (float) nor.z).endVertex();
    }

    @Override
    public ResourceLocation getTextureLocation(PlasmaBeamEntity e) { return STREAK; }
}

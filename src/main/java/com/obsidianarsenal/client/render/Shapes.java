package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.client.mesh.Mesh;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/** Small procedural shapes for the glow effects: tapered tubes along a beam and flat rings / discs facing a direction. */
public final class Shapes {
    private Shapes() {}

    static void v(VertexConsumer vc, Matrix4f m, Matrix3f n, Vec3 p, Vec3 nor, float u, float vv, float r, float g, float b, float a) {
        vc.vertex(m, (float) p.x, (float) p.y, (float) p.z).color(r, g, b, a).uv(u, vv).overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT).normal(n, (float) nor.x, (float) nor.y, (float) nor.z).endVertex();
    }

    /**
     * An open tube from the origin along {@code dir} for {@code len} blocks, radius {@code r}, swelling from nothing
     * over the first {@code taper} blocks and rounding off over the last half block. The texture scrolls by
     * {@code vScroll} and repeats every {@code 1/vPerBlock} blocks; {@code twist} turns it round the axis.
     */
    public static void tube(VertexConsumer vc, PoseStack.Pose pose, Vec3 dir, double len, double r, double taper, float twist,
                            float vScroll, float vPerBlock, float cr, float cg, float cb, float ca) {
        Vec3 e1 = Mesh.perp(dir), e2 = dir.cross(e1).normalize();
        int seg = 20;
        int rings = Math.max(4, (int) Math.ceil(len / 0.75)) + 6;
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        double[] z = new double[rings + 1], rr = new double[rings + 1];
        for (int k = 0; k <= rings; k++) {
            double f = (double) k / rings;
            double zz = k < 6 ? taper * Math.pow((double) k / 6, 1.5) : taper + (len - taper) * (k - 6) / (rings - 6);
            z[k] = Math.min(len, zz);
            double swell = taper <= 0 ? 1 : Math.sqrt(Math.min(1, z[k] / taper));
            double end = Math.min(1, Math.max(0, (len - z[k]) / 0.5));
            rr[k] = r * swell * Math.sqrt(end);
            if (f < 0) rr[k] = 0;
        }
        for (int k = 0; k < rings; k++) {
            for (int s = 0; s < seg; s++) {
                double t0 = 2 * Math.PI * s / seg + twist, t1 = 2 * Math.PI * (s + 1) / seg + twist;
                Vec3 r0 = e1.scale(Math.cos(t0)).add(e2.scale(Math.sin(t0))), r1 = e1.scale(Math.cos(t1)).add(e2.scale(Math.sin(t1)));
                Vec3 a0 = dir.scale(z[k]).add(r0.scale(rr[k])), a1 = dir.scale(z[k]).add(r1.scale(rr[k]));
                Vec3 b0 = dir.scale(z[k + 1]).add(r0.scale(rr[k + 1])), b1 = dir.scale(z[k + 1]).add(r1.scale(rr[k + 1]));
                float u0 = (float) s / seg, u1 = (float) (s + 1) / seg;
                float v0 = (float) (z[k] * vPerBlock) + vScroll, v1 = (float) (z[k + 1] * vPerBlock) + vScroll;
                v(vc, m, nm, a0, r0, u0, v0, cr, cg, cb, ca); v(vc, m, nm, b0, r0, u0, v1, cr, cg, cb, ca);
                v(vc, m, nm, b1, r1, u1, v1, cr, cg, cb, ca); v(vc, m, nm, a1, r1, u1, v0, cr, cg, cb, ca);
            }
        }
    }

    /**
     * A flat ring centred at {@code c} facing {@code normal}, from radius {@code r0} to {@code r1}; the colour runs
     * from (inner) to (outer) brightness, so a ring can fade softly out to nothing (inner 1, outer 0) for a halo.
     */
    public static void ring(VertexConsumer vc, PoseStack.Pose pose, Vec3 c, Vec3 normal, double r0, double r1,
                            float cr, float cg, float cb, float inner, float outer) {
        Vec3 n = normal.normalize(), u = Mesh.perp(n), w = n.cross(u).normalize();
        Matrix4f m = pose.pose();
        Matrix3f nm = pose.normal();
        int seg = 40;
        for (int s = 0; s < seg; s++) {
            double t0 = 2 * Math.PI * s / seg, t1 = 2 * Math.PI * (s + 1) / seg;
            Vec3 d0 = u.scale(Math.cos(t0)).add(w.scale(Math.sin(t0))), d1 = u.scale(Math.cos(t1)).add(w.scale(Math.sin(t1)));
            v(vc, m, nm, c.add(d0.scale(r0)), n, 0, 0, cr * inner, cg * inner, cb * inner, inner);
            v(vc, m, nm, c.add(d0.scale(r1)), n, 0, 1, cr * outer, cg * outer, cb * outer, outer);
            v(vc, m, nm, c.add(d1.scale(r1)), n, 1, 1, cr * outer, cg * outer, cb * outer, outer);
            v(vc, m, nm, c.add(d1.scale(r0)), n, 1, 0, cr * inner, cg * inner, cb * inner, inner);
        }
    }
}

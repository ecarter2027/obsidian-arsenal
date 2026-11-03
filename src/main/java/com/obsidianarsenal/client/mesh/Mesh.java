package com.obsidianarsenal.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Smooth procedural meshes (thorns, tubes, capsules, rounded blobs) emitted straight into an entity vertex buffer.
 * Used instead of block-shaped cubes wherever a part should look sharp and rounded. All inputs are in render units
 * (blocks) in the current pose's space. Quads only (entity render types); triangles are written as degenerate quads.
 */
public final class Mesh {
    private Mesh() {}

    /** Where one emitted vertex goes: the buffer plus its lighting. */
    public record Out(VertexConsumer vc, int light, int overlay, float r, float g, float b, float a) {}

    private static void v(Out o, Matrix4f m, Matrix3f n, Vec3 p, Vec3 nor, float u, float vv) {
        o.vc().vertex(m, (float) p.x, (float) p.y, (float) p.z).color(o.r(), o.g(), o.b(), o.a()).uv(u, vv)
                .overlayCoords(o.overlay()).uv2(o.light()).normal(n, (float) nor.x, (float) nor.y, (float) nor.z).endVertex();
    }

    private static void quad(Out o, Matrix4f m, Matrix3f n, Vec3[] p, Vec3[] nr, float[] uv) {
        for (int i = 0; i < 4; i++) v(o, m, n, p[i], nr[i], uv[i * 2], uv[i * 2 + 1]);
    }

    /** A unit vector perpendicular to d. */
    public static Vec3 perp(Vec3 d) {
        Vec3 ref = Math.abs(d.y) < 0.9 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        return d.cross(ref).normalize();
    }

    /** Draws a lattice of rings: ring k has centre c[k], radii (ra[k], rb[k]) along axes (ea[k], eb[k]). */
    private static void lattice(Out o, PoseStack.Pose pose, Vec3[] c, double[] ra, double[] rb, Vec3[] ea, Vec3[] eb, int seg, int from, int to, float vScale) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int k = from; k < to; k++) {
            for (int s = 0; s < seg; s++) {
                double t0 = 2 * Math.PI * s / seg, t1 = 2 * Math.PI * (s + 1) / seg;
                Vec3 a0 = ring(c[k], ra[k], rb[k], ea[k], eb[k], t0), a1 = ring(c[k], ra[k], rb[k], ea[k], eb[k], t1);
                Vec3 b1 = ring(c[k + 1], ra[k + 1], rb[k + 1], ea[k + 1], eb[k + 1], t1), b0 = ring(c[k + 1], ra[k + 1], rb[k + 1], ea[k + 1], eb[k + 1], t0);
                Vec3 n0 = radial(ea[k], eb[k], ra[k], rb[k], t0, c[k + 1].subtract(c[k]), ra[k] - ra[k + 1]);
                Vec3 n1 = radial(ea[k], eb[k], ra[k], rb[k], t1, c[k + 1].subtract(c[k]), ra[k] - ra[k + 1]);
                float u0 = (float) s / seg, u1 = (float) (s + 1) / seg, v0 = k * vScale, v1 = (k + 1) * vScale;
                quad(o, m, n, new Vec3[]{a0, b0, b1, a1}, new Vec3[]{n0, n0, n1, n1}, new float[]{u0, v0, u0, v1, u1, v1, u1, v0});
            }
        }
    }

    private static Vec3 ring(Vec3 c, double ra, double rb, Vec3 ea, Vec3 eb, double t) {
        return c.add(ea.scale(Math.cos(t) * ra)).add(eb.scale(Math.sin(t) * rb));
    }

    private static Vec3 radial(Vec3 ea, Vec3 eb, double ra, double rb, double t, Vec3 along, double shrink) {
        Vec3 out = ea.scale(Math.cos(t) / Math.max(ra, 1e-4)).add(eb.scale(Math.sin(t) / Math.max(rb, 1e-4))).normalize();
        double l = along.length();
        if (l > 1e-6) out = out.add(along.scale(shrink / (l * l))).normalize();
        return out;
    }

    private static void fan(Out o, PoseStack.Pose pose, Vec3 c, double ra, double rb, Vec3 ea, Vec3 eb, Vec3 normal, int seg) {
        Matrix4f m = pose.pose();
        Matrix3f n = pose.normal();
        for (int s = 0; s < seg; s++) {
            double t0 = 2 * Math.PI * s / seg, t1 = 2 * Math.PI * (s + 1) / seg;
            Vec3 a = ring(c, ra, rb, ea, eb, t0), b = ring(c, ra, rb, ea, eb, t1);
            quad(o, m, n, new Vec3[]{c, a, b, b}, new Vec3[]{normal, normal, normal, normal},
                    new float[]{0.5f, 0.5f, (float) (0.5 + 0.5 * Math.cos(t0)), (float) (0.5 + 0.5 * Math.sin(t0)),
                            (float) (0.5 + 0.5 * Math.cos(t1)), (float) (0.5 + 0.5 * Math.sin(t1)), (float) (0.5 + 0.5 * Math.cos(t1)), (float) (0.5 + 0.5 * Math.sin(t1))});
        }
    }

    /**
     * Curved thorn: round base of radius r, tapering with a concave profile to a needle point. The spine of the thorn
     * bends by `bend` at the tip. The last `glowFrac` of the length goes to the glow buffer (null = no glow).
     */
    public static void thorn(Out body, Out glow, PoseStack.Pose pose, Vec3 base, Vec3 dir, double len, double r, Vec3 bend, double glowFrac) {
        final int rings = 10, seg = 12;
        Vec3 d = dir.normalize();
        Vec3[] c = new Vec3[rings + 1], ea = new Vec3[rings + 1], eb = new Vec3[rings + 1];
        double[] rad = new double[rings + 1];
        Vec3 ref = perp(d);
        for (int k = 0; k <= rings; k++) {
            double t = (double) k / rings;
            c[k] = base.add(d.scale(len * t)).add(bend.scale(t * t));
            Vec3 tan = d.scale(len).add(bend.scale(2 * t)).normalize();
            Vec3 a = tan.cross(ref).normalize();
            if (a.lengthSqr() < 1e-6) a = perp(tan);
            ea[k] = a;
            eb[k] = tan.cross(a).normalize();
            rad[k] = r * Math.pow(1 - t, 1.35);
        }
        int split = glow == null ? rings : (int) Math.round(rings * (1 - glowFrac));
        lattice(body, pose, c, rad, rad, ea, eb, seg, 0, split, 1f / rings);
        if (glow != null && split < rings) lattice(glow, pose, c, rad, rad, ea, eb, seg, split, rings, 1f / rings);
        fan(body, pose, c[0], rad[0], rad[0], ea[0], eb[0], d.scale(-1), seg);
    }

    /** Elliptical band around `axis` from `bottom`, height h, radii (ra, rb), widening by `flare` at the top, with rounded rims. */
    public static void tube(Out o, PoseStack.Pose pose, Vec3 bottom, Vec3 axis, double h, double ra, double rb, double flare) {
        final int seg = 20;
        Vec3 ax = axis.normalize(), e1 = perp(ax), e2 = ax.cross(e1).normalize();
        double bev = Math.min(0.06, h * 0.2);
        double[] ys = {0, bev * 0.4, bev, h - bev, h - bev * 0.4, h};
        double[] sc = {0.93, 0.985, 1.0, 1.0, 0.985, 0.93};
        int nr = ys.length;
        Vec3[] c = new Vec3[nr], ea = new Vec3[nr], eb = new Vec3[nr];
        double[] r1 = new double[nr], r2 = new double[nr];
        for (int k = 0; k < nr; k++) {
            double f = 1 + (flare - 1) * (ys[k] / h);
            c[k] = bottom.add(ax.scale(ys[k]));
            ea[k] = e1; eb[k] = e2;
            r1[k] = ra * f * sc[k]; r2[k] = rb * f * sc[k];
        }
        lattice(o, pose, c, r1, r2, ea, eb, seg, 0, nr - 1, 1f / (nr - 1));
        fan(o, pose, c[0], r1[0], r2[0], e1, e2, ax.scale(-1), seg);
        fan(o, pose, c[nr - 1], r1[nr - 1], r2[nr - 1], e2, e1, ax, seg);
    }

    /** Capsule from a to b with radius r (hemispherical ends). */
    public static void capsule(Out o, PoseStack.Pose pose, Vec3 a, Vec3 b, double r) {
        final int half = 5, seg = 14;
        Vec3 d = b.subtract(a);
        double len = d.length();
        Vec3 ax = len < 1e-6 ? new Vec3(0, 1, 0) : d.scale(1 / len), e1 = perp(ax), e2 = ax.cross(e1).normalize();
        int nr = 2 * (half + 1);
        Vec3[] c = new Vec3[nr], ea = new Vec3[nr], eb = new Vec3[nr];
        double[] rr = new double[nr];
        for (int k = 0; k <= half; k++) {           // cap at a
            double phi = -Math.PI / 2 + (Math.PI / 2) * k / half;
            c[k] = a.add(ax.scale(r * Math.sin(phi)));
            rr[k] = Math.max(1e-4, r * Math.cos(phi));
        }
        for (int k = 0; k <= half; k++) {           // cap at b
            double phi = (Math.PI / 2) * k / half;
            c[half + 1 + k] = b.add(ax.scale(r * Math.sin(phi)));
            rr[half + 1 + k] = Math.max(1e-4, r * Math.cos(phi));
        }
        for (int k = 0; k < nr; k++) { ea[k] = e1; eb[k] = e2; }
        lattice(o, pose, c, rr, rr, ea, eb, seg, 0, nr - 1, 1f / (nr - 1));
    }

    /** Rounded box / ellipsoid (superellipsoid): exponent 2 = ellipsoid, higher = boxier with soft edges. */
    public static void blob(Out o, PoseStack.Pose pose, Vec3 c, Vec3 radii, double p) {
        final int lat = 10, lon = 18;
        double m = 2.0 / p;
        Matrix4f mat = pose.pose();
        Matrix3f n = pose.normal();
        for (int i = 0; i < lat; i++) {
            double th0 = -Math.PI / 2 + Math.PI * i / lat, th1 = -Math.PI / 2 + Math.PI * (i + 1) / lat;
            for (int j = 0; j < lon; j++) {
                double ph0 = -Math.PI + 2 * Math.PI * j / lon, ph1 = -Math.PI + 2 * Math.PI * (j + 1) / lon;
                Vec3 p00 = sq(c, radii, th0, ph0, m), p01 = sq(c, radii, th0, ph1, m), p11 = sq(c, radii, th1, ph1, m), p10 = sq(c, radii, th1, ph0, m);
                quad(o, mat, n, new Vec3[]{p00, p10, p11, p01},
                        new Vec3[]{sn(c, radii, p00), sn(c, radii, p10), sn(c, radii, p11), sn(c, radii, p01)},
                        new float[]{(float) j / lon, (float) i / lat, (float) j / lon, (float) (i + 1) / lat,
                                (float) (j + 1) / lon, (float) (i + 1) / lat, (float) (j + 1) / lon, (float) i / lat});
            }
        }
    }

    private static double sp(double w, double m) { double s = Math.sin(w); return Math.signum(s) * Math.pow(Math.abs(s), m); }
    private static double cp(double w, double m) { double s = Math.cos(w); return Math.signum(s) * Math.pow(Math.abs(s), m); }

    private static Vec3 sq(Vec3 c, Vec3 r, double th, double ph, double m) {
        return c.add(r.x * cp(th, m) * cp(ph, m), r.y * sp(th, m), r.z * cp(th, m) * sp(ph, m));
    }

    private static Vec3 sn(Vec3 c, Vec3 r, Vec3 p) {
        Vec3 q = p.subtract(c);
        Vec3 nn = new Vec3(q.x / (r.x * r.x + 1e-6), q.y / (r.y * r.y + 1e-6), q.z / (r.z * r.z + 1e-6));
        return nn.lengthSqr() < 1e-12 ? new Vec3(0, 1, 0) : nn.normalize();
    }
}

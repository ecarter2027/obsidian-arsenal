package com.obsidianarsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.ObsidianArsenal;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.phys.Vec3;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Glowing magic circles: a school's outer ring band and inner figure (textures/entity/circle_<school>_*.png, from
 * tools/gen_assets.py v31) drawn as two flat full-bright quads facing along a normal, spinning in opposite directions.
 */
public final class MagicCircle {
    public static final int OBSIDIAN = 0, HELLFIRE = 1, PLASMA = 2;
    private static final String[] NAMES = {"obsidian", "hellfire", "plasma"};
    // outer ring band / inner figure colours: obsidian violet, hellfire orange-red, plasma crimson red around white
    private static final float[][] COLORS = {{0.78f, 0.5f, 1f}, {1f, 0.32f, 0.24f}, {1f, 0.1f, 0.14f}};
    private static final float[][] INNER_COLORS = {{0.78f, 0.5f, 1f}, {1f, 0.32f, 0.24f}, {1f, 0.97f, 0.97f}};
    private static final ResourceLocation[] OUTER = new ResourceLocation[3], INNER = new ResourceLocation[3];

    static {
        for (int i = 0; i < 3; i++) {
            OUTER[i] = ObsidianArsenal.id("textures/entity/circle_" + NAMES[i] + "_outer.png");
            INNER[i] = ObsidianArsenal.id("textures/entity/circle_" + NAMES[i] + "_inner.png");
        }
    }

    private MagicCircle() {}

    /** The school index for a spell school id (obsidian_arsenal:obsidian / hellfire / plasma). */
    public static int school(String schoolId) {
        if (schoolId.endsWith("hellfire")) return HELLFIRE;
        if (schoolId.endsWith("plasma")) return PLASMA;
        return OBSIDIAN;
    }

    /**
     * Draws a circle centred at {@code c} (camera-relative render space of the pose stack), facing {@code normal},
     * of radius {@code r}; {@code spin} turns the outer band (the inner figure turns the other way, faster).
     */
    public static void draw(PoseStack ps, MultiBufferSource buf, int school, Vec3 c, Vec3 normal, double r, float spin, float alpha) {
        if (alpha <= 0.01f || r <= 0.01) return;
        school = Math.floorMod(school, COLORS.length);   // (school 3 = black is a flash colour, not a circle)
        Vec3 n = normal.normalize();
        Vec3 ref = Math.abs(n.y) < 0.95 ? new Vec3(0, 1, 0) : new Vec3(1, 0, 0);
        Vec3 u = n.cross(ref).normalize(), v = n.cross(u).normalize();
        float[] col = COLORS[school];
        quad(ps, buf.getBuffer(RenderType.entityTranslucentEmissive(OUTER[school])), c, u, v, n, r, spin, col, alpha);
        quad(ps, buf.getBuffer(RenderType.entityTranslucentEmissive(INNER[school])), c.add(n.scale(0.01)), u, v, n, r * 0.92, -spin * 1.6f, INNER_COLORS[school], alpha);
    }

    private static void quad(PoseStack ps, VertexConsumer vc, Vec3 c, Vec3 u0, Vec3 v0, Vec3 n, double r, float spin, float[] col, float a) {
        double cs = Math.cos(spin), sn = Math.sin(spin);
        Vec3 u = u0.scale(cs).add(v0.scale(sn)).scale(r), v = v0.scale(cs).subtract(u0.scale(sn)).scale(r);
        Matrix4f m = ps.last().pose();
        Matrix3f nm = ps.last().normal();
        Vec3[] p = {c.subtract(u).subtract(v), c.add(u).subtract(v), c.add(u).add(v), c.subtract(u).add(v)};
        float[][] uv = {{0, 1}, {1, 1}, {1, 0}, {0, 0}};
        for (int side = 0; side < 2; side++) {                       // both faces: seen from above and below
            for (int k = 0; k < 4; k++) {
                int i = side == 0 ? k : 3 - k;
                float s = side == 0 ? 1 : -1;
                vc.vertex(m, (float) p[i].x, (float) p[i].y, (float) p[i].z).color(col[0], col[1], col[2], a).uv(uv[i][0], uv[i][1])
                        .overlayCoords(OverlayTexture.NO_OVERLAY).uv2(LightTexture.FULL_BRIGHT)
                        .normal(nm, (float) n.x * s, (float) n.y * s, (float) n.z * s).endVertex();
            }
        }
    }
}

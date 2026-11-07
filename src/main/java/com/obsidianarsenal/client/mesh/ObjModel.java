package com.obsidianarsenal.client.mesh;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.ObsidianArsenal;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;

/**
 * A small Wavefront OBJ mesh (triangles with UVs, assets/obsidian_arsenal/models/obj/<name>.obj, made by
 * tools/gen_assets.py), drawn flat-shaded into an entity vertex buffer: each triangle as a degenerate quad with its
 * face normal. Used for the jagged obsidian spikes.
 */
public final class ObjModel {
    private static final Map<String, ObjModel> CACHE = new HashMap<>();
    private final float[] pos;   // 9 per triangle
    private final float[] uv;    // 6 per triangle

    private ObjModel(float[] pos, float[] uv) { this.pos = pos; this.uv = uv; }

    public static ObjModel get(String name) {
        return CACHE.computeIfAbsent(name, ObjModel::load);
    }

    private static ObjModel load(String name) {
        ResourceLocation id = ObsidianArsenal.id("models/obj/" + name + ".obj");
        List<float[]> v = new ArrayList<>(), vt = new ArrayList<>();
        List<Float> p = new ArrayList<>(), t = new ArrayList<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(id);
            if (res.isEmpty()) return new ObjModel(new float[0], new float[0]);
            try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                for (String line; (line = r.readLine()) != null; ) {
                    String[] s = line.trim().split("\\s+");
                    switch (s[0]) {
                        case "v" -> v.add(new float[]{Float.parseFloat(s[1]), Float.parseFloat(s[2]), Float.parseFloat(s[3])});
                        case "vt" -> vt.add(new float[]{Float.parseFloat(s[1]), Float.parseFloat(s[2])});
                        case "f" -> {
                            for (int k = 2; k + 1 < s.length; k++) {       // fan-triangulated
                                for (String corner : new String[]{s[1], s[k], s[k + 1]}) {
                                    String[] ix = corner.split("/");
                                    float[] pv = v.get(Integer.parseInt(ix[0]) - 1);
                                    float[] tv = ix.length > 1 && !ix[1].isEmpty() ? vt.get(Integer.parseInt(ix[1]) - 1) : new float[]{0, 0};
                                    p.add(pv[0]); p.add(pv[1]); p.add(pv[2]); t.add(tv[0]); t.add(tv[1]);
                                }
                            }
                        }
                        default -> {}
                    }
                }
            }
        } catch (Exception ex) {
            ObsidianArsenal.LOGGER.error("Could not read OBJ model {}", id, ex);
        }
        float[] pa = new float[p.size()], ta = new float[t.size()];
        for (int i = 0; i < pa.length; i++) pa[i] = p.get(i);
        for (int i = 0; i < ta.length; i++) ta[i] = i % 2 == 1 ? 1.0F - t.get(i) : t.get(i);   // OBJ v runs bottom-up
        return new ObjModel(pa, ta);
    }

    /** Draws the mesh through {@code local} (model units -> the pose's space), lit and textured as the buffer says. */
    public void draw(VertexConsumer vc, PoseStack.Pose pose, Matrix4f local, int light, int overlay) {
        Matrix4f m = new Matrix4f(pose.pose()).mul(local);
        Matrix3f n = new Matrix3f(pose.normal()).mul(new Matrix3f(local).invert().transpose());
        Vector4f a = new Vector4f(), b = new Vector4f(), c = new Vector4f();
        Vector3f e1 = new Vector3f(), e2 = new Vector3f(), nor = new Vector3f();
        for (int i = 0, j = 0; i + 8 < pos.length; i += 9, j += 6) {
            e1.set(pos[i + 3] - pos[i], pos[i + 4] - pos[i + 1], pos[i + 5] - pos[i + 2]);
            e2.set(pos[i + 6] - pos[i], pos[i + 7] - pos[i + 1], pos[i + 8] - pos[i + 2]);
            e1.cross(e2, nor);
            n.transform(nor);
            if (nor.lengthSquared() < 1e-12F) continue;
            nor.normalize();
            m.transform(a.set(pos[i], pos[i + 1], pos[i + 2], 1));
            m.transform(b.set(pos[i + 3], pos[i + 4], pos[i + 5], 1));
            m.transform(c.set(pos[i + 6], pos[i + 7], pos[i + 8], 1));
            vertex(vc, a, uv[j], uv[j + 1], nor, light, overlay);
            vertex(vc, b, uv[j + 2], uv[j + 3], nor, light, overlay);
            vertex(vc, c, uv[j + 4], uv[j + 5], nor, light, overlay);
            vertex(vc, c, uv[j + 4], uv[j + 5], nor, light, overlay);
        }
    }

    private static void vertex(VertexConsumer vc, Vector4f p, float u, float v, Vector3f n, int light, int overlay) {
        vc.vertex(p.x(), p.y(), p.z(), 1, 1, 1, 1, u, v, overlay, light, n.x(), n.y(), n.z());
    }
}

package com.obsidianarsenal.efcompat.client;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.Matrix3f;
import org.joml.Matrix4f;
import org.joml.Quaternionf;
import org.joml.Vector3f;
import org.joml.Vector4f;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

/**
 * Obsidian Arsenal's armor spikes are jagged OBJ meshes drawn by its own GeckoLib layer, which Epic Fight's armor
 * conversion (cubes only) doesn't carry over. This draws them on Epic Fight's animated body: each armor bone's spikes
 * (obsidian_arsenal:smooth/obsidian_armor.json, Blockbench pixels from the feet) moved into its vanilla body part's
 * space and onto the Epic Fight joint that part rides (upper arm or forearm, thigh or shin by height), through the
 * rest frames worked out from Epic Fight's biped armature (as the Void Powers add-on places its suit parts).
 */
@EventBusSubscriber(modid = ObsidianEFCompat.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ArmorSpikeLayer extends PatchedLayer<AbstractClientPlayer, AbstractClientPlayerPatch<AbstractClientPlayer>,
        PlayerModel<AbstractClientPlayer>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> {
    private static final ResourceLocation SPEC = new ResourceLocation("obsidian_arsenal", "smooth/obsidian_armor.json");
    private static final ResourceLocation OBSIDIAN = new ResourceLocation("minecraft", "textures/block/obsidian.png");

    /** A vanilla body part: its pivot (model pixels, y down) and the Epic Fight joint + rest frame it rides. */
    private record Part(float px, float py, String joint, Matrix4f into) {}

    private static final Part HEAD = new Part(0, 0, "Head", rows(-1, 0, 0, 0, 0, -1, 0, -0.0140f, 0, 0, 1, 0.0009f));
    private static final Part BODY = new Part(0, 0, "Chest", rows(-1, 0, 0, 0, 0, -1, 0, 0.3860f, 0, 0, 1, 0.0009f));
    private static final Part R_ARM = new Part(-5, 2, "Arm_R", rows(-1, 0, 0, -0.0625f, 0, 1, 0, 0.0186f, 0, 0, -1, -0.0009f));
    private static final Part R_FORE = new Part(-5, 2, "Hand_R", rows(-1, 0, 0, -0.0625f, 0, 0.9939f, 0.1099f, -0.2796f, 0, 0.1099f, -0.9939f, -0.0319f));
    private static final Part L_ARM = new Part(5, 2, "Arm_L", rows(-1, 0, 0, 0.0625f, 0, 1, 0, 0.0186f, 0, 0, -1, -0.0009f));
    private static final Part L_FORE = new Part(5, 2, "Hand_L", rows(-1, 0, 0, 0.0625f, 0, 0.9939f, 0.1099f, -0.2796f, 0, 0.1099f, -0.9939f, -0.0319f));
    private static final Part R_LEG = new Part(-1.9f, 12, "Thigh_R", rows(-1, 0, 0, -0.0062f, 0, 1, 0, 0.0111f, 0, 0, -1, -0.0010f));
    private static final Part R_SHIN = new Part(-1.9f, 12, "Leg_R", rows(-1, 0, 0, -0.0062f, 0, 1, 0, -0.3636f, 0, 0, -1, -0.0010f));
    private static final Part L_LEG = new Part(1.9f, 12, "Thigh_L", rows(-1, 0, 0, 0.0063f, 0, 1, 0, 0.0111f, 0, 0, -1, -0.0010f));
    private static final Part L_SHIN = new Part(1.9f, 12, "Leg_L", rows(-1, 0, 0, 0.0063f, 0, 1, 0, -0.3636f, 0, 0, -1, -0.0010f));

    private record Spike(String model, float[] a, float[] b, float len, float r, float roll) {}

    private Map<String, List<Spike>> spikes;
    private final Map<String, float[][]> meshes = new HashMap<>();

    private static Matrix4f rows(float... r) {
        return new Matrix4f(r[0], r[4], r[8], 0, r[1], r[5], r[9], 0, r[2], r[6], r[10], 0, r[3], r[7], r[11], 1);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void register(PatchedRenderersEvent.Modify event) {
        if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer renderer) renderer.addCustomLayer(new ArmorSpikeLayer());
    }

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack poseStack,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot,
                               float partialTick) {
        Map<String, List<Spike>> all = spikes();
        if (all.isEmpty()) return;
        boolean firstPerson = patch.isFirstPerson();
        VertexConsumer vc = null;
        Armature armature = patch.getArmature();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            if (firstPerson && (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST)) continue;
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !"obsidian_arsenal".equals(id.getNamespace())) continue;
            String[] bones = switch (slot) {
                case HEAD -> new String[]{"armorHead"};
                case CHEST -> new String[]{"armorBody", "armorRightArm", "armorLeftArm"};
                case LEGS -> new String[]{"armorRightLeg", "armorLeftLeg"};
                default -> new String[]{"armorRightBoot", "armorLeftBoot"};
            };
            for (String bone : bones) {
                for (Spike s : all.getOrDefault(bone, List.of())) {
                    Part part = partFor(bone, s.a()[1]);
                    var joint = armature.searchJointByName(part.joint());
                    if (joint == null || joint.getId() >= poses.length) continue;
                    if (vc == null) vc = buffers.getBuffer(RenderType.entityCutoutNoCull(OBSIDIAN));
                    poseStack.pushPose();
                    MathUtils.mulStack(poseStack, poses[joint.getId()]);
                    poseStack.last().pose().mul(part.into());
                    poseStack.last().normal().mul(new Matrix3f(part.into()));
                    draw(vc, poseStack.last(), place(s, part), mesh(s.model()), light);
                    poseStack.popPose();
                }
            }
        }
    }

    /** Which body part a spike rides: by bone, and for the limbs by height (Blockbench pixels from the feet). */
    private static Part partFor(String bone, float y) {
        return switch (bone) {
            case "armorHead" -> HEAD;
            case "armorBody" -> BODY;
            case "armorRightArm" -> y >= 18 ? R_ARM : R_FORE;
            case "armorLeftArm" -> y >= 18 ? L_ARM : L_FORE;
            case "armorRightLeg" -> y >= 6 ? R_LEG : R_SHIN;
            case "armorLeftLeg" -> y >= 6 ? L_LEG : L_SHIN;
            case "armorRightBoot" -> R_SHIN;
            default -> L_SHIN;
        };
    }

    /** The spike's unit mesh placed in its body part's space (blocks; Minecraft's model axes, y down). */
    private static Matrix4f place(Spike s, Part part) {
        float x = (s.a()[0] - part.px()) / 16f, y = (24 - s.a()[1] - part.py()) / 16f, z = s.a()[2] / 16f;
        Vector3f d = new Vector3f(s.b()[0], -s.b()[1], s.b()[2]).normalize();
        return new Matrix4f().translate(x, y, z).rotate(new Quaternionf().rotationTo(0, 1, 0, d.x, d.y, d.z))
                .rotateY((float) Math.toRadians(s.roll())).scale(s.r() / 16f, s.len() / 16f, s.r() / 16f);
    }

    private static void draw(VertexConsumer vc, PoseStack.Pose pose, Matrix4f local, float[][] mesh, int light) {
        float[] pos = mesh[0], uv = mesh[1];
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
            vertex(vc, a, uv[j], uv[j + 1], nor, light);
            vertex(vc, b, uv[j + 2], uv[j + 3], nor, light);
            vertex(vc, c, uv[j + 4], uv[j + 5], nor, light);
            vertex(vc, c, uv[j + 4], uv[j + 5], nor, light);
        }
    }

    private static void vertex(VertexConsumer vc, Vector4f p, float u, float v, Vector3f n, int light) {
        vc.vertex(p.x(), p.y(), p.z(), 1, 1, 1, 1, u, v, OverlayTexture.NO_OVERLAY, light, n.x(), n.y(), n.z());
    }

    private Map<String, List<Spike>> spikes() {
        if (spikes != null) return spikes;
        Map<String, List<Spike>> out = new HashMap<>();
        try {
            var res = Minecraft.getInstance().getResourceManager().getResource(SPEC);
            if (res.isPresent()) try (Reader rd = new InputStreamReader(res.get().open(), StandardCharsets.UTF_8)) {
                JsonObject root = JsonParser.parseReader(rd).getAsJsonObject();
                for (var e : root.entrySet()) {
                    List<Spike> list = new ArrayList<>();
                    for (JsonElement pe : e.getValue().getAsJsonArray()) {
                        JsonObject o = pe.getAsJsonObject();
                        if (!"obj".equals(o.get("type").getAsString())) continue;
                        list.add(new Spike(o.get("mat").getAsString(), vec(o, "a"), vec(o, "b"), o.get("len").getAsFloat(),
                                o.get("r").getAsFloat(), o.has("p") ? o.get("p").getAsFloat() : 0));
                    }
                    out.put(e.getKey(), list);
                }
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().error("Could not read {}", SPEC, ex);
        }
        return spikes = out;
    }

    private static float[] vec(JsonObject o, String k) {
        var a = o.getAsJsonArray(k);
        return new float[]{a.get(0).getAsFloat(), a.get(1).getAsFloat(), a.get(2).getAsFloat()};
    }

    /** obsidian_arsenal:models/obj/<name>.obj as triangle positions and UVs (fan-triangulated, v flipped). */
    private float[][] mesh(String name) {
        return meshes.computeIfAbsent(name, n -> {
            List<float[]> v = new ArrayList<>(), vt = new ArrayList<>();
            List<Float> p = new ArrayList<>(), t = new ArrayList<>();
            try {
                var res = Minecraft.getInstance().getResourceManager().getResource(new ResourceLocation("obsidian_arsenal", "models/obj/" + n + ".obj"));
                if (res.isPresent()) try (BufferedReader r = new BufferedReader(new InputStreamReader(res.get().open(), StandardCharsets.UTF_8))) {
                    for (String line; (line = r.readLine()) != null; ) {
                        String[] s = line.trim().split("\\s+");
                        switch (s[0]) {
                            case "v" -> v.add(new float[]{Float.parseFloat(s[1]), Float.parseFloat(s[2]), Float.parseFloat(s[3])});
                            case "vt" -> vt.add(new float[]{Float.parseFloat(s[1]), Float.parseFloat(s[2])});
                            case "f" -> {
                                for (int k = 2; k + 1 < s.length; k++) {
                                    for (String corner : new String[]{s[1], s[k], s[k + 1]}) {
                                        String[] ix = corner.split("/");
                                        float[] pv = v.get(Integer.parseInt(ix[0]) - 1);
                                        float[] tv = ix.length > 1 && !ix[1].isEmpty() ? vt.get(Integer.parseInt(ix[1]) - 1) : new float[]{0, 0};
                                        p.add(pv[0]); p.add(pv[1]); p.add(pv[2]); t.add(tv[0]); t.add(1 - tv[1]);
                                    }
                                }
                            }
                            default -> {}
                        }
                    }
                }
            } catch (Exception ex) {
                com.mojang.logging.LogUtils.getLogger().error("Could not read spike mesh {}", n, ex);
            }
            float[] pa = new float[p.size()], ta = new float[t.size()];
            for (int i = 0; i < pa.length; i++) pa[i] = p.get(i);
            for (int i = 0; i < ta.length; i++) ta[i] = t.get(i);
            return new float[][]{pa, ta};
        });
    }
}

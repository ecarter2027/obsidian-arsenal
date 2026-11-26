package com.obsidianarsenal.efcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import java.lang.reflect.Proxy;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import org.slf4j.Logger;
import yesman.epicfight.api.client.model.Meshes;
import yesman.epicfight.api.model.Armature;
import yesman.epicfight.api.utils.math.MathUtils;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.api.utils.math.QuaternionUtils;
import yesman.epicfight.client.mesh.HumanoidMesh;
import yesman.epicfight.world.capabilities.EpicFightCapabilities;
import yesman.epicfight.world.capabilities.entitypatch.LivingEntityPatch;

/**
 * Purple Lightning afterimages that match Epic Fight's player model. Obsidian Arsenal draws its afterimages with the
 * vanilla player model, which doesn't line up with Epic Fight's own animated mesh (battle stances, combos, dodges...).
 * This fills Obsidian Arsenal's GhostHook: each afterimage snapshots the player's Epic Fight pose (the armature's joint
 * matrices from the last rendered frame, and the model matrix) and is redrawn with Epic Fight's player mesh in that
 * exact pose, the same way Epic Fight positions it (PatchedEntityRenderer.mulPoseStack). The hook is set through
 * reflection, so this add-on still loads against Obsidian Arsenal versions that predate it.
 */
@EventBusSubscriber(modid = ObsidianEFCompat.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class EfGhosts {
    private static final Logger LOG = com.mojang.logging.LogUtils.getLogger();

    private record Snapshot(OpenMatrix4f model, OpenMatrix4f[] poses, Armature armature, boolean slim) {}

    private EfGhosts() {}

    @SubscribeEvent
    public static void setup(FMLClientSetupEvent event) {
        try {
            Class<?> hook = Class.forName("com.obsidianarsenal.client.GhostHook");
            Class<?> impl = Class.forName("com.obsidianarsenal.client.GhostHook$Impl");
            Object proxy = Proxy.newProxyInstance(impl.getClassLoader(), new Class<?>[]{impl}, (p, m, args) -> switch (m.getName()) {
                case "capture" -> capture((AbstractClientPlayer) args[0]);
                case "render" -> {
                    render(args[0], (AbstractClientPlayer) args[1], (PoseStack) args[2], (MultiBufferSource) args[3],
                            (Float) args[4], (Float) args[5], (Float) args[6], (Float) args[7]);
                    yield null;
                }
                case "hashCode" -> System.identityHashCode(p);
                case "equals" -> p == args[0];
                case "toString" -> "ObsidianEFCompat afterimages";
                default -> null;
            });
            hook.getField("impl").set(null, proxy);
        } catch (ClassNotFoundException ignored) {
            // an Obsidian Arsenal without the hook: its afterimages stay vanilla
        } catch (ReflectiveOperationException e) {
            LOG.warn("Could not hook Obsidian Arsenal's afterimages", e);
        }
    }

    private static Object capture(AbstractClientPlayer player) {
        LivingEntityPatch<?> patch = EpicFightCapabilities.getEntityPatch(player, LivingEntityPatch.class);
        if (patch == null || patch.getArmature() == null) return null;
        OpenMatrix4f[] live = patch.getArmature().getPoseMatrices();
        if (live == null || live.length == 0) return null;
        OpenMatrix4f[] poses = new OpenMatrix4f[live.length];
        for (int i = 0; i < live.length; i++) poses[i] = new OpenMatrix4f(live[i]);
        return new Snapshot(new OpenMatrix4f(patch.getModelMatrix(1.0F)), poses, patch.getArmature(), "slim".equals(player.getModelName()));
    }

    private static void render(Object snap, AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffers, float r, float g, float b, float a) {
        Snapshot s = (Snapshot) snap;
        HumanoidMesh mesh = (s.slim() ? Meshes.ALEX : Meshes.BIPED).get();
        if (mesh == null) return;
        poseStack.mulPose(QuaternionUtils.YP.rotationDegrees(180.0F));
        MathUtils.mulStack(poseStack, s.model());
        mesh.draw(poseStack, buffers, RenderType.entityTranslucent(player.getSkinTextureLocation()), LightTexture.FULL_BRIGHT,
                r, g, b, a, OverlayTexture.NO_OVERLAY, s.armature(), s.poses());
    }
}

package com.obsidianarsenal.client;

import com.mojang.blaze3d.pipeline.RenderTarget;
import com.mojang.blaze3d.pipeline.TextureTarget;
import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.*;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.entity.BlackHoleEntity;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;
import org.joml.Vector4f;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL30;

import java.util.ArrayList;
import java.util.List;

/**
 * Gravitational lensing + lensed accretion disk around Black Holes ("black_hole_lens" core shader).
 *
 * Two paths, same shader:
 *  - Vanilla rendering: after particles, copy the frame and draw a camera-facing disc in the world (depth-tested,
 *    so blocks in front hide it properly).
 *  - Iris / Oculus shader pack active: shader packs draw the world into their own buffers and only write the
 *    finished image at the end of the frame, so the hole's screen position is recorded during the world render
 *    and the lens is applied to the FINISHED, shaded frame just before the HUD draws (a screen-space post effect).
 *    Occlusion there is a line-of-sight ray from the camera, since depth is no longer available at that point.
 */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, value = Dist.CLIENT)
public final class BlackHoleLensRenderer {
    /** Set by ClientModEvents#registerShaders. */
    public static ShaderInstance lensShader;
    private static TextureTarget sceneCopy;
    /** Lens disc radius in blocks at full size. */
    public static final float LENS_RADIUS = 4.2f;

    /** Screen-space holes recorded during the world render, drawn later (shader-pack path). */
    private record ScreenHole(float x, float y, float rx, float ry, float tilt) {}
    private static final List<ScreenHole> PENDING = new ArrayList<>();

    private BlackHoleLensRenderer() {}

    public static boolean lensAvailable() { return lensShader != null; }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || lensShader == null) return;
        Minecraft mc = Minecraft.getInstance();
        PENDING.clear();
        if (mc.level == null) return;

        Camera cam = e.getCamera();
        Vec3 cp = cam.getPosition();
        float pt = e.getPartialTick();
        List<BlackHoleEntity> holes = mc.level.getEntitiesOfClass(BlackHoleEntity.class, new AABB(cp, cp).inflate(192),
                h -> h.sizeFactor(pt) > 0.01f && e.getFrustum().isVisible(h.getBoundingBox().inflate(LENS_RADIUS * h.tierScale())));
        if (holes.isEmpty()) return;

        if (shaderPackActive()) {
            recordScreenHoles(e, holes, cam, cp, pt);
            return;
        }

        // ---------------- vanilla path: in-world, depth-tested disc
        copyScene(mc);
        RenderSystem.setShader(() -> lensShader);
        RenderSystem.setShaderTexture(0, sceneCopy.getColorTextureId());
        RenderSystem.enableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();

        Matrix4f m = e.getPoseStack().last().pose();
        Vector3f left = cam.getLeftVector(), up = cam.getUpVector();
        for (BlackHoleEntity h : holes) {
            float r = LENS_RADIUS * h.sizeFactor(pt) * h.tierScale();
            Vec3 c = h.getPosition(pt).subtract(cp);
            lensShader.safeGetUniform("Tilt").set(tilt(cp, h.getPosition(pt)));
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            float rx = -left.x() * r, ry = -left.y() * r, rz = -left.z() * r;   // camera right * r
            float ux = up.x() * r, uy = up.y() * r, uz = up.z() * r;
            float cx = (float) c.x, cy = (float) c.y, cz = (float) c.z;
            bb.vertex(m, cx - rx - ux, cy - ry - uy, cz - rz - uz).uv(0, 0).endVertex();
            bb.vertex(m, cx + rx - ux, cy + ry - uy, cz + rz - uz).uv(1, 0).endVertex();
            bb.vertex(m, cx + rx + ux, cy + ry + uy, cz + rz + uz).uv(1, 1).endVertex();
            bb.vertex(m, cx - rx + ux, cy - ry + uy, cz - rz + uz).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(bb.end());
        }
        RenderSystem.depthMask(true);
        RenderSystem.enableCull();
    }

    /** Shader-pack path, step 1: project each visible hole to the screen while the camera matrices are known. */
    private static void recordScreenHoles(RenderLevelStageEvent e, List<BlackHoleEntity> holes, Camera cam, Vec3 cp, float pt) {
        Minecraft mc = Minecraft.getInstance();
        Matrix4f view = new Matrix4f(e.getPoseStack().last().pose());
        Matrix4f proj = new Matrix4f(e.getProjectionMatrix());
        Vector3f left = cam.getLeftVector();
        float aspect = (float) mc.getWindow().getWidth() / Math.max(1, mc.getWindow().getHeight());
        for (BlackHoleEntity h : holes) {
            Vec3 pos = h.getPosition(pt);
            // line of sight (no depth buffer later on)
            var hit = mc.level.clip(new ClipContext(cp, pos, ClipContext.Block.VISUAL, ClipContext.Fluid.NONE, mc.player));
            if (hit.getType() != HitResult.Type.MISS && hit.getLocation().distanceToSqr(cp) < pos.distanceToSqr(cp) - 1.0) continue;
            Vec3 rel = pos.subtract(cp);
            Vector4f c = proj.transform(view.transform(new Vector4f((float) rel.x, (float) rel.y, (float) rel.z, 1f)));
            if (c.w <= 0.05f) continue;                      // behind the camera
            float r = LENS_RADIUS * h.sizeFactor(pt) * h.tierScale();
            Vector4f edge = proj.transform(view.transform(new Vector4f((float) rel.x - left.x() * r, (float) rel.y - left.y() * r, (float) rel.z - left.z() * r, 1f)));
            float sx = c.x / c.w, sy = c.y / c.w;
            float rx = Math.abs(edge.x / edge.w - sx);
            PENDING.add(new ScreenHole(sx, sy, rx, rx * aspect, tilt(cp, pos)));
        }
    }

    /** Shader-pack path, step 2: the shaded frame is finished; distort it before the HUD is drawn. */
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderGui(RenderGuiEvent.Pre e) {
        drawPending(false);
    }

    /**
     * Shader-pack path, fallback: with the HUD hidden (F1) - or anything else that skips the HUD pass - the GUI event
     * never fires, so the lens is applied here instead, once the whole frame (world + shader pack) is finished and
     * before it is shown. Skipped while a menu is open so the menu itself is never distorted.
     */
    @SubscribeEvent
    public static void onRenderTick(TickEvent.RenderTickEvent e) {
        if (e.phase != TickEvent.Phase.END || PENDING.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null || mc.level == null) { PENDING.clear(); return; }
        drawPending(true);
    }

    private static void drawPending(boolean rebindViewport) {
        if (PENDING.isEmpty() || lensShader == null) return;
        Minecraft mc = Minecraft.getInstance();
        copyScene(mc);
        if (rebindViewport) mc.getMainRenderTarget().bindWrite(true);

        RenderSystem.backupProjectionMatrix();
        RenderSystem.setProjectionMatrix(new Matrix4f(), VertexSorting.ORTHOGRAPHIC_Z);
        PoseStack mv = RenderSystem.getModelViewStack();
        mv.pushPose();
        mv.setIdentity();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.disableDepthTest();
        RenderSystem.depthMask(false);
        RenderSystem.disableCull();
        RenderSystem.setShader(() -> lensShader);
        RenderSystem.setShaderTexture(0, sceneCopy.getColorTextureId());

        for (ScreenHole h : PENDING) {
            lensShader.safeGetUniform("Tilt").set(h.tilt());
            BufferBuilder bb = Tesselator.getInstance().getBuilder();
            bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX);
            bb.vertex(h.x() - h.rx(), h.y() - h.ry(), 0).uv(0, 0).endVertex();
            bb.vertex(h.x() + h.rx(), h.y() - h.ry(), 0).uv(1, 0).endVertex();
            bb.vertex(h.x() + h.rx(), h.y() + h.ry(), 0).uv(1, 1).endVertex();
            bb.vertex(h.x() - h.rx(), h.y() + h.ry(), 0).uv(0, 1).endVertex();
            BufferUploader.drawWithShader(bb.end());
        }
        PENDING.clear();

        RenderSystem.enableCull();
        RenderSystem.depthMask(true);
        RenderSystem.enableDepthTest();
        mv.popPose();
        RenderSystem.applyModelViewMatrix();
        RenderSystem.restoreProjectionMatrix();
    }

    /** sin(elevation of the camera above the hole's disk plane), signed: + looking from above. */
    private static float tilt(Vec3 camPos, Vec3 holePos) {
        Vec3 v = camPos.subtract(holePos);
        double len = v.length();
        return len < 1e-3 ? 0.3f : (float) (v.y / len);
    }

    private static void copyScene(Minecraft mc) {
        RenderTarget main = mc.getMainRenderTarget();
        if (sceneCopy == null || sceneCopy.width != main.width || sceneCopy.height != main.height) {
            if (sceneCopy != null) sceneCopy.destroyBuffers();
            sceneCopy = new TextureTarget(main.width, main.height, false, Minecraft.ON_OSX);
            sceneCopy.setFilterMode(GL11.GL_LINEAR);
        }
        GlStateManager._glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, main.frameBufferId);
        GlStateManager._glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, sceneCopy.frameBufferId);
        GlStateManager._glBlitFrameBuffer(0, 0, main.width, main.height, 0, 0, sceneCopy.width, sceneCopy.height,
                GL11.GL_COLOR_BUFFER_BIT, GL11.GL_NEAREST);
        main.bindWrite(false);
    }

    private static Boolean irisPresent;

    /** True while an Iris/Oculus shader pack is in use (Iris's public API, looked up reflectively). */
    public static boolean shaderPackActive() {
        try {
            if (irisPresent == Boolean.FALSE) return false;
            Class<?> api = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            irisPresent = true;
            Object inst = api.getMethod("getInstance").invoke(null);
            return (boolean) api.getMethod("isShaderPackInUse").invoke(inst);
        } catch (Throwable t) {
            irisPresent = false;
            return false;
        }
    }
}

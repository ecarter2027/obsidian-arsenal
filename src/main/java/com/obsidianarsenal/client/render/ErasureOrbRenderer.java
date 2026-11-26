package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.client.MagicCircle;
import com.obsidianarsenal.client.mesh.Mesh;
import com.obsidianarsenal.entity.ErasureOrbEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;

/**
 * The Erasure orb: a pitch black sphere with a thin, glowing red outline (a bright ring facing the camera, exactly at
 * the sphere's silhouette, inside a faint red haze). Grows in while it charges, flickers a little, and on impact
 * swells to the blast radius and fades. Volley orbs also draw the plasma magic circle they are fired from.
 */
public class ErasureOrbRenderer extends EntityRenderer<ErasureOrbEntity> {
    private static final float RR = 1f, RG = 0.08f, RB = 0.06f;

    public ErasureOrbRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0f;
    }

    @Override
    public void render(ErasureOrbEntity e, float yaw, float pt, PoseStack pose, MultiBufferSource buffers, int light) {
        int phase = e.phase(), mode = e.mode();
        float age = e.tickCount - e.phaseAt() + pt, time = e.tickCount + pt;
        double s = e.size(), r = s;
        float fade = 1f, rim = 1f;
        switch (phase) {
            case ErasureOrbEntity.CHARGE -> {
                float k = Math.min(1f, age / Math.max(1, e.chargeTicks()));
                r = s * (mode == ErasureOrbEntity.VOLLEY ? Math.min(1f, age / 4f) : 0.15 + 0.85 * k * k * (3 - 2 * k));
                rim = 0.8f + 0.5f * k + 0.15f * Mth.sin(time * 2.1f);
            }
            case ErasureOrbEntity.BLAST -> {
                r = e.blastNow(age);
                fade = Mth.clamp(1f - (age - ErasureOrbEntity.EXPAND) / ErasureOrbEntity.FADE, 0f, 1f);
                rim = 1.6f;
            }
            case ErasureOrbEntity.HELD -> {
                r = s * Math.min(1f, (e.tickCount + pt) / 5f) * (1 + 0.03f * Mth.sin(time * 1.3f));
                rim = 1f + 0.12f * Mth.sin(time * 2.7f);
            }
            default -> rim = 1.1f;
        }
        PoseStack.Pose p = pose.last();
        Vec3 here = e.getPosition(pt);
        Vec3 cam = entityRenderDispatcher.camera.getPosition().subtract(here);

        // the volley's magic circle: charging in it, then fading where it stood once the orb is away
        if (mode == ErasureOrbEntity.VOLLEY) {
            float in = phase == ErasureOrbEntity.CHARGE ? Math.min(1f, age / 4f) : Mth.clamp(1f - age / 6f, 0f, 1f);
            Vec3 at = phase == ErasureOrbEntity.CHARGE ? e.aim().scale(-0.15) : e.anchor().subtract(here);
            if (in > 0.01f) MagicCircle.draw(pose, buffers, MagicCircle.PLASMA, at, e.aim(), (0.6 + s * 1.6) * in, time * 0.15f, 0.95f * in);
        }
        if (r < 0.01 || fade <= 0.01f) return;

        // faint red haze (additive), drawn first so the black sphere covers it
        var glow = buffers.getBuffer(OaRenderTypes.glow(OaRenderTypes.WHITE));
        Shapes.ring(glow, p, Vec3.ZERO, cam, r * 0.9, r * (phase == ErasureOrbEntity.BLAST ? 1.35 : 1.9), RR, RG, RB, 0.35f * fade, 0f);
        // the black sphere
        var core = buffers.getBuffer(RenderType.entityTranslucent(OaRenderTypes.WHITE));
        Mesh.blob(new Mesh.Out(core, LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.01f, 0f, 0.01f, fade), p, Vec3.ZERO, new Vec3(r, r, r), 2);
        // the thin glowing outline, at the silhouette: its inner edge tucks just inside the sphere
        var line = buffers.getBuffer(OaRenderTypes.glow(OaRenderTypes.WHITE));
        double w = Math.max(0.035, r * 0.07);
        Shapes.ring(line, p, Vec3.ZERO, cam, r * 0.97, r + w, RR, 0.25f, 0.2f, rim * fade, rim * fade);
        Shapes.ring(line, p, Vec3.ZERO, cam, r + w, r + w * 2.6, RR, RG, RB, 0.8f * rim * fade, 0f);
        super.render(e, yaw, pt, pose, buffers, light);
    }

    @Override
    public ResourceLocation getTextureLocation(ErasureOrbEntity e) { return OaRenderTypes.WHITE; }
}

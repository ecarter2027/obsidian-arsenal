package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.client.mesh.Mesh;
import com.obsidianarsenal.client.mesh.SmoothPartsLayer;
import com.obsidianarsenal.entity.SpellVisualEntity;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.phys.Vec3;

/**
 * The spells' 3D set pieces, modelled from smooth procedural meshes (client/mesh/Mesh) rather than particles.
 * Everything is a function of the entity's age, so it plays the same on every client.
 */
public class SpellVisualRenderer extends EntityRenderer<SpellVisualEntity> {
    private static final ResourceLocation GLOW = SmoothPartsLayer.GLOW;
    private static final ResourceLocation METAL = SmoothPartsLayer.OBSIDIAN;
    private static final ResourceLocation STEEL = new ResourceLocation("minecraft", "textures/block/iron_block.png");
    static final double CANNON_Y = 46, MUZZLE_Y = CANNON_Y - 6.5;

    public SpellVisualRenderer(EntityRendererProvider.Context ctx) {
        super(ctx);
        this.shadowRadius = 0;
    }

    @Override public ResourceLocation getTextureLocation(SpellVisualEntity e) { return GLOW; }

    @Override
    public void render(SpellVisualEntity e, float yaw, float pt, PoseStack ps, MultiBufferSource buf, int light) {
        float t = e.tickCount + pt;
        if (e.kind() == SpellVisualEntity.ORBITAL) orbital(e, t, ps, buf, light);
        else if (e.kind() == SpellVisualEntity.KIRIN) kirin(e, t, ps, buf);
        else if (e.kind() == SpellVisualEntity.FLASH) flash(e, t, ps, buf);
        else circle(e, t, ps, buf);
    }

    // =========================================================================================== a glowing flash
    private static final float[][] FLASH_COLORS = {{0.75f, 0.4f, 1f}, {1f, 0.3f, 0.18f}, {1f, 0.18f, 0.22f}, {0.05f, 0.02f, 0.06f}};

    /** A bright core in a soft halo swelling and fading, with a few light spikes: school colour, or black (Amaterasu). */
    private void flash(SpellVisualEntity e, float t, PoseStack ps, MultiBufferSource buf) {
        PoseStack.Pose pose = ps.last();
        float life = e.life(), k = Mth.clamp(t / life, 0, 1);
        float grow = ease(t / 4), fadeOut = 1 - ease((k - 0.25f) / 0.75f);
        float[] c = FLASH_COLORS[Math.min(3, e.school())];
        double r = e.radius();
        Vec3 at = new Vec3(0, e.raise(), 0);
        Mesh.blob(glow(buf, c[0], c[1], c[2], 0.25f * fadeOut), pose, at, new Vec3(1, 1, 1).scale(r * (1.4 + 0.8 * grow)), 2);
        Mesh.blob(glow(buf, c[0], c[1], c[2], 0.7f * fadeOut), pose, at, new Vec3(1, 1, 1).scale(r * 0.75 * grow), 2);
        if (e.school() != 3)
            Mesh.blob(glow(buf, 1f, 1f, 1f, 0.9f * fadeOut), pose, at, new Vec3(1, 1, 1).scale(r * 0.35 * grow), 2);
        RandomSource rs = RandomSource.create(e.getId() * 41L);
        Mesh.Out spike = glow(buf, Math.min(1, c[0] + 0.3f), Math.min(1, c[1] + 0.3f), Math.min(1, c[2] + 0.3f), 0.85f * fadeOut);
        for (int i = 0; i < 6; i++) {
            Vec3 d = new Vec3(rs.nextGaussian(), rs.nextGaussian(), rs.nextGaussian()).normalize();
            Mesh.thorn(spike, null, pose, at.add(d.scale(r * 0.3)), d, r * (0.6 + 1.2 * grow), r * 0.08, Vec3.ZERO, 0);
        }
    }

    // =========================================================================================== a spell's magic circle
    private void circle(SpellVisualEntity e, float t, PoseStack ps, MultiBufferSource buf) {
        float life = e.life();
        float in = ease(t / 6), out = 1 - ease((t - (life - 8)) / 8);
        float a = 0.9f * in * out;
        double r = e.radius() * (0.55 + 0.45 * in);
        float spin = e.facing() + t * 0.05f;
        if (e.raise() > 0) {
            com.obsidianarsenal.client.MagicCircle.draw(ps, buf, e.school(), new Vec3(0, e.raise(), 0), DOWN, r, spin, a);
        } else {
            com.obsidianarsenal.client.MagicCircle.draw(ps, buf, e.school(), new Vec3(0, 0.04, 0), UP, r, spin, a);
            com.obsidianarsenal.client.MagicCircle.draw(ps, buf, e.school(), new Vec3(0, 0.06, 0), UP, r * 0.45, -spin * 1.8f, a * 0.8f);
        }
    }

    private static Mesh.Out glow(MultiBufferSource buf, float r, float g, float b, float a) {
        return new Mesh.Out(buf.getBuffer(RenderType.entityTranslucentEmissive(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, r, g, b, Mth.clamp(a, 0, 1));
    }

    private static Mesh.Out solid(MultiBufferSource buf, int light, float shade) {
        return new Mesh.Out(buf.getBuffer(RenderType.entityCutoutNoCull(METAL)), light, OverlayTexture.NO_OVERLAY, shade, shade, shade, 1);
    }

    private static float ease(float x) {
        x = Mth.clamp(x, 0, 1);
        return x * x * (3 - 2 * x);
    }

    private static final Vec3 UP = new Vec3(0, 1, 0), DOWN = new Vec3(0, -1, 0);

    // =========================================================================================== Railgun: orbital strike
    private void orbital(SpellVisualEntity e, float t, PoseStack ps, MultiBufferSource buf, int light) {
        PoseStack.Pose pose = ps.last();
        float fire = SpellVisualEntity.ORBITAL_FIRE, end = SpellVisualEntity.ORBITAL_LIFE;
        float fade = 1 - ease((t - (end - 12)) / 12);

        // ---- the target: a plasma magic circle on the ground, and a stack of circles spinning up between the ground and the
        //      cannon as it charges; the beam punches down through them and they flare, then shrink away with it
        {
            float lock = ease((t - 4) / 16), gone = ease((t - (fire + 40)) / 14);
            float spin0 = e.facing() + t * 0.05f;
            com.obsidianarsenal.client.MagicCircle.draw(ps, buf, com.obsidianarsenal.client.MagicCircle.PLASMA, new Vec3(0, 0.05, 0), UP,
                    (9 - 2.5 * lock) * (1 - 0.5 * gone), spin0, 0.95f * (1 - gone));
            com.obsidianarsenal.client.MagicCircle.draw(ps, buf, com.obsidianarsenal.client.MagicCircle.PLASMA, new Vec3(0, 0.07, 0), UP,
                    3.2 * lock * (1 - 0.5 * gone), -spin0 * 2, 0.9f * (1 - gone));
            for (int k = 0; k < 4; k++) {
                float show = ease((t - (26 + k * 8)) / 7) * (1 - gone);
                if (show <= 0) continue;
                float charge = ease((t - 40) / (fire - 40));
                float flare = t >= fire ? 1.25f - 0.25f * ease((t - fire) / 6) : 1f;
                double y = 8 + k * 8.5;
                double rad = (4.6 - k * 0.6) * show * flare;
                float sp = e.facing() + t * (0.05f + 0.25f * charge) * (k % 2 == 0 ? 1 : -1);
                com.obsidianarsenal.client.MagicCircle.draw(ps, buf, com.obsidianarsenal.client.MagicCircle.PLASMA, new Vec3(0, y, 0), UP, rad, sp, 0.85f * show);
            }
        }

        // ---- the orbital cannon overhead (Plasma school: white steel, crimson light): housing, a long barrel aimed straight
        //      down with glowing red bands, six vanes unfolding
        if (t < end) {
            Vec3 top = new Vec3(0, CANNON_Y, 0);
            float drop = 1 - ease((t - 10) / 18);                   // slides into place from higher up
            top = top.add(0, 18 * drop, 0);
            Vec3 muzzle = top.add(0, -6.5, 0);
            Mesh.Out hull = new Mesh.Out(buf.getBuffer(RenderType.entityCutoutNoCull(STEEL)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.92f, 0.92f, 0.95f, 1);
            Mesh.Out trim = glow(buf, 1f, 0.25f, 0.28f, 1f * fade);
            Mesh.blob(hull, pose, top, new Vec3(2.6, 1.3, 2.6), 3);
            Mesh.blob(hull, pose, top.add(0, 1.2, 0), new Vec3(1.4, 0.9, 1.4), 2.5);
            Mesh.tube(trim, pose, top.add(0, -0.15, 0), UP, 0.3, 2.75, 2.75, 1.0);          // a crimson band round the housing
            Mesh.tube(hull, pose, muzzle, UP, 6.0, 0.75, 0.75, 1.5);
            for (int k = 0; k < 4; k++)                                                      // glowing bands down the barrel
                Mesh.tube(trim, pose, muzzle.add(0, 0.8 + k * 1.35, 0), UP, 0.22, 0.82 + k * 0.07, 0.82 + k * 0.07, 1.0);
            Mesh.tube(hull, pose, muzzle.add(0, -0.3, 0), UP, 0.8, 1.05, 1.05, 1.0);
            float open = ease((t - 26) / 22);                       // vanes: folded along the barrel -> spread wide
            Mesh.Out vaneGlow = glow(buf, 1f, 0.3f, 0.33f, 1f * fade);
            Mesh.blob(glow(buf, 1f, 0.2f, 0.22f, 0.18f * fade), pose, top.add(0, -2, 0), new Vec3(6, 4.5, 6), 2);   // bloom round the cannon
            for (int i = 0; i < 6; i++) {
                double ang = e.facing() + i * Math.PI / 3 + t * 0.02;
                Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                Vec3 dir = out.scale(Math.sin(open * 1.45)).add(0, -Math.cos(open * 1.45), 0).normalize();
                Mesh.thorn(hull, vaneGlow, pose, top.add(out.scale(1.8)), dir, 5.5, 0.55, Vec3.ZERO, 0.3);
            }
            // charging: a white core in a crimson shell swelling at the muzzle, energy lines drawn in from the vane tips,
            // and arcs snapping between the vanes
            float charge = ease((t - 40) / (fire - 40));
            if (t < fire + 2 && charge > 0) {
                float pulse = 0.8f + 0.2f * Mth.sin(t * 1.7f);
                Vec3 core = muzzle.add(0, -0.4, 0);
                Mesh.blob(glow(buf, 1f, 0.25f, 0.28f, 0.22f * charge), pose, core, new Vec3(1, 1, 1).scale(1.0 + 4.5 * charge * pulse), 2);   // bloom
                Mesh.blob(glow(buf, 1f, 0.2f, 0.25f, 0.8f), pose, core, new Vec3(1, 1, 1).scale(0.3 + 1.8 * charge * pulse), 2);
                Mesh.blob(glow(buf, 1f, 1f, 1f, 1f), pose, core, new Vec3(1, 1, 1).scale(0.15 + 0.95 * charge * pulse), 2);
                RandomSource ar = RandomSource.create(e.getId() * 13L + (long) (t * 0.7f));
                for (int i = 0; i < 6; i++) {
                    double ang = e.facing() + i * Math.PI / 3 + t * 0.02;
                    Vec3 out = new Vec3(Math.cos(ang), 0, Math.sin(ang));
                    Vec3 dir = out.scale(Math.sin(open * 1.45)).add(0, -Math.cos(open * 1.45), 0).normalize();
                    Vec3 tip = top.add(out.scale(1.8)).add(dir.scale(5.5));
                    if (charge > 0.35) Mesh.capsule(glow(buf, 1f, 0.85f, 0.85f, 0.8f * charge), pose, tip, core, 0.06 + 0.07 * charge);
                    if (charge > 0.5 && ar.nextInt(3) == 0) arc(glow(buf, 1f, 1f, 1f, 0.9f), pose, tip, core.add(ar.nextGaussian(), ar.nextGaussian(), ar.nextGaussian()), ar, 4, 0.5);
                }
            }
            // ---- the beam: a white-hot core in a crimson sheath, ground to muzzle, lightning crawling up it
            if (t >= fire) {
                float on = ease((t - fire) / 3) * fade;
                float flick = 0.85f + 0.15f * Mth.sin(t * 3.1f);
                double h = muzzle.y - 0.4;
                Mesh.tube(glow(buf, 1f, 0.2f, 0.24f, 0.16f * on), pose, Vec3.ZERO, UP, h, 7.0 * on * flick, 7.0 * on * flick, 0.5);   // wide bloom
                Mesh.tube(glow(buf, 1f, 0.15f, 0.2f, 0.65f * on), pose, Vec3.ZERO, UP, h, 3.4 * on * flick, 3.4 * on * flick, 0.55);
                Mesh.tube(glow(buf, 1f, 0.55f, 0.58f, 0.9f * on), pose, Vec3.ZERO, UP, h, 1.7 * on, 1.7 * on, 0.6);
                Mesh.tube(glow(buf, 1f, 1f, 1f, 1f * on), pose, Vec3.ZERO, UP, h, 0.8 * on * flick, 0.8 * on * flick, 0.7);
                RandomSource lr = RandomSource.create(e.getId() * 29L + (long) (t * 0.6f));
                Mesh.Out bolt = glow(buf, 1f, 0.97f, 0.97f, 1f * on);
                for (int i = 0; i < 5; i++) {                       // lightning crawling up the outside of the beam
                    double y0 = lr.nextDouble() * h, ang = lr.nextDouble() * Math.PI * 2;
                    Vec3 a = new Vec3(Math.cos(ang) * 3.3, y0, Math.sin(ang) * 3.3);
                    Vec3 b = new Vec3(Math.cos(ang + 0.6) * 3.6, y0 + 4 + lr.nextDouble() * 5, Math.sin(ang + 0.6) * 3.6);
                    arc(bolt, pose, a, b, lr, 5, 0.9);
                }

                // ---- the impact
                float k0 = (t - fire);
                float flash = 1 - ease(k0 / 8);
                if (flash > 0)                                     // a blinding white dome
                    Mesh.blob(glow(buf, 1f, 0.97f, 0.97f, 0.95f * flash), pose, Vec3.ZERO, new Vec3(1, 0.65, 1).scale(2 + 13 * ease(k0 / 5)), 2);
                if (flash > 0)
                    Mesh.blob(glow(buf, 1f, 0.3f, 0.32f, 0.3f * flash), pose, Vec3.ZERO, new Vec3(1, 0.7, 1).scale(4 + 22 * ease(k0 / 6)), 2);   // halo
                float wave = ease(k0 / 14), waveFade = 1 - ease((k0 - 4) / 12);
                if (waveFade > 0)                                  // a shockwave rolling out along the ground
                    Mesh.tube(glow(buf, 1f, 0.8f, 0.8f, 0.8f * waveFade), pose, new Vec3(0, 0.1, 0), UP, 0.8 + 1.4 * (1 - wave), 3 + 24 * wave, 3 + 24 * wave, 1.15);
                float blast = ease(k0 / 10), after = 1 - ease((k0 - 6) / 18);
                Mesh.tube(glow(buf, 1f, 0.2f, 0.22f, 0.75f * after), pose, Vec3.ZERO, UP, 3 + 5 * blast, 2 + 6 * blast, 2 + 6 * blast, 0.25);
                RandomSource r = RandomSource.create(e.getId() * 31L);
                // glowing cracks splitting out across the ground, cooling from white to red to dark
                float cool = ease((k0 - 10) / 30);
                Mesh.Out crack = glow(buf, 1f, 0.9f - 0.75f * cool, 0.9f - 0.8f * cool, 0.9f * (1 - cool * 0.85f) * fade);
                for (int i = 0; i < 14; i++) {
                    double ang = i * Math.PI * 2 / 14 + r.nextDouble() * 0.3, len = (10 + r.nextDouble() * 9) * ease(k0 / 6);
                    Vec3 prev = new Vec3(Math.cos(ang) * 6, 0.12, Math.sin(ang) * 6);
                    for (int s = 1; s <= 4; s++) {
                        double a2 = ang + (r.nextDouble() - 0.5) * 0.5;
                        Vec3 p = new Vec3(Math.cos(a2) * (6 + len * s / 4), 0.12, Math.sin(a2) * (6 + len * s / 4));
                        Mesh.capsule(crack, pose, prev, p, 0.16 - s * 0.025);
                        prev = p;
                    }
                }
                // the crater floor glowing molten red until it fades
                Mesh.blob(glow(buf, 1f, 0.15f, 0.12f, 0.55f * (1 - ease((k0 - 20) / 24)) * fade), pose, new Vec3(0, -2.5, 0), new Vec3(7, 0.5, 7), 2);
                // debris: dark chunks thrown high on ballistic arcs
                Mesh.Out rock = new Mesh.Out(buf.getBuffer(RenderType.entityCutoutNoCull(METAL)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.5f, 0.42f, 0.45f, 1);
                Mesh.Out ember = glow(buf, 1f, 0.3f, 0.2f, 0.9f);
                for (int i = 0; i < 26; i++) {
                    double ang = r.nextDouble() * Math.PI * 2, sp = 0.35 + r.nextDouble() * 0.6, up = 0.9 + r.nextDouble() * 1.3;
                    double tt = Math.min(k0, 40) * (0.6 + r.nextDouble() * 0.4);
                    double y = up * tt - 0.045 * tt * tt;
                    if (y < -1 || k0 < 1) continue;
                    Vec3 p = new Vec3(Math.cos(ang) * sp * tt, y, Math.sin(ang) * sp * tt);
                    double s = 0.25 + r.nextDouble() * 0.45;
                    Mesh.blob(rock, pose, p, new Vec3(s, s * 0.8, s), 1.6);
                    if (i % 3 == 0) Mesh.blob(ember, pose, p, new Vec3(s, s, s).scale(0.55), 2);
                }
                for (int i = 0; i < 6; i++) {                        // and white shards racing up the beam
                    double y = (k0 * 2.4 + i * 7) % h;
                    double ang = r.nextDouble() * Math.PI * 2;
                    Mesh.thorn(glow(buf, 1f, 0.95f, 0.95f, 0.9f * on), null, pose, new Vec3(Math.cos(ang) * 3.4, y, Math.sin(ang) * 3.4), DOWN, 3.0, 0.22, Vec3.ZERO, 0);
                }
            }
        }
    }

    /** A jagged lightning arc of glowing capsules from a to b. */
    private static void arc(Mesh.Out o, PoseStack.Pose pose, Vec3 a, Vec3 b, RandomSource r, int segs, double jag) {
        Vec3 prev = a;
        for (int s = 1; s <= segs; s++) {
            Vec3 p = a.lerp(b, s / (double) segs);
            if (s < segs) p = p.add((r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag, (r.nextDouble() - 0.5) * jag);
            Mesh.capsule(o, pose, prev, p, 0.06);
            prev = p;
        }
    }

    // =========================================================================================== Kirin: the lightning dragon
    /** The dragon's coiling dive, s = 0 in the cloud to 1 on the target. */
    private static Vec3 dive(double s, double spin) {
        double rad = 4.2 * (1 - s) + 0.2;
        double a = s * 10 + spin;
        return new Vec3(Math.cos(a) * rad, 19 * (1 - s) + 0.6, Math.sin(a) * rad);
    }

    private void kirin(SpellVisualEntity e, float t, PoseStack ps, MultiBufferSource buf) {
        PoseStack.Pose pose = ps.last();
        float strike = SpellVisualEntity.KIRIN_STRIKE, end = SpellVisualEntity.KIRIN_LIFE;
        RandomSource r = RandomSource.create(e.getId() * 17L);

        // ---- storm cloud: dark heavy masses gathering overhead, lit from inside by violet flashes
        float cloud = ease(t / 10) * (1 - ease((t - (end - 14)) / 14));
        if (cloud > 0) {
            Mesh.Out dark = new Mesh.Out(buf.getBuffer(RenderType.entityTranslucent(GLOW)), LightTexture.FULL_BRIGHT, OverlayTexture.NO_OVERLAY, 0.09f, 0.07f, 0.13f, 0.92f * cloud);
            for (int i = 0; i < 11; i++) {
                double a = r.nextDouble() * Math.PI * 2, d = r.nextDouble() * 7;
                Vec3 c = new Vec3(Math.cos(a) * d, 20.5 + r.nextDouble() * 1.5, Math.sin(a) * d);
                double s = (2.2 + r.nextDouble() * 2.2) * (0.4 + 0.6 * cloud);
                Mesh.blob(dark, pose, c, new Vec3(s * 1.4, s * 0.55, s * 1.3), 2.2);
                if (((int) (t * 1.3) + i) % 7 == 0)
                    Mesh.blob(glow(buf, 0.7f, 0.45f, 1f, 0.55f * cloud), pose, c.add(0, -0.4, 0), new Vec3(s, s * 0.35, s), 2);
            }
        }

        // ---- a circle opening under the cloud, facing down: where the dragon comes through
        {
            float open = ease((t - 2) / 8) * (1 - ease((t - strike) / 10));
            com.obsidianarsenal.client.MagicCircle.draw(ps, buf, com.obsidianarsenal.client.MagicCircle.OBSIDIAN, new Vec3(0, 18.6, 0), DOWN,
                    7.5 * open, e.facing() + t * 0.08f, 0.9f * open);
        }

        // ---- the dragon: a glowing coiling body behind a horned head, plunging from the cloud onto the target
        float head = ease((t - 7) / (strike - 7));
        float gone = ease((t - strike) / 12);                         // it bursts apart into the ground after the strike
        if (t >= 7 && gone < 1) {
            float alpha = 1 - gone;
            double spin = e.facing();
            Mesh.Out body = glow(buf, 0.72f, 0.42f, 1f, 0.75f * alpha);
            Mesh.Out core = glow(buf, 0.97f, 0.92f, 1f, 0.95f * alpha);
            int n = 30;
            Vec3 prev = null;
            for (int i = 0; i <= n; i++) {
                double s = head - i * 0.03;
                if (s < 0) break;
                Vec3 p = dive(s, spin);
                if (gone > 0) p = p.add(0, -gone * 3 * (1 - i / (double) n), 0);
                if (prev != null) {
                    double rr = 1.05 * (1 - i / (double) (n + 4)) + 0.15;
                    Mesh.capsule(body, pose, prev, p, rr);
                    Mesh.capsule(core, pose, prev, p, rr * 0.38);
                    if (i % 3 == 0) {                                  // dorsal spines
                        Vec3 d = p.subtract(prev).normalize();
                        Vec3 side = Mesh.perp(d);
                        Mesh.thorn(core, null, pose, p.add(UP.scale(rr * 0.8)), UP.add(d.scale(-0.8)).normalize(), rr * 1.6, rr * 0.35, Vec3.ZERO, 0);
                        Mesh.thorn(body, null, pose, p.add(side.scale(rr)), side.add(d.scale(-0.6)).normalize(), rr * 1.3, rr * 0.25, Vec3.ZERO, 0);
                        Mesh.thorn(body, null, pose, p.add(side.scale(-rr)), side.scale(-1).add(d.scale(-0.6)).normalize(), rr * 1.3, rr * 0.25, Vec3.ZERO, 0);
                    }
                }
                prev = p;
            }
            // head: skull, open jaws, two swept-back horns, trailing whiskers
            Vec3 hp = dive(head, spin), back = dive(Math.max(0, head - 0.02), spin);
            Vec3 fwd = hp.subtract(back);
            fwd = fwd.lengthSqr() < 1e-6 ? DOWN : fwd.normalize();
            Vec3 side = Mesh.perp(fwd), up = side.cross(fwd).normalize();
            Mesh.blob(body, pose, hp, new Vec3(1.3, 1.3, 1.3), 2.4);
            Mesh.blob(core, pose, hp.add(fwd.scale(0.3)), new Vec3(0.6, 0.6, 0.6), 2);
            Mesh.thorn(body, core, pose, hp.add(up.scale(0.4)), fwd.add(up.scale(0.25)).normalize(), 2.4, 0.55, Vec3.ZERO, 0.35);        // upper jaw
            Mesh.thorn(body, core, pose, hp.add(up.scale(-0.4)), fwd.add(up.scale(-0.45)).normalize(), 2.0, 0.45, Vec3.ZERO, 0.35);      // lower jaw
            for (int sgn = -1; sgn <= 1; sgn += 2) {
                Mesh.thorn(core, null, pose, hp.add(up.scale(0.8)).add(side.scale(0.5 * sgn)), up.add(fwd.scale(-1.2)).add(side.scale(0.3 * sgn)).normalize(), 2.6, 0.28, up.scale(0.5), 0);
                Mesh.capsule(body, pose, hp.add(side.scale(0.9 * sgn)), hp.add(side.scale(2.2 * sgn)).add(fwd.scale(-2.5)), 0.08);
            }
            // lightning crackling off the body
            RandomSource cr = RandomSource.create(e.getId() * 7L + (long) (t * 0.5f));
            for (int i = 0; i < 6; i++) {
                double s = head - cr.nextDouble() * 0.8;
                if (s < 0) continue;
                Vec3 a = dive(s, spin), b = a.add((cr.nextDouble() - 0.5) * 3, (cr.nextDouble() - 0.5) * 3, (cr.nextDouble() - 0.5) * 3);
                Vec3 mid = a.lerp(b, 0.5).add((cr.nextDouble() - 0.5) * 1.2, (cr.nextDouble() - 0.5) * 1.2, (cr.nextDouble() - 0.5) * 1.2);
                Mesh.capsule(core, pose, a, mid, 0.05);
                Mesh.capsule(core, pose, mid, b, 0.05);
            }
        }

        // ---- the strike: a column of white light and lightning spikes splitting out across the ground
        if (t >= strike && t < strike + 18) {
            float k = ease((t - strike) / 4), f = 1 - ease((t - strike - 4) / 14);
            Mesh.tube(glow(buf, 0.8f, 0.6f, 1f, 0.6f * f), pose, Vec3.ZERO, UP, 22, 2.2 * k, 2.2 * k, 0.4);
            Mesh.tube(glow(buf, 1f, 1f, 1f, 0.9f * f), pose, Vec3.ZERO, UP, 22, 0.8 * k, 0.8 * k, 0.4);
            Mesh.Out spike = glow(buf, 0.9f, 0.75f, 1f, 0.9f * f);
            for (int i = 0; i < 8; i++) {
                double a = i * Math.PI / 4 + r.nextDouble() * 0.4;
                Vec3 d = new Vec3(Math.cos(a), 0.05, Math.sin(a)).normalize();
                Mesh.thorn(spike, null, pose, d.scale(0.6).add(0, 0.15, 0), d, 2 + 5 * k, 0.32, new Vec3((r.nextDouble() - 0.5) * 2, 0, (r.nextDouble() - 0.5) * 2), 0);
            }
        }
    }
}

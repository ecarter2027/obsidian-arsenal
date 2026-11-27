package com.obsidianarsenal.client;

import com.obsidianarsenal.ObsidianArsenal;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.Camera;
import net.minecraft.client.CameraType;
import net.minecraft.client.Minecraft;
import net.minecraft.util.Mth;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.ViewportEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.util.ObfuscationReflectionHelper;

/**
 * Short cinematic camera moves for the big spells, played on the caster's own screen only (sent by the server with
 * ModNetwork.CutsceneS2C). The camera leaves the player for a moment, follows a few keyframes eased into each other,
 * then hands control back: third person is switched on for the shot (the HUD hidden behind film letterbox bars,
 * keyframes may zoom the field of view) and restored afterwards. Holding
 * sneak skips a cutscene.
 *  orbit    - circles the caster (Kagutsuchi)
 *  eyes     - a close-up on the caster's eyes, then a cut to the target (Amaterasu)
 *  sky      - looks up at the storm over the target, then pans down onto the strike (Kirin)
 *  line     - over the shoulder, then racing along the shot to its target (Railgun, Indra's Arrow)
 *  pullback - from the caster's face back and up to take in the whole black hole (Black Hole)
 */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, value = Dist.CLIENT)
public final class Cutscene {
    private static final Method SET_POSITION = ObfuscationReflectionHelper.findMethod(Camera.class, "m_90584_", double.class, double.class, double.class);

    /** A keyframe: when, camera position, look-at point, shake strength, field of view (0 = the player's own). */
    private record Key(float t, Vec3 cam, Vec3 look, float shake, float fov) {
        Key(float t, Vec3 cam, Vec3 look) { this(t, cam, look, 0, 0); }
        Key(float t, Vec3 cam, Vec3 look, float shake) { this(t, cam, look, shake, 0); }
    }

    private static List<Key> keys;
    private static float length, age;
    private static CameraType savedCamera;
    private static boolean savedHideGui;

    private Cutscene() {}

    /** Starts a cutscene (client): kind, the caster's position and facing, and the spell's target point. */
    public static void play(String kind, Vec3 origin, float yaw, Vec3 target) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || !com.obsidianarsenal.OaConfig.cutscene(kind)) return;
        Vec3 f = Vec3.directionFromRotation(0, yaw);
        Vec3 s = new Vec3(-f.z, 0, f.x);
        Vec3 eye = origin.add(0, 1.6, 0), chest = origin.add(0, 1.2, 0);
        Vec3 tgt = target.add(0, 1.0, 0);
        List<Key> k = new ArrayList<>();
        switch (kind) {
            case "orbit" -> {
                for (int i = 0; i <= 6; i++) {
                    double a = Math.toRadians(-70 + i * 30);
                    Vec3 dir = f.scale(Math.cos(a)).add(s.scale(Math.sin(a)));
                    k.add(new Key(i * 6, chest.add(dir.scale(3.6 - i * 0.15)).add(0, 0.4 + i * 0.12, 0), chest));
                }
            }
            case "eyes" -> {
                k.add(new Key(0, eye.add(f.scale(1.4)), eye));
                k.add(new Key(12, eye.add(f.scale(0.75)).add(0, 0.02, 0), eye));
                Vec3 back = origin.subtract(target);
                back = back.lengthSqr() < 1e-4 ? f.scale(-1) : new Vec3(back.x, 0, back.z).normalize();
                k.add(new Key(13, tgt.add(back.scale(4.0)).add(s.scale(1.2)).add(0, 1.0, 0), tgt));
                k.add(new Key(40, tgt.add(back.scale(2.4)).add(s.scale(0.6)).add(0, 0.6, 0), tgt));
            }
            case "sky" -> {
                Vec3 side = target.add(s.scale(9)).add(f.scale(-4)).add(0, 1.5, 0);
                k.add(new Key(0, side, target.add(0, 18, 0)));
                k.add(new Key(10, side.add(0, 0.5, 0), target.add(0, 14, 0)));
                k.add(new Key(18, side.add(s.scale(2)).add(0, 3, 0), tgt));
                k.add(new Key(40, side.add(s.scale(4)).add(0, 5, 0), tgt));
            }
            case "line" -> {
                Vec3 shoulder = eye.add(s.scale(0.9)).add(f.scale(-1.6)).add(0, 0.3, 0);
                k.add(new Key(0, shoulder, eye.add(f.scale(12))));
                k.add(new Key(8, shoulder.add(f.scale(0.4)), eye.add(f.scale(12))));
                Vec3 near = target.add(target.subtract(origin).normalize().scale(-5)).add(s.scale(2.5)).add(0, 1.8, 0);
                k.add(new Key(18, near, tgt));
                k.add(new Key(36, near.add(s.scale(1.5)).add(0, 0.8, 0), tgt));
            }
            case "orbital" -> {
                // Railgun's orbital strike (SpellVisualEntity ORBITAL: target circle, cannon arriving at +46 from tick 10,
                // circle stack from 26, charge from 40, fires at 72)
                Vec3 toT = target.subtract(origin);
                Vec3 tf = toT.horizontalDistanceSqr() < 1e-4 ? f : new Vec3(toT.x, 0, toT.z).normalize();
                Vec3 ts = new Vec3(-tf.z, 0, tf.x);
                Vec3 muzzle = target.add(0, 39.5, 0), cannon = target.add(0, 46, 0);
                // 1. close on the caster's face as the hand goes up
                k.add(new Key(0, eye.add(f.scale(2.3)).add(s.scale(0.4)).add(0, -0.3, 0), eye, 0, 50));
                k.add(new Key(10, eye.add(f.scale(1.5)).add(s.scale(0.25)).add(0, -0.1, 0), eye, 0, 42));
                // 2. cut: over the shoulder, the target circle locking on
                Vec3 shoulder = eye.add(s.scale(0.9)).add(f.scale(-1.8)).add(0, 0.35, 0);
                k.add(new Key(11, shoulder, tgt, 0, 62));
                k.add(new Key(24, shoulder.add(f.scale(0.7)), tgt, 0, 52));
                // 3. cut: a crane shot rising from the target, up through the circle stack toward the cannon
                Vec3 crane = target.add(ts.scale(3.5)).add(tf.scale(-2));
                k.add(new Key(25, crane.add(0, 1.5, 0), target.add(0, 22, 0), 0, 78));
                k.add(new Key(48, crane.add(ts.scale(1.5)).add(0, 31, 0), cannon, 0, 72));
                // 4. cut: orbiting the muzzle as the core swells
                for (int q = 0; q <= 4; q++) {
                    double a = Math.toRadians(-40 + q * 30);
                    Vec3 dir = tf.scale(Math.cos(a)).add(ts.scale(Math.sin(a)));
                    k.add(new Key(49 + q * 4.5f, muzzle.add(dir.scale(10 - q)).add(0, -1.5 + q * 0.4, 0), muzzle, q == 4 ? 0.2f : 0, 60 - q * 2));
                }
                // 5. cut: down the barrel as it fires - a zoom punch and a rumble
                Vec3 pov = muzzle.add(ts.scale(1.6)).add(tf.scale(1.2)).add(0, -1.2, 0);
                k.add(new Key(68, pov, target, 0.3f, 70));
                k.add(new Key(72, pov.add(0, -0.4, 0), target, 0.8f, 100));
                k.add(new Key(77, pov.add(0, -1.2, 0), target, 0.7f, 92));
                // 6. cut: the impact, wide, shaking hard
                Vec3 wide = target.add(ts.scale(17)).add(tf.scale(-10)).add(0, 4.5, 0);
                k.add(new Key(78, wide, tgt, 1.1f, 72));
                k.add(new Key(96, wide.add(ts.scale(1)).add(0, 1, 0), tgt, 0.4f, 68));
                // 7. a slow pull back over the crater
                k.add(new Key(130, wide.add(ts.scale(7)).add(tf.scale(-9)).add(0, 15, 0), tgt.add(0, 2, 0), 0, 64));
            }
            case "pullback" -> {
                k.add(new Key(0, eye.add(f.scale(2.0)), eye));
                k.add(new Key(16, eye.add(f.scale(2.6)).add(s.scale(1)), chest));
                k.add(new Key(60, origin.add(f.scale(-11)).add(s.scale(5)).add(0, 7, 0), tgt));
            }
            default -> { return; }
        }
        if (keys == null) {
            savedCamera = mc.options.getCameraType();
            savedHideGui = mc.options.hideGui;
        }
        keys = k;
        length = k.get(k.size() - 1).t();
        age = 0;
        mc.options.setCameraType(CameraType.THIRD_PERSON_BACK);
    }

    private static void finish() {
        Minecraft mc = Minecraft.getInstance();
        keys = null;
        if (savedCamera != null) mc.options.setCameraType(savedCamera);
        mc.options.hideGui = savedHideGui;
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END || keys == null) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) { finish(); return; }
        if (mc.isPaused()) return;
        age++;
        if (age > length || mc.options.keyShift.isDown()) finish();
    }

    /** Film-style letterbox bars sliding in at the start and out at the end of every cutscene. */
    private static float bars(float pt) {
        if (keys == null) return 0;
        float t = age + pt;
        return Mth.clamp(Math.min(t / 6f, (length - t) / 6f), 0, 1);
    }

    /** The HUD is hidden during a cutscene (the letterbox bars are drawn instead). */
    @SubscribeEvent
    public static void hideHud(net.minecraftforge.client.event.RenderGuiOverlayEvent.Pre event) {
        if (keys != null && com.obsidianarsenal.OaConfig.HIDE_HUD.get()) event.setCanceled(true);
    }

    @SubscribeEvent
    public static void letterbox(net.minecraftforge.client.event.RenderGuiEvent.Post event) {
        float b = bars(event.getPartialTick());
        if (b <= 0 || !com.obsidianarsenal.OaConfig.LETTERBOX.get()) return;
        var g = event.getGuiGraphics();
        int w = g.guiWidth(), h = g.guiHeight(), bar = Math.round(h * 0.12f * b);
        g.fill(0, 0, w, bar, 0xFF000000);
        g.fill(0, h - bar, w, h, 0xFF000000);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void fov(ViewportEvent.ComputeFov event) {
        if (keys == null || !com.obsidianarsenal.OaConfig.FOV_EFFECTS.get()) return;
        float t = Math.min(length, age + (float) event.getPartialTick());
        Key a = keys.get(0), b = a;
        for (int i = 0; i + 1 < keys.size(); i++) {
            if (t <= keys.get(i + 1).t()) { a = keys.get(i); b = keys.get(i + 1); break; }
            a = b = keys.get(i + 1);
        }
        if (a.fov() <= 0 && b.fov() <= 0) return;
        float k = b.t() > a.t() ? Mth.clamp((t - a.t()) / (b.t() - a.t()), 0, 1) : 1;
        k = k * k * (3 - 2 * k);
        double fa = a.fov() > 0 ? a.fov() : event.getFOV(), fb = b.fov() > 0 ? b.fov() : event.getFOV();
        event.setFOV(fa + (fb - fa) * k);
    }

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void camera(ViewportEvent.ComputeCameraAngles event) {
        if (keys == null) return;
        float t = Math.min(length, age + (float) event.getPartialTick());
        Key a = keys.get(0), b = a;
        for (int i = 0; i + 1 < keys.size(); i++) {
            if (t <= keys.get(i + 1).t()) { a = keys.get(i); b = keys.get(i + 1); break; }
            a = b = keys.get(i + 1);
        }
        float k = b.t() > a.t() ? Mth.clamp((t - a.t()) / (b.t() - a.t()), 0, 1) : 1;
        k = k * k * (3 - 2 * k);
        Vec3 cam = a.cam().lerp(b.cam(), k), look = a.look().lerp(b.look(), k).subtract(cam);
        float shake = a.shake() + (b.shake() - a.shake()) * k;
        if (!com.obsidianarsenal.OaConfig.CAMERA_SHAKE.get()) shake = 0;
        if (shake > 0) {                                                       // a rumble for the big hits
            double ph = t * 2.7;
            cam = cam.add(Math.sin(ph * 3.1) * 0.18 * shake, Math.sin(ph * 4.3) * 0.15 * shake, Math.cos(ph * 2.3) * 0.18 * shake);
        }
        try {
            SET_POSITION.invoke(event.getCamera(), cam.x, cam.y, cam.z);
        } catch (ReflectiveOperationException ex) {
            finish();
            return;
        }
        event.setYaw((float) Math.toDegrees(Math.atan2(-look.x, look.z)));
        event.setPitch((float) -Math.toDegrees(Math.atan2(look.y, Math.sqrt(look.x * look.x + look.z * look.z))));
        event.setRoll(shake > 0 ? (float) Math.sin(t * 5.1) * 2.5f * shake : 0);
    }
}

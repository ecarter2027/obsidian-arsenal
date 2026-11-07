package com.obsidianarsenal.efcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.joml.Matrix3f;
import org.joml.Matrix4f;

/**
 * Obsidian Stretch on the client. For {@link #GROW} ticks the body stretches from where the player stands to the
 * destination (the far end flows out to it); then the dashing player is moved there and for {@link #CATCH} ticks the
 * near end catches up, so the body pulls in after them and ends normal. The stretch runs along the dash direction in
 * world space (thinning a little while long) and is applied to the render pose before Epic Fight draws the player
 * (it renders at normal priority), so the body, armor and held items all stretch together; the entity renderer pops
 * the pose afterwards. Anchored in world space, so other players see it right even while their position catches up.
 */
@EventBusSubscriber(modid = ObsidianEFCompat.MODID, value = Dist.CLIENT)
public final class StretchFx {
    private static final float GROW = 2.0F, CATCH = 3.0F;
    private static final Map<Integer, Dash> ACTIVE = new HashMap<>();

    private record Dash(Vec3 start, Vec3 dir, float dist, long startTick, boolean[] moved) {}

    private StretchFx() {}

    public static void start(int entity, Vec3 start, Vec3 dir, float dist) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || dist < 0.3F) return;
        ACTIVE.put(entity, new Dash(start, dir, dist, mc.level.getGameTime(), new boolean[1]));
    }

    @SubscribeEvent
    public static void tick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { ACTIVE.clear(); return; }
        long now = mc.level.getGameTime();
        LocalPlayer self = mc.player;
        ACTIVE.entrySet().removeIf(e -> now - e.getValue().startTick() > GROW + CATCH + 1);
        Dash dash = self == null ? null : ACTIVE.get(self.getId());
        if (dash != null && !dash.moved()[0] && now - dash.startTick() >= GROW) {
            // the snap: straight to the destination, stopped by anything solid on the way
            dash.moved()[0] = true;
            Vec3 target = dash.start().add(dash.dir().scale(dash.dist()));
            self.move(MoverType.SELF, target.subtract(self.position()));
            self.setDeltaMovement(dash.dir().scale(0.35));
            self.fallDistance = 0.0F;
            self.hasImpulse = true;
        }
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void stretch(RenderLivingEvent.Pre<?, ?> event) {
        Dash dash = ACTIVE.get(event.getEntity().getId());
        Minecraft mc = Minecraft.getInstance();
        if (dash == null || mc.level == null || mc.screen != null) return;
        float age = mc.level.getGameTime() - dash.startTick() + event.getPartialTick();
        if (age >= GROW + CATCH) return;
        float out = ease(Math.min(1.0F, age / GROW)), back = ease(Mth.clamp((age - GROW) / CATCH, 0.0F, 1.0F));
        LivingEntity entity = event.getEntity();
        float pt = event.getPartialTick();
        Vec3 pos = new Vec3(Mth.lerp(pt, entity.xo, entity.getX()), Mth.lerp(pt, entity.yo, entity.getY()), Mth.lerp(pt, entity.zo, entity.getZ()));
        Vec3 d = dash.dir();
        double rear = dash.start().add(d.scale(dash.dist() * back)).subtract(pos).dot(d);
        double front = dash.start().add(d.scale(dash.dist() * out)).subtract(pos).dot(d);
        apply(event.getPoseStack(), d, rear, front, entity.getBbWidth(), entity.getBbHeight());
    }

    private static float ease(float t) { return t * t * (3.0F - 2.0F * t); }

    /**
     * Stretches the body (feet at the pose origin) so that it spans from the body standing at {@code rear} to the body
     * standing at {@code front}, both measured along the unit direction d; across d it thins to keep some volume.
     */
    private static void apply(PoseStack stack, Vec3 d, double rear, double front, float width, float height) {
        double e = Math.abs(d.y) * height + Math.sqrt(Math.max(0.0, 1.0 - d.y * d.y)) * width;   // the body's depth along d
        Vec3 c = new Vec3(0.0, height * 0.5, 0.0);
        double cd = c.dot(d);
        double s0 = cd - e / 2, s0New = rear + cd - e / 2;
        double k = (e + front - rear) / e;
        if (k <= 1.01) return;
        double q = Mth.clamp(1.0 / Math.sqrt(k), 0.45, 1.0);
        java.util.function.Function<Vec3, Vec3> f = p -> {
            Vec3 u = p.subtract(c);
            double s = u.dot(d);
            Vec3 perp = u.subtract(d.scale(s));
            double sNew = s0New + (cd + s - s0) * k;
            return c.add(perp.scale(q)).add(d.scale(sNew - cd));
        };
        Vec3 t = f.apply(Vec3.ZERO);
        Vec3 x = f.apply(new Vec3(1, 0, 0)).subtract(t), y = f.apply(new Vec3(0, 1, 0)).subtract(t), z = f.apply(new Vec3(0, 0, 1)).subtract(t);
        Matrix4f m = new Matrix4f((float) x.x, (float) x.y, (float) x.z, 0, (float) y.x, (float) y.y, (float) y.z, 0,
                (float) z.x, (float) z.y, (float) z.z, 0, (float) t.x, (float) t.y, (float) t.z, 1);
        PoseStack.Pose pose = stack.last();
        pose.pose().mul(m);
        pose.normal().mul(new Matrix3f(m).invert().transpose());
    }
}

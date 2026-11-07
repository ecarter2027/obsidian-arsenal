package com.obsidianarsenal.efcompat.client;

import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import org.lwjgl.glfw.GLFW;

/**
 * The movement keys for Obsidian Stretch, read the instant the dodge fires: a key counts if it is held right now or was
 * pressed within {@link #GRACE_MS} before (the player's movement input only updates once a tick, and a key tapped a
 * moment before the dodge key should still steer it).
 */
@EventBusSubscriber(modid = ObsidianEFCompat.MODID, value = Dist.CLIENT)
public final class DodgeKeys {
    public static final long GRACE_MS = 150;
    private static final long[] PRESSED = new long[4];   // forward, back, left, right: when last pressed (ms)

    private DodgeKeys() {}

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void key(InputEvent.Key event) {
        if (event.getAction() != GLFW.GLFW_PRESS) return;
        var o = Minecraft.getInstance().options;
        KeyMapping[] keys = {o.keyUp, o.keyDown, o.keyLeft, o.keyRight};
        for (int i = 0; i < 4; i++) if (keys[i].matches(event.getKey(), event.getScanCode())) PRESSED[i] = System.currentTimeMillis();
    }

    private static boolean on(KeyMapping key, int i) {
        return key.isDown() || System.currentTimeMillis() - PRESSED[i] <= GRACE_MS;
    }

    /** +1 forward, -1 back, 0 neither (or both). */
    public static float forward() {
        var o = Minecraft.getInstance().options;
        return (on(o.keyUp, 0) ? 1 : 0) - (on(o.keyDown, 1) ? 1 : 0);
    }

    /** +1 left, -1 right, 0 neither (or both). */
    public static float left() {
        var o = Minecraft.getInstance().options;
        return (on(o.keyLeft, 2) ? 1 : 0) - (on(o.keyRight, 3) ? 1 : 0);
    }
}

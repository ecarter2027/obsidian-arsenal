package com.obsidianarsenal.client;

import com.obsidianarsenal.ObsidianArsenal;
import it.unimi.dsi.fastutil.ints.Int2LongOpenHashMap;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OutlineBufferSource;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Tints the vanilla Glowing outline of players who are casting. The level renderer sets the outline colour (team
 * colour, normally white) right before it draws each glowing entity; we override it at the start of that draw.
 */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, value = Dist.CLIENT)
public final class CastGlowClient {
    private CastGlowClient() {}

    /** entity id -> (rgb << 32 | expiry game time) */
    private static final Int2LongOpenHashMap ACTIVE = new Int2LongOpenHashMap();

    public static void set(int entityId, int rgb, int ticks) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        ACTIVE.put(entityId, ((long) (rgb & 0xFFFFFF) << 32) | ((mc.level.getGameTime() + ticks) & 0xFFFFFFFFL));
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onRenderLiving(RenderLivingEvent.Pre<?, ?> e) {
        if (ACTIVE.isEmpty() || !e.getEntity().isCurrentlyGlowing()) return;
        int id = e.getEntity().getId();
        if (!ACTIVE.containsKey(id)) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        long v = ACTIVE.get(id);
        if ((v & 0xFFFFFFFFL) < (mc.level.getGameTime() & 0xFFFFFFFFL)) { ACTIVE.remove(id); return; }
        int rgb = (int) (v >>> 32);
        OutlineBufferSource outline = mc.renderBuffers().outlineBufferSource();
        outline.setColor((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF, 255);
    }
}

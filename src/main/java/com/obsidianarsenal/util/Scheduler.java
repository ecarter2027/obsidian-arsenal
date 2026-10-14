package com.obsidianarsenal.util;

import com.obsidianarsenal.ObsidianArsenal;
import net.minecraft.server.level.ServerLevel;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.server.ServerStoppingEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

/** Server-side "run this in N ticks" helper (staggered spikes/shards, combo hit frames, Indra blast shells). */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID)
public final class Scheduler {
    private record Task(long runAt, Runnable runnable) {}

    private static final List<Task> TASKS = new ArrayList<>();
    private static final List<Task> ADDED = new ArrayList<>();
    private static long now = 0;

    private Scheduler() {}

    public static void later(ServerLevel level, int delayTicks, Runnable r) {
        if (delayTicks <= 0) { r.run(); return; }
        ADDED.add(new Task(now + delayTicks, r));
    }

    @SubscribeEvent
    public static void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        now++;
        if (!ADDED.isEmpty()) { TASKS.addAll(ADDED); ADDED.clear(); }
        List<Task> due = null;
        for (Iterator<Task> it = TASKS.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.runAt <= now) {
                if (due == null) due = new ArrayList<>();
                due.add(t);
                it.remove();
            }
        }
        if (due != null) for (Task t : due) {
            try { t.runnable.run(); } catch (Exception ex) { ObsidianArsenal.LOGGER.error("Scheduled task failed", ex); }
        }
    }

    @SubscribeEvent
    public static void onStop(ServerStoppingEvent e) { TASKS.clear(); ADDED.clear(); }
}

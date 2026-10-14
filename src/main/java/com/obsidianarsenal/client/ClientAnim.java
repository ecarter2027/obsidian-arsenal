package com.obsidianarsenal.client;

import com.obsidianarsenal.ObsidianArsenal;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonConfiguration;
import dev.kosmx.playerAnim.api.firstPerson.FirstPersonMode;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.KeyframeAnimationPlayer;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.api.layered.modifier.AbstractFadeModifier;
import dev.kosmx.playerAnim.core.data.KeyframeAnimation;
import dev.kosmx.playerAnim.core.util.Ease;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationAccess;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationRegistry;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.Entity;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

/**
 * PlayerAnimator glue for the gauntlet combo (assets/obsidian_arsenal/player_animation/*.json).
 *
 * Smoothness:
 *  - every attack blends IN from whatever pose the player is in (a running arm swing or the previous attack)
 *  - near the end of an attack it blends OUT back to vanilla movement instead of snapping
 *  - the animations themselves are dense per-tick curves and only drive the parts they use,
 *    so the legs keep walking normally during punches
 */
public final class ClientAnim {
    public static final ResourceLocation LAYER = ObsidianArsenal.id("combat");

    private static final int FADE_IN = 3;    // ticks
    private static final int FADE_OUT = 6;   // ticks

    private static final FirstPersonConfiguration FIRST_PERSON =
            new FirstPersonConfiguration().setShowRightArm(true).setShowLeftArm(true).setShowRightItem(true).setShowLeftItem(true);

    private record Playing(ModifierLayer<IAnimation> layer, KeyframeAnimationPlayer player, long fadeOutAt) {}
    private static final Map<Integer, Playing> PLAYING = new HashMap<>();

    private ClientAnim() {}

    @SuppressWarnings("unchecked")
    public static void play(int entityId, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        Entity e = mc.level.getEntity(entityId);
        if (!(e instanceof AbstractClientPlayer player)) return;
        var layer = (ModifierLayer<IAnimation>) PlayerAnimationAccess.getPlayerAssociatedData(player).get(LAYER);
        if (layer == null) return;
        KeyframeAnimation anim = PlayerAnimationRegistry.getAnimation(ObsidianArsenal.id(name));
        if (anim == null) {
            ObsidianArsenal.LOGGER.warn("Missing player animation {}:{}", ObsidianArsenal.MODID, name);
            return;
        }
        KeyframeAnimationPlayer p = new KeyframeAnimationPlayer(anim)
                .setFirstPersonMode(FirstPersonMode.THIRD_PERSON_MODEL)
                .setFirstPersonConfiguration(FIRST_PERSON);
        // cross-fade from the current pose (vanilla or the previous combo hit) into the new attack
        layer.replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_IN, Ease.INOUTSINE), p, true);
        long now = mc.level.getGameTime();
        PLAYING.put(entityId, new Playing(layer, p, now + Math.max(FADE_IN + 1, anim.endTick - FADE_OUT)));
    }

    /** Called every client tick: start the fade-out back to vanilla movement for attacks that are finishing. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { PLAYING.clear(); return; }
        long now = mc.level.getGameTime();
        for (Iterator<Map.Entry<Integer, Playing>> it = PLAYING.entrySet().iterator(); it.hasNext(); ) {
            Playing pl = it.next().getValue();
            if (pl.layer().getAnimation() != pl.player()) { it.remove(); continue; }   // replaced by a newer hit
            if (now >= pl.fadeOutAt()) {
                pl.layer().replaceAnimationWithFade(AbstractFadeModifier.standardFadeIn(FADE_OUT, Ease.INOUTSINE), null);
                it.remove();
            }
        }
    }
}

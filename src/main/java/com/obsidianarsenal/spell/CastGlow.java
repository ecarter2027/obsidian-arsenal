package com.obsidianarsenal.spell;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.network.ModNetwork;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Vector3f;

import java.util.Map;

/**
 * Casting glow: while a player is casting any Iron's spell (and briefly after an instant cast) they get the vanilla
 * Glowing outline, tinted per spell. Server side applies the (icon-less) Glowing effect and tells every tracking
 * client which colour to use; the client tints the outline when it draws that player.
 */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID)
public final class CastGlow {
    private CastGlow() {}

    /** Hand-picked colours for this mod's spells; anything else uses its school's colour. */
    private static final Map<String, Integer> COLORS = Map.ofEntries(
            Map.entry("obsidian_spike_explosion", 0x9B3DFF),
            Map.entry("obsidian_storm", 0x6A5CFF),
            Map.entry("obsidian_tentacles", 0x7A1FBF),
            Map.entry("purple_lightning_cloak", 0xD27DFF),
            Map.entry("obsidian_shatter", 0xB48CFF),
            Map.entry("indras_arrow", 0xE040FF),
            Map.entry("columns_of_hell", 0xFF2A00),
            Map.entry("flaming_judgement", 0xFF6A00),
            Map.entry("gyroscope", 0xFFB000),
            Map.entry("amaterasu", 0xA0001E),
            Map.entry("plasma_beam", 0xFF2A3C),
            Map.entry("black_hole", 0x8A2BE2));

    private static final int HOLD = 8, AFTER_CAST = 20;

    public static int colorFor(String spellId) {
        int colon = spellId.indexOf(':');
        if (colon > 0 && spellId.substring(0, colon).equals(ObsidianArsenal.MODID)) {
            Integer c = COLORS.get(spellId.substring(colon + 1));
            if (c != null) return c;
        }
        try {
            AbstractSpell spell = SpellRegistry.getSpell(spellId);
            SchoolType school = spell == null ? null : spell.getSchoolType();
            if (school != null) {
                Vector3f v = school.getTargetingColor();
                if (v != null) return ((int) (v.x * 255) << 16) | ((int) (v.y * 255) << 8) | (int) (v.z * 255);
                var tc = school.getDisplayName().getStyle().getColor();
                if (tc != null) return tc.getValue();
            }
        } catch (Exception ignored) {}
        return 0xFFFFFF;
    }

    private static void glow(ServerPlayer p, String spellId, int ticks) {
        p.addEffect(new MobEffectInstance(MobEffects.GLOWING, ticks, 0, true, false, false));
        ModNetwork.castGlow(p, colorFor(spellId), ticks + 2);
    }

    /** Long / continuous casts: keep glowing for as long as the cast lasts. */
    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof ServerPlayer p)) return;
        MagicData data = MagicData.getPlayerMagicData(p);
        if (data == null || !data.isCasting()) return;
        String id = data.getCastingSpellId();
        if (id == null || id.isEmpty()) return;
        if (p.tickCount % 5 == 0 || !p.hasEffect(MobEffects.GLOWING)) glow(p, id, HOLD);
    }

    /** Every cast (including instant ones) leaves a short glow. */
    @SubscribeEvent
    public static void onCast(SpellOnCastEvent e) {
        if (e.getEntity() instanceof ServerPlayer p) glow(p, e.getSpellId(), AFTER_CAST);
    }
}

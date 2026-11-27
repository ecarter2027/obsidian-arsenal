package com.obsidianarsenal;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

/**
 * Obsidian Arsenal's settings.
 *  config/obsidian_arsenal-client.toml - per player, visuals only: cutscenes (all, or spell by spell), letterbox bars,
 *      camera shake, zoom, HUD hiding, magic circles, glow flashes, black hole lensing, afterimages, particle amount,
 *      scroll descriptions
 *  config/obsidian_arsenal-common.toml - gameplay: block destruction, flying debris, spell damage multiplier
 * Mana, cooldowns, levels and enabling or disabling each spell are Iron's Spells' own per-spell settings
 * (config/irons_spellbooks-server.toml).
 */
public final class OaConfig {
    private OaConfig() {}

    // ------------------------------------------------------------------------------------------------- client
    public static final ForgeConfigSpec CLIENT_SPEC;
    public static final BooleanValue CUTSCENES, CUT_RAILGUN, CUT_KIRIN, CUT_AMATERASU, CUT_INDRA, CUT_BLACK_HOLE, CUT_KAGUTSUCHI;
    public static final BooleanValue LETTERBOX, HIDE_HUD, CAMERA_SHAKE, FOV_EFFECTS;
    public static final BooleanValue CAST_CIRCLES, SPELL_CIRCLES, GLOW_FLASHES, BLACK_HOLE_LENS, AFTERIMAGES, SCROLL_DESCRIPTIONS;
    public static final DoubleValue PARTICLE_AMOUNT;

    // ------------------------------------------------------------------------------------------------- common
    public static final ForgeConfigSpec COMMON_SPEC;
    public static final BooleanValue BLOCK_DESTRUCTION, FLYING_DEBRIS;
    public static final DoubleValue DAMAGE_MULTIPLIER;

    static {
        ForgeConfigSpec.Builder b = new ForgeConfigSpec.Builder();
        b.push("cutscenes");
        CUTSCENES = b.comment("Play spell cutscenes at all (the camera shots for the big spells). Sneak skips one while it plays.").define("enabled", true);
        CUT_RAILGUN = b.comment("Railgun's orbital strike cutscene.").define("railgun", true);
        CUT_KIRIN = b.comment("Kirin's storm and dragon cutscene.").define("kirin", true);
        CUT_AMATERASU = b.comment("Amaterasu's eye close-up.").define("amaterasu", true);
        CUT_INDRA = b.comment("Indra's Arrow's shot down the line.").define("indras_arrow", true);
        CUT_BLACK_HOLE = b.comment("Black Hole's pull-back shot.").define("black_hole", true);
        CUT_KAGUTSUCHI = b.comment("Kagutsuchi's orbiting shot.").define("kagutsuchi", true);
        LETTERBOX = b.comment("Black film bars at the top and bottom during cutscenes.").define("letterbox", true);
        HIDE_HUD = b.comment("Hide the HUD during cutscenes.").define("hideHud", true);
        CAMERA_SHAKE = b.comment("Camera shake on the big hits in cutscenes.").define("cameraShake", true);
        FOV_EFFECTS = b.comment("Zoom (field of view) changes in cutscenes.").define("fovEffects", true);
        b.pop();
        b.push("visuals");
        CAST_CIRCLES = b.comment("Magic circles around players while they cast this mod's spells.").define("castCircles", true);
        SPELL_CIRCLES = b.comment("Magic circles on the ground (or overhead) for area spells.").define("spellCircles", true);
        GLOW_FLASHES = b.comment("Glowing flashes on spell impacts and blinks.").define("glowFlashes", true);
        BLACK_HOLE_LENS = b.comment("Black Hole bends the light around it (a screen shader; turn off if it misbehaves with shader packs).").define("blackHoleLens", true);
        AFTERIMAGES = b.comment("Purple afterimages trailing players under Purple Lightning Cloak.").define("afterimages", true);
        PARTICLE_AMOUNT = b.comment("How many of this mod's particles to show, 0.0 (none) to 1.0 (all).").defineInRange("particleAmount", 1.0, 0.0, 1.0);
        SCROLL_DESCRIPTIONS = b.comment("Show each spell's short description in its scroll tooltip.").define("scrollDescriptions", true);
        b.pop();
        CLIENT_SPEC = b.build();

        ForgeConfigSpec.Builder c = new ForgeConfigSpec.Builder();
        c.push("gameplay");
        BLOCK_DESTRUCTION = c.comment("Spells can break or erase blocks (craters, beams, Erasure). Also needs the mobGriefing gamerule.").define("blockDestruction", true);
        FLYING_DEBRIS = c.comment("Big blasts throw real blocks into the air as debris (Railgun, Kirin...).").define("flyingDebris", true);
        DAMAGE_MULTIPLIER = c.comment("Multiplies all damage dealt by this mod's spells.").defineInRange("damageMultiplier", 1.0, 0.0, 10.0);
        c.pop();
        COMMON_SPEC = c.build();
    }

    /** Whether the cutscene of this kind should play (client). */
    public static boolean cutscene(String kind) {
        if (!CUTSCENES.get()) return false;
        return switch (kind) {
            case "orbital" -> CUT_RAILGUN.get();
            case "sky" -> CUT_KIRIN.get();
            case "eyes" -> CUT_AMATERASU.get();
            case "line" -> CUT_INDRA.get();
            case "pullback" -> CUT_BLACK_HOLE.get();
            case "orbit" -> CUT_KAGUTSUCHI.get();
            default -> true;
        };
    }
}

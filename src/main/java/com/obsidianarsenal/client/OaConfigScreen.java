package com.obsidianarsenal.client;

import com.obsidianarsenal.OaConfig;
import net.minecraft.client.OptionInstance;
import net.minecraft.client.Options;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.OptionsList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.CommonComponents;
import net.minecraftforge.common.ForgeConfigSpec.BooleanValue;
import net.minecraftforge.common.ForgeConfigSpec.DoubleValue;

import java.util.ArrayList;
import java.util.List;

/**
 * Obsidian Arsenal's settings in game (Mods list > Obsidian Arsenal > Config): every OaConfig option as a toggle or a
 * slider, saved to the config files as soon as it changes. Gameplay settings change the local files; on a server, the
 * server's own config is what counts.
 */
public class OaConfigScreen extends Screen {
    private final Screen parent;
    private OptionsList list;

    public OaConfigScreen(Screen parent) {
        super(Component.literal("Obsidian Arsenal Settings"));
        this.parent = parent;
    }

    private static OptionInstance<Boolean> toggle(String label, String tip, BooleanValue v) {
        return OptionInstance.createBoolean(label, OptionInstance.cachedConstantTooltip(Component.literal(tip)), v.get(), b -> { v.set(b); v.save(); });
    }

    /** A slider over [0, max] (the value set is 0..1, scaled), shown as a percentage or a multiplier. */
    private static OptionInstance<Double> slider(String label, String tip, DoubleValue v, double max, boolean percent) {
        return new OptionInstance<>(label, OptionInstance.cachedConstantTooltip(Component.literal(tip)),
                (caption, unit) -> Options.genericValueLabel(caption, Component.literal(percent
                        ? Math.round(unit * max * 100) + "%" : String.format("%.1fx", unit * max))),
                OptionInstance.UnitDouble.INSTANCE, v.get() / max, d -> { v.set(Math.round(d * max * 20) / 20.0); v.save(); });
    }

    @Override
    protected void init() {
        list = new OptionsList(minecraft, width, height, 32, height - 32, 25);
        List<OptionInstance<?>> o = new ArrayList<>();
        // cutscenes
        o.add(toggle("Cutscenes", "Play spell cutscenes at all. Sneak skips one while it plays.", OaConfig.CUTSCENES));
        o.add(toggle("Railgun Cutscene", "Railgun's orbital strike cutscene.", OaConfig.CUT_RAILGUN));
        o.add(toggle("Kirin Cutscene", "Kirin's storm and dragon cutscene.", OaConfig.CUT_KIRIN));
        o.add(toggle("Amaterasu Cutscene", "Amaterasu's eye close-up.", OaConfig.CUT_AMATERASU));
        o.add(toggle("Indra's Arrow Cutscene", "Indra's Arrow's shot down the line.", OaConfig.CUT_INDRA));
        o.add(toggle("Black Hole Cutscene", "Black Hole's pull-back shot.", OaConfig.CUT_BLACK_HOLE));
        o.add(toggle("Kagutsuchi Cutscene", "Kagutsuchi's orbiting shot.", OaConfig.CUT_KAGUTSUCHI));
        o.add(toggle("Letterbox Bars", "Black film bars during cutscenes.", OaConfig.LETTERBOX));
        o.add(toggle("Hide HUD", "Hide the HUD during cutscenes.", OaConfig.HIDE_HUD));
        o.add(toggle("Camera Shake", "Camera shake on the big hits in cutscenes.", OaConfig.CAMERA_SHAKE));
        o.add(toggle("Zoom Effects", "Field of view changes in cutscenes.", OaConfig.FOV_EFFECTS));
        // visuals
        o.add(toggle("Casting Circles", "Magic circles around players while they cast.", OaConfig.CAST_CIRCLES));
        o.add(toggle("Spell Circles", "Magic circles on the ground or overhead for area spells.", OaConfig.SPELL_CIRCLES));
        o.add(toggle("Glow Flashes", "Glowing flashes on spell impacts and blinks.", OaConfig.GLOW_FLASHES));
        o.add(toggle("Black Hole Lensing", "Black Hole bends the light around it. Turn off if it clashes with a shader pack.", OaConfig.BLACK_HOLE_LENS));
        o.add(toggle("Afterimages", "Purple afterimages trailing players under Purple Lightning Cloak.", OaConfig.AFTERIMAGES));
        o.add(toggle("Scroll Descriptions", "Each spell's short description in its scroll tooltip.", OaConfig.SCROLL_DESCRIPTIONS));
        o.add(slider("Particles", "How many of this mod's particles to show.", OaConfig.PARTICLE_AMOUNT, 1.0, true));
        // gameplay
        o.add(toggle("Block Destruction", "Spells can break or erase blocks. Also needs the mobGriefing gamerule.", OaConfig.BLOCK_DESTRUCTION));
        o.add(toggle("Flying Debris", "Big blasts throw real blocks into the air.", OaConfig.FLYING_DEBRIS));
        o.add(slider("Spell Damage", "Multiplies all damage dealt by this mod's spells.", OaConfig.DAMAGE_MULTIPLIER, 4.0, false));
        list.addSmall(o.toArray(new OptionInstance[0]));
        addWidget(list);
        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> onClose()).bounds(width / 2 - 100, height - 27, 200, 20).build());
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        list.render(g, mouseX, mouseY, partialTick);
        g.drawCenteredString(font, title, width / 2, 12, 0xFFFFFF);
        super.render(g, mouseX, mouseY, partialTick);
    }

    @Override
    public void onClose() { minecraft.setScreen(parent); }
}

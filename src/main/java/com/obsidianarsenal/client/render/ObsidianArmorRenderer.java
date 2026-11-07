package com.obsidianarsenal.client.render;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianArmorItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;

/**
 * Plated skin-armor: geo/item/armor/obsidian_armor.geo.json - a skin-tight living-obsidian layer under slanted,
 * overlapping black plates that end in sharp points (V visor, swept temple blades, crest fin, chevron ab plates,
 * pointed pauldrons, knee and toe points). Purple seams, veins and eye slits glow via obsidian_armor_glowmask.png.
 */
public class ObsidianArmorRenderer extends GeoArmorRenderer<ObsidianArmorItem> {
    public ObsidianArmorRenderer() {
        super(new DefaultedItemGeoModel<>(ObsidianArsenal.id("armor/obsidian_armor")));
        addRenderLayer(new software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer<>(this));
        addRenderLayer(new com.obsidianarsenal.client.mesh.SmoothPartsLayer<>(this, "obsidian_armor"));
    }
}

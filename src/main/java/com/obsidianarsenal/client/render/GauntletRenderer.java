package com.obsidianarsenal.client.render;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianGauntletItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;

/**
 * geo/item/obsidian_gauntlet.geo.json (cube-less bones that the animations drive) + smooth/obsidian_gauntlet.json:
 * an obsidian claw growing out of the wrist, drawn by SmoothPartsLayer with the vanilla obsidian texture.
 */
public class GauntletRenderer extends GeoItemRenderer<ObsidianGauntletItem> {
    public GauntletRenderer() {
        super(new DefaultedItemGeoModel<>(ObsidianArsenal.id("obsidian_gauntlet")));
        addRenderLayer(new com.obsidianarsenal.client.mesh.SmoothPartsLayer<>(this, "obsidian_gauntlet"));
    }
}

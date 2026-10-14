package com.obsidianarsenal.client.render;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianGauntletItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoItemRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * geo/item/obsidian_gauntlet.geo.json + animations/item/obsidian_gauntlet.animation.json + textures/item/obsidian_gauntlet.png.
 * The purple veins glow in the dark via textures/item/obsidian_gauntlet_glowmask.png.
 */
public class GauntletRenderer extends GeoItemRenderer<ObsidianGauntletItem> {
    public GauntletRenderer() {
        super(new DefaultedItemGeoModel<>(ObsidianArsenal.id("obsidian_gauntlet")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}

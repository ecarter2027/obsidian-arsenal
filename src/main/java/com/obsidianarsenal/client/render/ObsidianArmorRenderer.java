package com.obsidianarsenal.client.render;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianArmorItem;
import software.bernie.geckolib.model.DefaultedItemGeoModel;
import software.bernie.geckolib.renderer.GeoArmorRenderer;
import software.bernie.geckolib.renderer.layer.AutoGlowingGeoLayer;

/**
 * Spine armor: geo/item/armor/obsidian_armor.geo.json - no plating, just obsidian spines growing out of the body
 * (crest on the helmet, dorsal ridge + shoulder spikes on the chestplate, hip/knee spurs, heel spurs).
 * The tips glow purple via textures/item/armor/obsidian_armor_glowmask.png.
 */
public class ObsidianArmorRenderer extends GeoArmorRenderer<ObsidianArmorItem> {
    public ObsidianArmorRenderer() {
        super(new DefaultedItemGeoModel<>(ObsidianArsenal.id("armor/obsidian_armor")));
        addRenderLayer(new AutoGlowingGeoLayer<>(this));
    }
}

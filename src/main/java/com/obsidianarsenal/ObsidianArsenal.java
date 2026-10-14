package com.obsidianarsenal;

import com.mojang.logging.LogUtils;
import com.obsidianarsenal.network.ModNetwork;
import com.obsidianarsenal.registry.*;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.fml.ModList;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import org.slf4j.Logger;

/**
 * Obsidian Arsenal.
 *
 * Hard dependencies (all four are mandatory in mods.toml AND compiled against):
 *  - GeckoLib            : every 3D model + model animation (gauntlet, spikes, shards, tentacles, columns, rings, projectiles)
 *  - PlayerAnimator      : the gauntlet's 7-hit combo body animations and every spell's casting animation
 *  - Iron's Spells       : the Obsidian and Hellfire schools, 10 spells, scrolls, mana, spell power, resistances
 *  - AAA Particles       : Effekseer VFX for every spell, effect and gauntlet attack
 */
@Mod(ObsidianArsenal.MODID)
public class ObsidianArsenal {
    public static final String MODID = "obsidian_arsenal";
    public static final Logger LOGGER = LogUtils.getLogger();

    public static final String[] REQUIRED_MODS = {"geckolib", "playeranimator", "irons_spellbooks", "aaa_particles"};

    public ObsidianArsenal() {
        // Belt and braces: Forge already refuses to load without these (mods.toml), but fail loudly if someone strips it.
        for (String dep : REQUIRED_MODS) {
            if (!ModList.get().isLoaded(dep)) {
                throw new IllegalStateException("[Obsidian Arsenal] Required mod '" + dep + "' is not installed.");
            }
        }

        IEventBus bus = FMLJavaModLoadingContext.get().getModEventBus();
        ModAttributes.ATTRIBUTES.register(bus);   // must precede schools (schools reference these attributes)
        ModSchools.SCHOOLS.register(bus);
        ModItems.ITEMS.register(bus);
        ModItems.TABS.register(bus);
        ModEntities.ENTITIES.register(bus);
        ModEffects.EFFECTS.register(bus);
        ModParticles.PARTICLES.register(bus);
        ModSpells.SPELLS.register(bus);
        bus.addListener(ModAttributes::addToEntities);
        bus.addListener(this::commonSetup);
    }

    private void commonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(ModNetwork::register);
    }

    public static ResourceLocation id(String path) {
        return new ResourceLocation(MODID, path);
    }
}

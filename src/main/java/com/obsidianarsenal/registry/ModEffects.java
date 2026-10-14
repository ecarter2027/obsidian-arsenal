package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.effect.AmaterasuEffect;
import com.obsidianarsenal.effect.PurpleLightningEffect;
import net.minecraft.world.effect.MobEffect;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModEffects {
    public static final DeferredRegister<MobEffect> EFFECTS = DeferredRegister.create(ForgeRegistries.MOB_EFFECTS, ObsidianArsenal.MODID);

    public static final RegistryObject<MobEffect> PURPLE_LIGHTNING = EFFECTS.register("purple_lightning", PurpleLightningEffect::new);
    public static final RegistryObject<MobEffect> AMATERASU = EFFECTS.register("amaterasu", AmaterasuEffect::new);
}

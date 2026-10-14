package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import net.minecraft.core.particles.ParticleType;
import net.minecraft.core.particles.SimpleParticleType;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** Full-bright, animated glow particles layered under the 3D models and AAA effects. */
public class ModParticles {
    public static final DeferredRegister<ParticleType<?>> PARTICLES =
            DeferredRegister.create(ForgeRegistries.PARTICLE_TYPES, ObsidianArsenal.MODID);

    /** Red / orange hellfire flame. */
    public static final RegistryObject<SimpleParticleType> HELLFIRE = reg("hellfire_flame");
    /** Black flame with crimson rim (Amaterasu). */
    public static final RegistryObject<SimpleParticleType> AMATERASU = reg("amaterasu_flame");
    /** Violet flame (Indra's Arrow, purple fire). */
    public static final RegistryObject<SimpleParticleType> PURPLE_FLAME = reg("purple_flame");
    /** Small bright purple spark (replaces dust). */
    public static final RegistryObject<SimpleParticleType> PURPLE_SPARK = reg("purple_spark");
    /** Bigger, darker violet glow. */
    public static final RegistryObject<SimpleParticleType> DEEP_PURPLE_SPARK = reg("deep_purple_spark");
    /** Small bright red ember. */
    public static final RegistryObject<SimpleParticleType> RED_SPARK = reg("red_spark");
    /** Hot red/white plasma spark. */
    public static final RegistryObject<SimpleParticleType> PLASMA_SPARK = reg("plasma_spark");
    /** Soft violet wisp spiralling into a black hole. */
    public static final RegistryObject<SimpleParticleType> VOID_WISP = reg("void_wisp");
    /** Jagged purple-white electric crackle (lightning). */
    public static final RegistryObject<SimpleParticleType> PURPLE_LIGHTNING = reg("purple_lightning");

    private static RegistryObject<SimpleParticleType> reg(String name) {
        // 'true' = always render, even past the vanilla particle distance limiter
        return PARTICLES.register(name, () -> new SimpleParticleType(true));
    }
}

package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.spell.ArsenalSpells;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/** The ten spells, registered into Iron's spell registry (they appear in Iron's scrolls, spellbooks, Scroll Forge, loot). */
public class ModSpells {
    public static final DeferredRegister<AbstractSpell> SPELLS = DeferredRegister.create(SpellRegistry.SPELL_REGISTRY_KEY, ObsidianArsenal.MODID);

    public static final List<RegistryObject<AbstractSpell>> ALL = new ArrayList<>();

    // ---- Obsidian school ----
    public static final RegistryObject<AbstractSpell> OBSIDIAN_SPIKE_EXPLOSION = reg("obsidian_spike_explosion", ArsenalSpells.ObsidianSpikeExplosion::new);
    public static final RegistryObject<AbstractSpell> OBSIDIAN_STORM           = reg("obsidian_storm", ArsenalSpells.ObsidianStorm::new);
    public static final RegistryObject<AbstractSpell> OBSIDIAN_TENTACLES       = reg("obsidian_tentacles", ArsenalSpells.ObsidianTentacles::new);
    public static final RegistryObject<AbstractSpell> PURPLE_LIGHTNING_CLOAK   = reg("purple_lightning_cloak", ArsenalSpells.PurpleLightningCloak::new);
    public static final RegistryObject<AbstractSpell> OBSIDIAN_SHATTER         = reg("obsidian_shatter", ArsenalSpells.ObsidianShatter::new);
    public static final RegistryObject<AbstractSpell> INDRAS_ARROW             = reg("indras_arrow", ArsenalSpells.IndrasArrow::new);
    // ---- Hellfire school ----
    public static final RegistryObject<AbstractSpell> COLUMNS_OF_HELL          = reg("columns_of_hell", ArsenalSpells.ColumnsOfHell::new);
    public static final RegistryObject<AbstractSpell> FLAMING_JUDGEMENT        = reg("flaming_judgement", ArsenalSpells.FlamingJudgement::new);
    public static final RegistryObject<AbstractSpell> GYROSCOPE                = reg("gyroscope", ArsenalSpells.Gyroscope::new);
    public static final RegistryObject<AbstractSpell> AMATERASU                = reg("amaterasu", ArsenalSpells.Amaterasu::new);
    // ---- Plasma school ----
    public static final RegistryObject<AbstractSpell> PLASMA_BEAM              = reg("plasma_beam", ArsenalSpells.PlasmaBeam::new);
    public static final RegistryObject<AbstractSpell> BLACK_HOLE               = reg("black_hole", ArsenalSpells.BlackHole::new);
    // ---- added in 1.0.12 ----
    public static final RegistryObject<AbstractSpell> OBSIDIAN_WALL            = reg("obsidian_wall", ArsenalSpells.ObsidianWall::new);
    public static final RegistryObject<AbstractSpell> INFERNAL_NOVA            = reg("infernal_nova", ArsenalSpells.InfernalNova::new);
    public static final RegistryObject<AbstractSpell> PLASMA_BARRAGE           = reg("plasma_barrage", ArsenalSpells.PlasmaBarrage::new);
    // ---- added in 1.0.13 ----
    public static final RegistryObject<AbstractSpell> KIRIN                    = reg("kirin", ArsenalSpells.Kirin::new);
    public static final RegistryObject<AbstractSpell> OBSIDIAN_LANCE_RAIN      = reg("obsidian_lance_rain", ArsenalSpells.ObsidianLanceRain::new);
    public static final RegistryObject<AbstractSpell> SHARD_STEP               = reg("shard_step", ArsenalSpells.ShardStep::new);
    public static final RegistryObject<AbstractSpell> GLASS_PRISON             = reg("glass_prison", ArsenalSpells.GlassPrison::new);
    public static final RegistryObject<AbstractSpell> KAGUTSUCHI               = reg("kagutsuchi", ArsenalSpells.Kagutsuchi::new);
    public static final RegistryObject<AbstractSpell> HELLBRAND                = reg("hellbrand", ArsenalSpells.Hellbrand::new);
    public static final RegistryObject<AbstractSpell> INFERNAL_CHAINS          = reg("infernal_chains", ArsenalSpells.InfernalChains::new);
    public static final RegistryObject<AbstractSpell> RAILGUN                  = reg("railgun", ArsenalSpells.Railgun::new);
    public static final RegistryObject<AbstractSpell> PLASMA_BLINK             = reg("plasma_blink", ArsenalSpells.PlasmaBlink::new);
    public static final RegistryObject<AbstractSpell> ION_STORM                = reg("ion_storm", ArsenalSpells.IonStorm::new);

    // ---- added in 1.0.13: the Abyssal Ray and the three Erasure variants ----
    public static final RegistryObject<AbstractSpell> ABYSSAL_RAY              = reg("abyssal_ray", ArsenalSpells.AbyssalRay::new);
    public static final RegistryObject<AbstractSpell> ERASURE                  = reg("erasure", ArsenalSpells.Erasure::new);
    public static final RegistryObject<AbstractSpell> ERASURE_VOLLEY           = reg("erasure_volley", ArsenalSpells.ErasureVolley::new);
    public static final RegistryObject<AbstractSpell> ERASURE_GRASP            = reg("erasure_grasp", ArsenalSpells.ErasureGrasp::new);

    private static RegistryObject<AbstractSpell> reg(String name, Supplier<AbstractSpell> supplier) {
        RegistryObject<AbstractSpell> ro = SPELLS.register(name, supplier);
        ALL.add(ro);
        return ro;
    }
}

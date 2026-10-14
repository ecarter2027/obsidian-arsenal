package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import io.redspace.ironsspellbooks.api.registry.SchoolRegistry;
import io.redspace.ironsspellbooks.api.spells.SchoolType;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.tags.ItemTags;
import net.minecraft.tags.TagKey;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * Two brand-new schools registered straight into Iron's school registry. Iron's then handles:
 * school colors in tooltips, the school's scroll model (models/item/scroll_<school>.json), Scroll Forge focus
 * (item tag <school>_focus), spell power/resist attributes, and the school damage type used by SpellDamageSource.
 */
public class ModSchools {
    public static final DeferredRegister<SchoolType> SCHOOLS =
            DeferredRegister.create(SchoolRegistry.SCHOOL_REGISTRY_KEY, ObsidianArsenal.MODID);

    public static final ResourceLocation OBSIDIAN_RESOURCE = ObsidianArsenal.id("obsidian");
    public static final ResourceLocation HELLFIRE_RESOURCE = ObsidianArsenal.id("hellfire");

    public static final TagKey<Item> OBSIDIAN_FOCUS = ItemTags.create(ObsidianArsenal.id("obsidian_focus"));
    public static final ResourceLocation PLASMA_RESOURCE = ObsidianArsenal.id("plasma");
    public static final TagKey<Item> PLASMA_FOCUS = ItemTags.create(ObsidianArsenal.id("plasma_focus"));
    public static final ResourceKey<DamageType> PLASMA_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, ObsidianArsenal.id("plasma_magic"));

    public static final TagKey<Item> HELLFIRE_FOCUS = ItemTags.create(ObsidianArsenal.id("hellfire_focus"));

    public static final ResourceKey<DamageType> OBSIDIAN_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, ObsidianArsenal.id("obsidian_magic"));
    public static final ResourceKey<DamageType> HELLFIRE_MAGIC = ResourceKey.create(Registries.DAMAGE_TYPE, ObsidianArsenal.id("hellfire_magic"));

    public static final RegistryObject<SchoolType> OBSIDIAN = SCHOOLS.register("obsidian", () -> new SchoolType(
            OBSIDIAN_RESOURCE,
            OBSIDIAN_FOCUS,
            Component.translatable("school.obsidian_arsenal.obsidian").withStyle(Style.EMPTY.withColor(0xA040FF)),
            LazyOptional.of(ModAttributes.OBSIDIAN_SPELL_POWER::get),
            LazyOptional.of(ModAttributes.OBSIDIAN_MAGIC_RESIST::get),
            LazyOptional.of(() -> SoundEvents.DEEPSLATE_BREAK),
            OBSIDIAN_MAGIC));

    public static final RegistryObject<SchoolType> HELLFIRE = SCHOOLS.register("hellfire", () -> new SchoolType(
            HELLFIRE_RESOURCE,
            HELLFIRE_FOCUS,
            Component.translatable("school.obsidian_arsenal.hellfire").withStyle(Style.EMPTY.withColor(0xE01010)),
            LazyOptional.of(ModAttributes.HELLFIRE_SPELL_POWER::get),
            LazyOptional.of(ModAttributes.HELLFIRE_MAGIC_RESIST::get),
            LazyOptional.of(() -> SoundEvents.BLAZE_SHOOT),
            HELLFIRE_MAGIC));

    public static final RegistryObject<SchoolType> PLASMA = SCHOOLS.register("plasma", () -> new SchoolType(
            PLASMA_RESOURCE,
            PLASMA_FOCUS,
            Component.translatable("school.obsidian_arsenal.plasma").withStyle(Style.EMPTY.withColor(0xFF2A3C)),
            LazyOptional.of(ModAttributes.PLASMA_SPELL_POWER::get),
            LazyOptional.of(ModAttributes.PLASMA_MAGIC_RESIST::get),
            LazyOptional.of(() -> SoundEvents.BEACON_POWER_SELECT),
            PLASMA_MAGIC));
}

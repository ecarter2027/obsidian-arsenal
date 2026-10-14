package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import io.redspace.ironsspellbooks.api.attribute.MagicRangedAttribute;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * Iron's-style spell power / resistance attributes for the two new schools. They are {@link MagicRangedAttribute}s,
 * so Iron's treats them exactly like its own (tooltips, syncing, Curios/armor modifiers, spell power math).
 */
public class ModAttributes {
    public static final DeferredRegister<Attribute> ATTRIBUTES = DeferredRegister.create(ForgeRegistries.ATTRIBUTES, ObsidianArsenal.MODID);

    public static final RegistryObject<Attribute> OBSIDIAN_SPELL_POWER = power("obsidian");
    public static final RegistryObject<Attribute> OBSIDIAN_MAGIC_RESIST = resist("obsidian");
    public static final RegistryObject<Attribute> HELLFIRE_SPELL_POWER = power("hellfire");
    public static final RegistryObject<Attribute> HELLFIRE_MAGIC_RESIST = resist("hellfire");
    public static final RegistryObject<Attribute> PLASMA_SPELL_POWER = power("plasma");
    public static final RegistryObject<Attribute> PLASMA_MAGIC_RESIST = resist("plasma");

    private static RegistryObject<Attribute> power(String school) {
        return ATTRIBUTES.register(school + "_spell_power", () -> new MagicRangedAttribute(
                "attribute." + ObsidianArsenal.MODID + "." + school + "_spell_power", 1.0D, -100, 100).setSyncable(true));
    }

    private static RegistryObject<Attribute> resist(String school) {
        return ATTRIBUTES.register(school + "_magic_resist", () -> new MagicRangedAttribute(
                "attribute." + ObsidianArsenal.MODID + "." + school + "_magic_resist", 1.0D, -100, 100).setSyncable(true));
    }

    /** Same approach Iron's uses for its own school attributes: every living entity type gets them. */
    public static void addToEntities(EntityAttributeModificationEvent e) {
        e.getTypes().forEach(type -> ATTRIBUTES.getEntries().forEach(attr -> e.add(type, attr.get())));
    }
}

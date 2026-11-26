package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianArmorItem;
import com.obsidianarsenal.item.ObsidianGauntletItem;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.spells.ISpellContainer;
import io.redspace.ironsspellbooks.registries.ItemRegistry;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

public class ModItems {
    public static final DeferredRegister<Item> ITEMS = DeferredRegister.create(ForgeRegistries.ITEMS, ObsidianArsenal.MODID);
    public static final DeferredRegister<CreativeModeTab> TABS = DeferredRegister.create(Registries.CREATIVE_MODE_TAB, ObsidianArsenal.MODID);

    private static Item.Properties armorProps() { return new Item.Properties().fireResistant(); }

    public static final RegistryObject<Item> OBSIDIAN_HELMET = ITEMS.register("obsidian_helmet", () -> new ObsidianArmorItem(ArmorItem.Type.HELMET, armorProps()));
    public static final RegistryObject<Item> OBSIDIAN_CHESTPLATE = ITEMS.register("obsidian_chestplate", () -> new ObsidianArmorItem(ArmorItem.Type.CHESTPLATE, armorProps()));
    public static final RegistryObject<Item> OBSIDIAN_LEGGINGS = ITEMS.register("obsidian_leggings", () -> new ObsidianArmorItem(ArmorItem.Type.LEGGINGS, armorProps()));
    public static final RegistryObject<Item> OBSIDIAN_BOOTS = ITEMS.register("obsidian_boots", () -> new ObsidianArmorItem(ArmorItem.Type.BOOTS, armorProps()));

    public static final RegistryObject<Item> OBSIDIAN_GAUNTLET = ITEMS.register("obsidian_gauntlet",
            () -> new ObsidianGauntletItem(new Item.Properties().fireResistant().stacksTo(1)));

    public static final RegistryObject<CreativeModeTab> MAIN_TAB = TABS.register("main", () ->
            CreativeModeTab.builder()
                    .title(Component.translatable("itemGroup." + ObsidianArsenal.MODID))
                    .icon(() -> new ItemStack(OBSIDIAN_GAUNTLET.get()))
                    .displayItems((params, out) -> {
                        out.accept(OBSIDIAN_GAUNTLET.get());
                        out.accept(OBSIDIAN_HELMET.get());
                        out.accept(OBSIDIAN_CHESTPLATE.get());
                        out.accept(OBSIDIAN_LEGGINGS.get());
                        out.accept(OBSIDIAN_BOOTS.get());
                        // Every spell's scroll (Iron's own Scroll item, max level), grouped by school - Obsidian, Hellfire,
                        // Plasma - and within a school from the commonest spell to the rarest. Iron's Scrolls tab lists every level.
                        java.util.List<String> schools = java.util.List.of(ModSchools.OBSIDIAN_RESOURCE.toString(),
                                ModSchools.HELLFIRE_RESOURCE.toString(), ModSchools.PLASMA_RESOURCE.toString());
                        java.util.List<AbstractSpell> sorted = new java.util.ArrayList<>();
                        for (RegistryObject<AbstractSpell> spell : ModSpells.ALL) sorted.add(spell.get());
                        sorted.sort(java.util.Comparator
                                .comparingInt((AbstractSpell s) -> {
                                    int i = schools.indexOf(s.getSchoolType().getId().toString());
                                    return i < 0 ? schools.size() : i;
                                })
                                .thenComparing(s -> s.getMinRarity()));
                        for (AbstractSpell s : sorted) {
                            ItemStack scroll = new ItemStack(ItemRegistry.SCROLL.get());
                            ISpellContainer.createScrollContainer(s, s.getMaxLevel(), scroll).save(scroll);
                            out.accept(scroll);
                        }
                    })
                    .build());
}

package com.obsidianarsenal.registry;

import com.obsidianarsenal.ObsidianArsenal;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ArmorMaterial;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.crafting.Ingredient;

/**
 * Better than netherite on every stat:
 *  durability x50 (netherite x37) | defense 4/7/9/4 (3/6/8/3) | toughness 4 (3) | knockback resist 0.15 (0.1) | enchantability 22 (15)
 */
public enum ModArmorMaterial implements ArmorMaterial {
    OBSIDIAN;

    private static final int[] BASE_DURABILITY = {13, 15, 16, 11};  // boots, legs, chest, helmet
    private static final int[] DEFENSE = {4, 7, 9, 4};

    private static int idx(ArmorItem.Type t) {
        return switch (t) { case BOOTS -> 0; case LEGGINGS -> 1; case CHESTPLATE -> 2; case HELMET -> 3; };
    }

    @Override public int getDurabilityForType(ArmorItem.Type type) { return BASE_DURABILITY[idx(type)] * 50; }
    @Override public int getDefenseForType(ArmorItem.Type type) { return DEFENSE[idx(type)]; }
    @Override public int getEnchantmentValue() { return 22; }
    @Override public SoundEvent getEquipSound() { return SoundEvents.ARMOR_EQUIP_NETHERITE; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.OBSIDIAN); }
    @Override public String getName() { return ObsidianArsenal.MODID + ":obsidian"; }
    @Override public float getToughness() { return 4.0F; }
    @Override public float getKnockbackResistance() { return 0.15F; }
}

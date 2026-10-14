package com.obsidianarsenal.item;

import net.minecraft.world.item.Items;
import net.minecraft.world.item.Tier;
import net.minecraft.world.item.crafting.Ingredient;

/** Better than netherite (2031 uses / speed 9 / +4 dmg): 3500 uses, speed 13, +5 dmg. */
public enum ObsidianTier implements Tier {
    INSTANCE;

    @Override public int getUses() { return 3500; }
    @Override public float getSpeed() { return 13.0F; }
    @Override public float getAttackDamageBonus() { return 5.0F; }
    @Override public int getLevel() { return 4; }
    @Override public int getEnchantmentValue() { return 22; }
    @Override public Ingredient getRepairIngredient() { return Ingredient.of(Items.OBSIDIAN); }
}

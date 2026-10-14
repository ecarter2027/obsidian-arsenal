package com.obsidianarsenal.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.obsidianarsenal.registry.ModArmorMaterial;
import com.obsidianarsenal.registry.ModAttributes;
import io.redspace.ironsspellbooks.api.registry.AttributeRegistry;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.core.object.PlayState;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * Obsidian armor: better-than-netherite vanilla stats (see {@link ModArmorMaterial}) plus Iron's Spells stats per
 * piece: +max mana and +Obsidian / +Hellfire spell power, so the set is built for this mod's two schools.
 */
public class ObsidianArmorItem extends ArmorItem implements GeoItem {
    private static final RawAnimation IDLE = RawAnimation.begin().thenLoop("animation.obsidian_armor.idle");
    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);
    private static final Map<Type, UUID> UUIDS = new EnumMap<>(Map.of(
            Type.HELMET, UUID.fromString("3a5e1c2e-7c55-4d7e-9e0f-1b0f4f6a0a01"),
            Type.CHESTPLATE, UUID.fromString("3a5e1c2e-7c55-4d7e-9e0f-1b0f4f6a0a02"),
            Type.LEGGINGS, UUID.fromString("3a5e1c2e-7c55-4d7e-9e0f-1b0f4f6a0a03"),
            Type.BOOTS, UUID.fromString("3a5e1c2e-7c55-4d7e-9e0f-1b0f4f6a0a04")));

    private Multimap<Attribute, AttributeModifier> merged;

    public ObsidianArmorItem(Type type, Properties props) {
        super(ModArmorMaterial.OBSIDIAN, type, props);
    }

    /** Built lazily: attributes are registered after items, so they can't be resolved in the constructor. */
    @Override
    public Multimap<Attribute, AttributeModifier> getDefaultAttributeModifiers(EquipmentSlot slot) {
        if (slot != getEquipmentSlot()) return super.getDefaultAttributeModifiers(slot);
        if (merged == null) {
            UUID id = UUIDS.get(getType());
            ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
            b.putAll(super.getDefaultAttributeModifiers(slot));
            b.put(AttributeRegistry.MAX_MANA.get(), new AttributeModifier(id, "Obsidian armor mana", 125, AttributeModifier.Operation.ADDITION));
            b.put(ModAttributes.OBSIDIAN_SPELL_POWER.get(), new AttributeModifier(id, "Obsidian armor power", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
            b.put(ModAttributes.HELLFIRE_SPELL_POWER.get(), new AttributeModifier(id, "Obsidian armor power", 0.10, AttributeModifier.Operation.MULTIPLY_BASE));
            b.put(ModAttributes.HELLFIRE_MAGIC_RESIST.get(), new AttributeModifier(id, "Obsidian armor resist", 0.05, AttributeModifier.Operation.MULTIPLY_BASE));
            merged = b.build();
        }
        return merged;
    }

    // ---------------------------------------------------------------- GeckoLib spine model
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private com.obsidianarsenal.client.render.ObsidianArmorRenderer renderer;

            @Override
            public HumanoidModel<?> getHumanoidArmorModel(LivingEntity living, ItemStack stack, EquipmentSlot slot, HumanoidModel<?> original) {
                if (renderer == null) renderer = new com.obsidianarsenal.client.render.ObsidianArmorRenderer();
                renderer.prepForRender(living, stack, slot, original);
                return renderer;
            }
        });
    }

    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        controllers.add(new AnimationController<>(this, "spines", 0, state -> state.setAndContinue(IDLE)));
    }

    @Override
    public AnimatableInstanceCache getAnimatableInstanceCache() {
        return cache;
    }
}

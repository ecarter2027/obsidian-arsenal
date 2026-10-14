package com.obsidianarsenal.item;

import com.google.common.collect.ImmutableMultimap;
import com.google.common.collect.Multimap;
import com.obsidianarsenal.client.render.GauntletRenderer;
import com.obsidianarsenal.entity.GrappleHookEntity;
import com.obsidianarsenal.registry.ModAttributes;
import net.minecraft.ChatFormatting;
import net.minecraft.client.renderer.BlockEntityWithoutLevelRenderer;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.InteractionResultHolder;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.*;
import net.minecraft.world.item.context.UseOnContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraftforge.client.extensions.common.IClientItemExtensions;
import net.minecraftforge.common.ToolAction;
import net.minecraftforge.common.ToolActions;
import org.jetbrains.annotations.Nullable;
import software.bernie.geckolib.animatable.GeoItem;
import software.bernie.geckolib.animatable.SingletonGeoAnimatable;
import software.bernie.geckolib.core.animatable.instance.AnimatableInstanceCache;
import software.bernie.geckolib.core.animation.AnimatableManager;
import software.bernie.geckolib.core.animation.AnimationController;
import software.bernie.geckolib.core.animation.RawAnimation;
import software.bernie.geckolib.util.GeckoLibUtil;

import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;

/**
 * The Obsidian Gauntlet (GeckoLib item).
 *  - MINING mode (default): pickaxe + axe + shovel + hoe, beyond netherite tier.
 *  - COMBAT mode: left click runs the 7-hit combo (PlayerAnimator body anims + GeckoLib gauntlet anims + AAA VFX).
 * Sneak + right click toggles mode. While held it also boosts Obsidian spell power (Iron's attribute).
 */
public class ObsidianGauntletItem extends DiggerItem implements GeoItem {
    public static final String TAG_COMBAT = "Combat";
    private static final UUID MAGIC_UUID = UUID.fromString("9b3c7e1a-2f44-4a8e-a2c0-6d5f0b1e7c21");

    private final AnimatableInstanceCache cache = GeckoLibUtil.createInstanceCache(this);

    public ObsidianGauntletItem(Properties props) {
        super(3.0F, -2.4F, ObsidianTier.INSTANCE, BlockTags.MINEABLE_WITH_PICKAXE, props);
        SingletonGeoAnimatable.registerSyncedAnimatable(this);
    }

    public static boolean isCombat(ItemStack s) {
        return s.hasTag() && s.getTag().getBoolean(TAG_COMBAT);
    }

    // ------------------------------------------------------------------------------------------------ multitool
    private static boolean mineable(BlockState s) {
        return s.is(BlockTags.MINEABLE_WITH_PICKAXE) || s.is(BlockTags.MINEABLE_WITH_AXE)
                || s.is(BlockTags.MINEABLE_WITH_SHOVEL) || s.is(BlockTags.MINEABLE_WITH_HOE);
    }

    @Override
    public float getDestroySpeed(ItemStack stack, BlockState state) {
        return !isCombat(stack) && mineable(state) ? ObsidianTier.INSTANCE.getSpeed() : 1.0F;
    }

    @Override
    public boolean isCorrectToolForDrops(ItemStack stack, BlockState state) {
        return !isCombat(stack) && mineable(state);
    }

    @Override
    public boolean canPerformAction(ItemStack stack, ToolAction action) {
        if (isCombat(stack)) return false;
        return ToolActions.DEFAULT_PICKAXE_ACTIONS.contains(action) || ToolActions.DEFAULT_AXE_ACTIONS.contains(action)
                || ToolActions.DEFAULT_SHOVEL_ACTIONS.contains(action) || ToolActions.DEFAULT_HOE_ACTIONS.contains(action);
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(EquipmentSlot slot, ItemStack stack) {
        if (slot != EquipmentSlot.MAINHAND) return super.getAttributeModifiers(slot, stack);
        boolean combat = isCombat(stack);
        ImmutableMultimap.Builder<Attribute, AttributeModifier> b = ImmutableMultimap.builder();
        b.put(Attributes.ATTACK_DAMAGE, new AttributeModifier(BASE_ATTACK_DAMAGE_UUID, "Weapon modifier", combat ? 9.0 : 4.0, AttributeModifier.Operation.ADDITION));
        b.put(Attributes.ATTACK_SPEED, new AttributeModifier(BASE_ATTACK_SPEED_UUID, "Weapon modifier", combat ? -2.0 : -2.6, AttributeModifier.Operation.ADDITION));
        b.put(ModAttributes.OBSIDIAN_SPELL_POWER.get(), new AttributeModifier(MAGIC_UUID, "Gauntlet power", 0.15, AttributeModifier.Operation.MULTIPLY_BASE));
        return b.build();
    }

    /** Mining-mode right click: strip / path / till by delegating to the vanilla netherite tools. */
    @Override
    public InteractionResult useOn(UseOnContext ctx) {
        Player player = ctx.getPlayer();
        ItemStack stack = ctx.getItemInHand();
        if (player != null && player.isShiftKeyDown()) {
            toggleMode(player, stack);
            return InteractionResult.sidedSuccess(ctx.getLevel().isClientSide);
        }
        if (isCombat(stack)) return InteractionResult.PASS;
        for (Item tool : new Item[]{Items.NETHERITE_AXE, Items.NETHERITE_SHOVEL, Items.NETHERITE_HOE}) {
            UseOnContext c = new UseOnContext(ctx.getLevel(), player, ctx.getHand(), new ItemStack(tool),
                    new BlockHitResult(ctx.getClickLocation(), ctx.getClickedFace(), ctx.getClickedPos(), ctx.isInside()));
            InteractionResult r = tool.useOn(c);
            if (r.consumesAction()) {
                if (player != null) stack.hurtAndBreak(1, player, p -> p.broadcastBreakEvent(ctx.getHand()));
                return r;
            }
        }
        return InteractionResult.PASS;
    }

    @Override
    public InteractionResultHolder<ItemStack> use(Level level, Player player, InteractionHand hand) {
        ItemStack stack = player.getItemInHand(hand);
        if (player.isShiftKeyDown()) {
            toggleMode(player, stack);
            return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
        }
        // grappling hook: right-click fires (80 blocks), right-click again lets go
        if (level instanceof ServerLevel sl) {
            GrappleHookEntity existing = activeHook(sl, player);
            if (existing != null) existing.release();
            else {
                GrappleHookEntity hook = GrappleHookEntity.fire(sl, player);
                player.getPersistentData().putInt(HOOK_TAG, hook.getId());
                player.getCooldowns().addCooldown(this, 8);
            }
        }
        return InteractionResultHolder.sidedSuccess(stack, level.isClientSide);
    }

    private static final String HOOK_TAG = "OA_GrappleHook";

    @Nullable
    private static GrappleHookEntity activeHook(ServerLevel sl, Player player) {
        if (!player.getPersistentData().contains(HOOK_TAG)) return null;
        return sl.getEntity(player.getPersistentData().getInt(HOOK_TAG)) instanceof GrappleHookEntity h && h.isAlive() && h.getOwner() == player ? h : null;
    }

    private void toggleMode(Player player, ItemStack stack) {
        boolean combat = !isCombat(stack);
        stack.getOrCreateTag().putBoolean(TAG_COMBAT, combat);
        if (player.level() instanceof ServerLevel sl) {
            sl.playSound(null, player.blockPosition(), SoundEvents.ANVIL_PLACE, SoundSource.PLAYERS, 0.7f, combat ? 0.6f : 1.2f);
            player.displayClientMessage(Component.translatable(combat ? "message.obsidian_arsenal.combat_mode" : "message.obsidian_arsenal.mining_mode")
                    .withStyle(combat ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE), true);
            triggerAnim(player, GeoItem.getOrAssignId(stack, sl), "main", combat ? "mode_combat" : "mode_mining");
        }
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        boolean c = isCombat(stack);
        tooltip.add(Component.translatable(c ? "tooltip.obsidian_arsenal.gauntlet.mode_combat" : "tooltip.obsidian_arsenal.gauntlet.mode_mining")
                .withStyle(c ? ChatFormatting.DARK_RED : ChatFormatting.LIGHT_PURPLE));
        tooltip.add(Component.translatable("tooltip.obsidian_arsenal.gauntlet.toggle").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable("tooltip.obsidian_arsenal.gauntlet.grapple").withStyle(ChatFormatting.GRAY));
        tooltip.add(Component.translatable(c ? "tooltip.obsidian_arsenal.gauntlet.combat" : "tooltip.obsidian_arsenal.gauntlet.mining").withStyle(ChatFormatting.GRAY));
    }

    // ------------------------------------------------------------------------------------------------ GeckoLib
    @Override
    public void registerControllers(AnimatableManager.ControllerRegistrar controllers) {
        AnimationController<ObsidianGauntletItem> main = new AnimationController<>(this, "main", 0,
                state -> state.setAndContinue(RawAnimation.begin().thenLoop("animation.obsidian_gauntlet.idle")));
        main.triggerableAnim("mode_combat", RawAnimation.begin().thenPlay("animation.obsidian_gauntlet.mode_combat"));
        main.triggerableAnim("mode_mining", RawAnimation.begin().thenPlay("animation.obsidian_gauntlet.mode_mining"));
        for (int i = 1; i <= 7; i++)
            main.triggerableAnim("attack_" + i, RawAnimation.begin().thenPlay("animation.obsidian_gauntlet.attack_" + i));
        controllers.add(main);
    }

    @Override public AnimatableInstanceCache getAnimatableInstanceCache() { return cache; }

    /** Forge client hook -> GeckoLib item renderer (in hand, first person, GUI, dropped, item frames). */
    @Override
    public void initializeClient(Consumer<IClientItemExtensions> consumer) {
        consumer.accept(new IClientItemExtensions() {
            private GauntletRenderer renderer;
            @Override
            public BlockEntityWithoutLevelRenderer getCustomRenderer() {
                if (renderer == null) renderer = new GauntletRenderer();
                return renderer;
            }
        });
    }
}

package com.obsidianarsenal.efcompat.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.efcompat.ObsidianEFCompat;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ArmorItem;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod.EventBusSubscriber;
import net.minecraftforge.registries.ForgeRegistries;
import yesman.epicfight.api.client.forgeevent.PatchedRenderersEvent;
import yesman.epicfight.api.client.model.SkinnedMesh;
import yesman.epicfight.api.utils.math.OpenMatrix4f;
import yesman.epicfight.client.renderer.patched.entity.PatchedLivingEntityRenderer;
import yesman.epicfight.client.renderer.patched.layer.PatchedLayer;
import yesman.epicfight.client.renderer.patched.layer.WearableItemLayer;
import yesman.epicfight.client.world.capabilites.entitypatch.player.AbstractClientPlayerPatch;

/**
 * Epic Fight draws Obsidian Arsenal's GeckoLib armor from a mesh it converts once (WearableItemLayer), with the base
 * texture only, so the purple seams, veins and eye slits lose their glow in battle mode. This draws the same cached
 * mesh again, posed by the same joints, with the armor's glowmask in the emissive "eyes" render type (black adds
 * nothing, so only the purple parts light up).
 */
@EventBusSubscriber(modid = ObsidianEFCompat.MODID, value = Dist.CLIENT, bus = EventBusSubscriber.Bus.MOD)
public final class ArmorGlowLayer extends PatchedLayer<AbstractClientPlayer, AbstractClientPlayerPatch<AbstractClientPlayer>,
        PlayerModel<AbstractClientPlayer>, RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>>> {
    private static final ResourceLocation GLOWMASK = new ResourceLocation("obsidian_arsenal", "textures/item/armor/obsidian_armor_glowmask.png");

    @SuppressWarnings({"unchecked", "rawtypes"})
    @SubscribeEvent
    public static void register(PatchedRenderersEvent.Modify event) {
        if (event.get(EntityType.PLAYER) instanceof PatchedLivingEntityRenderer renderer) renderer.addCustomLayer(new ArmorGlowLayer());
    }

    @Override
    protected void renderLayer(AbstractClientPlayerPatch<AbstractClientPlayer> patch, AbstractClientPlayer player,
                               RenderLayer<AbstractClientPlayer, PlayerModel<AbstractClientPlayer>> layer, PoseStack poseStack,
                               MultiBufferSource buffers, int light, OpenMatrix4f[] poses, float bob, float yRot, float xRot,
                               float partialTick) {
        boolean firstPerson = patch.isFirstPerson();
        for (EquipmentSlot slot : EquipmentSlot.values()) {
            if (slot.getType() != EquipmentSlot.Type.ARMOR) continue;
            if (firstPerson && (slot == EquipmentSlot.HEAD || slot == EquipmentSlot.CHEST)) continue;   // as Epic Fight's own armor layer
            ItemStack stack = player.getItemBySlot(slot);
            if (!(stack.getItem() instanceof ArmorItem armor) || armor.getEquipmentSlot() != slot) continue;
            ResourceLocation id = ForgeRegistries.ITEMS.getKey(stack.getItem());
            if (id == null || !"obsidian_arsenal".equals(id.getNamespace())) continue;
            SkinnedMesh mesh = WearableItemLayer.getCachedModel(stack.getItem());
            if (mesh == null) continue;   // converted on the armor layer's first draw; glows from the next frame on
            mesh.draw(poseStack, buffers, RenderType.eyes(GLOWMASK), LightTexture.FULL_BRIGHT, 1.0F, 1.0F, 1.0F, 1.0F,
                    OverlayTexture.NO_OVERLAY, patch.getArmature(), poses);
        }
    }
}

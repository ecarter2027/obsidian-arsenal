package com.obsidianarsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.item.ObsidianGauntletItem;
import com.obsidianarsenal.network.ModNetwork;
import com.obsidianarsenal.registry.ModEffects;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.player.PlayerRenderer;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.InputEvent;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, value = Dist.CLIENT)
public class ClientForgeEvents {

    /** Gauntlet in combat mode: left click sends a combo request instead of a vanilla swing. */
    @SubscribeEvent
    public static void onInteract(InputEvent.InteractionKeyMappingTriggered e) {
        if (!e.isAttack()) return;
        LocalPlayer p = Minecraft.getInstance().player;
        if (p == null) return;
        ItemStack s = p.getMainHandItem();
        if (s.getItem() instanceof ObsidianGauntletItem && ObsidianGauntletItem.isCombat(s)) {
            e.setCanceled(true);
            e.setSwingHand(false);
            ModNetwork.CHANNEL.sendToServer(new ModNetwork.AttackC2S());
        }
    }

    // ------------------------------------------------------------------ Purple Lightning: purple afterimage trail
    private static final int GHOST_LIFE = 14;
    private static final List<Ghost> GHOSTS = new ArrayList<>();

    private static class Ghost {
        AbstractClientPlayer player;
        double x, y, z;
        float bodyYaw, headYaw, headPitch, limb, limbSpeed;
        boolean crouch;
        int life = GHOST_LIFE;
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent e) {
        if (e.phase != TickEvent.Phase.END) return;
        ClientAnim.tick();
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) { GHOSTS.clear(); return; }
        if (mc.isPaused()) return;
        GHOSTS.removeIf(g -> --g.life <= 0);
        for (Player pl : mc.level.players()) {
            if (!(pl instanceof AbstractClientPlayer ap) || !ap.hasEffect(ModEffects.PURPLE_LIGHTNING.get())) continue;
            if (ap.position().distanceToSqr(ap.xo, ap.yo, ap.zo) < 0.02) continue;
            if (GHOSTS.size() > 160) break;
            Ghost g = new Ghost();
            g.player = ap;
            g.x = ap.getX(); g.y = ap.getY(); g.z = ap.getZ();
            g.bodyYaw = ap.yBodyRot;
            g.headYaw = Mth.wrapDegrees(ap.yHeadRot - ap.yBodyRot);
            g.headPitch = ap.getXRot();
            g.limb = ap.walkAnimation.position();
            g.limbSpeed = ap.walkAnimation.speed();
            g.crouch = ap.isCrouching();
            GHOSTS.add(g);
        }
    }

    @SubscribeEvent
    public static void onRenderLevel(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES || GHOSTS.isEmpty()) return;
        Minecraft mc = Minecraft.getInstance();
        PoseStack ps = e.getPoseStack();
        Vec3 cam = e.getCamera().getPosition();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        for (Ghost g : GHOSTS) {
            if (g.player == mc.player && mc.options.getCameraType().isFirstPerson() && g.life > GHOST_LIFE - 3) continue;
            if (!(mc.getEntityRenderDispatcher().getRenderer(g.player) instanceof PlayerRenderer pr)) continue;
            PlayerModel<AbstractClientPlayer> m = pr.getModel();
            ResourceLocation skin = g.player.getSkinTextureLocation();
            float alpha = 0.55f * g.life / GHOST_LIFE;
            ps.pushPose();
            ps.translate(g.x - cam.x, g.y - cam.y, g.z - cam.z);
            ps.mulPose(Axis.YP.rotationDegrees(180f - g.bodyYaw));
            ps.scale(-1f, -1f, 1f);
            ps.translate(0f, -1.501f, 0f);
            m.setAllVisible(true);
            m.young = false; m.riding = false; m.crouching = g.crouch; m.attackTime = 0f;
            m.setupAnim(g.player, g.limb, g.limbSpeed, g.player.tickCount, g.headYaw, g.headPitch);
            m.renderToBuffer(ps, buf.getBuffer(RenderType.entityTranslucent(skin)), 15728880, OverlayTexture.NO_OVERLAY, 0.62f, 0.2f, 1.0f, alpha);
            ps.popPose();
        }
        buf.endBatch();
    }
}

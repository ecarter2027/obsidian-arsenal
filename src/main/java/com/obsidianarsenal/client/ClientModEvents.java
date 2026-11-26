package com.obsidianarsenal.client;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.client.render.BlackHoleRenderer;
import com.obsidianarsenal.client.render.ConstructRenderer;
import com.obsidianarsenal.client.render.PlasmaBeamRenderer;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import net.minecraft.client.renderer.ShaderInstance;
import net.minecraftforge.client.event.RegisterShadersEvent;
import java.io.IOException;
import com.obsidianarsenal.client.render.HellfireStreamRenderer;
import com.obsidianarsenal.client.render.ProjectileGeoRenderer;
import com.obsidianarsenal.entity.ConstructEntity;
import com.obsidianarsenal.registry.ModEntities;
import com.obsidianarsenal.registry.ModParticles;
import dev.kosmx.playerAnim.api.layered.IAnimation;
import dev.kosmx.playerAnim.api.layered.ModifierLayer;
import dev.kosmx.playerAnim.minecraftApi.PlayerAnimationFactory;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.client.event.RegisterParticleProvidersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;

@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public class ClientModEvents {

    /** The gauntlet's grappling hook (default H). */
    public static final net.minecraft.client.KeyMapping GRAPPLE_KEY = new net.minecraft.client.KeyMapping(
            "key.obsidian_arsenal.grapple", com.mojang.blaze3d.platform.InputConstants.Type.KEYSYM,
            org.lwjgl.glfw.GLFW.GLFW_KEY_H, "key.categories.obsidian_arsenal");

    @SubscribeEvent
    public static void registerKeys(net.minecraftforge.client.event.RegisterKeyMappingsEvent e) {
        e.register(GRAPPLE_KEY);
    }

    @SubscribeEvent
    public static void clientSetup(FMLClientSetupEvent e) {
        // Our own PlayerAnimator layer for the gauntlet combo (priority above Iron's casting layer so combo wins).
        e.enqueueWork(() -> PlayerAnimationFactory.ANIMATION_DATA_FACTORY.registerFactory(
                ClientAnim.LAYER, 1100, player -> new ModifierLayer<IAnimation>()));
    }

    @SubscribeEvent
    public static void registerRenderers(EntityRenderersEvent.RegisterRenderers e) {
        for (ConstructEntity.Kind k : ConstructEntity.Kind.values())
            e.registerEntityRenderer(ModEntities.CONSTRUCTS.get(k).get(), ctx -> new ConstructRenderer(ctx, k));
        e.registerEntityRenderer(ModEntities.SPELL_VISUAL.get(), com.obsidianarsenal.client.render.SpellVisualRenderer::new);
        e.registerEntityRenderer(ModEntities.OBSIDIAN_ORB.get(), ctx -> new ProjectileGeoRenderer<>(ctx, "obsidian_orb", false));
        e.registerEntityRenderer(ModEntities.INDRA_ARROW.get(), ctx -> new ProjectileGeoRenderer<>(ctx, "indra_arrow", true));
        e.registerEntityRenderer(ModEntities.HELLFIRE_STREAM.get(), HellfireStreamRenderer::new);
        e.registerEntityRenderer(ModEntities.PLASMA_BEAM.get(), PlasmaBeamRenderer::new);
        e.registerEntityRenderer(ModEntities.BLACK_HOLE.get(), BlackHoleRenderer::new);
        e.registerEntityRenderer(ModEntities.GRAPPLE_HOOK.get(), com.obsidianarsenal.client.render.GrappleHookRenderer::new);
        e.registerEntityRenderer(ModEntities.VOID_RAY.get(), com.obsidianarsenal.client.render.VoidRayRenderer::new);
        e.registerEntityRenderer(ModEntities.ERASURE_ORB.get(), com.obsidianarsenal.client.render.ErasureOrbRenderer::new);
    }

    @SubscribeEvent
    public static void registerParticles(RegisterParticleProvidersEvent e) {
        // v27: every particle is a crisp SharpParticle, styled per type (tools/gen_fx.py -> FxList)
        for (com.obsidianarsenal.registry.FxList.Entry fx : com.obsidianarsenal.registry.FxList.ALL)
            e.registerSpriteSet(ModParticles.BY_NAME.get(fx.name()).get(), SharpParticle.provider(fx.style()));
    }

    /** Core shader for the black hole's gravitational-lensing distortion. */
    @SubscribeEvent
    public static void registerShaders(RegisterShadersEvent e) {
        try {
            e.registerShader(new ShaderInstance(e.getResourceProvider(), ObsidianArsenal.id("black_hole_lens"), DefaultVertexFormat.POSITION_TEX),
                    shader -> BlackHoleLensRenderer.lensShader = shader);
        } catch (IOException ex) {
            ObsidianArsenal.LOGGER.error("Could not load the black hole lens shader; black holes will render without distortion", ex);
        }
    }
}

package com.obsidianarsenal.client.render;

import com.mojang.blaze3d.platform.GlStateManager;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.VertexFormat;
import com.obsidianarsenal.ObsidianArsenal;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.resources.ResourceLocation;
import org.lwjgl.opengl.GL14;

/**
 * Additive light that doesn't write depth ({@link #glow}): layers of it build up into bright light, and a solid
 * layer drawn after it inside the glow (the Abyssal Ray's black core, the Erasure orb) stays black. Colours are
 * premultiplied: the vertex colour IS the light added (alpha is ignored).
 */
public final class OaRenderTypes extends RenderType {
    public static final ResourceLocation WHITE = ObsidianArsenal.id("textures/entity/flat_white.png");
    public static final ResourceLocation SHEATH = ObsidianArsenal.id("textures/entity/void_sheath.png");

    private OaRenderTypes(String name, VertexFormat format, VertexFormat.Mode mode, int bufferSize,
                          boolean affectsCrumbling, boolean sortOnUpload, Runnable setup, Runnable clear) {
        super(name, format, mode, bufferSize, affectsCrumbling, sortOnUpload, setup, clear);
    }

    /** Plain additive blending that also resets the blend equation (particle libraries can leave it changed). */
    private static final TransparencyStateShard ADDITIVE = new TransparencyStateShard("obsidian_arsenal_additive", () -> {
        RenderSystem.enableBlend();
        RenderSystem.blendEquation(GL14.GL_FUNC_ADD);
        RenderSystem.blendFunc(GlStateManager.SourceFactor.ONE, GlStateManager.DestFactor.ONE);
    }, () -> {
        RenderSystem.disableBlend();
        RenderSystem.defaultBlendFunc();
    });

    public static RenderType glow(ResourceLocation texture) {
        return create("obsidian_arsenal_glow", DefaultVertexFormat.NEW_ENTITY, VertexFormat.Mode.QUADS, 256, false, true,
                CompositeState.builder()
                        .setShaderState(RENDERTYPE_ENERGY_SWIRL_SHADER)
                        .setTextureState(new TextureStateShard(texture, false, false))
                        .setTransparencyState(ADDITIVE)
                        .setCullState(NO_CULL)
                        .setLightmapState(LIGHTMAP)
                        .setOverlayState(OVERLAY)
                        .setWriteMaskState(COLOR_WRITE)
                        .createCompositeState(false));
    }
}

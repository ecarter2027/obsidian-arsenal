package com.obsidianarsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import org.jetbrains.annotations.Nullable;

/**
 * Lets another mod draw the Purple Lightning afterimages itself. Obsidian EF Compat sets {@link #impl} so a player
 * Epic Fight is rendering leaves afterimages of Epic Fight's own mesh in its real pose; without it (or for players
 * Epic Fight doesn't draw) the vanilla player model is used.
 */
public final class GhostHook {
    public interface Impl {
        /** A snapshot of this player's current pose, or null to fall back to the vanilla model. */
        @Nullable Object capture(AbstractClientPlayer player);

        /** Draws a snapshot; the pose stack is already at the afterimage's position (relative to the camera). */
        void render(Object snapshot, AbstractClientPlayer player, PoseStack poseStack, MultiBufferSource buffers, float r, float g, float b, float alpha);
    }

    @Nullable public static Impl impl;

    private GhostHook() {}
}

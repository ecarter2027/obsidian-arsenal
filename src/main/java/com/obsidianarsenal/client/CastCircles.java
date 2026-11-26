package com.obsidianarsenal.client;

import com.mojang.blaze3d.vertex.PoseStack;
import com.obsidianarsenal.ObsidianArsenal;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.capabilities.magic.SyncedSpellData;
import io.redspace.ironsspellbooks.player.ClientMagicData;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * Magic circles around anyone channelling or charging one of this mod's spells (Iron's synced casting state): a circle
 * on the ground under them and one at their hands facing where they aim. The beam spells (Plasma Beam, Flaming
 * Judgement) stack three more along the beam that it fires through, growing as they go; the charged shots (Indra's
 * Arrow, Railgun, Black Hole) draw a large aiming circle; Kirin and Railgun raise one over the caster's head too.
 * Circles fade in over the first ticks of a cast.
 */
@Mod.EventBusSubscriber(modid = ObsidianArsenal.MODID, value = Dist.CLIENT)
public final class CastCircles {
    private static final Map<Integer, Integer> SINCE = new HashMap<>();   // entity id -> tick its cast was first seen

    private CastCircles() {}

    @SubscribeEvent
    public static void render(RenderLevelStageEvent e) {
        if (e.getStage() != RenderLevelStageEvent.Stage.AFTER_PARTICLES) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null) return;
        float pt = e.getPartialTick();
        Vec3 cam = e.getCamera().getPosition();
        PoseStack ps = e.getPoseStack();
        MultiBufferSource.BufferSource buf = mc.renderBuffers().bufferSource();
        boolean any = false;
        for (Player p : mc.level.players()) {
            SyncedSpellData d = ClientMagicData.getSyncedSpellData(p);
            String id = d.isCasting() ? d.getCastingSpellId() : null;
            if (id == null || !id.startsWith(ObsidianArsenal.MODID + ":") || p.distanceToSqr(cam) > 96 * 96) {
                SINCE.remove(p.getId());
                continue;
            }
            AbstractSpell spell = SpellRegistry.getSpell(id);
            int school = MagicCircle.school(spell.getSchoolType().getId().toString());
            String name = id.substring(id.indexOf(':') + 1);
            int since = SINCE.computeIfAbsent(p.getId(), k -> p.tickCount);
            float age = p.tickCount - since + pt;
            float in = Math.min(1f, age / 6f);
            float spin = (p.tickCount + pt) * 0.06f;
            Vec3 feet = p.getPosition(pt).subtract(cam);
            Vec3 look = p.getViewVector(pt);
            Vec3 hands = p.getEyePosition(pt).subtract(cam).add(look.scale(0.95)).add(0, -0.25, 0);
            boolean beam = name.equals("plasma_beam") || name.equals("flaming_judgement");
            boolean charged = name.equals("indras_arrow") || name.equals("railgun") || name.equals("black_hole");
            ps.pushPose();
            MagicCircle.draw(ps, buf, school, feet.add(0, 0.03, 0), new Vec3(0, 1, 0), 1.6 * in + 0.4, spin, 0.85f * in);
            MagicCircle.draw(ps, buf, school, hands, look, (charged ? 1.1 : 0.7) * in, spin * 1.5f, 0.9f * in);
            if (beam) {
                for (int k = 0; k < 3; k++) {
                    double dist = 2.0 + k * 1.8;
                    Vec3 at = p.getEyePosition(pt).subtract(cam).add(look.scale(dist)).add(0, -0.25, 0);
                    MagicCircle.draw(ps, buf, school, at, look, (0.9 + k * 0.35) * in, spin * (k % 2 == 0 ? -1.2f : 1.2f), 0.75f * in);
                }
            }
            if (name.equals("kirin") || name.equals("railgun")) {
                MagicCircle.draw(ps, buf, school, feet.add(0, p.getBbHeight() + 0.8, 0), new Vec3(0, 1, 0), 1.3 * in, -spin * 2, 0.85f * in);
            }
            ps.popPose();
            any = true;
        }
        if (any) buf.endBatch();
    }
}

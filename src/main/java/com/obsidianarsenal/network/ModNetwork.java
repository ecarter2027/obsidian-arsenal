package com.obsidianarsenal.network;

import com.obsidianarsenal.ObsidianArsenal;
import com.obsidianarsenal.client.ClientAnim;
import com.obsidianarsenal.combat.GauntletCombat;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

import java.util.function.Supplier;

public final class ModNetwork {
    private static final String VERSION = "4";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ObsidianArsenal.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, AttackC2S.class, AttackC2S::encode, AttackC2S::decode, AttackC2S::handle);
        CHANNEL.registerMessage(id++, PlayAnimS2C.class, PlayAnimS2C::encode, PlayAnimS2C::decode, PlayAnimS2C::handle);
        CHANNEL.registerMessage(id++, CastGlowS2C.class, CastGlowS2C::encode, CastGlowS2C::decode, CastGlowS2C::handle);
        CHANNEL.registerMessage(id++, GrappleC2S.class, GrappleC2S::encode, GrappleC2S::decode, GrappleC2S::handle);
        CHANNEL.registerMessage(id++, CutsceneS2C.class, CutsceneS2C::encode, CutsceneS2C::decode, CutsceneS2C::handle);
    }

    /** Play a PlayerAnimator animation (assets/obsidian_arsenal/player_animation/*.json) on this player for everyone tracking them. */
    public static void playAnim(ServerPlayer player, String name) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayAnimS2C(player.getId(), name));
    }

    /** Tint this player's Glowing outline for `ticks` ticks on every client that can see them. */
    public static void castGlow(ServerPlayer player, int rgb, int ticks) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new CastGlowS2C(player.getId(), rgb, ticks));
    }

    public static class CastGlowS2C {
        private final int entityId, rgb, ticks;
        public CastGlowS2C(int entityId, int rgb, int ticks) { this.entityId = entityId; this.rgb = rgb; this.ticks = ticks; }
        public void encode(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeInt(rgb); buf.writeVarInt(ticks); }
        public static CastGlowS2C decode(FriendlyByteBuf buf) { return new CastGlowS2C(buf.readVarInt(), buf.readInt(), buf.readVarInt()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.obsidianarsenal.client.CastGlowClient.set(entityId, rgb, ticks)));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Plays a spell cutscene on this caster's own screen (client/Cutscene). Only players get one. */
    public static void cutscene(net.minecraft.world.entity.LivingEntity caster, String kind, net.minecraft.world.phys.Vec3 target) {
        if (caster instanceof ServerPlayer sp)
            CHANNEL.send(PacketDistributor.PLAYER.with(() -> sp), new CutsceneS2C(kind, caster.getX(), caster.getY(), caster.getZ(), caster.getYRot(), target.x, target.y, target.z));
    }

    public static class CutsceneS2C {
        private final String kind;
        private final double ox, oy, oz, tx, ty, tz;
        private final float yaw;
        public CutsceneS2C(String kind, double ox, double oy, double oz, float yaw, double tx, double ty, double tz) {
            this.kind = kind; this.ox = ox; this.oy = oy; this.oz = oz; this.yaw = yaw; this.tx = tx; this.ty = ty; this.tz = tz;
        }
        public void encode(FriendlyByteBuf b) { b.writeUtf(kind); b.writeDouble(ox); b.writeDouble(oy); b.writeDouble(oz); b.writeFloat(yaw); b.writeDouble(tx); b.writeDouble(ty); b.writeDouble(tz); }
        public static CutsceneS2C decode(FriendlyByteBuf b) { return new CutsceneS2C(b.readUtf(), b.readDouble(), b.readDouble(), b.readDouble(), b.readFloat(), b.readDouble(), b.readDouble(), b.readDouble()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> com.obsidianarsenal.client.Cutscene.play(kind,
                    new net.minecraft.world.phys.Vec3(ox, oy, oz), yaw, new net.minecraft.world.phys.Vec3(tx, ty, tz))));
            ctx.get().setPacketHandled(true);
        }
    }

    /** Grapple key pressed: fire or release the gauntlet's grappling hook. */
    public static class GrappleC2S {
        public void encode(FriendlyByteBuf buf) {}
        public static GrappleC2S decode(FriendlyByteBuf buf) { return new GrappleC2S(); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) com.obsidianarsenal.item.ObsidianGauntletItem.grapple(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class AttackC2S {
        public void encode(FriendlyByteBuf buf) {}
        public static AttackC2S decode(FriendlyByteBuf buf) { return new AttackC2S(); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> {
                ServerPlayer p = ctx.get().getSender();
                if (p != null) GauntletCombat.request(p);
            });
            ctx.get().setPacketHandled(true);
        }
    }

    public static class PlayAnimS2C {
        private final int entityId;
        private final String name;
        public PlayAnimS2C(int entityId, String name) { this.entityId = entityId; this.name = name; }
        public void encode(FriendlyByteBuf buf) { buf.writeVarInt(entityId); buf.writeUtf(name); }
        public static PlayAnimS2C decode(FriendlyByteBuf buf) { return new PlayAnimS2C(buf.readVarInt(), buf.readUtf()); }
        public void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> ClientAnim.play(entityId, name)));
            ctx.get().setPacketHandled(true);
        }
    }
}

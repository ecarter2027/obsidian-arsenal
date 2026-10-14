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
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            ObsidianArsenal.id("main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private ModNetwork() {}

    public static void register() {
        int id = 0;
        CHANNEL.registerMessage(id++, AttackC2S.class, AttackC2S::encode, AttackC2S::decode, AttackC2S::handle);
        CHANNEL.registerMessage(id++, PlayAnimS2C.class, PlayAnimS2C::encode, PlayAnimS2C::decode, PlayAnimS2C::handle);
    }

    /** Play a PlayerAnimator animation (assets/obsidian_arsenal/player_animation/*.json) on this player for everyone tracking them. */
    public static void playAnim(ServerPlayer player, String name) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new PlayAnimS2C(player.getId(), name));
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

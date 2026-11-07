package com.obsidianarsenal.efcompat;

import java.util.function.Supplier;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/** Tells the dashing player and everyone tracking them where an Obsidian Stretch goes, so all of them draw the stretch. */
public final class StretchNet {
    private static final String VERSION = "1";
    public static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ObsidianEFCompat.MODID, "main"), () -> VERSION, VERSION::equals, VERSION::equals);

    private StretchNet() {}

    public static void register() {
        CHANNEL.registerMessage(0, Stretch.class, Stretch::encode, Stretch::decode, Stretch::handle);
    }

    public static void send(ServerPlayer player, Vec3 start, Vec3 dir, float dist) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> player), new Stretch(player.getId(), start, dir, dist));
    }

    public record Stretch(int entity, Vec3 start, Vec3 dir, float dist) {
        void encode(FriendlyByteBuf buf) {
            buf.writeVarInt(entity);
            buf.writeDouble(start.x); buf.writeDouble(start.y); buf.writeDouble(start.z);
            buf.writeFloat((float) dir.x); buf.writeFloat((float) dir.y); buf.writeFloat((float) dir.z);
            buf.writeFloat(dist);
        }

        static Stretch decode(FriendlyByteBuf buf) {
            return new Stretch(buf.readVarInt(), new Vec3(buf.readDouble(), buf.readDouble(), buf.readDouble()),
                    new Vec3(buf.readFloat(), buf.readFloat(), buf.readFloat()), buf.readFloat());
        }

        void handle(Supplier<NetworkEvent.Context> ctx) {
            ctx.get().enqueueWork(() -> DistExecutor.unsafeRunWhenOn(Dist.CLIENT,
                    () -> () -> com.obsidianarsenal.efcompat.client.StretchFx.start(entity, start, dir, dist)));
            ctx.get().setPacketHandled(true);
        }
    }
}

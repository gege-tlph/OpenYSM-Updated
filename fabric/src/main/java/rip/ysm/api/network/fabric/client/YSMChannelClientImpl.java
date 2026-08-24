package rip.ysm.api.network.fabric.client;

import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.network.protocol.Packet;
import net.minecraft.resources.Identifier;
import rip.ysm.api.network.fabric.YSMChannelImpl;
import rip.ysm.api.network.fabric.YSMPayload;

public final class YSMChannelClientImpl {

    private YSMChannelClientImpl() {
    }

    public static void init(Identifier channelId) {
        ClientPlayNetworking.registerGlobalReceiver(YSMPayload.TYPE, (payload, context) ->
                YSMChannelImpl.dispatch(payload.toBuf(), new ClientPacketContext(context.client(), context.player().connection.getConnection())));
    }

    public static void sendToServer(net.minecraft.network.FriendlyByteBuf buf) {
        ClientPlayNetworking.send(YSMPayload.fromBuf(buf));
    }

    public static Packet<?> toServerboundPacket(net.minecraft.network.FriendlyByteBuf buf) {
        return new net.minecraft.network.protocol.common.ServerboundCustomPayloadPacket(YSMPayload.fromBuf(buf));
    }
}

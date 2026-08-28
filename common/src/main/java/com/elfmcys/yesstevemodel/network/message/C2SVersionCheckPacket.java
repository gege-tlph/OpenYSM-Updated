package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.elfmcys.yesstevemodel.capability.ModelInfoCapability;
import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import dev.architectury.utils.GameInstance;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import rip.ysm.api.network.PacketContext;

public class C2SVersionCheckPacket {

    private final String version;

    public C2SVersionCheckPacket() {
        this(NetworkHandler.VERSION);
    }

    public C2SVersionCheckPacket(String version) {
        this.version = version;
    }

    public static C2SVersionCheckPacket decode(FriendlyByteBuf buf) {
        return new C2SVersionCheckPacket(buf.readUtf());
    }

    public static void encode(C2SVersionCheckPacket message, FriendlyByteBuf buf) {
        buf.writeUtf(message.version);
    }

    public static void handle(C2SVersionCheckPacket message, PacketContext ctx) {
        ServerPlayer sender = ctx.getSender();
        if (sender != null && NetworkHandler.setChannelVersion(ctx.getConnection(), message.version)) {
            ServerModelManager.validatePlayerModel(sender);
            ModelInfoCapability.get(sender).ifPresent(cap -> {
                cap.setMandatory(false);
                cap.stopAnimation(sender);
            });
            AuthModelsCapability.get(sender).ifPresent(cap -> {
                NetworkHandler.sendToClientPlayer(new S2CSyncAuthModelsPacket(cap.getAuthModels()), sender);
            });
            StarModelsCapability.get(sender).ifPresent(cap -> {
                NetworkHandler.sendToClientPlayer(new S2CSyncStarModelsPacket(cap.getStarModels()), sender);
            });
            syncOnlineRosterTo(sender);
            ServerModelManager.requestPlayerAuth(sender, null);
        }
    }

    /**
     * Send a client that has just completed the version handshake the current model of
     * every other online player, and mark its own model dirty so the players already
     * online learn about it.
     *
     * <p>Model state otherwise only travels on change: {@code CapabilityEvent#onServerTick}
     * broadcasts a dirty capability with {@code sendToTrackingEntityAndSelf}, and
     * {@code EnterServerEvent} only syncs a joining player to itself. Without this, a player
     * who joins after everyone else sees them all as vanilla until they happen to change
     * model — and re-issuing the same model never fixes it, because an unchanged capability
     * never becomes dirty.
     */
    private static void syncOnlineRosterTo(ServerPlayer sender) {
        MinecraftServer server = GameInstance.getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == sender) {
                continue;
            }
            ModelInfoCapability.get(other).ifPresent(otherCap ->
                    otherCap.createSyncMessage(other, true)
                            .ifPresent(message -> NetworkHandler.sendToClientPlayer(message, sender)));
        }
        ModelInfoCapability.get(sender).ifPresent(ModelInfoCapability::markDirty);
    }
}
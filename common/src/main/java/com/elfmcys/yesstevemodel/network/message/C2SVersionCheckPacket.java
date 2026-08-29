package com.elfmcys.yesstevemodel.network.message;

import com.elfmcys.yesstevemodel.capability.AuthModelsCapability;
import com.elfmcys.yesstevemodel.capability.ModelInfoCapability;
import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.elfmcys.yesstevemodel.model.ServerModelManager;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
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
        // ServerPlayer#level() is typed ServerLevel in 26.1.2, so the owning server comes
        // straight from the packet context - no global lookup needed.
        MinecraftServer server = sender.level().getServer();
        if (server == null) {
            return;
        }
        for (ServerPlayer other : server.getPlayerList().getPlayers()) {
            if (other == sender) {
                continue;
            }
            // fullSync=false. The outgoing message is rebuilt from `other` either way, but
            // fullSync=true additionally calls PlayerStateSynchronizer#buildFullSyncMessage's
            // syncMessage.reset(), which clears that player's *shared* accumulator - the
            // flags, effect amplifiers and molang vars queued by syncEffectAdded and friends
            // that have not been flushed yet. CapabilityEvent#onServerTick can afford the
            // reset because it immediately rebroadcasts to tracking players and self; here we
            // send only to the joiner, so resetting would silently drop everyone else's
            // pending state until something re-dirtied them.
            ModelInfoCapability.get(other).ifPresent(otherCap ->
                    otherCap.createSyncMessage(other, false)
                            .ifPresent(message -> NetworkHandler.sendToClientPlayer(message, sender)));
        }
        ModelInfoCapability.get(sender).ifPresent(ModelInfoCapability::markDirty);
    }
}
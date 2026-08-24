package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.entity.PlayerPreviewEntity;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.network.chat.Component;
import net.minecraft.world.scores.DisplaySlot;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.Scoreboard;
import rip.ysm.compat.touhoulittlemaid.TouhouLittleMaidCompat;
import rip.ysm.compat.gun.swarfare.SWarfareCompat;
import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerArmorLayer;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerElytraLayer;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerItemInHandLayer;
import com.elfmcys.yesstevemodel.client.renderer.layer.CustomPlayerParrotLayer;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoReplacedEntityRenderer;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.MissingTextureAtlasSprite;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Team;
import org.jetbrains.annotations.NotNull;

public class CustomPlayerRenderer extends GeoReplacedEntityRenderer<Player, CustomPlayerEntity, AvatarRenderState> {

    private Identifier currentTexture;
    private Player currentPlayer;

    public CustomPlayerRenderer(EntityRendererProvider.Context context) {
        super(context);
        addLayerRenderer(new CustomPlayerItemInHandLayer(Minecraft.getInstance().gameRenderer.itemInHandRenderer));
        addLayerRenderer(new CustomPlayerElytraLayer(context));
        addLayerRenderer(new CustomPlayerParrotLayer(context));
        addLayerRenderer(new CustomPlayerArmorLayer(context));
    }

    public void render(Player player, AvatarRenderState renderState, float entityYaw, float partialTick, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight) {
        PlayerCapability capability;
        if (SWarfareCompat.isPlayerAiming(player) || (capability = PlayerCapability.get(player).orElse(null)) == null) {
            return;
        }
        this.currentPlayer = player;
        capability.tickModel();
        SpecialPlayerRenderEvent renderEvent = new SpecialPlayerRenderEvent(player, capability, capability.getModelId());
        this.currentTexture = renderEvent.getTextureLocation();
        if (SpecialPlayerRenderEvent.post(renderEvent).isFalse()) {
            return;
        }
        renderEntityWithTexture(capability, renderState, renderEvent.getTextureLocation(), entityYaw, partialTick, poseStack, bufferSource, packedLight);
    }

    @Override
    public boolean shouldShowName(Player entity, double distance) {
        Minecraft minecraft;
        LocalPlayer localPlayer;
        double dDistanceToSqr = distance;
        float nameRenderDistance = entity.isDiscrete() ? 32.0f : 64.0f;
        if (dDistanceToSqr >= nameRenderDistance * nameRenderDistance || (localPlayer = (minecraft = Minecraft.getInstance()).player) == null) {
            return false;
        }
        boolean isVisible = !entity.isInvisibleTo(localPlayer);
        if (entity != localPlayer) {
            Team team = entity.getTeam();
            Team team2 = localPlayer.getTeam();
            if (team != null) {
                switch (team.getNameTagVisibility()) {
                    case ALWAYS:
                        return isVisible;
                    case NEVER:
                        return false;
                    case HIDE_FOR_OTHER_TEAMS:
                        return team2 == null ? isVisible : team.isAlliedTo(team2) && (team.canSeeFriendlyInvisibles() || isVisible);
                    case HIDE_FOR_OWN_TEAM:
                        return team2 == null ? isVisible : !team.isAlliedTo(team2) && isVisible;
                    default:
                        throw new IncompatibleClassChangeError();
                }
            }
        }
        return Minecraft.renderNames() && entity != minecraft.getCameraEntity() && isVisible && !entity.isVehicle();
    }

    @Override
    public @NotNull AvatarRenderState createRenderState() {
        return new AvatarRenderState();
    }

    @NotNull
    public Identifier getTextureLocation(Player player) {
        return this.currentTexture == null ? PlayerCapability.get(player).map((cap) -> cap.getTextureLocation()).orElse(MissingTextureAtlasSprite.getLocation()) : this.currentTexture;
    }

    @Override
    public net.minecraft.resources.Identifier getTextureLocation(net.minecraft.client.renderer.entity.state.AvatarRenderState state) {
        if (this.currentTexture != null) {
            return this.currentTexture;
        }

        Player player = this.currentPlayer;
        if (player == null) {
            player = Minecraft.getInstance().player;
        }
        if (player != null) {
            return PlayerCapability.get(player)
                    .map(PlayerCapability::getTextureLocation)
                    .orElse(MissingTextureAtlasSprite.getLocation());
        }
        return MissingTextureAtlasSprite.getLocation();
    }

    // TODO: port to 1.21.4
//    @Override
//    protected void renderNameTag(AvatarRenderState entityRenderState, Component component, PoseStack poseStack, MultiBufferSource multiBufferSource, int i) {
//        Scoreboard scoreboard;
//        Objective displayObjective;
//        if (PlayerPreviewEntity.isPreviewPlayer(player)) {
//            return;
//        }
//        double dDistanceToSqr = this.entityRenderDispatcher.distanceToSqr(player);
//        poseStack.pushPose();
//        if (dDistanceToSqr < 100.0d && (displayObjective = (scoreboard = player.getScoreboard()).getDisplayObjective(DisplaySlot.BELOW_NAME)) != null) {
//            super.renderNameTag(entityRenderState, Component.literal(Integer.toString(scoreboard.getOrCreatePlayerScore(player, displayObjective).get())).append(" ").append(displayObjective.getDisplayName()), poseStack, multiBufferSource, i);
//            poseStack.translate(0.0d, 0.25875d, 0.0d);
//        }
//        super.renderNameTag(entityRenderState, component, poseStack, multiBufferSource, i);
//        poseStack.popPose();
//    }

    @Override
    public void setupRotations(Player player, AvatarRenderState state, PoseStack poseStack, float ageInTicks, float rotationYaw, float partialTicks, float scale) {
        super.setupRotations(player, state, poseStack, ageInTicks, rotationYaw, partialTicks, scale);
        Entity vehicle = player.getVehicle();
        if (TouhouLittleMaidCompat.isSimplePlanesEntity(vehicle) || TouhouLittleMaidCompat.isImmersiveAircraftEntity(vehicle)) {
            poseStack.translate(0.0d, 0.5d, 0.0d);
        }
    }
}

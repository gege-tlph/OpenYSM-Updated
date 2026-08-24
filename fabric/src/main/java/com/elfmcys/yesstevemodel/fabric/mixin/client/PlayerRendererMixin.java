package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerRenderEvent;
import com.elfmcys.yesstevemodel.client.renderer.EntityRenderStateBindings;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.entity.LivingEntityRenderer;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;

/**
 * @author IzumiiKonata
 * Date: 2026/5/10 23:20
 */
@Mixin(AvatarRenderer.class)
public abstract class PlayerRendererMixin extends LivingEntityRenderer<AbstractClientPlayer, AvatarRenderState, PlayerModel> {

    public PlayerRendererMixin(EntityRendererProvider.Context context, PlayerModel entityModel, float f) {
        super(context, entityModel, f);
    }

    @Override
    public void submit(AvatarRenderState livingEntityRenderState, PoseStack poseStack, net.minecraft.client.renderer.SubmitNodeCollector submitNodeCollector, net.minecraft.client.renderer.state.level.CameraRenderState cameraRenderState) {
        Entity entity = EntityRenderStateBindings.get(livingEntityRenderState);
        if (entity instanceof Player player) {
            if (ReplacePlayerRenderEvent.onRenderPlayerPre(player, livingEntityRenderState, Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true), poseStack, submitNodeCollector, cameraRenderState)) {
                return;
            }
        }

        super.submit(livingEntityRenderState, poseStack, submitNodeCollector, cameraRenderState);
    }
}


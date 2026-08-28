package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.util.CameraUtil;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.player.Player;
import rip.ysm.compat.firstperson.FirstPersonCompat;
import rip.ysm.compat.oculus.OculusCompat;
import rip.ysm.compat.playeranimator.PlayerAnimatorCompat;
import rip.ysm.compat.realcamera.RealCameraCompat;

public class ReplacePlayerRenderEvent {

    private ReplacePlayerRenderEvent() {
    }

    public static boolean onRenderPlayerPre(Player entity, AvatarRenderState renderState, float partialTick, PoseStack poseStack, SubmitNodeCollector collector, CameraRenderState cameraState) {
        if (!YesSteveModel.isAvailable()) {
            return false;
        }
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (entity.equals(localPlayer) && GeneralConfig.DISABLE_SELF_MODEL.get().booleanValue()) {
            return false;
        }
        if ((!entity.equals(localPlayer) && GeneralConfig.DISABLE_OTHER_MODEL.get().booleanValue()) || entity.isSpectator()) {
            return false;
        }
        boolean[] cancelled = {false};
        PlayerCapability.get(entity).ifPresent(cap -> {
            if (cap.isModelActive()) {
                if (!CameraUtil.isFirstPerson(cap)
                        || FirstPersonCompat.isFirstPersonActive()
                        || RealCameraCompat.isActive()
                        || GeneralConfig.DISABLE_EXTERNAL_FP_ANIM.get().booleanValue()
                        || !PlayerAnimatorCompat.isPlayerAnimated(localPlayer)) {
                    cancelled[0] = true;
                    MultiBufferSource.BufferSource bufferSource = Minecraft.getInstance().renderBuffers().bufferSource();
                    RenderContext.enter(collector, cameraState);
                    try {
                        int packedLight = ModelPreviewRenderer.isPreview()
                                ? 15728880
                                : renderState.lightCoords;
                        RendererManager.getPlayerRenderer().render(entity, renderState, entity.getYRot(), ModelPreviewRenderer.isPreview() ? 1.0f : partialTick, poseStack, bufferSource, packedLight);
                    } finally {
                        // The replacement model itself is submitted through the 26.1
                        // collector (IGeoRenderer#renderWithBoneAndRenderType), so it no
                        // longer depends on this flush to reach the framebuffer.
                        //
                        // What still writes immediately into the shared BufferSource are the
                        // not-yet-migrated cosmetic layers (CustomPlayerElytraLayer renders a
                        // vanilla ElytraModel straight into it). Flush those here, or they
                        // are stranded in the batch until something else happens to flush it.
                        // Skip the Oculus shadow pass: flushing there caused shadow-map
                        // culling to intermittently drop player geometry (2026-05-15, 62c4257).
                        if (!OculusCompat.isRenderingShadowPass()) {
                            bufferSource.endBatch();
                        }
                        RenderContext.exit();
                    }
                }
            }
        });
        return cancelled[0];
    }
}



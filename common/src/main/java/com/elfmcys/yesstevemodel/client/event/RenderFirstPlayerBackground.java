package com.elfmcys.yesstevemodel.client.event;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.client.renderer.CustomEntityTranslucentRenderType;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.client.renderer.CustomPlayerRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RendererManager;
import com.elfmcys.yesstevemodel.config.GeneralConfig;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.NativeModelRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import dev.architectury.event.EventResult;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.player.Player;

public class RenderFirstPlayerBackground {
    // 因为RenderHandEvent可有几率会渲染多次，所以为了避免多次渲染，这样设计
    private static boolean currentFrameRendered = false;

    private RenderFirstPlayerBackground() {
    }

    public static void resetFrame() {
        currentFrameRendered = false;
    }

    public static void onRenderHand(PoseStack poseStack, MultiBufferSource multiBufferSource, int packedLight, float partialTick) {
        if (!YesSteveModel.isAvailable()) {
            return;
        }
        if (GeneralConfig.DISABLE_SELF_MODEL.get()) {
            return;
        }
        if (GeneralConfig.DISABLE_SELF_HANDS.get()) {
            return;
        }
        LocalPlayer player = Minecraft.getInstance().player;
        if (player == null || currentFrameRendered) {
            return;
        }
        currentFrameRendered = true;
        PlayerCapability.get(player).ifPresent(cap -> {
            if (!cap.isModelActive()) {
                return;
            }
            String modelId = cap.getModelId();
            ModelAssembly modelAssembly = cap.getModelAssembly();
            if (modelAssembly == null || !modelAssembly.getAnimationBundle().getArmModel().hasCustomLimbs) {
                return;
            }
            CustomPlayerRenderer instance = RendererManager.getPlayerRenderer();
            EventResult result = SpecialPlayerRenderEvent.post(new SpecialPlayerRenderEvent(player, cap, modelId));
            if (result.isFalse()) {
                return;
            }
            Identifier resourceLocationB_ = cap.getTextureLocation();
            int textureIndex = cap.getTextureIndex();
            RenderType renderType = CustomEntityTranslucentRenderType.get(resourceLocationB_);
            if (instance != null) {
                poseStack.pushPose();
                if (Minecraft.getInstance().options.bobView().get()) {
                    applyHandTransform(poseStack, partialTick, player);
                }
                poseStack.translate(0.0d, -1.5d, 0.0d);
                GeoModel armModel = modelAssembly.getAnimationBundle().getArmModel();
                SubmitNodeCollector collector = RenderContext.collector();
                if (collector != null) {
                    // Same first-person pass constraint as HandItemRenderer: submit the
                    // geometry instead of writing it into the shared BufferSource, which
                    // in 26.1.2 leaves most of the arm's faces undrawn.
                    float[] boneTransforms = armModel.getBoneTransformData() == null
                            ? null : armModel.getBoneTransformData().clone();
                    collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                        boolean previousSubmittedDraw = RenderContext.isSubmittedDraw();
                        RenderContext.beginSubmittedDraw();
                        try {
                            NativeModelRenderer.renderMesh(buffer, pose, armModel, boneTransforms, null, textureIndex, 3,
                                    packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, resourceLocationB_);
                        } finally {
                            RenderContext.endSubmittedDraw(previousSubmittedDraw);
                        }
                    });
                } else {
                    VertexConsumer buffer = multiBufferSource.getBuffer(renderType);
                    NativeModelRenderer.renderMesh(buffer, poseStack.last(), armModel, armModel.getBoneTransformData(), null, textureIndex, 3, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f, resourceLocationB_);
                }
                poseStack.popPose();
            }
        });
    }

    private static void applyHandTransform(PoseStack poseStack, float partialTick, Player player) {
        AbstractClientPlayer ap = (AbstractClientPlayer) player;
        float walkPhase = ap.avatarState().getBackwardsInterpolatedWalkDistance(partialTick);
        float fLerp = ap.avatarState().getInterpolatedBob(partialTick);
        poseStack.translate((-Mth.sin(walkPhase * 3.1415927f)) * fLerp * 0.5f, Math.abs(Mth.cos(walkPhase * 3.1415927f) * fLerp), 0.0d);
        poseStack.mulPose(Axis.ZN.rotationDegrees(Mth.sin(walkPhase * 3.1415927f) * fLerp * 3.0f));
        poseStack.mulPose(Axis.XN.rotationDegrees(Math.abs(Mth.cos((walkPhase * 3.1415927f) - 0.2f) * fLerp) * 5.0f));
    }
}

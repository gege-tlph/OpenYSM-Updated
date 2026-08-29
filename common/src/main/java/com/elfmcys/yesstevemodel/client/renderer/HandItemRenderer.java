package com.elfmcys.yesstevemodel.client.renderer;

import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.client.entity.PlayerGeoEntity;
import com.elfmcys.yesstevemodel.client.model.ModelAssembly;
import com.elfmcys.yesstevemodel.event.api.SpecialPlayerRenderEvent;
import com.elfmcys.yesstevemodel.geckolib3.geo.LayerTypeConstants;
import com.elfmcys.yesstevemodel.geckolib3.geo.NativeModelRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;

public class HandItemRenderer {

    private PlayerGeoEntity geoModel = null;

    public void renderHandItem(LocalPlayer localPlayer, ModelAssembly modelAssembly, PlayerCapability capability, HumanoidArm arm, PoseStack poseStack, MultiBufferSource bufferSource, int packedLight, float partialTick) {
        AnimatedGeoModel model;
        if (this.geoModel == null || this.geoModel.getEntity() != localPlayer) {
            this.geoModel = new PlayerGeoEntity(localPlayer, capability);
        }
        this.geoModel.tickModel();
        // The model must be posed for the first-person view, not the world view.
        // AnimatableEntity#processAnimation derives that from
        // ModelPreviewRenderer.isFirstPersonOnRenderThread(), which WorldRendererMixin only
        // sets while renderLevel runs - and in 26.1.2 the hand is rendered outside it. Posing
        // with the world pose here put the body's arm across the camera, which read as an
        // arm with missing faces.
        boolean previousFirstPerson = ModelPreviewRenderer.isFirstPersonModeRaw();
        ModelPreviewRenderer.setFirstPersonMode(true);
        try {
            if (this.geoModel.processAnimationImpl(partialTick, true) == null
                    || (model = this.geoModel.getCurrentModel()) == null) {
                return;
            }
        } finally {
            ModelPreviewRenderer.setFirstPersonMode(previousFirstPerson);
        }
        SpecialPlayerRenderEvent event = new SpecialPlayerRenderEvent(localPlayer, capability, capability.getModelId());
        if (SpecialPlayerRenderEvent.post(event).isFalse()) {
            return;
        }
        Identifier Identifier = event.getTextureLocation() == null ? capability.getTextureLocation() : event.getTextureLocation();
        int textureIndex = event.getTextureLocation() == null ? capability.getTextureIndex() : 0;
        int renderPartMask = arm == HumanoidArm.LEFT ? LayerTypeConstants.TYPE_LEFT : LayerTypeConstants.TYPE_RIGHT;
        RenderType renderType = CustomEntityTranslucentRenderType.get(Identifier);
        poseStack.pushPose();
        if (arm == HumanoidArm.LEFT) {
            poseStack.translate(0.25d, 1.8d, 0.0d);
        } else {
            poseStack.translate(-0.25d, 1.8d, 0.0d);
        }
        poseStack.scale(-1.0f, -1.0f, 1.0f);


        SubmitNodeCollector collector = RenderContext.collector();
        if (collector != null) {
            // 26.1.2 renders the first-person hand as its own pass. Writing the arm
            // straight into the shared BufferSource and flushing it mid-pass produced
            // an arm with missing faces (only some quads survived); the geometry has to
            // be submitted so the engine draws it inside that pass.
            GeoModel geoModel = model.getGeoModel();
            float[] boneParams = model.getMatrixData() == null ? null : model.getMatrixData().clone();
            float[] absPivotData = model.getAbsPivotData() == null ? null : model.getAbsPivotData().clone();
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) -> {
                boolean outerFirstPerson = ModelPreviewRenderer.isFirstPersonModeRaw();
                boolean previousSubmittedDraw = RenderContext.isSubmittedDraw();
                ModelPreviewRenderer.setFirstPersonMode(true);
                RenderContext.beginSubmittedDraw();
                try {
                    NativeModelRenderer.renderMesh(buffer, pose, geoModel, boneParams, absPivotData, textureIndex,
                            renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
                } finally {
                    RenderContext.endSubmittedDraw(previousSubmittedDraw);
                    ModelPreviewRenderer.setFirstPersonMode(outerFirstPerson);
                }
            });
        } else {
            VertexConsumer buffer = bufferSource.getBuffer(renderType);
            NativeModelRenderer.renderMesh(buffer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), textureIndex, renderPartMask, packedLight, OverlayTexture.NO_OVERLAY, 1.0f, 1.0f, 1.0f, 1.0f);
        }
        poseStack.popPose();
    }
}
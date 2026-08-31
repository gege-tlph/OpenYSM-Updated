package com.elfmcys.yesstevemodel.geckolib3.geo;

import com.elfmcys.yesstevemodel.client.renderer.CustomEntityTranslucentRenderType;
import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.elfmcys.yesstevemodel.geckolib3.core.AnimatableEntity;
import com.elfmcys.yesstevemodel.geckolib3.core.util.Color;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.geo.render.built.GeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.EModelRenderCycle;
import com.elfmcys.yesstevemodel.geckolib3.util.IRenderCycle;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.resources.Identifier;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

public interface IGeoRenderer<T extends AnimatableEntity<?>> {
    MultiBufferSource getCurrentRTB();

    default void setCurrentRTB(MultiBufferSource bufferSource) {
    }

    default void renderWithBone(AnimatedGeoModel model, T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer vertexConsumer, int packedLight, int packedOverlayIn, float red, float green, float blue, float alpha) {
        setCurrentRTB(bufferSource);
        renderEarly(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
        renderLate(animatable, poseStack, partialTick, bufferSource, vertexConsumer, packedLight, packedOverlayIn, red, green, blue, alpha);
    }

    default void renderWithBoneAndRenderType(AnimatedGeoModel model, T animatable, float partialTick, RenderType renderType, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, int i, @Nullable VertexConsumer vertexConsumer, int i2, int i3, float f2, float f3, float f4, float f5) {
        Identifier tex = animatable.getTextureLocation();
        SubmitNodeCollector collector = RenderContext.collector();
        if (collector != null && vertexConsumer == null) {
            animatable.resetAnimationState();
            // 26.1.2 renders entities in two phases: submit() only queues nodes into
            // the collector, and geometry is written during a later draw phase. Writing
            // vertices straight into the shared BufferSource here (as the pre-26.1 code
            // did) put them in with whatever matrices and GL state happened to be bound
            // when something else flushed the batch — which is why the replacement model
            // showed up pinned to a fixed screen position while other clients kept seeing
            // the vanilla model.
            //
            // The callback runs after this method returns, so every value it touches must
            // be captured now: by draw time the shared pose buffers have been rewritten
            // for whichever entity was posed next.
            GeoModel geoModel = model.getGeoModel();
            float[] boneParams = model.getMatrixData() == null ? null : model.getMatrixData().clone();
            float[] stateBuffer = model.getAbsPivotData() == null ? null : model.getAbsPivotData().clone();
            boolean previewMode = ModelPreviewRenderer.isPreview();
            boolean extraPlayerMode = ModelPreviewRenderer.isExtraPlayer();
            CameraRenderState camera = RenderContext.camera();
            collector.submitCustomGeometry(poseStack, renderType, (pose, buffer) ->
                    renderSubmittedMesh(buffer, pose, geoModel, boneParams, stateBuffer, i, i2, i3,
                            f2, f3, f4, f5, tex, previewMode, extraPlayerMode, collector, camera));
            setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
            return;
        }
        // No collector: GUI/preview rendering, which draws immediately into its own
        // buffer source and flushes it itself.
        if (vertexConsumer == null) {
            vertexConsumer = bufferSource.getBuffer(renderType);
        }
        animatable.resetAnimationState();
        NativeModelRenderer.renderMesh(vertexConsumer, poseStack.last(), model.getGeoModel(), model.getMatrixData(), model.getAbsPivotData(), i, 0, i2, i3, f2, f3, f4, f5, tex);
        setCurrentModelRenderCycle(EModelRenderCycle.REPEATED);
    }

    /**
     * Draw-phase half of {@link #renderWithBoneAndRenderType}. This runs after the submit
     * phase has already unwound, so it restores the state the mesh renderer reads from
     * thread-wide flags: the preview flags that were set when the model was posed, and the
     * collector itself — {@link RenderContext#isSubmittedDraw()} is what keeps
     * {@link NativeModelRenderer} off the raw-GL fast path, whose captured model-view
     * matrix is meaningless once the draw phase has taken over.
     */
    private static void renderSubmittedMesh(VertexConsumer buffer, PoseStack.Pose pose, GeoModel geoModel,
                                            float[] boneParams, float[] stateBuffer, int textureIndex,
                                            int packedLight, int packedOverlay,
                                            float red, float green, float blue, float alpha,
                                            Identifier tex, boolean previewMode, boolean extraPlayerMode,
                                            SubmitNodeCollector collector, CameraRenderState camera) {
        boolean previousPreview = ModelPreviewRenderer.isPreview();
        boolean previousExtraPlayer = ModelPreviewRenderer.isExtraPlayer();
        SubmitNodeCollector previousCollector = RenderContext.collector();
        CameraRenderState previousCamera = RenderContext.camera();
        boolean previousSubmittedDraw = RenderContext.isSubmittedDraw();
        ModelPreviewRenderer.setPreviewMode(previewMode);
        ModelPreviewRenderer.setExtraPlayerMode(extraPlayerMode);
        RenderContext.enter(collector, camera);
        RenderContext.beginSubmittedDraw();
        try {
            NativeModelRenderer.renderMesh(buffer, pose, geoModel, boneParams, stateBuffer, textureIndex, 0,
                    packedLight, packedOverlay, red, green, blue, alpha, tex);
        } finally {
            RenderContext.endSubmittedDraw(previousSubmittedDraw);
            if (previousCollector == null) {
                RenderContext.exit();
            } else {
                RenderContext.enter(previousCollector, previousCamera);
            }
            ModelPreviewRenderer.setExtraPlayerMode(previousExtraPlayer);
            ModelPreviewRenderer.setPreviewMode(previousPreview);
        }
    }

    default void renderEarly(T animatable, PoseStack poseStack, float partialTick,
                             @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight,
                             int packedOverlayIn, float red, float green, float blue, float alpha) {
        if (getCurrentModelRenderCycle() == EModelRenderCycle.INITIAL) {
            float width = animatable.getHeightScale();
            float height = animatable.getWidthScale();
            poseStack.scale(width, height, width);
        }
    }

    default void renderLate(T animatable, PoseStack poseStack, float partialTick, MultiBufferSource bufferSource,
                            @Nullable VertexConsumer buffer, int packedLight, int packedOverlayIn, float red, float green, float blue,
                            float alpha) {
    }

    @Nullable
    default RenderType getRenderType(Identifier identifier, boolean z, boolean z2, boolean z3) {
        if (z) {
            if (z3) {
                return CustomEntityTranslucentRenderType.get(identifier);
            }
            return RenderTypes.entityCutout(identifier);
        }
        if (z2) {
            return RenderTypes.outline(identifier);
        }
        return null;
    }

    // Every call site (AbstractProjectileRenderer, GeoEntityRenderer, GeoReplacedEntityRenderer)
    // immediately dereferences the result (color.getRed()/getGreen()/...) with no null check;
    // @NotNull documents that contract instead of leaving it implicit (SpotBugs NP_NULL_ON_SOME_PATH
    // flagged the callers because a `default` method with no nullability annotation is fair game
    // for a hypothetical null-returning override, even though none exists).
    @NotNull
    default Color getRenderColor(T animatable, float partialTick, PoseStack poseStack, @Nullable MultiBufferSource bufferSource, @Nullable VertexConsumer buffer, int packedLight) {
        return Color.WHITE;
    }

    @NotNull
    default IRenderCycle getCurrentModelRenderCycle() {
        return EModelRenderCycle.INITIAL;
    }

    default void setCurrentModelRenderCycle(IRenderCycle cycle) {
    }
}

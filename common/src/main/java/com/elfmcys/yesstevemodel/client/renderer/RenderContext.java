package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;

public final class RenderContext {
    private static final ThreadLocal<SubmitNodeCollector> COLLECTOR = new ThreadLocal<>();
    private static final ThreadLocal<CameraRenderState> CAMERA = new ThreadLocal<>();

    private RenderContext() {
    }

    public static void enter(SubmitNodeCollector collector, CameraRenderState cameraState) {
        COLLECTOR.set(collector);
        CAMERA.set(cameraState);
    }

    public static void exit() {
        COLLECTOR.remove();
        CAMERA.remove();
    }

    @Nullable
    public static SubmitNodeCollector collector() {
        return COLLECTOR.get();
    }

    /**
     * True while rendering runs under 26.1's submit/collector pipeline — either in the
     * submit phase itself or inside a deferred {@code submitCustomGeometry} callback.
     *
     * <p>Renderers that bypass the {@link net.minecraft.client.renderer.MultiBufferSource}
     * and issue raw GL with {@code RenderSystem.getModelViewMatrix()} must not run here:
     * the matrix in effect during the deferred draw is not the one the model was posed
     * against, which draws the model at an arbitrary place on screen.
     */
    public static boolean isCollectorActive() {
        return COLLECTOR.get() != null;
    }

    @Nullable
    public static CameraRenderState camera() {
        return CAMERA.get();
    }
}


package com.elfmcys.yesstevemodel.client.renderer;

import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import org.jetbrains.annotations.Nullable;

public final class RenderContext {
    private static final ThreadLocal<SubmitNodeCollector> COLLECTOR = new ThreadLocal<>();
    private static final ThreadLocal<CameraRenderState> CAMERA = new ThreadLocal<>();
    private static final ThreadLocal<Boolean> SUBMITTED_DRAW = ThreadLocal.withInitial(() -> Boolean.FALSE);

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
     * True only while a deferred {@code submitCustomGeometry} callback is running.
     *
     * <p>Renderers that bypass the {@link net.minecraft.client.renderer.MultiBufferSource}
     * and issue raw GL with {@code RenderSystem.getModelViewMatrix()} must not run there:
     * by draw time that matrix no longer describes the model's placement, so the model is
     * drawn at an arbitrary spot on screen.
     *
     * <p>Deliberately narrower than "a collector is set": immediate-mode drawing that merely
     * happens inside the submit phase (first-person arm and held item, GUI previews) still
     * runs against the current matrix and keeps its raw-GL fast path.
     */
    public static boolean isSubmittedDraw() {
        return SUBMITTED_DRAW.get();
    }

    public static void beginSubmittedDraw() {
        SUBMITTED_DRAW.set(Boolean.TRUE);
    }

    public static void endSubmittedDraw(boolean previous) {
        if (previous) {
            SUBMITTED_DRAW.set(Boolean.TRUE);
        } else {
            SUBMITTED_DRAW.remove();
        }
    }

    @Nullable
    public static CameraRenderState camera() {
        return CAMERA.get();
    }
}


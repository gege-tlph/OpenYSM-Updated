package com.elfmcys.yesstevemodel.client.gui;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.gui.navigation.ScreenRectangle;
import net.minecraft.client.gui.render.TextureSetup;
import net.minecraft.client.renderer.state.gui.GuiElementRenderState;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;
import org.joml.Matrix3x2f;
import org.joml.Matrix3x2fc;
import org.joml.Vector2f;

public record RadialSliceRenderState(
        Matrix3x2fc pose,
        float x0, float y0,
        float x1, float y1,
        float x2, float y2,
        float x3, float y3,
        int color,
        @Nullable ScreenRectangle scissorArea,
        @Nullable ScreenRectangle bounds
) implements GuiElementRenderState {

    public static RadialSliceRenderState of(
            Matrix3x2fc currentPose,
            float x0, float y0,
            float x1, float y1,
            float x2, float y2,
            float x3, float y3,
            int color,
            @Nullable ScreenRectangle scissorArea
    ) {
        Matrix3x2f snapshot = new Matrix3x2f(currentPose);
        return new RadialSliceRenderState(snapshot, x0, y0, x1, y1, x2, y2, x3, y3, color, scissorArea, computeBounds(snapshot, x0, y0, x1, y1, x2, y2, x3, y3, scissorArea));
    }

    @Override
    public void buildVertices(VertexConsumer consumer) {
        consumer.addVertexWith2DPose(this.pose, this.x0, this.y0).setColor(this.color);
        consumer.addVertexWith2DPose(this.pose, this.x1, this.y1).setColor(this.color);
        consumer.addVertexWith2DPose(this.pose, this.x2, this.y2).setColor(this.color);
        consumer.addVertexWith2DPose(this.pose, this.x3, this.y3).setColor(this.color);
    }

    @Override
    public RenderPipeline pipeline() {
        return RenderPipelines.GUI;
    }

    @Override
    public TextureSetup textureSetup() {
        return TextureSetup.noTexture();
    }

    private static @Nullable ScreenRectangle computeBounds(
            Matrix3x2fc pose,
            float x0, float y0, float x1, float y1, float x2, float y2, float x3, float y3,
            @Nullable ScreenRectangle scissor
    ) {
        Vector2f v0 = pose.transformPosition(x0, y0, new Vector2f());
        Vector2f v1 = pose.transformPosition(x1, y1, new Vector2f());
        Vector2f v2 = pose.transformPosition(x2, y2, new Vector2f());
        Vector2f v3 = pose.transformPosition(x3, y3, new Vector2f());
        float minX = Math.min(Math.min(v0.x, v1.x), Math.min(v2.x, v3.x));
        float maxX = Math.max(Math.max(v0.x, v1.x), Math.max(v2.x, v3.x));
        float minY = Math.min(Math.min(v0.y, v1.y), Math.min(v2.y, v3.y));
        float maxY = Math.max(Math.max(v0.y, v1.y), Math.max(v2.y, v3.y));
        ScreenRectangle bounds = new ScreenRectangle(Mth.floor(minX), Mth.floor(minY), Mth.ceil(maxX - minX), Mth.ceil(maxY - minY));
        return scissor != null ? bounds.intersection(scissor) : bounds;
    }
}


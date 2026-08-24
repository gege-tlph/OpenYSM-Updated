package com.elfmcys.yesstevemodel.geckolib3.extended;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;

public interface LivingEntityRendererAccessor {
    void tlm$renderNameTag(LivingEntityRenderState state, PoseStack pPoseStack, SubmitNodeCollector collector, CameraRenderState cameraState);
}


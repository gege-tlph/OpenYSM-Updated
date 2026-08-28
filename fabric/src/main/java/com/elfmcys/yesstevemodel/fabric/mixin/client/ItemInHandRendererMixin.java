package com.elfmcys.yesstevemodel.fabric.mixin.client;

import com.elfmcys.yesstevemodel.client.event.ReplacePlayerHandRenderEvent;
import com.elfmcys.yesstevemodel.client.renderer.RenderContext;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.player.AvatarRenderer;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.HumanoidArm;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(AvatarRenderer.class)
public abstract class ItemInHandRendererMixin {

    @Inject(method = "renderRightHand", at = @At("HEAD"), cancellable = true)
    public void ysm$onRenderPlayerArm(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, Identifier identifier, boolean bl, CallbackInfo ci) {
        if (ysm$dispatchHandRender(poseStack, submitNodeCollector, packedLight, HumanoidArm.RIGHT)) {
            ci.cancel();
        }
    }

    @Inject(method = "renderLeftHand", at = @At("HEAD"), cancellable = true)
    public void ysm$onRenderMapHand(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, Identifier identifier, boolean bl, CallbackInfo ci) {
        if (ysm$dispatchHandRender(poseStack, submitNodeCollector, packedLight, HumanoidArm.LEFT)) {
            ci.cancel();
        }
    }

    @Unique
    private boolean ysm$dispatchHandRender(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidArm humanoidArm) {
        Minecraft minecraft = Minecraft.getInstance();
        if (minecraft.player == null) {
            return false;
        }
        MultiBufferSource.BufferSource bufferSource = minecraft.renderBuffers().bufferSource();
        RenderContext.enter(submitNodeCollector, null);
        try {
            boolean cancelled = ReplacePlayerHandRenderEvent.onRenderArm(minecraft.player, humanoidArm, poseStack, bufferSource, packedLight);
            if (cancelled) {
                bufferSource.endBatch();
            }
            return cancelled;
        } finally {
            RenderContext.exit();
        }
    }
}

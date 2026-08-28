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
    private static int ysm$probeLeft = 2;

    @Unique

    private boolean ysm$dispatchHandRender(PoseStack poseStack, SubmitNodeCollector submitNodeCollector, int packedLight, HumanoidArm humanoidArm) {
        if (System.getenv("YSM_ARM_PROBE") != null && ysm$probeLeft-- > 0) {
            // Where does vanilla's own arm actually land? Submit the vanilla ModelPart's
            // pivot through the same pose and print it, then print ours, in one frame.
            AvatarRenderer self = (AvatarRenderer) (Object) this;
            net.minecraft.client.model.player.PlayerModel pm =
                    (net.minecraft.client.model.player.PlayerModel) self.getModel();
            net.minecraft.client.model.geom.ModelPart vanillaArm =
                    humanoidArm == HumanoidArm.RIGHT ? pm.rightArm : pm.leftArm;
            org.joml.Matrix4f base = new org.joml.Matrix4f(poseStack.last().pose());
            // ModelPart pivots are in 1/16 units; vanilla applies that inside compile().
            org.joml.Vector3f vanillaPivot = base.transformPosition(
                    new org.joml.Vector3f(vanillaArm.x / 16f, vanillaArm.y / 16f, vanillaArm.z / 16f));
            org.joml.Matrix4f ours = new org.joml.Matrix4f(poseStack.last().pose());
            ours.translate(humanoidArm == HumanoidArm.LEFT ? 0.25f : -0.25f, 1.8f, 0f);
            ours.scale(-1f, -1f, 1f);
            // Approximate shoulder position in YSM model space (blocks, +Y up).
            org.joml.Vector3f oursPos = ours.transformPosition(new org.joml.Vector3f(0f, 1.4f, 0f));
            System.out.println(String.format(
                    "[ysm-arm-probe] arm=%s vanillaPart=(%.3f,%.3f,%.3f) vanillaWorld=(%.3f,%.3f,%.3f) ours=(%.3f,%.3f,%.3f) delta=(%.3f,%.3f,%.3f)",
                    humanoidArm, vanillaArm.x, vanillaArm.y, vanillaArm.z,
                    vanillaPivot.x, vanillaPivot.y, vanillaPivot.z,
                    oursPos.x, oursPos.y, oursPos.z,
                    vanillaPivot.x - oursPos.x, vanillaPivot.y - oursPos.y, vanillaPivot.z - oursPos.z));
        }
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

package com.elfmcys.yesstevemodel.client.renderer.layer;

import com.elfmcys.yesstevemodel.client.entity.CustomPlayerEntity;
import net.minecraft.client.renderer.entity.HumanoidMobRenderer;
import net.minecraft.client.renderer.entity.state.HumanoidRenderState;
import net.minecraft.client.renderer.entity.state.LivingEntityRenderState;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import rip.ysm.compat.cosmeticarmorreworked.CosmeticArmorHelper;
import com.elfmcys.yesstevemodel.geckolib3.geo.GeoLayerRenderer;
import com.elfmcys.yesstevemodel.geckolib3.geo.animated.AnimatedGeoModel;
import com.elfmcys.yesstevemodel.geckolib3.util.RenderUtils;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.blaze3d.vertex.VertexMultiConsumer;
import net.minecraft.client.model.object.equipment.ElytraModel;
import net.minecraft.client.model.geom.ModelLayers;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.rendertype.RenderType;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.PlayerModelPart;
import net.minecraft.world.item.ItemStack;
import com.mojang.math.Axis;

public class CustomPlayerElytraLayer extends GeoLayerRenderer<CustomPlayerEntity> {

    private static final Identifier WINGS_LOCATION = Identifier.parse("textures/entity/equipment/wings/elytra.png");

    private final ElytraModel elytraModel;

    public CustomPlayerElytraLayer(EntityRendererProvider.Context context) {
        this.elytraModel = new ElytraModel(context.getModelSet().bakeLayer(ModelLayers.ELYTRA));
    }

    @Override
    public void render(AvatarRenderState state, PoseStack poseStack, MultiBufferSource bufferSource, int packedLightIn, CustomPlayerEntity entityLivingBaseIn, float limbSwing, float limbSwingAmount, float partialTick, float ageInTicks, float netHeadYaw, float headPitch) {
        Identifier cloakTextureLocation;
        LivingEntity entity = entityLivingBaseIn.getEntity();
        ItemStack stack = CosmeticArmorHelper.getElytraItem(entity);
        AnimatedGeoModel animatedGeoModel = entityLivingBaseIn.getCurrentModel();
        if (!stack.isEmpty() && animatedGeoModel != null && !animatedGeoModel.elytraBones().isEmpty() && (entity instanceof AbstractClientPlayer abstractClientPlayer)) {
            net.minecraft.world.entity.player.PlayerSkin skin = abstractClientPlayer.getSkin();
            if (skin.elytra() != null) {
                cloakTextureLocation = skin.elytra().texturePath();
            } else if (skin.cape() != null && abstractClientPlayer.isModelPartShown(PlayerModelPart.CAPE)) {
                cloakTextureLocation = skin.cape().texturePath();
            } else {
                cloakTextureLocation = WINGS_LOCATION;
            }
            poseStack.pushPose();
            renderElytra(poseStack, animatedGeoModel);
//            poseStack.translate(0.0d, 1.5d, 0.0d);
            poseStack.mulPose(Axis.ZP.rotationDegrees(180.0f));
            this.elytraModel.setupAnim(state);
            // 26.1.2 removed ItemRenderer#getFoilBuffer, which used to wrap the base buffer with
            // the glint buffer when the stack had foil. Rebuild that here rather than dropping
            // the glint: an enchanted elytra or cape has to keep its shimmer on the replacement
            // model, or it looks wrong next to a vanilla player wearing the same item.
            VertexConsumer elytraBuffer = bufferSource.getBuffer(RenderTypes.armorCutoutNoCull(cloakTextureLocation));
            if (stack.hasFoil()) {
                elytraBuffer = VertexMultiConsumer.create(bufferSource.getBuffer(RenderTypes.armorEntityGlint()), elytraBuffer);
            }
            this.elytraModel.renderToBuffer(poseStack, elytraBuffer, packedLightIn, OverlayTexture.NO_OVERLAY, -1);
            poseStack.popPose();
        }
    }

    public void renderElytra(PoseStack poseStack, AnimatedGeoModel model) {
        RenderUtils.prepMatrixForLocator(poseStack, model.elytraBones());
    }
}

package com.elfmcys.yesstevemodel.mixin.client;

import com.elfmcys.yesstevemodel.client.renderer.ModelPreviewRenderer;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.inventory.InventoryScreen;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin({InventoryScreen.class})
public class InventoryScreenMixin {
    @Inject(at = {@At("HEAD")}, method = {"renderEntityInInventoryFollowsMouse"}, remap = false)
    private static void renderEntityInInventoryFollowsAnglePre(GuiGraphicsExtractor guiGraphics, int i, int j, int k, int l, int m, float f, float g, float h, LivingEntity livingEntity, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(true);
    }

    @Inject(at = {@At("RETURN")}, method = {"renderEntityInInventoryFollowsMouse"}, remap = false)
    private static void renderEntityInInventoryFollowsAnglePost(GuiGraphicsExtractor guiGraphics, int i, int j, int k, int l, int m, float f, float g, float h, LivingEntity livingEntity, CallbackInfo ci) {
        ModelPreviewRenderer.setPreviewMode(false);
    }
}


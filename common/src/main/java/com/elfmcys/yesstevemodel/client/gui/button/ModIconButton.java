package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.capability.PlayerCapability;
import com.elfmcys.yesstevemodel.capability.StarModelsCapability;
import com.elfmcys.yesstevemodel.network.NetworkHandler;
import com.elfmcys.yesstevemodel.network.message.C2SSetStarModelPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class ModIconButton extends FlatColorButton {

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    public ModIconButton(int x, int y) {
        super(x, y, 20, 20, Component.empty(), button -> {
        });
    }

    @Override
    public void renderContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.renderContents(guiGraphics, mouseX, mouseY, partialTick);
        int iconOffsetX = (this.width - 16) / 2;
        int iconOffsetY = (this.height - 16) / 2;
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                    if (cap2.containsModel(cap.getModelId())) {
                        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICON_TEXTURE, getX() + iconOffsetX, getY() + iconOffsetY, 16.0f, 0.0f, 16, 16, 256, 256);
                    } else {
                        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICON_TEXTURE, getX() + iconOffsetX, getY() + iconOffsetY, 0.0f, 0.0f, 16, 16, 256, 256);
                    }
                });
            });
        }
    }

    @Override
    public void onPress(InputWithModifiers input) {
        LocalPlayer localPlayer = Minecraft.getInstance().player;
        if (localPlayer != null) {
            PlayerCapability.get(localPlayer).ifPresent(cap -> {
                StarModelsCapability.get(localPlayer).ifPresent(cap2 -> {
                    String str = cap.getModelId();
                    if (cap2.containsModel(str)) {
                        cap2.removeModel(str);
                        NetworkHandler.sendToServer(C2SSetStarModelPacket.remove(str));
                    } else {
                        cap2.addModel(str);
                        NetworkHandler.sendToServer(C2SSetStarModelPacket.add(str));
                    }
                });
            });
        }
    }
}


package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;

public class IconButton extends FlatColorButton {

    private static final Identifier ICON_TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/icon.png");

    private final int iconU;

    private final int iconV;

    public IconButton(int x, int y, int width, int height, int iconU, int iconV, OnPress onPress) {
        super(x, y, width, height, Component.empty(), onPress);
        this.iconU = iconU;
        this.iconV = iconV;
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, ICON_TEXTURE, getX() + ((this.width - 16) / 2), getY() + ((this.height - 16) / 2), this.iconU, this.iconV, 16, 16, 256, 256);
    }
}




package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.YesSteveModel;
import com.elfmcys.yesstevemodel.client.gui.ISpecialWidget;
import net.fabricmc.api.EnvType;
import net.fabricmc.api.Environment;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractButton;
import net.minecraft.client.gui.narration.NarrationElementOutput;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.Identifier;


import java.util.function.Consumer;

@Environment(EnvType.CLIENT)
public class ConfigCheckBox extends AbstractButton implements ISpecialWidget {

    private static final Identifier TEXTURE = Identifier.fromNamespaceAndPath(YesSteveModel.MOD_ID, "texture/roulette.png");

    private final Consumer<Boolean> consumer2;

    private final Component component2;

    protected boolean isStateTriggered;

    public ConfigCheckBox(int x, int y, int width, Component component, Consumer<Boolean> consumer) {
        super(x, y, width, 12, component);
        this.component2 = component;
        this.consumer2 = consumer;
    }

    public ConfigCheckBox(int x, int y, Component component, Consumer<Boolean> consumer) {
        this(x, y, 115, component, consumer);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        int u = isStateTriggered ? 128 : 0;
        int v = isHovered() ? 12 : 0;
        guiGraphics.blit(net.minecraft.client.renderer.RenderPipelines.GUI_TEXTURED, TEXTURE, getX(), getY(), u, v, this.width, this.height, 256, 256);
        guiGraphics.text(Minecraft.getInstance().font, this.component2, getX() + 14, getY() + 2, -1, false);
    }

    public void setStateTriggered(boolean state) {
        this.isStateTriggered = state;
    }

    public boolean isStateTriggered() {
        return this.isStateTriggered;
    }

    @Override
    public void onPress(InputWithModifiers input) {
        this.isStateTriggered = !this.isStateTriggered;
        this.consumer2.accept(Boolean.valueOf(this.isStateTriggered));
    }

    @Override
    protected void updateWidgetNarration(NarrationElementOutput output) {
        this.defaultButtonNarrationText(output);
    }
}



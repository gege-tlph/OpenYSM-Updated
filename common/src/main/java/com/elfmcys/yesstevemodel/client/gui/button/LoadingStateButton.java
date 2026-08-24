package com.elfmcys.yesstevemodel.client.gui.button;

import com.elfmcys.yesstevemodel.config.LoadingStateConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.PlainTextButton;
import net.minecraft.client.input.InputWithModifiers;
import net.minecraft.network.chat.Component;
import org.jspecify.annotations.NonNull;

public class LoadingStateButton extends Button.Plain {
    public LoadingStateButton(int x, int y) {
        super(x, y, 100, 20, Component.empty(), button -> {
        }, DEFAULT_NARRATION);
    }

    @Override
    protected void extractContents(GuiGraphicsExtractor guiGraphics, int mouseX, int mouseY, float partialTick) {
        super.extractContents(guiGraphics, mouseX, mouseY, partialTick);
        guiGraphics.text(Minecraft.getInstance().font, Component.translatable("gui.yes_steve_model.config.loading_state_position"), getX() + 105, getY() + 6, -1, false);
    }

    public @NonNull Component getMessage() {
        return Component.literal(LoadingStateConfig.LOADING_STATE_POSITION.get().name());
    }

    @Override
    public void onPress(InputWithModifiers input) {
        LoadingStateConfig.Position stateConfig;
        switch (LoadingStateConfig.LOADING_STATE_POSITION.get()) {
            case TOP_LEFT:
                stateConfig = LoadingStateConfig.Position.TOP_CENTER;
                break;
            case TOP_CENTER:
                stateConfig = LoadingStateConfig.Position.TOP_RIGHT;
                break;
            case TOP_RIGHT:
                stateConfig = LoadingStateConfig.Position.BOTTOM_RIGHT;
                break;
            case BOTTOM_RIGHT:
                stateConfig = LoadingStateConfig.Position.BOTTOM_CENTER;
                break;
            case BOTTOM_CENTER:
                stateConfig = LoadingStateConfig.Position.BOTTOM_LEFT;
                break;
            case BOTTOM_LEFT:
                stateConfig = LoadingStateConfig.Position.TOP_LEFT;
                break;
            default:
                throw new IncompatibleClassChangeError();
        }
        LoadingStateConfig.LOADING_STATE_POSITION.set(stateConfig);

    }
}




package com.livajq.mobarmory.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;

public class TextInputScreen extends Screen {
    
    private final Screen parent;
    private final String title;
    private final Consumer<String> onConfirm;
    private final String initialText;
    
    private EditBox box;
    private String errorMessage = null;
    
    public TextInputScreen(Screen parent, String title, String initialText, Consumer<String> onConfirm) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.initialText = initialText;
        this.onConfirm = onConfirm;
    }
    
    @Override
    protected void init() {
        int w = 200;
        int h = 20;
        
        box = new EditBox(this.font, this.width / 2 - w / 2, this.height / 2 - 10, w, h, Component.literal(""));
        box.setValue(initialText == null ? "" : initialText);
        this.addRenderableWidget(box);
        
        this.addRenderableWidget(Button.builder(Component.literal("OK"), btn -> {
            String value = box.getValue().trim();
            
            if (value.isEmpty()) {
                errorMessage = "Name cannot be empty";
                return;
            }

            onConfirm.accept(value);
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 - 50, this.height / 2 + 20, 40, 20).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 + 10, this.height / 2 + 20, 60, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
 
        gfx.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 40, 0xFFFFFF);

        if (errorMessage != null) {
            gfx.drawCenteredString(this.font, errorMessage, this.width / 2, this.height / 2 - 25, 0xFF4444);
        }
        
        super.render(gfx, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) this.minecraft.setScreen(null);
                        else this.minecraft.setScreen(this);
                    },
                    Component.literal("Exit Editor"),
                    Component.literal("Are you sure you want to exit? Unsaved changes will be lost.")
            ));
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

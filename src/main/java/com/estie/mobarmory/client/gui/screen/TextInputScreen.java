package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.MultilineTextBox;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextInputScreen extends Screen {
    
    private final Screen parent;
    private final String title;
    private final Consumer<String> onConfirm;
    private final String initialText;
    private final Predicate<String> validator;
    private final String invalidMessage;
    private final boolean allowEmpty;
    private final boolean multiline;
    
    public TextInputScreen(Screen parent, String title, String initialText, Consumer<String> onConfirm) {
        this(parent, title, initialText, onConfirm, null, null, false, false);
    }
    
    public TextInputScreen(Screen parent, String title, String initialText, Consumer<String> onConfirm,
                           Predicate<String> validator, String invalidMessage, boolean allowEmpty) {
        this(parent, title, initialText, onConfirm, validator, invalidMessage, allowEmpty, false);
    }
    
    public TextInputScreen(Screen parent, String title, String initialText, Consumer<String> onConfirm,
                           Predicate<String> validator, String invalidMessage, boolean allowEmpty, boolean multiline) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.initialText = initialText;
        this.onConfirm = onConfirm;
        this.validator = validator;
        this.invalidMessage = invalidMessage;
        this.allowEmpty = allowEmpty;
        this.multiline = multiline;
    }
    
    private EditBox box;
    private MultilineTextBox multilineBox;
    private String errorMessage = null;
    
    @Override
    protected void init() {
        if (multiline) {
            int w = this.width * 3 / 4, h = this.height - 100;
            multilineBox = new MultilineTextBox(this.font, this.width / 2 - w / 2, 50, w, h, initialText);
            this.addRenderableWidget(multilineBox);
            this.setInitialFocus(multilineBox);
        } else {
            int w = 200, h = 20;
            box = new EditBox(this.font, this.width / 2 - w / 2, this.height / 2 - 10, w, h, Component.literal(""));
            box.setMaxLength(4096);
            box.setValue(initialText == null ? "" : initialText);
            this.addRenderableWidget(box);
        }
        
        int buttonY = this.height - 40;
        this.addRenderableWidget(Button.builder(Component.literal("OK"), btn -> {
            String value = currentValue().trim();
            if (value.isEmpty() && !allowEmpty) { errorMessage = "Cannot be empty"; return; }
            onConfirm.accept(value);
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 - 50, buttonY, 40, 20).build());
        
        this.addRenderableWidget(Button.builder(Component.literal("Cancel"), btn -> {
            this.minecraft.setScreen(parent);
        }).bounds(this.width / 2 + 10, buttonY, 60, 20).build());
    }
    
    private String currentValue() {
        return multiline ? multilineBox.getValue() : box.getValue();
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        gfx.drawCenteredString(this.font, title, this.width / 2, 15, 0xFFFFFF);
        
        String value = currentValue();
        if (validator != null && !value.isBlank() && !validator.test(value.trim())) {
            gfx.drawCenteredString(this.font, invalidMessage, this.width / 2, 30, 0xFF5555);
        } else if (errorMessage != null) {
            gfx.drawCenteredString(this.font, errorMessage, this.width / 2, 30, 0xFF4444);
        }
        super.render(gfx, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean shouldCloseOnEsc() { return false; }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> this.minecraft.setScreen(confirmed ? null : this),
                    Component.literal("Exit Editor"),
                    Component.literal("Are you sure you want to exit? Unsaved changes will be lost.")
            ));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.MultilineTextBox;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.function.Consumer;
import java.util.function.Predicate;

public class TextInputScreen extends GuiScreen {
    
    private final GuiScreen parent;
    private final String title;
    private final Consumer<String> onConfirm;
    private final String initialText;
    private final Predicate<String> validator;
    private final String invalidMessage;
    private final boolean allowEmpty;
    private final boolean multiline;
    
    private GuiTextField box;
    private MultilineTextBox multilineBox;
    private String errorMessage = null;
    
    public TextInputScreen(
            GuiScreen parent,
            String title,
            String initialText,
            Consumer<String> onConfirm) {
        
        this(parent, title, initialText, onConfirm, null, null, false, false);
    }
    
    public TextInputScreen(
            GuiScreen parent,
            String title,
            String initialText,
            Consumer<String> onConfirm,
            Predicate<String> validator,
            String invalidMessage,
            boolean allowEmpty) {
        
        this(parent, title, initialText, onConfirm,
                validator, invalidMessage, allowEmpty, false);
    }
    
    public TextInputScreen(
            GuiScreen parent,
            String title,
            String initialText,
            Consumer<String> onConfirm,
            Predicate<String> validator,
            String invalidMessage,
            boolean allowEmpty,
            boolean multiline) {
        
        this.parent = parent;
        this.title = title;
        this.initialText = initialText;
        this.onConfirm = onConfirm;
        this.validator = validator;
        this.invalidMessage = invalidMessage;
        this.allowEmpty = allowEmpty;
        this.multiline = multiline;
    }
    
    @Override
    public void initGui() {
        if (multiline) {
            int w = this.width * 3 / 4;
            int h = this.height - 100;
            
            multilineBox = new MultilineTextBox(
                    this.fontRenderer,
                    this.width / 2 - w / 2,
                    50,
                    w,
                    h,
                    initialText == null ? "" : initialText
            );
            
            multilineBox.setFocused(true);
        } else {
            int w = 200;
            int h = 20;
            
            box = new GuiTextField(
                    0,
                    this.fontRenderer,
                    this.width / 2 - w / 2,
                    this.height / 2 - 10,
                    w,
                    h
            );
            
            box.setMaxStringLength(4096);
            box.setText(initialText == null ? "" : initialText);
            box.setFocused(true);
        }
        
        int buttonY = this.height - 40;
        
        this.buttonList.add(new GuiButton(
                0,
                this.width / 2 - 50,
                buttonY,
                40,
                20,
                "OK"
        ));
        
        this.buttonList.add(new GuiButton(
                1,
                this.width / 2 + 10,
                buttonY,
                60,
                20,
                "Cancel"
        ));
    }
    
    private String currentValue() {
        return multiline
                ? multilineBox.getValue()
                : box.getText();
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id == 0) {
            String value = currentValue().trim();
            
            if (value.isEmpty() && !allowEmpty) {
                errorMessage = "Cannot be empty";
                return;
            }
            
            onConfirm.accept(value);
            this.mc.displayGuiScreen(parent);
            return;
        }
        
        if (button.id == 1) {
            this.mc.displayGuiScreen(parent);
        }
    }
    
    @Override
    public void updateScreen() {
        super.updateScreen();
        
        if (box != null) {
            box.updateCursorCounter();
        }
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiYesNo(
                    (result, id) -> {
                        if (result) mc.displayGuiScreen(null);
                        else mc.displayGuiScreen(TextInputScreen.this);
                    },
                    "Exit Editor",
                    "Are you sure you want to exit? Unsaved changes will be lost.",
                    0
            ));
            return;
        }
        
        if (multiline) {
            if (multilineBox != null) {
                multilineBox.keyTyped(typedChar, keyCode);
            }
        } else {
            if (box != null) {
                box.textboxKeyTyped(typedChar, keyCode);
            }
        }
        
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (!multiline && box != null) {
            box.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        if (multiline && multilineBox != null) {
            multilineBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
        if (multilineBox != null) multilineBox.mouseDragged(mouseX, mouseY);
    }
    
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        super.mouseReleased(mouseX, mouseY, state);
        if (multilineBox != null) multilineBox.mouseReleased(mouseX, mouseY, state);
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        this.drawCenteredString(
                this.fontRenderer,
                title,
                this.width / 2,
                15,
                0xFFFFFF
        );
        
        String value = currentValue();
        
        if (validator != null
                && !value.trim().isEmpty()
                && !validator.test(value.trim())) {
            
            this.drawCenteredString(
                    this.fontRenderer,
                    invalidMessage,
                    this.width / 2,
                    30,
                    0xFF5555
            );
            
        } else if (errorMessage != null) {
            
            this.drawCenteredString(
                    this.fontRenderer,
                    errorMessage,
                    this.width / 2,
                    30,
                    0xFF4444
            );
        }
        
        if (box != null) {
            box.drawTextBox();
        }
        
        if (multilineBox != null) {
            multilineBox.draw();
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
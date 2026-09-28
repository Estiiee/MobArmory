package com.estie.mobarmory.client.gui.screen;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class EnchantmentTextInputScreen extends GuiScreen {
    
    private final GuiScreen parent;
    private final String title;
    private final String initialId;
    private final String initialLevel;
    private final BiConsumer<String, String> onDone;
    private final Runnable onDelete;
    private final Predicate<String> idValidator;
    private final String invalidMessage;
    
    private GuiTextField idBox;
    private GuiTextField levelBox;
    
    public EnchantmentTextInputScreen(
            GuiScreen parent,
            String title,
            String initialId,
            String initialLevel,
            BiConsumer<String, String> onDone,
            Runnable onDelete) {
        
        this(parent, title, initialId, initialLevel, onDone, onDelete, null, null);
    }
    
    public EnchantmentTextInputScreen(
            GuiScreen parent,
            String title,
            String initialId,
            String initialLevel,
            BiConsumer<String, String> onDone,
            Runnable onDelete,
            Predicate<String> idValidator,
            String invalidMessage) {
        
        this.parent = parent;
        this.title = title;
        this.initialId = initialId;
        this.initialLevel = initialLevel;
        this.onDone = onDone;
        this.onDelete = onDelete;
        this.idValidator = idValidator;
        this.invalidMessage = invalidMessage;
    }
    
    @Override
    public void initGui() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 30;
        
        this.idBox = new GuiTextField(
                0,
                this.fontRenderer,
                centerX - 100,
                y,
                200,
                20
        );
        this.idBox.setMaxStringLength(256);
        this.idBox.setText(this.initialId == null ? "" : this.initialId);
        this.idBox.setFocused(true);
        
        this.levelBox = new GuiTextField(
                1,
                this.fontRenderer,
                centerX - 100,
                y + 30,
                200,
                20
        );
        this.levelBox.setMaxStringLength(32);
        this.levelBox.setText(this.initialLevel == null ? "" : this.initialLevel);
        
        y += 70;
        
        this.buttonList.add(new GuiButton(
                0,
                centerX - 40,
                y,
                80,
                20,
                "OK"
        ));
        
        this.buttonList.add(new GuiButton(
                1,
                centerX - 140,
                y,
                80,
                20,
                "Delete"
        ));
        
        this.buttonList.add(new GuiButton(
                2,
                centerX + 60,
                y,
                80,
                20,
                "Back"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                String idVal = this.idBox.getText().trim();
                
                if (!idVal.contains(":")) {
                    idVal = "minecraft:" + idVal;
                }
                
                this.onDone.accept(idVal, this.levelBox.getText().trim());
                this.mc.displayGuiScreen(this.parent);
                break;
            
            case 1:
                this.onDelete.run();
                this.mc.displayGuiScreen(this.parent);
                break;
            
            case 2:
                this.mc.displayGuiScreen(this.parent);
                break;
        }
    }
    
    @Override
    public void updateScreen() {
        super.updateScreen();
        
        if (this.idBox != null) {
            this.idBox.updateCursorCounter();
        }
        
        if (this.levelBox != null) {
            this.levelBox.updateCursorCounter();
        }
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiYesNo(
                    (result, id) -> {
                        if (result) {
                            mc.displayGuiScreen(null);
                        } else {
                            mc.displayGuiScreen(EnchantmentTextInputScreen.this);
                        }
                    },
                    "Exit Editor",
                    "Are you sure you want to exit? Unsaved changes will be lost.",
                    0
            ));
            return;
        }
        
        if (this.idBox != null) {
            this.idBox.textboxKeyTyped(typedChar, keyCode);
        }
        
        if (this.levelBox != null) {
            this.levelBox.textboxKeyTyped(typedChar, keyCode);
        }
        
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton) throws IOException {
        
        if (this.idBox != null) {
            this.idBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        if (this.levelBox != null) {
            this.levelBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks) {
        
        this.drawDefaultBackground();
        
        this.drawCenteredString(
                this.fontRenderer,
                this.title,
                this.width / 2,
                this.height / 2 - 60,
                0xFFFFFF
        );
        
        this.fontRenderer.drawString(
                "ID",
                this.width / 2 - 100,
                this.height / 2 - 45,
                0xAAAAAA
        );
        
        this.fontRenderer.drawString(
                "Level",
                this.width / 2 - 100,
                this.height / 2 - 15,
                0xAAAAAA
        );
        
        if (this.idValidator != null
                && this.idBox != null
                && !this.idBox.getText().trim().isEmpty()
                && !this.idValidator.test(normalizedId())) {
            
            this.drawCenteredString(
                    this.fontRenderer,
                    this.invalidMessage,
                    this.width / 2,
                    this.height / 2 - 75,
                    0xFF5555
            );
        }
        
        if (this.idBox != null) {
            this.idBox.drawTextBox();
        }
        
        if (this.levelBox != null) {
            this.levelBox.drawTextBox();
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    private String normalizedId() {
        String raw = this.idBox.getText().trim();
        return raw.contains(":") ? raw : "minecraft:" + raw;
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
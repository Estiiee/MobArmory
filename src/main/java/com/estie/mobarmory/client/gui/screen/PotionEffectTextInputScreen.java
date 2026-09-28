package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.TriStringConsumer;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;

import java.io.IOException;

public class PotionEffectTextInputScreen extends GuiScreen {
    
    private final GuiScreen parent;
    private final String title;
    private final String initialId;
    private final String initialDuration;
    private final String initialAmplifier;
    private final TriStringConsumer onDone;
    private final Runnable onDelete;
    
    private GuiTextField idBox;
    private GuiTextField durationBox;
    private GuiTextField amplifierBox;
    
    public PotionEffectTextInputScreen(
            GuiScreen parent,
            String title,
            String initialId,
            String initialDuration,
            String initialAmplifier,
            TriStringConsumer onDone,
            Runnable onDelete) {
        
        this.parent = parent;
        this.title = title;
        this.initialId = initialId;
        this.initialDuration = initialDuration;
        this.initialAmplifier = initialAmplifier;
        this.onDone = onDone;
        this.onDelete = onDelete;
    }
    
    @Override
    public void initGui() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 50;
        
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
        
        y += 30;
        
        this.durationBox = new GuiTextField(
                1,
                this.fontRenderer,
                centerX - 100,
                y,
                200,
                20
        );
        this.durationBox.setMaxStringLength(32);
        this.durationBox.setText(
                this.initialDuration == null ? "" : this.initialDuration
        );
        
        y += 30;
        
        this.amplifierBox = new GuiTextField(
                2,
                this.fontRenderer,
                centerX - 100,
                y,
                200,
                20
        );
        this.amplifierBox.setMaxStringLength(32);
        this.amplifierBox.setText(
                this.initialAmplifier == null ? "" : this.initialAmplifier
        );
        
        y += 40;
        
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
                
                this.onDone.accept(
                        idVal,
                        this.durationBox.getText().trim(),
                        this.amplifierBox.getText().trim()
                );
                
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
        
        if (this.durationBox != null) {
            this.durationBox.updateCursorCounter();
        }
        
        if (this.amplifierBox != null) {
            this.amplifierBox.updateCursorCounter();
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
                            mc.displayGuiScreen(PotionEffectTextInputScreen.this);
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
        
        if (this.durationBox != null) {
            this.durationBox.textboxKeyTyped(typedChar, keyCode);
        }
        
        if (this.amplifierBox != null) {
            this.amplifierBox.textboxKeyTyped(typedChar, keyCode);
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
        
        if (this.durationBox != null) {
            this.durationBox.mouseClicked(mouseX, mouseY, mouseButton);
        }
        
        if (this.amplifierBox != null) {
            this.amplifierBox.mouseClicked(mouseX, mouseY, mouseButton);
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
                this.height / 2 - 70,
                0xFFFFFF
        );
        
        this.fontRenderer.drawString(
                "Effect ID",
                this.width / 2 - 100,
                this.height / 2 - 65,
                0xAAAAAA
        );
        
        if (this.idBox != null && !this.idBox.getText().trim().isEmpty()) {
            String raw = this.idBox.getText().trim();
            String normalized = raw.contains(":")
                    ? raw
                    : "minecraft:" + raw;
            
            if (!EditScreenShared.effectExists(normalized)) {
                this.drawCenteredString(
                        this.fontRenderer,
                        "Warning: effect not found",
                        this.width / 2,
                        this.height / 2 - 85,
                        0xFF5555
                );
            }
        }
        
        if (this.idBox != null) {
            this.idBox.drawTextBox();
        }
        
        if (this.durationBox != null) {
            this.durationBox.drawTextBox();
        }
        
        if (this.amplifierBox != null) {
            this.amplifierBox.drawTextBox();
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
}
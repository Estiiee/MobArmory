package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.TriStringConsumer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class PotionEffectTextInputScreen extends Screen {
    
    private final Screen parent;
    private final String title;
    private final String initialId, initialDuration, initialAmplifier;
    private final TriStringConsumer onDone;
    private final Runnable onDelete;
    
    private EditBox idBox, durationBox, amplifierBox;
    
    public PotionEffectTextInputScreen(Screen parent, String title, String initialId, String initialDuration,
                                       String initialAmplifier, TriStringConsumer onDone, Runnable onDelete) {
        super(Component.literal(title));
        this.parent = parent;
        this.title = title;
        this.initialId = initialId;
        this.initialDuration = initialDuration;
        this.initialAmplifier = initialAmplifier;
        this.onDone = onDone;
        this.onDelete = onDelete;
    }
    
    @Override
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 50;
        
        idBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Effect ID"));
        idBox.setMaxLength(256);
        idBox.setValue(initialId);
        this.addRenderableWidget(idBox);
        y += 30;
        
        durationBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Duration"));
        durationBox.setValue(initialDuration);
        this.addRenderableWidget(durationBox);
        y += 30;
        
        amplifierBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Amplifier"));
        amplifierBox.setValue(initialAmplifier);
        this.addRenderableWidget(amplifierBox);
        y += 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("OK"),
                btn -> {
                    String idVal = idBox.getValue().trim();
                    if (!idVal.contains(":")) idVal = "minecraft:" + idVal;
                    onDone.accept(idVal, durationBox.getValue().trim(), amplifierBox.getValue().trim());
                    this.minecraft.setScreen(parent);
                }).bounds(centerX - 40, y, 80, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete"),
                btn -> {
                    onDelete.run();
                    this.minecraft.setScreen(parent);
                }).bounds(centerX - 140, y, 80, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(parent)
        ).bounds(centerX + 60, y, 80, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 70, 0xFFFFFF);
        
        if (idBox != null && !idBox.getValue().isBlank()) {
            String normalized = idBox.getValue().trim().contains(":") ? idBox.getValue().trim() : "minecraft:" + idBox.getValue().trim();
            if (!EditScreenShared.effectExists(normalized)) {
                gfx.drawCenteredString(this.font, "Warning: effect not found", this.width / 2, this.height / 2 - 55, 0xFF5555);
            }
        }
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
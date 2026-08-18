package com.estie.mobarmory.client.gui.screen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.function.BiConsumer;
import java.util.function.Predicate;

public class EnchantmentTextInputScreen extends Screen {
    
    private final Screen parent;
    private final String title;
    private final String initialId;
    private final String initialLevel;
    private final BiConsumer<String, String> onDone;
    private final Runnable onDelete;
    private final Predicate<String> idValidator; // nullable
    private final String invalidMessage;
    
    private EditBox idBox;
    private EditBox levelBox;
    
    public EnchantmentTextInputScreen(Screen parent, String title, String initialId, String initialLevel,
                                      BiConsumer<String, String> onDone, Runnable onDelete) {
        this(parent, title, initialId, initialLevel, onDone, onDelete, null, null);
    }
    
    public EnchantmentTextInputScreen(Screen parent, String title, String initialId, String initialLevel,
                                      BiConsumer<String, String> onDone, Runnable onDelete,
                                      Predicate<String> idValidator, String invalidMessage) {
        super(Component.literal(title));
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
    protected void init() {
        int centerX = this.width / 2;
        int y = this.height / 2 - 30;
        
        idBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("ID"));
        idBox.setMaxLength(256);
        idBox.setValue(initialId);
        this.addRenderableWidget(idBox);
        y += 30;
        
        levelBox = new EditBox(this.font, centerX - 100, y, 200, 20, Component.literal("Level"));
        levelBox.setValue(initialLevel);
        this.addRenderableWidget(levelBox);
        y += 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("OK"),
                btn -> {
                    String idVal = idBox.getValue().trim();
                    if (!idVal.contains(":")) idVal = "minecraft:" + idVal;
                    onDone.accept(idVal, levelBox.getValue().trim());
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
        
        gfx.drawCenteredString(this.font, title, this.width / 2, this.height / 2 - 60, 0xFFFFFF);
        
        if (idValidator != null && idBox != null && !idBox.getValue().isBlank() && !idValidator.test(normalizedId())) {
            gfx.drawCenteredString(this.font, invalidMessage, this.width / 2, this.height / 2 - 45, 0xFF5555);
        }
    }
    
    private String normalizedId() {
        String raw = idBox.getValue().trim();
        return raw.contains(":") ? raw : "minecraft:" + raw;
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
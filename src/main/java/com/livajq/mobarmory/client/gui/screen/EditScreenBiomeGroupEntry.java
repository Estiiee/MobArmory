package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class EditScreenBiomeGroupEntry extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenBiomeGroupEntry(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup) {
        super(Component.literal("Edit Biome Group"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
    }
    
    @Override
    protected void init() {
        int leftX = 20;
        int y = 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Matchers"),
                btn -> this.minecraft.setScreen(new EditScreenBiomeMatchers(main, difficultyGroup, biomeGroup))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Set Chance"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Set Chance (0.0 - 1.0)",
                        "" + biomeGroup.chance,
                        value -> {
                            try {
                                float f = Float.parseFloat(value);
                                biomeGroup.chance = Mth.clamp(f, 0f, 1f);
                            } catch (Exception ignored) {}
                        }
                ))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Equipment Sets"),
                btn -> this.minecraft.setScreen(new EditScreenEquipmentSets(main, difficultyGroup, biomeGroup))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete Group"),
                btn -> {
                    difficultyGroup.biomeGroups.remove(biomeGroup);
                    this.minecraft.setScreen(new EditScreenBiomeGroups(main, difficultyGroup));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenBiomeGroups(main, difficultyGroup))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                btn -> {
                    String initial = main.entry.fileName != null ? main.entry.fileName : "";
                    this.minecraft.setScreen(new TextInputScreen(
                            this,
                            "Save As...",
                            initial,
                            name -> {
                                main.entry.fileName = name;
                                main.saveToFile();
                                this.minecraft.setScreen(null);
                            }
                    ));
                }
        ).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, this.font, main.entry, this.width, PREVIEW_SIZE, "Biome Group");
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int previewY = 60;
        int infoY = previewY + PREVIEW_SIZE + 12;
        
        StringBuilder stringBuilder = new StringBuilder();
        int shown = Math.min(3, biomeGroup.matchers.size());
        for (int i = 0; i < shown; i++) {
            stringBuilder.append(MobEquipmentReloadListener.biomeMatchToString(biomeGroup.matchers.get(i))).append(" ");
        }
        int remaining = biomeGroup.matchers.size() - shown;
        if (remaining > 0) stringBuilder.append("and ").append(remaining).append(" more...");
        
        List<FormattedCharSequence> lines = this.font.split(Component.literal("Matchers: " + stringBuilder), PREVIEW_SIZE);
        
        int dy = 0;
        for (FormattedCharSequence line : lines) {
            gfx.drawCenteredString(this.font, line, previewX + PREVIEW_SIZE / 2, infoY + dy, 0xFFFFFF);
            dy += this.font.lineHeight;
        }
        
        float effectiveChance = EditScreenShared.hasOverride(biomeGroup.chance) ? biomeGroup.chance
                : EditScreenShared.hasOverride(difficultyGroup.chance) ? difficultyGroup.chance
                : main.entry.chance;
        
        gfx.drawCenteredString(this.font, "Chance: " + (int)(effectiveChance * 100) + "%",
                previewX + PREVIEW_SIZE / 2, infoY + dy + 4, 0xAAAAAA);
        
        gfx.drawCenteredString(this.font, "Equipment Sets: " + biomeGroup.sets.size(),
                previewX + PREVIEW_SIZE / 2, infoY + dy + 18, 0xAAAAAA);
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
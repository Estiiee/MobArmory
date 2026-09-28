package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class EditScreenDifficultyGroupEntry extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenDifficultyGroupEntry(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup) {
        this.main = main;
        this.difficultyGroup = difficultyGroup;
    }
    
    @Override
    public void initGui() {
        int leftX = 20;
        int y = 40;
        
        this.buttonList.add(new GuiButton(0, leftX, y, LEFT_PANEL_WIDTH, 20, "Add Matcher"));
        y += 24;
        this.buttonList.add(new GuiButton(1, leftX, y, LEFT_PANEL_WIDTH, 20, "Set Chance"));
        y += 24;
        this.buttonList.add(new GuiButton(2, leftX, y, LEFT_PANEL_WIDTH, 20, "Biome Groups"));
        y += 24;
        this.buttonList.add(new GuiButton(3, leftX, y, LEFT_PANEL_WIDTH, 20, "Delete Group"));
        y += 24;
        this.buttonList.add(new GuiButton(4, leftX, y, LEFT_PANEL_WIDTH, 20, "Back"));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new EditScreenDifficultyMatcher(main, difficultyGroup));
                break;
            case 1:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Chance (0.0 - 1.0)",
                        "" + difficultyGroup.chance,
                        value -> {
                            try {
                                float f = Float.parseFloat(value);
                                difficultyGroup.chance = MathHelper.clamp(f, -1f, 1f);
                            } catch (Exception ignored) {}
                        }
                ));
                break;
            case 2:
                this.mc.displayGuiScreen(new EditScreenBiomeGroups(main, difficultyGroup));
                break;
            case 3:
                main.entry.difficultyGroups.remove(difficultyGroup);
                this.mc.displayGuiScreen(new EditScreenDifficultyGroups(main));
                break;
            case 4:
                this.mc.displayGuiScreen(new EditScreenDifficultyGroups(main));
                break;
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        EditScreenShared.renderHeader(this.mc, main.entry, width, PREVIEW_SIZE, Arrays.asList(
                EditScreenShared.crumbMain(main.entry),
                EditScreenShared.current("Difficulty Group")));
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int previewY = 60;
        int infoY = previewY + PREVIEW_SIZE + 12;
        
        StringBuilder stringBuilder = new StringBuilder();
        for (MobEquipmentReloadListener.DifficultyLevel difficultyLevel : difficultyGroup.matchers) {
            stringBuilder.append(difficultyLevel.toString()).append(" ");
        }
        
        String raw = "Matchers: " + stringBuilder;
        
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(raw, PREVIEW_SIZE);
        
        int dy = 0;
        for (String line : lines) {
            this.drawCenteredString(this.fontRenderer, line, previewX + PREVIEW_SIZE / 2, infoY + dy, 0xFFFFFF);
            dy += this.fontRenderer.FONT_HEIGHT;
        }
        
        int usedHeight = dy;
        
        this.drawCenteredString(this.fontRenderer,
                "Chance: " + (int) ((EditScreenShared.hasOverride(difficultyGroup.chance) ? difficultyGroup.chance : main.entry.chance) * 100) + "%",
                previewX + PREVIEW_SIZE / 2, infoY + usedHeight + 4, 0xAAAAAA);
        
        this.drawCenteredString(this.fontRenderer,
                "Biome Groups: " + difficultyGroup.biomeGroups.size(),
                previewX + PREVIEW_SIZE / 2, infoY + usedHeight + 18, 0xAAAAAA);
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiYesNo((result, id) -> {
                if (result) mc.displayGuiScreen(null);
                else mc.displayGuiScreen(EditScreenDifficultyGroupEntry.this);
            }, "Exit Editor", "Are you sure you want to exit? Unsaved changes will be lost.", 0));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (EditScreenShared.breadcrumbClicked(mouseX, mouseY)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
}
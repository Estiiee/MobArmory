package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.util.math.MathHelper;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class EditScreenBiomeGroupEntry extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenBiomeGroupEntry(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup) {
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
    }
    
    @Override
    public void initGui() {
        int leftX = 20;
        int y = 40;
        
        this.buttonList.add(new GuiButton(0, leftX, y, LEFT_PANEL_WIDTH, 20, "Matchers"));
        y += 24;
        this.buttonList.add(new GuiButton(1, leftX, y, LEFT_PANEL_WIDTH, 20, "Set Chance"));
        y += 24;
        this.buttonList.add(new GuiButton(2, leftX, y, LEFT_PANEL_WIDTH, 20, "Equipment Sets"));
        y += 24;
        this.buttonList.add(new GuiButton(3, leftX, y, LEFT_PANEL_WIDTH, 20, "Delete Group"));
        y += 24;
        this.buttonList.add(new GuiButton(4, leftX, y, LEFT_PANEL_WIDTH, 20, "Back"));
        
        this.buttonList.add(new GuiButton(5, this.width / 2 - 50, this.height - 40, 100, 20, "Save"));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new EditScreenBiomeMatchers(main, difficultyGroup, biomeGroup));
                break;
            case 1:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Chance (0.0 - 1.0)",
                        "" + biomeGroup.chance,
                        value -> {
                            try {
                                float f = Float.parseFloat(value);
                                biomeGroup.chance = MathHelper.clamp(f, -1f, 1f);
                            } catch (Exception ignored) {}
                        }
                ));
                break;
            case 2:
                this.mc.displayGuiScreen(new EditScreenEquipmentSets(main, difficultyGroup, biomeGroup));
                break;
            case 3:
                difficultyGroup.biomeGroups.remove(biomeGroup);
                this.mc.displayGuiScreen(new EditScreenBiomeGroups(main, difficultyGroup));
                break;
            case 4:
                this.mc.displayGuiScreen(new EditScreenBiomeGroups(main, difficultyGroup));
                break;
            case 5:
                String initial = main.entry.fileName != null ? main.entry.fileName : "";
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Save As...",
                        initial,
                        name -> {
                            main.entry.fileName = name;
                            main.saveToFile();
                            this.mc.displayGuiScreen(null);
                        }
                ));
                break;
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        EditScreenShared.renderHeader(this.mc, main.entry, width, PREVIEW_SIZE, Arrays.asList(
                EditScreenShared.crumbMain(main.entry),
                EditScreenShared.crumbDifficultyGroup(main, difficultyGroup),
                EditScreenShared.current("Biome Group")));
        
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
        
        List<String> lines = this.fontRenderer.listFormattedStringToWidth("Matchers: " + stringBuilder, PREVIEW_SIZE);
        
        int dy = 0;
        for (String line : lines) {
            this.drawCenteredString(this.fontRenderer, line, previewX + PREVIEW_SIZE / 2, infoY + dy, 0xFFFFFF);
            dy += this.fontRenderer.FONT_HEIGHT;
        }
        
        float effectiveChance = EditScreenShared.hasOverride(biomeGroup.chance)
                ? biomeGroup.chance : EditScreenShared.hasOverride(difficultyGroup.chance)
                ? difficultyGroup.chance : main.entry.chance;
        
        String chanceVal = effectiveChance < 0.0F ? "Not Set" : (int) (effectiveChance * 100) + "%";
        
        this.drawCenteredString(
                this.fontRenderer,
                "Chance: " + chanceVal,
                previewX + PREVIEW_SIZE / 2,
                infoY + dy + 4,
                0xAAAAAA
        );
        
        this.drawCenteredString(this.fontRenderer, "Equipment Sets: " + biomeGroup.sets.size(),
                previewX + PREVIEW_SIZE / 2, infoY + dy + 18, 0xAAAAAA);
    }
    
    @Override
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiYesNo(
                    (result, id) -> {
                        if (result) mc.displayGuiScreen(null);
                        else mc.displayGuiScreen(EditScreenBiomeGroupEntry.this);
                    },
                    "Exit Editor",
                    "Are you sure you want to exit? Unsaved changes will be lost.",
                    0
            ));
            return;
        }
        
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (EditScreenShared.breadcrumbClicked(mouseX, mouseY)) return;
        if (EditScreenShared.mouseClicked(mouseX, mouseY, mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    protected void mouseClickMove(int mouseX, int mouseY, int clickedMouseButton, long timeSinceLastClick) {
        if (EditScreenShared.mouseDragged(mouseX, mouseY)) {
            return;
        }
        
        super.mouseClickMove(mouseX, mouseY, clickedMouseButton, timeSinceLastClick);
    }
    
    @Override
    protected void mouseReleased(int mouseX, int mouseY, int state) {
        if (EditScreenShared.mouseReleased()) return;
        super.mouseReleased(mouseX, mouseY, state);
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        
        EditScreenShared.mouseScrolled(
                Mouse.getEventX(),
                Mouse.getEventY(),
                Mouse.getEventDWheel()
        );
    }
}
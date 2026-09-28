package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.BiomeMatcherList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class EditScreenBiomeMatchers extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private BiomeMatcherList list;
    
    private static final int LEFT_PANEL_WIDTH = 200;
    
    public EditScreenBiomeMatchers(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup) {
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
    }
    
    @Override
    public void initGui() {
        this.list = new BiomeMatcherList(this.mc, 20, 40, this.width - 40, this.height - 100, 20,
                biomeGroup, () -> this.mc.displayGuiScreen(new EditScreenBiomeMatchers(main, difficultyGroup, biomeGroup)));
        this.list.entries.addAll(biomeGroup.matchers);
        
        int leftX = 20;
        int btnWidth = LEFT_PANEL_WIDTH / 2 - 5;
        
        this.buttonList.add(new GuiButton(0, leftX, this.height - 40, btnWidth, 20, "Add Matcher"));
        this.buttonList.add(new GuiButton(1, leftX + btnWidth + 10, this.height - 40, btnWidth, 20, "Back"));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            this.mc.displayGuiScreen(new TextInputScreen(
                    this,
                    "Add Matcher (global / TAG_NAME / minecraft:biome_id)",
                    "",
                    value -> {
                        try {
                            biomeGroup.matchers.add(MobEquipmentReloadListener.parseBiomeMatch(value));
                        } catch (Exception ignored) {}
                        this.mc.displayGuiScreen(new EditScreenBiomeMatchers(main, difficultyGroup, biomeGroup));
                    }
            ));
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(new EditScreenBiomeGroupEntry(main, difficultyGroup, biomeGroup));
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        list.render(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        this.drawCenteredString(this.fontRenderer, "Biome matchers", this.width / 2, 15, 0xFFFFFF);
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
                else mc.displayGuiScreen(EditScreenBiomeMatchers.this);
            }, "Exit Editor", "Are you sure you want to exit? Unsaved changes will be lost.", 0));
            return;
        }
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (list.mouseClicked(mouseX, mouseY, mouseButton)) return;
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        if (wheel != 0) list.mouseScrolled(wheel > 0 ? 1 : -1);
    }
}
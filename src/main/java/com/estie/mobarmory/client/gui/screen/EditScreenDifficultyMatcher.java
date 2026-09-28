package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.HashSet;
import java.util.Set;

public class EditScreenDifficultyMatcher extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup group;
    
    private final Set<MobEquipmentReloadListener.DifficultyLevel> selected = new HashSet<>();
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    public EditScreenDifficultyMatcher(
            EditScreenMain main,
            MobEquipmentReloadListener.DifficultyGroup group) {
        
        this.main = main;
        this.group = group;
        
        selected.addAll(group.matchers);
    }
    
    @Override
    public void initGui() {
        int leftX = 20;
        int y = 40;
        
        for (MobEquipmentReloadListener.DifficultyLevel lvl :
                MobEquipmentReloadListener.DifficultyLevel.values()) {
            
            GuiButton button = new GuiButton(
                    lvl.ordinal(),
                    leftX,
                    y,
                    LEFT_PANEL_WIDTH,
                    20,
                    lvl.name()
            );
            
            updateButtonColor(button, lvl);
            this.buttonList.add(button);
            
            y += 24;
        }
        
        this.buttonList.add(new GuiButton(
                100,
                leftX,
                y + 10,
                LEFT_PANEL_WIDTH,
                20,
                "Save"
        ));
        
        this.buttonList.add(new GuiButton(
                101,
                leftX,
                y + 34,
                LEFT_PANEL_WIDTH,
                20,
                "Back"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= 0 &&
                button.id < MobEquipmentReloadListener.DifficultyLevel.values().length) {
            
            MobEquipmentReloadListener.DifficultyLevel lvl =
                    MobEquipmentReloadListener.DifficultyLevel.values()[button.id];
            
            if (selected.contains(lvl)) {
                selected.remove(lvl);
            } else {
                selected.add(lvl);
            }
            
            updateButtonColor(button, lvl);
            return;
        }
        
        if (button.id == 100) {
            group.matchers.clear();
            group.matchers.addAll(selected);
            
            if (group.matchers.isEmpty()) {
                group.matchers.add(
                        MobEquipmentReloadListener.DifficultyLevel.GLOBAL
                );
            }
            
            this.mc.displayGuiScreen(
                    new EditScreenDifficultyGroupEntry(main, group)
            );
            
        } else if (button.id == 101) {
            this.mc.displayGuiScreen(
                    new EditScreenDifficultyGroupEntry(main, group)
            );
        }
    }
    
    private void updateButtonColor(
            GuiButton button,
            MobEquipmentReloadListener.DifficultyLevel lvl) {
        
        if (selected.contains(lvl)) {
            button.packedFGColour = 0x00FF00;
        } else {
            button.packedFGColour = 0xFF4444;
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        this.drawCenteredString(
                this.fontRenderer,
                "Toggle difficulty matchers",
                this.width / 2,
                15,
                0xFFFFFF
        );
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
                        else mc.displayGuiScreen(EditScreenDifficultyMatcher.this);
                    }, "Exit Editor", "Are you sure you want to exit? Unsaved changes will be lost.", 0
            ));
            return;
        }
        
        super.keyTyped(typedChar, keyCode);
    }
}
package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.DifficultyGroupList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;

public class EditScreenDifficultyGroups extends GuiScreen {
    
    private final EditScreenMain main;
    private DifficultyGroupList list;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    public EditScreenDifficultyGroups(EditScreenMain main) {
        this.main = main;
    }
    
    @Override
    public void initGui() {
        this.list = new DifficultyGroupList(this.mc, 20, 40, this.width - 40, this.height - 100, 20, main);
        
        this.list.entries.addAll(main.entry.difficultyGroups);
        
        this.buttonList.add(new GuiButton(
                0,
                20,
                this.height - 40,
                LEFT_PANEL_WIDTH,
                20,
                "Add Group"
        ));
        
        this.buttonList.add(new GuiButton(
                1,
                this.width - LEFT_PANEL_WIDTH - 20,
                this.height - 40,
                LEFT_PANEL_WIDTH,
                20,
                "Back"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id == 0) {
            MobEquipmentReloadListener.DifficultyGroup newGroup =
                    new MobEquipmentReloadListener.DifficultyGroup(
                            new ArrayList<>(Collections.singletonList(
                                    MobEquipmentReloadListener.DifficultyLevel.GLOBAL
                            )),
                            -1.0F,
                            new ArrayList<>(),
                            new ArrayList<>()
                    );
            
            main.entry.difficultyGroups.add(newGroup);
            this.mc.displayGuiScreen(
                    new EditScreenDifficultyGroupEntry(main, newGroup)
            );
            
        } else if (button.id == 1) {
            this.mc.displayGuiScreen(main);
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        list.render(mouseX, mouseY);
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        this.drawCenteredString(
                this.fontRenderer,
                "Difficulty groups",
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
                        else mc.displayGuiScreen(EditScreenDifficultyGroups.this);
                    }, "Exit Editor", "Are you sure you want to exit? Unsaved changes will be lost.", 0
            ));
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
        if (wheel != 0) {
            list.mouseScrolled(wheel > 0 ? 1 : -1);
        }
    }
}

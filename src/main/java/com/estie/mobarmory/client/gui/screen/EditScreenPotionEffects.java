package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.PotionEffectList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;

public class EditScreenPotionEffects extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private PotionEffectList list;
    
    public EditScreenPotionEffects(
            EditScreenMain main,
            MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
            MobEquipmentReloadListener.BiomeGroup biomeGroup,
            MobEquipmentReloadListener.EquipmentSet set) {
        
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
    }
    
    @Override
    public void initGui() {
        list = new PotionEffectList(
                this.mc,
                20,
                40,
                this.width - 40,
                this.height - 100,
                20,
                main,
                difficultyGroup,
                biomeGroup,
                set
        );
        
        list.entries.addAll(set.potionEffects);
        
        int leftX = 20;
        int rightX = this.width - LEFT_PANEL_WIDTH - 20;
        int y = this.height - 40;
        
        this.buttonList.add(new GuiButton(
                0,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Add Effect"
        ));
        
        this.buttonList.add(new GuiButton(
                1,
                rightX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Back"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                set.potionEffects.add(
                        new MobEquipmentReloadListener.PotionEffectEntry(
                                "minecraft:speed",
                                600,
                                0
                        )
                );
                
                this.mc.displayGuiScreen(new EditScreenPotionEffects(
                        main, difficultyGroup, biomeGroup, set
                ));
                break;
            
            case 1:
                this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                        main, difficultyGroup, biomeGroup, set
                ));
                break;
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        if (list != null) {
            list.render(mouseX, mouseY);
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        this.drawCenteredString(
                this.fontRenderer,
                "Potion Effects",
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
                        else mc.displayGuiScreen(EditScreenPotionEffects.this);
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
        if (list != null && list.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        
        if (list != null) {
            list.mouseScrolled(Mouse.getEventDWheel());
        }
    }
}
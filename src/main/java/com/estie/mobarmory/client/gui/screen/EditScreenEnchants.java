package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.EnchantList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;

public class EditScreenEnchants extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EntityEquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    private EnchantList list;
    
    public EditScreenEnchants(EditScreenMain main,
                              MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                              MobEquipmentReloadListener.BiomeGroup biomeGroup,
                              MobEquipmentReloadListener.EquipmentSet set,
                              EntityEquipmentSlot slot,
                              MobEquipmentReloadListener.WeightedItem item) {
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
        this.item = item;
    }
    
    @Override
    public void initGui() {
        if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined) {
            MobEquipmentReloadListener.EnchantData.Predefined p =
                    (MobEquipmentReloadListener.EnchantData.Predefined) item.enchant;
            
            this.list = new EnchantList(
                    this.mc,
                    20,
                    40,
                    this.width - 40,
                    this.height - 100,
                    20,
                    main,
                    difficultyGroup,
                    biomeGroup,
                    set,
                    slot,
                    item
            );
            
            for (int i = 0; i < p.ids().size(); i++) {
                this.list.entries.add(new EnchantList.Entry(
                        p.ids().get(i),
                        p.levels().get(i),
                        i
                ));
            }
        }
        
        int leftX = 20;
        int rightX = this.width - LEFT_PANEL_WIDTH - 20;
        int y = this.height - 40;
        
        this.buttonList.add(new GuiButton(
                0,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Add Entry"
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
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                MobEquipmentReloadListener.EnchantData.Predefined p;
                
                if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined) {
                    p = (MobEquipmentReloadListener.EnchantData.Predefined) item.enchant;
                } else {
                    p = new MobEquipmentReloadListener.EnchantData.Predefined(
                            new ArrayList<>(),
                            new ArrayList<>()
                    );
                    item.enchant = p;
                }
                
                p.ids().add("minecraft:unbreaking");
                p.levels().add(1);
                
                this.mc.displayGuiScreen(new EditScreenEnchants(
                        main, difficultyGroup, biomeGroup, set, slot, item
                ));
                break;
            
            case 1:
                this.mc.displayGuiScreen(new EditScreenEnchantEntry(
                        main, difficultyGroup, biomeGroup, set, slot, item
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
                "Enchantments",
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
                        else mc.displayGuiScreen(EditScreenEnchants.this);
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
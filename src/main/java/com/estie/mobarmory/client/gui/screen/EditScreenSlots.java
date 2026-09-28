package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.input.Keyboard;

import java.io.IOException;
import java.util.List;

public class EditScreenSlots extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    private static final EntityEquipmentSlot[] SLOTS = {
            EntityEquipmentSlot.HEAD,
            EntityEquipmentSlot.CHEST,
            EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET,
            EntityEquipmentSlot.MAINHAND,
            EntityEquipmentSlot.OFFHAND
    };
    
    public EditScreenSlots(
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
        int leftX = 20;
        int y = 40;
        
        int id = 0;
        
        for (EntityEquipmentSlot slot : SLOTS) {
            List<MobEquipmentReloadListener.WeightedItem> items = set.slots.get(slot);
            int count = items != null ? items.size() : 0;
            
            this.buttonList.add(new GuiButton(
                    id++,
                    leftX,
                    y,
                    LEFT_PANEL_WIDTH,
                    20,
                    slotLabel(slot) + " (" + count + ")"
            ));
            
            y += 24;
        }
        
        this.buttonList.add(new GuiButton(
                id,
                leftX,
                y + 10,
                LEFT_PANEL_WIDTH,
                20,
                "Back"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 0 && button.id < SLOTS.length) {
            EntityEquipmentSlot slot = SLOTS[button.id];
            
            this.mc.displayGuiScreen(new EditScreenSlotItems(
                    main,
                    difficultyGroup,
                    biomeGroup,
                    set,
                    slot
            ));
            return;
        }
        
        if (button.id == SLOTS.length) {
            this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                    main,
                    difficultyGroup,
                    biomeGroup,
                    set
            ));
        }
    }
    
    // Self-contained label matching the schema's JSON string keys exactly.
    static String slotLabel(EntityEquipmentSlot slot) {
        switch (slot) {
            case HEAD:
                return "head";
            case CHEST:
                return "chest";
            case LEGS:
                return "legs";
            case FEET:
                return "feet";
            case MAINHAND:
                return "mainhand";
            case OFFHAND:
                return "offhand";
            default:
                throw new IllegalArgumentException("Unknown equipment slot: " + slot);
        }
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        this.drawCenteredString(
                this.fontRenderer,
                "Equipment slots",
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
                        else mc.displayGuiScreen(EditScreenSlots.this);
                    },
                    "Exit Editor",
                    "Are you sure you want to exit? Unsaved changes will be lost.",
                    0
            ));
            return;
        }
        
        super.keyTyped(typedChar, keyCode);
    }
}
package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.WeightedItemList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class EditScreenSlotItems extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EntityEquipmentSlot slot;
    
    private WeightedItemList list;
    
    private static final int LEFT_PANEL_WIDTH = 200;
    
    public EditScreenSlotItems(
            EditScreenMain main,
            MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
            MobEquipmentReloadListener.BiomeGroup biomeGroup,
            MobEquipmentReloadListener.EquipmentSet set,
            EntityEquipmentSlot slot) {
        
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
    }
    
    @Override
    public void initGui() {
        this.list = new WeightedItemList(
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
                slot
        );
        
        List<MobEquipmentReloadListener.WeightedItem> items =
                set.slots.get(slot);
        
        if (items != null) {
            list.entries.addAll(items);
        }
        
        int leftX = 20;
        int rightX = this.width - LEFT_PANEL_WIDTH - 20;
        int btnWidth = LEFT_PANEL_WIDTH;
        
        this.buttonList.add(new GuiButton(
                0,
                leftX,
                this.height - 40,
                btnWidth,
                20,
                "Add Item"
        ));
        
        this.buttonList.add(new GuiButton(
                1,
                rightX,
                this.height - 40,
                btnWidth,
                20,
                "Back"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Add Item (e.g. minecraft:iron_sword)",
                        "",
                        value -> {
                            String raw = value.trim();
                            
                            if (!raw.contains(":")) {
                                raw = "minecraft:" + raw;
                            }
                            
                            MobEquipmentReloadListener.WeightedItem newItem =
                                    new MobEquipmentReloadListener.WeightedItem(
                                            raw,
                                            1,
                                            null
                                    );
                            
                            if (!set.slots.containsKey(slot)) {
                                set.slots.put(
                                        slot,
                                        new ArrayList<>()
                                );
                            }
                            
                            set.slots.get(slot).add(newItem);
                            
                            this.mc.displayGuiScreen(new EditScreenSlotItems(
                                    main,
                                    difficultyGroup,
                                    biomeGroup,
                                    set,
                                    slot
                            ));
                        }
                ));
                break;
            
            case 1:
                this.mc.displayGuiScreen(new EditScreenSlots(
                        main,
                        difficultyGroup,
                        biomeGroup,
                        set
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
                EditScreenSlots.slotLabel(slot) + " items",
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
                        else mc.displayGuiScreen(EditScreenSlotItems.this);
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
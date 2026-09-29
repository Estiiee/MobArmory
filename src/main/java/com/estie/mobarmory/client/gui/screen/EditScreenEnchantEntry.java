package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;

public class EditScreenEnchantEntry extends GuiScreen {
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EntityEquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenEnchantEntry(EditScreenMain main,
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
        int leftX = 20;
        int y = 40;
        
        String modeLabel;
        if (item.enchant == null) {
            modeLabel = "None";
        } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
            modeLabel = "Random";
        } else {
            modeLabel = "Predefined";
        }
        
        this.buttonList.add(new GuiButton(
                0, leftX, y, LEFT_PANEL_WIDTH, 20,
                "Current Type: " + modeLabel
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                1, leftX, y, LEFT_PANEL_WIDTH, 20,
                "Random Power Level"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                2, leftX, y, LEFT_PANEL_WIDTH, 20,
                "Predefined Enchantments"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                3, leftX, y, LEFT_PANEL_WIDTH, 20,
                "Back"
        ));
        
        this.buttonList.add(new GuiButton(4, this.width / 2 - 50, this.height - 40, 100, 20, "Save"));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) {
        switch (button.id) {
            case 0:
                if (item.enchant == null) {
                    item.enchant = new MobEquipmentReloadListener.EnchantData.Random(30);
                } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
                    item.enchant = new MobEquipmentReloadListener.EnchantData.Predefined(
                            new ArrayList<>(),
                            new ArrayList<>()
                    );
                } else {
                    item.enchant = null;
                }
                
                this.mc.displayGuiScreen(new EditScreenEnchantEntry(
                        main, difficultyGroup, biomeGroup, set, slot, item
                ));
                break;
            
            case 1:
                int currentPower = 30;
                
                if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
                    currentPower = ((MobEquipmentReloadListener.EnchantData.Random) item.enchant).power();
                }
                
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Random Enchant Power",
                        "" + currentPower,
                        value -> {
                            try {
                                int p = Math.max(1, Integer.parseInt(value));
                                item.enchant = new MobEquipmentReloadListener.EnchantData.Random(p);
                            } catch (Exception ignored) {}
                            
                            this.mc.displayGuiScreen(new EditScreenEnchantEntry(
                                    main, difficultyGroup, biomeGroup, set, slot, item
                            ));
                        }
                ));
                break;
            
            case 2:
                if (!(item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined)) {
                    item.enchant = new MobEquipmentReloadListener.EnchantData.Predefined(
                            new ArrayList<>(),
                            new ArrayList<>()
                    );
                }
                
                this.mc.displayGuiScreen(new EditScreenEnchants(
                        main, difficultyGroup, biomeGroup, set, slot, item
                ));
                break;
            
            case 3:
                this.mc.displayGuiScreen(new EditScreenWeightedItemEntry(
                        main, difficultyGroup, biomeGroup, set, slot, item
                ));
                break;
            
            case 4:
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
                EditScreenShared.crumbBiomeGroup(main, difficultyGroup, biomeGroup),
                EditScreenShared.crumbSet(main, difficultyGroup, biomeGroup, set),
                EditScreenShared.crumbSlot(main, difficultyGroup, biomeGroup, set, slot),
                EditScreenShared.crumbItem(main, difficultyGroup, biomeGroup, set, slot, item),
                EditScreenShared.current(EditScreenSlots.slotLabel(slot) + " enchant")
        ));
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int infoY = 60 + PREVIEW_SIZE + 12;
        
        String modeLabel;
        if (item.enchant == null) {
            modeLabel = "None";
        } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
            modeLabel = "Random";
        } else {
            modeLabel = "Predefined";
        }
        
        this.drawCenteredString(
                this.fontRenderer,
                "Type: " + modeLabel,
                previewX + PREVIEW_SIZE / 2,
                infoY,
                0xFFFFFF
        );
        
        if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
            MobEquipmentReloadListener.EnchantData.Random random =
                    (MobEquipmentReloadListener.EnchantData.Random) item.enchant;
            
            this.drawCenteredString(
                    this.fontRenderer,
                    "Power: " + random.power(),
                    previewX + PREVIEW_SIZE / 2,
                    infoY + 14,
                    0xAAAAAA
            );
        } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined) {
            MobEquipmentReloadListener.EnchantData.Predefined predefined =
                    (MobEquipmentReloadListener.EnchantData.Predefined) item.enchant;
            
            this.drawCenteredString(
                    this.fontRenderer,
                    "Entries: " + predefined.ids().size(),
                    previewX + PREVIEW_SIZE / 2,
                    infoY + 14,
                    0xAAAAAA
            );
        }
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
                        else mc.displayGuiScreen(EditScreenEnchantEntry.this);
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
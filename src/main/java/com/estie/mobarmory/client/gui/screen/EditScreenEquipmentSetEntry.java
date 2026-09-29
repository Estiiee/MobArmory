package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class EditScreenEquipmentSetEntry extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenEquipmentSetEntry(EditScreenMain main,
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
        int rightX = leftX + LEFT_PANEL_WIDTH + 10;
        
        int leftCount = 0;
        int rightCount = 0;
        
        // LEFT COLUMN
        
        this.buttonList.add(new GuiButton(
                0, leftX, 40 + leftCount++ * 24, LEFT_PANEL_WIDTH, 20, "Name"
        ));
        
        this.buttonList.add(new GuiButton(
                1, leftX, 40 + leftCount++ * 24, LEFT_PANEL_WIDTH, 20, "Weight"
        ));
        
        this.buttonList.add(new GuiButton(
                2, leftX, 40 + leftCount++ * 24, LEFT_PANEL_WIDTH, 20, "Slots"
        ));
        
        this.buttonList.add(new GuiButton(
                3, leftX, 40 + leftCount++ * 24, LEFT_PANEL_WIDTH, 20, "Loot Table"
        ));
        
        this.buttonList.add(new GuiButton(
                4, leftX, 40 + leftCount++ * 24, LEFT_PANEL_WIDTH, 20, "Mob NBT"
        ));
        
        // RIGHT COLUMN
        
        this.buttonList.add(new GuiButton(
                5, rightX, 40 + rightCount++ * 24, LEFT_PANEL_WIDTH, 20,
                "Potion Effects (" + set.potionEffects.size() + ")"
        ));
        
        this.buttonList.add(new GuiButton(
                6, rightX, 40 + rightCount++ * 24, LEFT_PANEL_WIDTH, 20, "Time of Day"
        ));
        
        this.buttonList.add(new GuiButton(
                7, rightX, 40 + rightCount++ * 24, LEFT_PANEL_WIDTH, 20, "Y Level"
        ));
        
        this.buttonList.add(new GuiButton(
                8, rightX, 40 + rightCount++ * 24, LEFT_PANEL_WIDTH, 20, "Delete Set"
        ));
        
        this.buttonList.add(new GuiButton(
                9, rightX, 40 + rightCount++ * 24, LEFT_PANEL_WIDTH, 20, "Back"
        ));
        
        this.buttonList.add(new GuiButton(10, this.width / 2 - 50, this.height - 40, 100, 20, "Save"));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Name (identifier only, optional)",
                        set.name != null ? set.name : "",
                        value -> {
                            set.name = value.trim().isEmpty() ? null : value;
                            this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                                    main, difficultyGroup, biomeGroup, set
                            ));
                        }
                ));
                break;
            
            case 1:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Weight (relative pick chance)",
                        "" + set.weight,
                        value -> {
                            try {
                                set.weight = Math.max(1, Integer.parseInt(value));
                            } catch (Exception ignored) {
                            }
                        }
                ));
                break;
            
            case 2:
                this.mc.displayGuiScreen(new EditScreenSlots(
                        main, difficultyGroup, biomeGroup, set
                ));
                break;
            
            case 3:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Loot Table (e.g. mobarmory:blaze_inferno_loot)",
                        set.lootTable != null ? set.lootTable : "",
                        value -> {
                            set.lootTable = value.trim().isEmpty() ? null : value;
                            this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                                    main, difficultyGroup, biomeGroup, set
                            ));
                        }
                ));
                break;
            
            case 4:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Mob NBT (e.g. CustomName: \"Boss\")",
                        set.mobNbt != null ? set.mobNbt : "",
                        value -> {
                            set.mobNbt = value.trim().isEmpty() ? null : value;
                            this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                                    main, difficultyGroup, biomeGroup, set
                            ));
                        },
                        EditScreenShared::nbtValid,
                        "Warning: invalid NBT syntax",
                        true,
                        true
                ));
                break;
            
            case 5:
                this.mc.displayGuiScreen(new EditScreenPotionEffects(
                        main, difficultyGroup, biomeGroup, set
                ));
                break;
            
            case 6:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Time of Day (e.g. 18:00-6:00, blank = always)",
                        MobEquipmentReloadListener.isTimeUnrestricted(set.timeOfDay)
                                ? ""
                                : MobEquipmentReloadListener.timeRangeToString(set.timeOfDay),
                        value -> {
                            try {
                                set.timeOfDay = value.trim().isEmpty()
                                        ? new MobEquipmentReloadListener.TimeRange(0, 24000)
                                        : MobEquipmentReloadListener.parseTimeRange(value);
                            } catch (Exception ignored) {
                            }
                            
                            this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                                    main, difficultyGroup, biomeGroup, set
                            ));
                        },
                        EditScreenShared::timeRangeValid,
                        "Warning: invalid format (use HH:MM-HH:MM)",
                        true
                ));
                break;
            
            case 7:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Y Level (e.g. <64, >=0, 40; blank = always)",
                        MobEquipmentReloadListener.isYLevelUnrestricted(set.yLevel)
                                ? ""
                                : MobEquipmentReloadListener.yLevelToString(set.yLevel),
                        value -> {
                            try {
                                set.yLevel = value.trim().isEmpty()
                                        ? new MobEquipmentReloadListener.YLevelCondition(
                                        MobEquipmentReloadListener.YComparator.LT, 350)
                                        : MobEquipmentReloadListener.parseYLevel(value);
                            } catch (Exception ignored) {
                            }
                            
                            this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                                    main, difficultyGroup, biomeGroup, set
                            ));
                        },
                        EditScreenShared::yLevelValid,
                        "Warning: invalid format (e.g. <64, >=0, 40)",
                        true
                ));
                break;
            
            case 8:
                biomeGroup.sets.remove(set);
                this.mc.displayGuiScreen(new EditScreenEquipmentSets(
                        main, difficultyGroup, biomeGroup
                ));
                break;
            
            case 9:
                this.mc.displayGuiScreen(new EditScreenEquipmentSets(
                        main, difficultyGroup, biomeGroup
                ));
                break;
            
            case 10:
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
        
        EditScreenShared.renderHeader(
                this.mc,
                main.entry,
                this.width,
                PREVIEW_SIZE,
                Arrays.asList(
                        EditScreenShared.crumbMain(main.entry),
                        EditScreenShared.crumbDifficultyGroup(main, difficultyGroup),
                        EditScreenShared.crumbBiomeGroup(main, difficultyGroup, biomeGroup),
                        EditScreenShared.current(set.name != null ? set.name : "Equipment Set")
                )
        );
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int infoY = 60 + PREVIEW_SIZE + 12;
        
        this.drawCenteredString(
                this.fontRenderer,
                "Name: " + (set.name != null ? set.name : "(unnamed)"),
                previewX + PREVIEW_SIZE / 2,
                infoY,
                0xFFFFFF
        );
        
        this.drawCenteredString(
                this.fontRenderer,
                "Weight: " + set.weight,
                previewX + PREVIEW_SIZE / 2,
                infoY + 14,
                0xAAAAAA
        );
        
        int itemCount = 0;
        for (List<MobEquipmentReloadListener.WeightedItem> items : set.slots.values()) {
            itemCount += items.size();
        }
        
        this.drawCenteredString(
                this.fontRenderer,
                "Items: " + itemCount + " across " + set.slots.size() + " slot(s)",
                previewX + PREVIEW_SIZE / 2,
                infoY + 28,
                0xAAAAAA
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
                        else mc.displayGuiScreen(EditScreenEquipmentSetEntry.this);
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
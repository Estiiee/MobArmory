package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.inventory.EntityEquipmentSlot;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Arrays;
import java.util.List;

public class EditScreenWeightedItemEntry extends GuiScreen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EntityEquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenWeightedItemEntry(
            EditScreenMain main,
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
        
        this.buttonList.add(new GuiButton(
                0,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Item"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                1,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "NBT"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                2,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Weight"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                3,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Enchant"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                4,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Delete Item"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                5,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Back"
        ));
        
        this.buttonList.add(new GuiButton(6, this.width / 2 - 50, this.height - 40, 100, 20, "Save"));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Item (e.g. minecraft:iron_sword)",
                        item.itemId != null ? item.itemId : "",
                        value -> {
                            String raw = value.trim();
                            
                            if (!raw.contains(":")) {
                                raw = "minecraft:" + raw;
                            }
                            
                            item.itemId = raw;
                            
                            this.mc.displayGuiScreen(
                                    new EditScreenWeightedItemEntry(
                                            main,
                                            difficultyGroup,
                                            biomeGroup,
                                            set,
                                            slot,
                                            item
                                    )
                            );
                        },
                        EditScreenShared::itemExists,
                        "Warning: item not found",
                        false
                ));
                break;
            
            case 1:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Item NBT (e.g. DifficultyMode: \"expert\")",
                        item.nbt != null ? item.nbt : "",
                        value -> {
                            item.nbt = value.trim().isEmpty() ? null : value;
                            
                            this.mc.displayGuiScreen(
                                    new EditScreenWeightedItemEntry(
                                            main,
                                            difficultyGroup,
                                            biomeGroup,
                                            set,
                                            slot,
                                            item
                                    )
                            );
                        },
                        EditScreenShared::nbtValid,
                        "Warning: invalid NBT syntax",
                        true
                ));
                break;
            
            case 2:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Weight (relative pick chance)",
                        "" + item.weight,
                        value -> {
                            try {
                                item.weight = Math.max(1, Integer.parseInt(value));
                            } catch (Exception ignored) {
                            }
                        }
                ));
                break;
            
            case 3:
                this.mc.displayGuiScreen(new EditScreenEnchantEntry(
                        main,
                        difficultyGroup,
                        biomeGroup,
                        set,
                        slot,
                        item
                ));
                break;
            
            case 4:
                List<MobEquipmentReloadListener.WeightedItem> items = set.slots.get(slot);
                
                if (items != null) {
                    items.remove(item);
                    
                    // Keep the invariant that an existing slot key always has items.
                    if (items.isEmpty()) {
                        set.slots.remove(slot);
                    }
                }
                
                this.mc.displayGuiScreen(new EditScreenSlotItems(
                        main,
                        difficultyGroup,
                        biomeGroup,
                        set,
                        slot
                ));
                break;
            
            case 5:
                this.mc.displayGuiScreen(new EditScreenSlotItems(
                        main,
                        difficultyGroup,
                        biomeGroup,
                        set,
                        slot
                ));
                break;
                
            case 6:
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
                width,
                PREVIEW_SIZE,
                Arrays.asList(
                        EditScreenShared.crumbMain(main.entry),
                        EditScreenShared.crumbDifficultyGroup(main, difficultyGroup),
                        EditScreenShared.crumbBiomeGroup(main, difficultyGroup, biomeGroup),
                        EditScreenShared.crumbSet(main, difficultyGroup, biomeGroup, set),
                        EditScreenShared.crumbSlot(main, difficultyGroup, biomeGroup, set, slot),
                        EditScreenShared.current(EditScreenSlots.slotLabel(slot) + " item")
                )
        );
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int infoY = 60 + PREVIEW_SIZE + 12;
        
        String raw = "Item: " + item.itemId;
        
        List<String> lines = this.fontRenderer.listFormattedStringToWidth(
                raw,
                PREVIEW_SIZE
        );
        
        int dy = 0;
        int maxLines = Math.min(3, lines.size());
        
        for (int i = 0; i < maxLines; i++) {
            this.drawCenteredString(
                    this.fontRenderer,
                    lines.get(i),
                    previewX + PREVIEW_SIZE / 2,
                    infoY + dy,
                    0xFFFFFF
            );
            
            dy += this.fontRenderer.FONT_HEIGHT;
        }
        
        int usedHeight = dy;
        
        this.drawCenteredString(
                this.fontRenderer,
                "Weight: " + item.weight,
                previewX + PREVIEW_SIZE / 2,
                infoY + usedHeight + 2,
                0xAAAAAA
        );
        
        String enchantLabel;
        
        if (item.enchant == null) {
            enchantLabel = "None";
        } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
            enchantLabel = "Random";
        } else {
            enchantLabel = "Predefined";
        }
        
        this.drawCenteredString(
                this.fontRenderer,
                "Enchant: " + enchantLabel,
                previewX + PREVIEW_SIZE / 2,
                infoY + usedHeight + 2 + 14,
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
                        else mc.displayGuiScreen(EditScreenWeightedItemEntry.this);
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
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick) {
        
        if (EditScreenShared.mouseDragged(mouseX, mouseY)) {
            return;
        }
        
        super.mouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
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
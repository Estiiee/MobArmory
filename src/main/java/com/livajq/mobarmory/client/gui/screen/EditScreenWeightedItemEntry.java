package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class EditScreenWeightedItemEntry extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenWeightedItemEntry(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                                       MobEquipmentReloadListener.BiomeGroup biomeGroup, MobEquipmentReloadListener.EquipmentSet set,
                                       EquipmentSlot slot, MobEquipmentReloadListener.WeightedItem item) {
        super(Component.literal("Edit Item"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
        this.item = item;
    }
    
    @Override
    protected void init() {
        int leftX = 20;
        int y = 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Item"),
                btn -> {
                    String currentId = item.itemId;
                    this.minecraft.setScreen(new TextInputScreen(
                            this,
                            "Set Item (e.g. minecraft:iron_sword)",
                            currentId != null ? currentId.toString() : "",
                            value -> {
                                try {
                                    String raw = value.trim();
                                    if (!raw.contains(":")) raw = "minecraft:" + raw;
                                    
                                    item.itemId = raw;
                                } catch (Exception ignored) {}
                                this.minecraft.setScreen(new EditScreenWeightedItemEntry(main, difficultyGroup, biomeGroup, set, slot, item));
                            }
                    ));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Weight"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Set Weight (relative pick chance)",
                        "" + item.weight,
                        value -> {
                            try {
                                item.weight = Math.max(1, Integer.parseInt(value));
                            } catch (Exception ignored) {}
                        }
                ))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Enchant"),
                btn -> this.minecraft.setScreen(new EditScreenEnchantEntry(main, difficultyGroup, biomeGroup, set, slot, item))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete Item"),
                btn -> {
                    List<MobEquipmentReloadListener.WeightedItem> items = set.slots.get(slot);
                    if (items != null) {
                        items.remove(item);
                        //keep the "a slot key only exists in the map if it has items" invariant
                        //that parseSet already relies on elsewhere
                        if (items.isEmpty()) set.slots.remove(slot);
                    }
                    this.minecraft.setScreen(new EditScreenSlotItems(main, difficultyGroup, biomeGroup, set, slot));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenSlotItems(main, difficultyGroup, biomeGroup, set, slot))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, this.font, main.entry, this.width, PREVIEW_SIZE,
                EditScreenSlots.slotLabel(slot) + " item");
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int infoY = 60 + PREVIEW_SIZE + 12;
        
        String raw = "Item: " + item.itemId;
        
        List<FormattedCharSequence> lines = this.font.split(Component.literal(raw), PREVIEW_SIZE);
        int dy = 0;
        int maxLines = Math.min(3, lines.size());
        
        for (int i = 0; i < maxLines; i++) {
            FormattedCharSequence line = lines.get(i);
            gfx.drawCenteredString(this.font,
                    line,
                    previewX + PREVIEW_SIZE / 2,
                    infoY + dy,
                    0xFFFFFF);
            dy += this.font.lineHeight;
        }
        
        int usedHeight = dy;
        
        gfx.drawCenteredString(this.font, "Weight: " + item.weight,
                previewX + PREVIEW_SIZE / 2,
                infoY + usedHeight + 2,
                0xAAAAAA);
        
        String enchantLabel = item.enchant == null ? "None"
                : item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random ? "Random" : "Predefined";
        
        gfx.drawCenteredString(this.font, "Enchant: " + enchantLabel,
                previewX + PREVIEW_SIZE / 2,
                infoY + usedHeight + 2 + 14,
                0xAAAAAA);
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) this.minecraft.setScreen(null);
                        else this.minecraft.setScreen(this);
                    },
                    Component.literal("Exit Editor"),
                    Component.literal("Are you sure you want to exit? Unsaved changes will be lost.")
            ));
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class EditScreenEnchantEntry extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenEnchantEntry(EditScreenMain main,
                                  MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                                  MobEquipmentReloadListener.BiomeGroup biomeGroup,
                                  MobEquipmentReloadListener.EquipmentSet set,
                                  EquipmentSlot slot,
                                  MobEquipmentReloadListener.WeightedItem item) {
        super(Component.literal("Edit Enchant"));
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
        
        String modeLabel;
        if (item.enchant == null) modeLabel = "None";
        else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) modeLabel = "Random";
        else modeLabel = "Predefined";
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Current Type: " + modeLabel),
                btn -> {
                    if (item.enchant == null) {
                        item.enchant = new MobEquipmentReloadListener.EnchantData.Random(30);
                    } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
                        item.enchant = new MobEquipmentReloadListener.EnchantData.Predefined(
                                new ArrayList<>(), new ArrayList<>()
                        );
                    } else {
                        item.enchant = null;
                    }
                    this.minecraft.setScreen(new EditScreenEnchantEntry(
                            main, difficultyGroup, biomeGroup, set, slot, item
                    ));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        // --- RANDOM POWER BUTTON ---
        this.addRenderableWidget(Button.builder(
                Component.literal("Random Power Level"),
                btn -> {
                    int currentPower = 30;
                    if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random r) {
                        currentPower = r.power();
                    }
                    
                    this.minecraft.setScreen(new TextInputScreen(
                            this,
                            "Set Random Enchant Power",
                            "" + currentPower,
                            value -> {
                                try {
                                    int p = Math.max(1, Integer.parseInt(value));
                                    item.enchant = new MobEquipmentReloadListener.EnchantData.Random(p);
                                } catch (Exception ignored) {}
                                this.minecraft.setScreen(new EditScreenEnchantEntry(
                                        main, difficultyGroup, biomeGroup, set, slot, item
                                ));
                            }
                    ));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Predefined Enchantments"),
                btn -> {
                    if (!(item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined)) {
                        item.enchant = new MobEquipmentReloadListener.EnchantData.Predefined(
                                new ArrayList<>(), new ArrayList<>()
                        );
                    }
                    this.minecraft.setScreen(new EditScreenEnchants(
                            main, difficultyGroup, biomeGroup, set, slot, item
                    ));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenWeightedItemEntry(
                        main, difficultyGroup, biomeGroup, set, slot, item
                ))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, font, main.entry, width, PREVIEW_SIZE, List.of(
                EditScreenShared.crumbMain(main.entry),
                EditScreenShared.crumbDifficultyGroup(main, difficultyGroup),
                EditScreenShared.crumbBiomeGroup(main, difficultyGroup, biomeGroup),
                EditScreenShared.crumbSet(main, difficultyGroup, biomeGroup, set),
                EditScreenShared.crumbSlot(main, difficultyGroup, biomeGroup, set, slot),
                EditScreenShared.crumbItem(main, difficultyGroup, biomeGroup, set, slot, item),
                EditScreenShared.current(EditScreenSlots.slotLabel(slot) + " enchant")));
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int infoY = 60 + PREVIEW_SIZE + 12;
        
        String modeLabel = item.enchant == null ? "None"
                : item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random ? "Random"
                : "Predefined";
        
        gfx.drawCenteredString(this.font,
                "Type: " + modeLabel,
                previewX + PREVIEW_SIZE / 2,
                infoY,
                0xFFFFFF);
        
        if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Random r) {
            gfx.drawCenteredString(this.font,
                    "Power: " + r.power(),
                    previewX + PREVIEW_SIZE / 2,
                    infoY + 14,
                    0xAAAAAA);
        } else if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined p) {
            gfx.drawCenteredString(this.font,
                    "Entries: " + p.ids().size(),
                    previewX + PREVIEW_SIZE / 2,
                    infoY + 14,
                    0xAAAAAA);
        }
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
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (EditScreenShared.breadcrumbClicked(mouseX, mouseY)) return true;
        if (EditScreenShared.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (EditScreenShared.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (EditScreenShared.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (EditScreenShared.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
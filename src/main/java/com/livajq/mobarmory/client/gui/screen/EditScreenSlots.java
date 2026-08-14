package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class EditScreenSlots extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    
    public EditScreenSlots(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                           MobEquipmentReloadListener.BiomeGroup biomeGroup, MobEquipmentReloadListener.EquipmentSet set) {
        super(Component.literal("Slots"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
    }
    
    @Override
    protected void init() {
        int leftX = 20;
        int y = 40;
        
        for (EquipmentSlot slot : SLOTS) {
            int count = set.slots.getOrDefault(slot, List.of()).size();
            
            this.addRenderableWidget(Button.builder(
                    Component.literal(slotLabel(slot) + " (" + count + ")"),
                    btn -> this.minecraft.setScreen(new EditScreenSlotItems(main, difficultyGroup, biomeGroup, set, slot))
            ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
            y += 24;
        }
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set))
        ).bounds(leftX, y + 10, LEFT_PANEL_WIDTH, 20).build());
    }
    
    //self-contained label matching the schema's json string keys exactly, rather than relying on
    //EquipmentSlot's own getName()/toString() format, which isn't something worth guessing at
    static String slotLabel(EquipmentSlot slot) {
        return switch (slot) {
            case HEAD -> "head";
            case CHEST -> "chest";
            case LEGS -> "legs";
            case FEET -> "feet";
            case MAINHAND -> "mainhand";
            case OFFHAND -> "offhand";
        };
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "Equipment slots", this.width / 2, 15, 0xFFFFFF);
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
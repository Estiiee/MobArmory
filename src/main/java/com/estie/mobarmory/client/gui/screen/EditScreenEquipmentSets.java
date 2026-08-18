package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.EquipmentSetList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.EnumMap;

public class EditScreenEquipmentSets extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private EquipmentSetList list;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    public EditScreenEquipmentSets(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup) {
        super(Component.literal("Equipment Sets"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
    }
    
    @Override
    protected void init() {
        this.list = new EquipmentSetList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
        
        for (MobEquipmentReloadListener.EquipmentSet set : biomeGroup.sets) {
            list.children().add(new EquipmentSetList.Entry(set, main, difficultyGroup, biomeGroup));
        }
        
        this.addWidget(list);
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Set"),
                btn -> {
                    MobEquipmentReloadListener.EquipmentSet newSet =
                            new MobEquipmentReloadListener.EquipmentSet("Unnamed Set", 1, new EnumMap<>(EquipmentSlot.class));
                    
                    biomeGroup.sets.add(newSet);
                    this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, newSet));
                }
        ).bounds(20, this.height - 40, LEFT_PANEL_WIDTH, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenBiomeGroupEntry(main, difficultyGroup, biomeGroup))
        ).bounds(this.width - LEFT_PANEL_WIDTH - 20, this.height - 40, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "Equipment sets", this.width / 2, 15, 0xFFFFFF);
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
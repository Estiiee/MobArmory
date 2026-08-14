package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.client.gui.widget.BiomeGroupList;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class EditScreenBiomeGroups extends Screen
{
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private BiomeGroupList list;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    public EditScreenBiomeGroups(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup) {
        super(Component.literal("Biome Groups"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
    }
    
    @Override
    protected void init() {
        
        this.list = new BiomeGroupList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
        
        for (MobEquipmentReloadListener.BiomeGroup group : difficultyGroup.biomeGroups) {
            list.children().add(new BiomeGroupList.Entry(group, main, difficultyGroup));
        }
        
        this.addWidget(list);
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Group"),
                btn -> {
                    MobEquipmentReloadListener.BiomeGroup newGroup =
                            new MobEquipmentReloadListener.BiomeGroup(
                                    new ArrayList<>(List.of(new MobEquipmentReloadListener.BiomeMatch.Global())),
                                    0.0F,
                                    new ArrayList<>()
                            );
                    
                    difficultyGroup.biomeGroups.add(newGroup);
                    this.minecraft.setScreen(new EditScreenBiomeGroupEntry(main, difficultyGroup, newGroup));
                }
        ).bounds(20, this.height - 40, LEFT_PANEL_WIDTH, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenDifficultyGroupEntry(main, difficultyGroup))
        ).bounds(this.width - LEFT_PANEL_WIDTH - 20, this.height - 40, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "Biome groups", this.width / 2, 15, 0xFFFFFF);
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
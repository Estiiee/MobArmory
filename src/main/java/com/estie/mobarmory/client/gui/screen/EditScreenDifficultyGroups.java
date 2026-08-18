package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.DifficultyGroupList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class EditScreenDifficultyGroups extends Screen {
    
    private final EditScreenMain main;
    private DifficultyGroupList list;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    public EditScreenDifficultyGroups(EditScreenMain main) {
        super(Component.literal("Difficulty Groups"));
        this.main = main;
    }
    
    @Override
    protected void init() {
        
        this.list = new DifficultyGroupList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
        
        for (int i = 0; i < main.entry.difficultyGroups.size(); i++) {
            MobEquipmentReloadListener.DifficultyGroup group = main.entry.difficultyGroups.get(i);
            list.children().add(new DifficultyGroupList.Entry(group, main, main.entry.difficultyGroups.get(i).matchers));
        }
        
        this.addWidget(list);
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Group"),
                btn -> {
                    MobEquipmentReloadListener.DifficultyGroup newGroup =
                            new MobEquipmentReloadListener.DifficultyGroup(
                                    new ArrayList<>(List.of(MobEquipmentReloadListener.DifficultyLevel.GLOBAL)),
                                    0.0F,
                                    new ArrayList<>(),
                                    new ArrayList<>()
                            );
                    
                    main.entry.difficultyGroups.add(newGroup);
                    this.minecraft.setScreen(new EditScreenDifficultyGroupEntry(main, newGroup));
                }
        ).bounds(20, this.height - 40, LEFT_PANEL_WIDTH, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(main)
        ).bounds(this.width - LEFT_PANEL_WIDTH - 20, this.height - 40, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "Difficulty groups", this.width / 2, 15, 0xFFFFFF);
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

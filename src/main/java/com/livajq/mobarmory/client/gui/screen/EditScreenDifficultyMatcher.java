package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.HashSet;
import java.util.Set;

public class EditScreenDifficultyMatcher extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup group;
    
    private final Set<MobEquipmentReloadListener.DifficultyLevel> selected = new HashSet<>();
    
    private static final int LEFT_PANEL_WIDTH = 120;
    
    public EditScreenDifficultyMatcher(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup group) {
        super(Component.literal("Edit Matchers"));
        this.main = main;
        this.group = group;
        
        selected.addAll(group.matchers);
    }
    
    @Override
    protected void init() {
        int leftX = 20;
        int y = 40;
        
        for (MobEquipmentReloadListener.DifficultyLevel lvl : MobEquipmentReloadListener.DifficultyLevel.values()) {
            
            Button btn = Button.builder(
                    Component.literal(lvl.name()),
                    b -> {
                        if (selected.contains(lvl)) selected.remove(lvl);
                        else selected.add(lvl);
                        updateButtonColor(b, lvl);
                    }
            ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build();
            
            updateButtonColor(btn, lvl);
            this.addRenderableWidget(btn);
            
            y += 24;
        }
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                btn -> {
                    group.matchers.clear();
                    group.matchers.addAll(selected);
                    if (group.matchers.isEmpty()) group.matchers.add(MobEquipmentReloadListener.DifficultyLevel.GLOBAL);
                    this.minecraft.setScreen(new EditScreenDifficultyGroupEntry(main, group));
                }
        ).bounds(leftX, y + 10, LEFT_PANEL_WIDTH, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenDifficultyGroupEntry(main, group))
        ).bounds(leftX, y + 34, LEFT_PANEL_WIDTH, 20).build());
    }
    
    private void updateButtonColor(Button btn, MobEquipmentReloadListener.DifficultyLevel lvl) {
        if (selected.contains(lvl)) btn.setFGColor(0x00FF00);
        else btn.setFGColor(0xFF4444);
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        gfx.drawCenteredString(this.font,
                "Toggle difficulty matchers",
                this.width / 2,
                15,
                0xFFFFFF);
    }
}

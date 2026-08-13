package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class EditScreen extends Screen {
    
    private final MobEquipmentReloadListener.MobEquipmentEntry entry;
    
    public EditScreen(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        super(Component.literal(entry.fileName != null ? "Edit: " + entry.fileName : "New Mob Equipment Entry"));
        this.entry = entry;
    }
    
    @Override
    protected void init() {
        this.addRenderableWidget(Button.builder(Component.literal("Save (todo)"), btn -> {
            // still nothing to build here yet - just confirming the full tree survived the round trip
        }).bounds(this.width / 2 - 50, this.height / 2, 100, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        String mobLabel = entry.mob != null ? entry.mob.toString() : "(no mob chosen)";
        String groupsLabel = entry.difficultyGroups.size() + " difficulty group(s) loaded";
        gfx.drawCenteredString(this.font, mobLabel, this.width / 2, 20, 0xFFFFFF);
        gfx.drawCenteredString(this.font, groupsLabel, this.width / 2, 34, 0xAAAAAA);
    }
}
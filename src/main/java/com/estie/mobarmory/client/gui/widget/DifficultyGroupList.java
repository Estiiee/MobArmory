package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.EditScreenDifficultyGroupEntry;
import com.estie.mobarmory.client.gui.screen.EditScreenMain;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;

public class DifficultyGroupList
        extends SimpleEntryList<MobEquipmentReloadListener.DifficultyGroup> {
    
    private final EditScreenMain main;
    
    public DifficultyGroupList(
            Minecraft mc,
            int left,
            int top,
            int width,
            int height,
            int itemHeight,
            EditScreenMain main) {
        
        super(mc, left, top, width, height, itemHeight);
        this.main = main;
    }
    
    @Override
    protected void renderEntry(
            MobEquipmentReloadListener.DifficultyGroup group,
            int index,
            int x,
            int y,
            int rowWidth,
            boolean hovered) {
        
        StringBuilder builder = new StringBuilder();
        
        for (MobEquipmentReloadListener.DifficultyLevel difficultyLevel :
                group.matchers) {
            
            builder.append(difficultyLevel.toString());
            builder.append(" ");
        }
        
        mc.fontRenderer.drawString(
                builder.toString(),
                x + 4,
                y + 6,
                0xFFFFFF
        );
    }
    
    @Override
    protected void onEntryClicked(
            MobEquipmentReloadListener.DifficultyGroup group,
            int index) {
        
        mc.displayGuiScreen(
                new EditScreenDifficultyGroupEntry(main, group)
        );
    }
}
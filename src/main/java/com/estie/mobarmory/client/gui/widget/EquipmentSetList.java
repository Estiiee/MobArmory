package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.EditScreenEquipmentSetEntry;
import com.estie.mobarmory.client.gui.screen.EditScreenMain;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;

public class EquipmentSetList extends SimpleEntryList<MobEquipmentReloadListener.EquipmentSet> {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    
    public EquipmentSetList(
            Minecraft mc,
            int left,
            int top,
            int width,
            int height,
            int itemHeight,
            EditScreenMain main,
            MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
            MobEquipmentReloadListener.BiomeGroup biomeGroup) {
        
        super(mc, left, top, width, height, itemHeight);
        
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
    }
    
    @Override
    protected void renderEntry(
            MobEquipmentReloadListener.EquipmentSet set,
            int index,
            int x,
            int y,
            int rowWidth,
            boolean hovered) {
        
        String label = (set.name != null ? set.name : "(unnamed)")
                + " - weight " + set.weight;
        
        this.mc.fontRenderer.drawString(
                label,
                x + 4,
                y + 6,
                0xFFFFFF
        );
    }
    
    @Override
    protected void onEntryClicked(
            MobEquipmentReloadListener.EquipmentSet set,
            int index) {
        
        this.mc.displayGuiScreen(new EditScreenEquipmentSetEntry(
                main,
                difficultyGroup,
                biomeGroup,
                set
        ));
    }
}
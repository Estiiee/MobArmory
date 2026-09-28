package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.EditScreenBiomeGroupEntry;
import com.estie.mobarmory.client.gui.screen.EditScreenMain;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;

public class BiomeGroupList extends SimpleEntryList<MobEquipmentReloadListener.BiomeGroup> {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    
    public BiomeGroupList(Minecraft mc, int left, int top, int width, int height, int itemHeight,
                          EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup) {
        super(mc, left, top, width, height, itemHeight);
        this.main = main;
        this.difficultyGroup = difficultyGroup;
    }
    
    @Override
    protected void renderEntry(MobEquipmentReloadListener.BiomeGroup group, int index, int x, int y, int rowWidth, boolean hovered) {
        StringBuilder builder = new StringBuilder();
        for (MobEquipmentReloadListener.BiomeMatch match : group.matchers) {
            builder.append(MobEquipmentReloadListener.biomeMatchToString(match)).append(" ");
        }
        mc.fontRenderer.drawString(builder.toString(), x + 4, y + 6, 0xFFFFFF);
    }
    
    @Override
    protected void onEntryClicked(MobEquipmentReloadListener.BiomeGroup group, int index) {
        mc.displayGuiScreen(new EditScreenBiomeGroupEntry(main, difficultyGroup, group));
    }
}
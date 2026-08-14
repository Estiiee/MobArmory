package com.livajq.mobarmory.client.gui.widget;

import com.livajq.mobarmory.client.gui.screen.EditScreenBiomeGroupEntry;
import com.livajq.mobarmory.client.gui.screen.EditScreenMain;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

public class BiomeGroupList extends ObjectSelectionList<BiomeGroupList.Entry> {
    
    public BiomeGroupList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        
        private final MobEquipmentReloadListener.BiomeGroup group;
        private final EditScreenMain main;
        private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
        private final String raw;
        
        public Entry(MobEquipmentReloadListener.BiomeGroup group, EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup) {
            this.group = group;
            this.main = main;
            this.difficultyGroup = difficultyGroup;
            
            StringBuilder builder = new StringBuilder();
            for (MobEquipmentReloadListener.BiomeMatch match : group.matchers) {
                builder.append(MobEquipmentReloadListener.biomeMatchToString(match));
                builder.append(" ");
            }
            this.raw = builder.toString();
        }
        
        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            gfx.drawString(Minecraft.getInstance().font, raw, left + 4, top + 6, 0xFFFFFF);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Minecraft.getInstance().setScreen(new EditScreenBiomeGroupEntry(main, difficultyGroup, group));
            return true;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal("");
        }
    }
}
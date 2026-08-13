package com.livajq.mobarmory.client.gui.widget;

import com.livajq.mobarmory.client.gui.screen.EditScreenDifficultyGroupEntry;
import com.livajq.mobarmory.client.gui.screen.EditScreenMain;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

import java.util.List;

public class DifficultyGroupList extends ObjectSelectionList<DifficultyGroupList.Entry> {
    
    public DifficultyGroupList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        
        private final MobEquipmentReloadListener.DifficultyGroup group;
        private final EditScreenMain main;
        private final String raw;
        
        public Entry(MobEquipmentReloadListener.DifficultyGroup group, EditScreenMain main, List<MobEquipmentReloadListener.DifficultyLevel> difficultyLevels) {
            this.group = group;
            this.main = main;
            StringBuilder builder = new StringBuilder();
            for (MobEquipmentReloadListener.DifficultyLevel difficultyLevel : difficultyLevels) {
                builder.append(difficultyLevel.toString());
                builder.append(" ");
            }
            this.raw = builder.toString();
        }
        
        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            gfx.drawString(Minecraft.getInstance().font,
                    raw,
                    left + 4,
                    top + 6,
                    0xFFFFFF);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Minecraft.getInstance().setScreen(new EditScreenDifficultyGroupEntry(main, group));
            return true;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal("");
        }
    }
}

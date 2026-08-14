package com.livajq.mobarmory.client.gui.widget;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;

public class BiomeMatcherList extends ObjectSelectionList<BiomeMatcherList.Entry> {
    
    public BiomeMatcherList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        
        private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
        private final MobEquipmentReloadListener.BiomeMatch match;
        private final String label;
        private final Runnable onChanged;
        
        public Entry(MobEquipmentReloadListener.BiomeGroup biomeGroup, MobEquipmentReloadListener.BiomeMatch match, Runnable onChanged) {
            this.biomeGroup = biomeGroup;
            this.match = match;
            this.label = MobEquipmentReloadListener.biomeMatchToString(match);
            this.onChanged = onChanged;
        }
        
        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            gfx.drawString(Minecraft.getInstance().font, label, left + 4, top + 6, 0xFFFFFF);
            gfx.drawString(Minecraft.getInstance().font, "[x]", left + width - 20, top + 6, 0xFF5555);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            biomeGroup.matchers.remove(match);
            if (biomeGroup.matchers.isEmpty()) biomeGroup.matchers.add(new MobEquipmentReloadListener.BiomeMatch.Global());
            onChanged.run();
            return true;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
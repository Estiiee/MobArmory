package com.livajq.mobarmory.client.gui.widget;

import com.livajq.mobarmory.client.gui.screen.EditScreenMain;
import com.livajq.mobarmory.client.gui.screen.EditScreenWeightedItemEntry;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

public class WeightedItemList extends ObjectSelectionList<WeightedItemList.Entry> {
    
    public WeightedItemList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        
        private final MobEquipmentReloadListener.WeightedItem item;
        private final EditScreenMain main;
        private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
        private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
        private final MobEquipmentReloadListener.EquipmentSet set;
        private final EquipmentSlot slot;
        private final String label;
        
        public Entry(MobEquipmentReloadListener.WeightedItem item, EditScreenMain main,
                     MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup,
                     MobEquipmentReloadListener.EquipmentSet set, EquipmentSlot slot) {
            
            this.item = item;
            this.main = main;
            this.difficultyGroup = difficultyGroup;
            this.biomeGroup = biomeGroup;
            this.set = set;
            this.slot = slot;
            
            String enchantSuffix = item.enchant != null ? " [enchanted]" : "";
            this.label = item.itemId + " - weight " + item.weight + enchantSuffix;
        }
        
        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            gfx.drawString(Minecraft.getInstance().font, label, left + 4, top + 6, 0xFFFFFF);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Minecraft.getInstance().setScreen(new EditScreenWeightedItemEntry(main, difficultyGroup, biomeGroup, set, slot, item));
            return true;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.*;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;

public class EnchantList extends ObjectSelectionList<EnchantList.Entry> {
    
    public EnchantList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        
        private final String id;
        private final int level;
        private final EditScreenMain main;
        private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
        private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
        private final MobEquipmentReloadListener.EquipmentSet set;
        private final EquipmentSlot slot;
        private final MobEquipmentReloadListener.WeightedItem item;
        private final int index;
        
        private final String label;
        
        public Entry(String id, int level,
                     EditScreenMain main,
                     MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                     MobEquipmentReloadListener.BiomeGroup biomeGroup,
                     MobEquipmentReloadListener.EquipmentSet set,
                     EquipmentSlot slot,
                     MobEquipmentReloadListener.WeightedItem item,
                     int index) {
            
            this.id = id;
            this.level = level;
            this.main = main;
            this.difficultyGroup = difficultyGroup;
            this.biomeGroup = biomeGroup;
            this.set = set;
            this.slot = slot;
            this.item = item;
            this.index = index;
            
            this.label = id + " - lvl " + level;
        }
        
        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height,
                           int mouseX, int mouseY, boolean hovered, float partialTick) {
            
            int color = hovered ? 0xFFFFA0 : 0xFFFFFF;
            gfx.drawString(Minecraft.getInstance().font, label, left + 4, top + 6, color);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            
            MobEquipmentReloadListener.EnchantData.Predefined p =
                    (MobEquipmentReloadListener.EnchantData.Predefined) item.enchant;
            
            Minecraft mc = Minecraft.getInstance();
            
            Screen parent = new EditScreenEnchants(
                    main, difficultyGroup, biomeGroup, set, slot, item
            );
            
            mc.setScreen(new EnchantmentTextInputScreen(
                    parent, "Edit Enchant", id, String.valueOf(level),
                    (newId, newLevelStr) -> {
                        try {
                            int newLevel = Math.max(1, Integer.parseInt(newLevelStr));
                            p.ids().set(index, newId);
                            p.levels().set(index, newLevel);
                        } catch (Exception ignored) {}
                    },
                    () -> {
                        p.ids().remove(index);
                        p.levels().remove(index);
                    },
                    EditScreenShared::enchantExists,
                    "Warning: enchantment not found"
            ));
            
            return true;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
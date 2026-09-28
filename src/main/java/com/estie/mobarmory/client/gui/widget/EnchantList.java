package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.*;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.EntityEquipmentSlot;

public class EnchantList extends SimpleEntryList<EnchantList.Entry> {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EntityEquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    public EnchantList(
            Minecraft mc,
            int left,
            int top,
            int width,
            int height,
            int itemHeight,
            EditScreenMain main,
            MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
            MobEquipmentReloadListener.BiomeGroup biomeGroup,
            MobEquipmentReloadListener.EquipmentSet set,
            EntityEquipmentSlot slot,
            MobEquipmentReloadListener.WeightedItem item) {
        
        super(mc, left, top, width, height, itemHeight);
        
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
        this.item = item;
    }
    
    @Override
    protected void renderEntry(
            Entry entry,
            int index,
            int x,
            int y,
            int rowWidth,
            boolean hovered) {
        
        int color = hovered ? 0xFFFFA0 : 0xFFFFFF;
        
        mc.fontRenderer.drawString(
                entry.id + " - lvl " + entry.level,
                x + 4,
                y + 6,
                color
        );
    }
    
    @Override
    protected void onEntryClicked(Entry entry, int index) {
        if (!(item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined)) {
            return;
        }
        
        MobEquipmentReloadListener.EnchantData.Predefined p =
                (MobEquipmentReloadListener.EnchantData.Predefined) item.enchant;
        
        mc.displayGuiScreen(new EnchantmentTextInputScreen(
                new EditScreenEnchants(
                        main,
                        difficultyGroup,
                        biomeGroup,
                        set,
                        slot,
                        item
                ),
                "Edit Enchant",
                entry.id,
                String.valueOf(entry.level),
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
    }
    
    public static class Entry {
        private final String id;
        private final int level;
        
        public Entry(String id, int level, int index) {
            this.id = id;
            this.level = level;
        }
    }
}
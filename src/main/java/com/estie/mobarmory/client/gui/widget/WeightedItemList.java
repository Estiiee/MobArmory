package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.EditScreenMain;
import com.estie.mobarmory.client.gui.screen.EditScreenWeightedItemEntry;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.inventory.EntityEquipmentSlot;

public class WeightedItemList extends SimpleEntryList<MobEquipmentReloadListener.WeightedItem> {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EntityEquipmentSlot slot;
    
    public WeightedItemList(
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
            EntityEquipmentSlot slot) {
        
        super(mc, left, top, width, height, itemHeight);
        
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
    }
    
    @Override
    protected void renderEntry(
            MobEquipmentReloadListener.WeightedItem item,
            int index,
            int x,
            int y,
            int rowWidth,
            boolean hovered) {
        
        String enchantSuffix = item.enchant != null
                ? " [enchanted]"
                : "";
        
        String label = item.itemId
                + " - weight "
                + item.weight
                + enchantSuffix;
        
        this.mc.fontRenderer.drawString(
                label,
                x + 4,
                y + 6,
                0xFFFFFF
        );
    }
    
    @Override
    protected void onEntryClicked(
            MobEquipmentReloadListener.WeightedItem item,
            int index) {
        
        this.mc.displayGuiScreen(
                new EditScreenWeightedItemEntry(
                        main,
                        difficultyGroup,
                        biomeGroup,
                        set,
                        slot,
                        item
                )
        );
    }
}
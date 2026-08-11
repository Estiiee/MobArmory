package com.livajq.mobarmory.data;

import net.minecraftforge.fml.loading.FMLLoader;

public class MobEquipmentList {
    
    public static void init() {
        if (FMLLoader.isProduction()) return;
        
        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(1.0f)
                
                .biomeGroup()
                .match("#forge:is_snowy")
                .set()
                .slot("head").item("minecraft:diamond_helmet").endItem().endSlot()
                .slot("chest").item("minecraft:diamond_chestplate").endItem().endSlot()
                .slot("legs").item("minecraft:diamond_leggings").endItem().endSlot()
                .slot("feet").item("minecraft:diamond_boots").endItem().endSlot()
                .slot("mainhand")
                .item("minecraft:diamond_sword")
                .randomEnchant().power(15)
                .endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                
                .biomeGroup()
                .match("#forge:is_desert")
                .set()
                .slot("head").item("minecraft:golden_helmet").endItem().endSlot()
                .slot("chest").item("minecraft:golden_chestplate").endItem().endSlot()
                .slot("legs").item("minecraft:golden_leggings").endItem().endSlot()
                .slot("feet").item("minecraft:golden_boots").endItem().endSlot()
                .slot("mainhand")
                .item("minecraft:golden_sword")
                .predefinedEnchant()
                .addPredefined("minecraft:smite", 2)
                .endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                
                .createFile("skeleton");
        
        MobEquipmentBuilder.mob("minecraft:wither_skeleton")
                .chance(0.5f)
                
                .biomeGroup()
                .global()
                .set()
                .slot("head").item("minecraft:leather_helmet").endItem().endSlot()
                .slot("chest").item("minecraft:leather_chestplate").endItem().endSlot()
                .slot("legs").item("minecraft:leather_leggings").endItem().endSlot()
                .slot("feet").item("minecraft:leather_boots").endItem().endSlot()
                
                .slot("mainhand")
                .item("minecraft:iron_hoe")
                .randomEnchant() //random enchant, default power = 30
                .endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                
                .createFile("wither_skeleton");
        
        MobEquipmentBuilder.mob("minecraft:wolf")
                .chance(0.5f)
                .biomeGroup()
                .global()
                .set()
                .slot("head").item("minecraft:diamond_helmet").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .biomeGroup().endBiomeGroup()
                .createFile("wolf");
                
    }
}
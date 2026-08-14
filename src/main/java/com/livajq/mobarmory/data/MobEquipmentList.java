package com.livajq.mobarmory.data;

import net.minecraftforge.fml.loading.FMLLoader;

public class MobEquipmentList {
    
    public static void init() {
        if (FMLLoader.isProduction()) return;
        
        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(1.0f)
                
                .difficultyGroup().global()
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
                .endDifficultyGroup()
                
                .createFile("skeleton");
        
        MobEquipmentBuilder.mob("minecraft:wither_skeleton")
                .chance(0.5f)
                .difficultyGroup().global()
                .biomeGroup().global()
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
                .endDifficultyGroup()
                
                .createFile("wither_skeleton");
        
        MobEquipmentBuilder.mob("minecraft:wolf")
                .chance(0.5f)
                .difficultyGroup().global()
                .biomeGroup().global()
                .set()
                .slot("head").item("minecraft:diamond_helmet").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                .createFile("wolf");
        
        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(0.8f)
                
                .difficultyGroup().match("easy")
                .biomeGroup().match("#forge:is_snowy")
                .chance(0.5f)
                .set()
                .slot("mainhand").item("minecraft:stone_sword").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
           
                .difficultyGroup().match("hard")
                .biomeGroup().match("minecraft:desert")
                .set()
                .slot("mainhand").item("minecraft:iron_sword")
                .randomEnchant().power(10).endEnchant()
                .endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
              
                .difficultyGroup().global()
                .biomeGroup().global()
                .set()
                .slot("mainhand").item("minecraft:bow").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("skeleton_test");
        
        MobEquipmentBuilder.mob("minecraft:zombie")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().global()
                .set()
                .slot("mainhand").item("minecraft:wooden_sword").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("zombie_test");
        
        MobEquipmentBuilder.mob("minecraft:stray")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().match("#forge:is_snowy")
                .set().weight(1)
                .slot("mainhand").item("minecraft:bow").endItem().endSlot()
                .endSet()
                
                .set().weight(3)
                .slot("mainhand").item("minecraft:crossbow").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("stray_test");
        
        MobEquipmentBuilder.mob("minecraft:piglin")
                .chance(0.6f)
                
                .difficultyGroup().match("normal")
                .biomeGroup().match("minecraft:nether_wastes")
                .set()
                .slot("mainhand").item("minecraft:golden_sword").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .difficultyGroup().match("hard")
                .biomeGroup().global()
                .set()
                .slot("mainhand").item("minecraft:golden_axe")
                .randomEnchant().power(20).endEnchant()
                .endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("piglin_test");
        
        MobEquipmentBuilder.mob("minecraft:enderman")
                .chance(0.2f)
                
                .difficultyGroup().match("easy")
                .biomeGroup().global()
                .set()
                .name("random set whatever")
                .slot("mainhand").item("minecraft:grass_block").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("enderman_loot");
        
        MobEquipmentBuilder.mob("minecraft:enderman")
                .chance(0.2f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().global()
                .set()
                .slot("mainhand").item("minecraft:end_stone").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("enderman_hard");
        
    }
}
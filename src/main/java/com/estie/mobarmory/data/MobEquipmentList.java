package com.estie.mobarmory.data;

import net.minecraftforge.fml.loading.FMLLoader;

public class MobEquipmentList {
    
    public static void init() {
        if (FMLLoader.isProduction()) return;
        
        MobEquipmentBuilder.mob("minecraft:blaze")
                .chance(0.9f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().match("minecraft:nether_wastes")
                .set().name("inferno_priest").weight(3)
                .mobNbt("CustomName:\"{\\\"text\\\":\\\"Inferno Priest\\\",\\\"color\\\":\\\"red\\\"}\",CustomNameVisible:1b")
                .potionEffect("minecraft:fire_resistance", 999999, 1)
                .potionEffect("minecraft:strength", 600, 1)
                .timeOfDay(12000, 24000)
                .yLevel(">=", 30)
                .slot("mainhand")
                .item("minecraft:fire_charge")
                .nbt("CustomName:\"{\\\"text\\\":\\\"Holy Flame\\\"}\"")
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("blaze_inferno");
        
        
        MobEquipmentBuilder.mob("minecraft:creeper")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().global()
                .set().name("bomber")
                .mobNbt("Fuse:5s,CustomName:\"{\\\"text\\\":\\\"Bomber\\\"}\",CustomNameVisible:1b")
                .potionEffect("minecraft:speed", 200, 1)
                .slot("head")
                .item("minecraft:tnt")
                .nbt("CustomName:\"{\\\"text\\\":\\\"Payload\\\"}\"")
                .endItem()
                .endSlot()
                .slot("chest")
                .item("minecraft:leather_chestplate")
                .randomEnchant().power(10).endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("creeper_bomber");
        
        MobEquipmentBuilder.mob("minecraft:enderman")
                .chance(0.5f)
                
                .difficultyGroup().global()
                .biomeGroup().global()
                .set().name("courier")
                .mobNbt("CustomName:\"{\\\"text\\\":\\\"Courier\\\"}\",CustomNameVisible:1b,HandDropChances:[1.0f,1.0f]")
                .slot("mainhand")
                .item("minecraft:grass_block").weight(1).endItem()
                .item("minecraft:end_stone").weight(2).endItem()
                .item("minecraft:obsidian").weight(1).endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("enderman_courier");
        
        MobEquipmentBuilder.mob("minecraft:pillager")
                .chance(0.7f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().match("#forge:is_forest")
                .set().name("elite_marksman").weight(5)
                .timeOfDay(6000, 18000) // only daytime
                .slot("mainhand")
                .item("minecraft:crossbow")
                .predefinedEnchant()
                .addPredefined("minecraft:quick_charge", 3)
                .addPredefined("minecraft:multishot", 1)
                .endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("pillager_marksman");
        
        
        MobEquipmentBuilder.mob("minecraft:zombie")
                .chance(0.3f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().match("#forge:is_snowy")
                .set().name("royal_guard")
                .mobNbt("CustomName:\"{\\\"text\\\":\\\"Royal Guard\\\",\\\"color\\\":\\\"gold\\\"}\",CustomNameVisible:1b")
                .potionEffect("minecraft:strength", 400, 2)
                .potionEffect("minecraft:resistance", 400, 1)
                .slot("head").item("minecraft:diamond_helmet").randomEnchant().power(20).endEnchant().endItem().endSlot()
                .slot("chest").item("minecraft:diamond_chestplate").randomEnchant().power(20).endEnchant().endItem().endSlot()
                .slot("legs").item("minecraft:diamond_leggings").randomEnchant().power(20).endEnchant().endItem().endSlot()
                .slot("feet").item("minecraft:diamond_boots").randomEnchant().power(20).endEnchant().endItem().endSlot()
                .slot("mainhand").item("minecraft:diamond_sword").randomEnchant().power(25).endEnchant().endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("zombie_royal_guard");
        
        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(0.6f)
                
                .difficultyGroup().global()
                .biomeGroup().global()
                .set().name("night_stalker")
                .timeOfDay(13000, 24000)
                .potionEffect("minecraft:invisibility", 200, 0)
                .slot("mainhand")
                .item("minecraft:bow").weight(2).endItem()
                .item("minecraft:crossbow").weight(1).endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("skeleton_night_stalker");
        
        MobEquipmentBuilder.mob("minecraft:piglin")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().match("minecraft:nether_wastes")
                .set().name("hoarder")
                .mobNbt("CustomName:\"{\\\"text\\\":\\\"Gold Hoarder\\\"}\",CustomNameVisible:1b,HandDropChances:[1.0f,1.0f]")
                .slot("mainhand")
                .item("minecraft:golden_sword")
                .predefinedEnchant()
                .addPredefined("minecraft:sharpness", 3)
                .endEnchant()
                .endItem()
                .endSlot()
                .slot("chest").item("minecraft:golden_chestplate").endItem().endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("piglin_hoarder");
        
    }
}
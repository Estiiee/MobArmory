package com.estie.mobarmory.data;

import net.minecraft.launchwrapper.Launch;

//examples of building equipment sets in code
public class MobEquipmentList {
    
    public static void init() {
        if (!(Boolean) Launch.blackboard.get("fml.deobfuscatedEnvironment")) return;

        MobEquipmentBuilder.mob("minecraft:blaze")
                .chance(0.9f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().match("NETHER")
                .dimension("-1")
                .set().name("inferno_priest").weight(3)
                .mobNbt("CustomName:\"§cInferno Priest\",CustomNameVisible:1b")
                .potionEffect("minecraft:fire_resistance", 999999, 1)
                .potionEffect("minecraft:strength", 600, 1)
                .yLevel(">=", 30)
                .slot("mainhand")
                .item("minecraft:fire_charge")
                .nbt("CustomName:\"Holy Flame\"")
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
                .mobNbt("Fuse:5s,CustomName:\"Bomber\",CustomNameVisible:1b")
                .potionEffect("minecraft:speed", 200, 1)
                .slot("head")
                .item("minecraft:tnt")
                .nbt("display:{Name:\"Payload\"}")
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
                .biomeGroup().match("END")
                .dimension("1")
                .set().name("courier")
                .mobNbt("CustomName:\"Courier\",CustomNameVisible:1b,HandDropChances:[1.0f,1.0f]")
                .slot("mainhand")
                .item("minecraft:grass").weight(1).endItem()
                .item("minecraft:end_stone").weight(2).endItem()
                .item("minecraft:obsidian").weight(1).endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("enderman_courier");
        

        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(0.6f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().match("FOREST")
                .structure("Mineshaft")
                .set().name("cave_stalker")
                .timeOfDay(13000, 24000)
                .potionEffect("minecraft:invisibility", 200, 0)
                .slot("mainhand")
                .item("minecraft:bow").weight(2).endItem()
                .item("minecraft:crossbow").weight(1).endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("skeleton_cave_stalker");
        

        MobEquipmentBuilder.mob("minecraft:zombie")
                .chance(0.3f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().match("SNOWY")
                .spawnTypes("natural")
                .set().name("royal_guard")
                .mobNbt("CustomName:\"§6Royal Guard\",CustomNameVisible:1b")
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
        

        MobEquipmentBuilder.mob("minecraft:zombie")
                .chance(0.8f)
                
                .difficultyGroup().global()
                .biomeGroup().matches("HOT", "SANDY")
                .dimension("0")
                .replacement("minecraft:husk", 1)
                .set().name("desert_raider")
                .mobNbt("CustomName:\"Desert Raider\",CustomNameVisible:1b")
                .slot("mainhand")
                .item("minecraft:iron_sword")
                .randomEnchant().power(15).endEnchant()
                .endItem()
                .endSlot()
                .slot("head")
                .item("minecraft:leather_helmet")
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("zombie_desert_raider");
        

        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(0.7f)
                
                .difficultyGroup().matches("normal", "hard")
                .biomeGroup().matches("COLD", "SNOWY")
                .dimension("0")
                .replacement("minecraft:stray", 1)
                .set().name("frost_hunter")
                .potionEffect("minecraft:slowness", 200, 0)
                .slot("mainhand")
                .item("minecraft:bow")
                .predefinedEnchant()
                .addPredefined("minecraft:power", 3)
                .addPredefined("minecraft:punch", 1)
                .endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("skeleton_frost_hunter");
        
        
        MobEquipmentBuilder.mob("minecraft:vindication_illager")
                .chance(0.7f)
                
                .difficultyGroup().match("hard")
                .biomeGroup().global()
                .structure("Village")
                .spawnTypes("natural", "spawner", "command", "spawn_egg")
                .set().name("elite_marksman").weight(5)
                //.timeOfDay(6000, 18000)
                .slot("mainhand")
                .item("minecraft:diamond_axe")
                .randomEnchant().power(20).endEnchant()
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("vindicator_marksman");

        
        MobEquipmentBuilder.mob("minecraft:piglin")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().match("NETHER")
                .dimension("-1")
                .set().name("hoarder")
                .mobNbt("CustomName:\"Gold Hoarder\",CustomNameVisible:1b,HandDropChances:[1.0f,1.0f]")
                .slot("mainhand")
                .item("minecraft:golden_sword")
                .predefinedEnchant()
                .addPredefined("minecraft:sharpness", 3)
                .endEnchant()
                .endItem()
                .endSlot()
                .slot("chest")
                .item("minecraft:golden_chestplate")
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                
                .createFile("piglin_hoarder");
        
        
        MobEquipmentBuilder.mob("minecraft:spider")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().global()
                .replacement("minecraft:rabbit", 1)
                .spawnTypes("jockey")
                .set()
                .name("Spider Jockey")
                .weight(1)
                .slot("mainhand")
                .item("minecraft:bow")
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                .createFile("spider_jockey");
        
        MobEquipmentBuilder.mob("minecraft:skeleton")
                .chance(1.0f)
                
                .difficultyGroup().global()
                .biomeGroup().global()
                .replacement("minecraft:stray", 1)
                .spawnTypes("jockey")
                .set()
                .name("Spider Jockey")
                .weight(1)
                .slot("mainhand")
                .item("minecraft:bow")
                .endItem()
                .endSlot()
                .endSet()
                .endBiomeGroup()
                .endDifficultyGroup()
                .createFile("skeleton_jockey");
    }
}

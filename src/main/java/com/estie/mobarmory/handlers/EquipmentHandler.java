package com.estie.mobarmory.handlers;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.MobEquipmentSpawnUtil;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.monster.EntityMob;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public class EquipmentHandler {
    
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!Config.enabled) return;
        if (!(event.getEntity() instanceof EntityLiving)) return;
        
        EntityLiving mob = (EntityLiving) event.getEntity();
        
        if (event.getWorld().isRemote) return;
        
        if (mob.getEntityData().getBoolean("MobArmory_SpawnFlag")) return;
        mob.getEntityData().setBoolean("MobArmory_SpawnFlag", true);
        
        ResourceLocation mobId = EntityList.getKey(mob);
        if (mobId == null) return;
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        
        if (entry == null) return;
        
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(event.getWorld());
        List<MobEquipmentReloadListener.DifficultyGroup> matchingDifficultyGroups = new ArrayList<MobEquipmentReloadListener.DifficultyGroup>();
        
        for (MobEquipmentReloadListener.DifficultyGroup group : entry.difficultyGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.DifficultyLevel matcher : group.matchers) {
                if (matcher == MobEquipmentReloadListener.DifficultyLevel.GLOBAL) globalGroup = true;
                else if (matcher == currentDifficulty) {
                    matches = true;
                    break;
                }
            }
            
            if (matches || globalGroup) matchingDifficultyGroups.add(group);
        }
        
        if (matchingDifficultyGroups.isEmpty()) return;
        
        MobEquipmentReloadListener.DifficultyGroup chosenDifficultyGroup = matchingDifficultyGroups.get(
                        mob.getRNG().nextInt(matchingDifficultyGroups.size()));
        
        if (chosenDifficultyGroup == null) return;
        
        Biome biome = event.getWorld().getBiome(mob.getPosition());
        
        ResourceLocation biomeId = biome.getRegistryName();
        List<MobEquipmentReloadListener.BiomeGroup> matchingBiomeGroups = new ArrayList<>();
        
        for (MobEquipmentReloadListener.BiomeGroup group : chosenDifficultyGroup.biomeGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.BiomeMatch matcher : group.matchers) {
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Global) globalGroup = true;
                
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Id) {
                    MobEquipmentReloadListener.BiomeMatch.Id idMatch =
                            (MobEquipmentReloadListener.BiomeMatch.Id) matcher;
                    
                    if (biomeId != null && biomeId.equals(idMatch.id())) {
                        matches = true;
                        break;
                    }
                }
                
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Tag) {
                    MobEquipmentReloadListener.BiomeMatch.Tag tagMatch = (MobEquipmentReloadListener.BiomeMatch.Tag) matcher;
                    
                    BiomeDictionary.Type type = BiomeDictionary.Type.getType(tagMatch.tag());
                    
                    if (BiomeDictionary.hasType(biome, type)) {
                        matches = true;
                        break;
                    }
                }
            }
            
            if (matches || globalGroup) matchingBiomeGroups.add(group);
        }
        
        if (matchingBiomeGroups.isEmpty() && chosenDifficultyGroup.globalSets.isEmpty()) return;
        
        MobEquipmentReloadListener.BiomeGroup chosenBiomeGroup = null;
        
        if (!matchingBiomeGroups.isEmpty()) chosenBiomeGroup = matchingBiomeGroups.get(mob.getRNG().nextInt(matchingBiomeGroups.size()));
        
        List<MobEquipmentReloadListener.EquipmentSet> candidateSets;
        Float biomeGroupChance;
        
        if (chosenBiomeGroup != null) {
            candidateSets = chosenBiomeGroup.sets;
            biomeGroupChance = chosenBiomeGroup.chance;
        } else {
            candidateSets = chosenDifficultyGroup.globalSets;
            biomeGroupChance = null;
        }
        
        float effectiveChance =
                hasOverride(biomeGroupChance) ? biomeGroupChance :
                        hasOverride(chosenDifficultyGroup.chance)
                                ? chosenDifficultyGroup.chance
                                : entry.chance;
        
        if (mob.getRNG().nextFloat() > effectiveChance) return;
        
        long currentTime = event.getWorld().getWorldTime();
        int mobY = mob.getPosition().getY();
        
        List<MobEquipmentReloadListener.EquipmentSet> eligible =
                candidateSets.stream()
                        .filter(s -> s.timeOfDay.matches(currentTime))
                        .filter(s -> s.yLevel.matches(mobY))
                        .collect(Collectors.toList());
        
        if (eligible.isEmpty()) return;
        
        MobEquipmentReloadListener.EquipmentSet chosenSet = pickWeightedSet(eligible, mob.getRNG());
        if (chosenSet == null) return;
        
        MobEquipmentSpawnUtil.applyEquipmentSet(mob, chosenSet);
    }
    
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntityLiving() instanceof EntityMob)) return;
        EntityMob mob = (EntityMob) event.getEntityLiving();
        if (!(mob.world instanceof WorldServer)) return;
        if (!mob.getEntityData().hasKey("MobArmoryLootTable")) return;
        
        String lootTableId = mob.getEntityData().getString("MobArmoryLootTable");
        
        ResourceLocation tableId = new ResourceLocation(lootTableId);
        
        WorldServer world = (WorldServer) mob.world;
        
        LootTable loot = world.getLootTableManager().getLootTableFromLocation(tableId);
        
        LootContext context = new LootContext(0.0F, world, world.getLootTableManager(), mob, null, event.getSource());
        
        for (ItemStack stack : loot.generateLootForPools(world.rand, context)) {
            event.getDrops().add(new EntityItem(world, mob.posX, mob.posY, mob.posZ, stack));
        }
    }
    
    private static boolean hasOverride(Float value) {
        return value != null && value >= 0.0F;
    }
    
    private static MobEquipmentReloadListener.DifficultyLevel currentDifficulty(
            World world) {
        
        if (world.getWorldInfo().isHardcoreModeEnabled()) return MobEquipmentReloadListener.DifficultyLevel.HARDCORE;
        
        switch (world.getDifficulty()) {
            case EASY:
            case PEACEFUL: return MobEquipmentReloadListener.DifficultyLevel.EASY;
            case NORMAL: return MobEquipmentReloadListener.DifficultyLevel.NORMAL;
            case HARD: return MobEquipmentReloadListener.DifficultyLevel.HARD;
            default: return MobEquipmentReloadListener.DifficultyLevel.NORMAL;
        }
    }
    
    private static MobEquipmentReloadListener.EquipmentSet pickWeightedSet(List<MobEquipmentReloadListener.EquipmentSet> sets, Random random) {
        int totalWeight = 0;
        
        for (MobEquipmentReloadListener.EquipmentSet set : sets) {
            totalWeight += set.weight;
        }
        
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (MobEquipmentReloadListener.EquipmentSet set : sets) {
            cumulative += set.weight;
            
            if (roll < cumulative) return set;
        }
        
        return sets.get(sets.size() - 1);
    }
}
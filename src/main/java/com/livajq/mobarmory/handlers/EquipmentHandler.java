package com.livajq.mobarmory.handlers;

import com.livajq.mobarmory.MobArmory;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.List;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public class EquipmentHandler {
    
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;
        if (event.loadedFromDisk()) return;
        
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) return;
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return;
        
        //pick the difficulty group that applies right now.
        //same order-dependent rule as biome matching below: first group that either
        //specifically matches or is "global" wins, so put specific difficulties before a global fallback.
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(mob.level());
        MobEquipmentReloadListener.DifficultyGroup chosenDifficultyGroup = null;
        
        for (MobEquipmentReloadListener.DifficultyGroup group : entry.difficultyGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.DifficultyLevel matcher : group.matchers()) {
                if (matcher == MobEquipmentReloadListener.DifficultyLevel.GLOBAL) globalGroup = true;
                else if (matcher == currentDifficulty) {
                    matches = true;
                    break;
                }
            }
            
            if (matches || globalGroup) {
                chosenDifficultyGroup = group;
                break;
            }
        }
        
        if (chosenDifficultyGroup == null) return;
        
        Holder<Biome> biomeHolder = mob.level().getBiome(mob.blockPosition());
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        
        //biome groups within the chosen difficulty group, if present
        MobEquipmentReloadListener.BiomeGroup chosenBiomeGroup = null;
        
        for (MobEquipmentReloadListener.BiomeGroup group : chosenDifficultyGroup.biomeGroups()) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.BiomeMatch matcher : group.matchers()) {
                
                //used either by the builder for unrestricted entries or as a fallback if the entity spawns outside any specific biome group
                //manual JSONs with no biome restrictions can skip the biome groups altogether
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Global) globalGroup = true;
                
                //biome id match
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Id idMatch) {
                    if (biomeKey != null && biomeKey.location().equals(idMatch.id())) {
                        matches = true;
                        break;
                    }
                }
                
                //biome tag match
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Tag tagMatch) {
                    if (biomeHolder.tags().anyMatch(t -> t.location().equals(tagMatch.tag()))) {
                        matches = true;
                        break;
                    }
                }
            }
            
            if (matches || globalGroup) {
                chosenBiomeGroup = group;
                break;
            }
        }
        
        List<MobEquipmentReloadListener.EquipmentSet> candidateSets;
        Float biomeGroupChance;
        
        if (chosenBiomeGroup != null) {
            candidateSets = chosenBiomeGroup.sets();
            biomeGroupChance = chosenBiomeGroup.chance();
        }
        //no biome restriction in this difficulty group: fall back to its own direct sets
        else if (!chosenDifficultyGroup.globalSets().isEmpty()) {
            candidateSets = chosenDifficultyGroup.globalSets();
            biomeGroupChance = null;
        }
        else {
            return;
        }
        
        //most specific chance wins: biome group > difficulty group > mob-level default
        float effectiveChance = biomeGroupChance != null ? biomeGroupChance
                : chosenDifficultyGroup.chance() != null ? chosenDifficultyGroup.chance()
                : entry.chance;
        
        if (mob.getRandom().nextFloat() > effectiveChance) return;
        
        MobEquipmentReloadListener.EquipmentSet chosenSet = pickWeightedSet(candidateSets, mob.getRandom());
        if (chosenSet == null) return;
        
        //apply items and their enchants
        for (var slotEntry : chosenSet.slots().entrySet()) {
            MobEquipmentReloadListener.WeightedItem chosen =
                    pickWeightedItem(slotEntry.getValue(), mob.getRandom());
            
            if (chosen != null) {
                ItemStack stack = new ItemStack(chosen.item());
                
                //random enchants
                if (chosen.enchant() instanceof MobEquipmentReloadListener.EnchantData.Random rnd) {
                    EnchantmentHelper.enchantItem(mob.getRandom(), stack, rnd.power(), false);
                }
                
                //predefined enchants
                if (chosen.enchant() instanceof MobEquipmentReloadListener.EnchantData.Predefined pre) {
                    for (int i = 0; i < pre.enchants().size(); i++) {
                        stack.enchant(pre.enchants().get(i).value(), pre.levels().get(i));
                    }
                }
                
                mob.setItemSlot(slotEntry.getKey(), stack);
            }
        }
    }
    
    //hardcore worlds are always forced to HARD difficulty, but "hardcore" as a matcher is treated
    //as its own distinct level since that's the more useful thing to key equipment off of.
    //peaceful is folded into easy for matching purposes - mobs generally don't spawn on peaceful anyway.
    private static MobEquipmentReloadListener.DifficultyLevel currentDifficulty(Level level) {
        if (level.getLevelData().isHardcore()) return MobEquipmentReloadListener.DifficultyLevel.HARDCORE;
        
        return switch (level.getDifficulty()) {
            case EASY, PEACEFUL -> MobEquipmentReloadListener.DifficultyLevel.EASY;
            case NORMAL -> MobEquipmentReloadListener.DifficultyLevel.NORMAL;
            case HARD -> MobEquipmentReloadListener.DifficultyLevel.HARD;
        };
    }
    
    private static MobEquipmentReloadListener.EquipmentSet pickWeightedSet(List<MobEquipmentReloadListener.EquipmentSet> sets, RandomSource random) {
        int totalWeight = sets.stream().mapToInt(MobEquipmentReloadListener.EquipmentSet::weight).sum();
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var set : sets) {
            cumulative += set.weight();
            if (roll < cumulative) return set;
        }
        return sets.get(sets.size() - 1);
    }
    
    private static MobEquipmentReloadListener.WeightedItem pickWeightedItem(List<MobEquipmentReloadListener.WeightedItem> items, RandomSource random) {
        int totalWeight = items.stream().mapToInt(MobEquipmentReloadListener.WeightedItem::weight).sum();
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var item : items) {
            cumulative += item.weight();
            if (roll < cumulative) return item;
        }
        return items.get(items.size() - 1);
    }
}
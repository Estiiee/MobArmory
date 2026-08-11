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
        
        if (mob.getRandom().nextFloat() > entry.chance) return;
        
        Holder<Biome> biomeHolder = mob.level().getBiome(mob.blockPosition());
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        
        //biome groups if present
        MobEquipmentReloadListener.EquipmentSet chosenSet = null;
        
        for (MobEquipmentReloadListener.BiomeGroup group : entry.biomeGroups) {
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
                chosenSet = pickWeightedSet(group.sets(), mob.getRandom());
                break;
            }
        }
        
        //global sets fallback
        if (chosenSet == null && !entry.globalSets.isEmpty()) {
            chosenSet = pickWeightedSet(entry.globalSets, mob.getRandom());
        }
        
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

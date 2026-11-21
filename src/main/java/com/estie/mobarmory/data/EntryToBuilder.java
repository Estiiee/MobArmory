package com.estie.mobarmory.data;

import com.estie.mobarmory.data.MobEquipmentReloadListener.BiomeGroup;
import com.estie.mobarmory.data.MobEquipmentReloadListener.BiomeMatch;
import com.estie.mobarmory.data.MobEquipmentReloadListener.DifficultyGroup;
import com.estie.mobarmory.data.MobEquipmentReloadListener.DifficultyLevel;
import com.estie.mobarmory.data.MobEquipmentReloadListener.DimensionMatch;
import com.estie.mobarmory.data.MobEquipmentReloadListener.EnchantData;
import com.estie.mobarmory.data.MobEquipmentReloadListener.EquipmentSet;
import com.estie.mobarmory.data.MobEquipmentReloadListener.MobEquipmentEntry;
import com.estie.mobarmory.data.MobEquipmentReloadListener.MobReplacement;
import com.estie.mobarmory.data.MobEquipmentReloadListener.PotionEffectEntry;
import com.estie.mobarmory.data.MobEquipmentReloadListener.SpawnTypeMatch;
import com.estie.mobarmory.data.MobEquipmentReloadListener.WeightedItem;
import com.estie.mobarmory.data.MobEquipmentReloadListener.YComparator;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EquipmentSlot;

import java.util.List;
import java.util.Map;

public final class EntryToBuilder {
    
    private EntryToBuilder() {}
    
    public static MobEquipmentBuilder toBuilder(MobEquipmentEntry entry) {
        MobEquipmentBuilder b = MobEquipmentBuilder
                .mobs(entry.mobs.stream().map(ResourceLocation::toString).toList())
                .chance(entry.chance);
        
        for (DifficultyGroup group : entry.difficultyGroups) {
            MobEquipmentBuilder.DifficultyGroupBuilder dg = b.difficultyGroup();
            
            for (DifficultyLevel level : group.matchers) {
                dg.match(level.toString());
            }
            if (hasOverride(group.chance)) dg.chance(group.chance);
            
            for (BiomeGroup biomeGroup : group.biomeGroups) {
                addBiomeGroup(dg, biomeGroup.matchers, biomeGroup.dimensions, biomeGroup.spawnTypes,
                        biomeGroup.structures, biomeGroup.replacements, biomeGroup.chance, biomeGroup.sets);
            }
            
            if (group.globalSets != null && !group.globalSets.isEmpty()) {
                addBiomeGroup(dg, List.of(new BiomeMatch.Global()), List.of(), List.of(), List.of(), List.of(), null, group.globalSets);
            }
        }
        return b;
    }
    
    private static void addBiomeGroup(MobEquipmentBuilder.DifficultyGroupBuilder dg,
                                      List<BiomeMatch> matchers, List<DimensionMatch> dimensions,
                                      List<SpawnTypeMatch> spawnTypes, List<ResourceLocation> structures,
                                      List<MobReplacement> replacements, Float chance, List<EquipmentSet> sets) {
        MobEquipmentBuilder.BiomeGroupBuilder bg = dg.biomeGroup();
        
        for (BiomeMatch match : matchers) {
            bg.match(MobEquipmentReloadListener.biomeMatchToString(match));
        }
        for (DimensionMatch dim : dimensions) {
            bg.dimension(MobEquipmentReloadListener.dimensionMatchToString(dim));
        }
        for (SpawnTypeMatch spawnType : spawnTypes) {
            bg.spawnType(MobEquipmentReloadListener.spawnTypeMatchToString(spawnType));
        }
        for (ResourceLocation structure : structures) {
            bg.structure(structure.toString());
        }
        for (MobReplacement replacement : replacements) {
            bg.replacement(replacement.mob().toString(), replacement.weight());
        }
        if (hasOverride(chance)) bg.chance(chance);
        
        for (EquipmentSet set : sets) {
            addSet(bg, set);
        }
    }
    
    private static void addSet(MobEquipmentBuilder.BiomeGroupBuilder bg, EquipmentSet set) {
        MobEquipmentBuilder.EquipmentSetBuilder sb = bg.set();
        
        if (set.name != null) sb.name(set.name);
        sb.weight(set.weight);
        if (set.lootTable != null) sb.lootTable(set.lootTable);
        
        for (Map.Entry<EquipmentSlot, List<WeightedItem>> slotEntry : set.slots.entrySet()) {
            MobEquipmentBuilder.SlotBuilder slot = sb.slot(slotEntry.getKey().getName());
            
            for (WeightedItem wi : slotEntry.getValue()) {
                MobEquipmentBuilder.WeightedItemBuilder item = slot.item(wi.itemId).weight(wi.weight);
                
                if (wi.nbt != null) item.nbt(wi.nbt);
                
                if (wi.enchant instanceof EnchantData.Random random) {
                    item.randomEnchant().power(random.power());
                } else if (wi.enchant instanceof EnchantData.Predefined predefined) {
                    MobEquipmentBuilder.EnchantBuilder enchant = item.predefinedEnchant();
                    for (int i = 0; i < predefined.ids().size(); i++) {
                        enchant.addPredefined(predefined.ids().get(i), predefined.levels().get(i));
                    }
                }
            }
        }
        
        if (set.mobNbt != null) sb.mobNbt(set.mobNbt);
        
        for (PotionEffectEntry pe : set.potionEffects) {
            sb.potionEffect(pe.effectId, pe.durationTicks, pe.amplifier);
        }
        
        if (!MobEquipmentReloadListener.isTimeUnrestricted(set.timeOfDay)) {
            sb.timeOfDay(set.timeOfDay.minTicks, set.timeOfDay.maxTicks);
        }
        if (!MobEquipmentReloadListener.isYLevelUnrestricted(set.yLevel)) {
            YComparator comparator = set.yLevel.comparator;
            sb.yLevel(comparator.symbol, set.yLevel.value);
        }
    }
    
    private static boolean hasOverride(Float chance) {
        return chance != null && chance >= 0.0F;
    }
}
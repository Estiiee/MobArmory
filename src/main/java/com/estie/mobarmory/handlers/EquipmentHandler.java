package com.estie.mobarmory.handlers;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.MobEquipmentSpawnUtil;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public class EquipmentHandler {
    
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!Config.enabled) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;
        if (event.loadedFromDisk()) return;
        
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) return;
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return;
        
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(mob.level());
        
        List<MobEquipmentReloadListener.DifficultyGroup> matchingDifficultyGroups = new ArrayList<>();
        
        for (MobEquipmentReloadListener.DifficultyGroup group : entry.difficultyGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.DifficultyLevel matcher : group.matchers) {
                if (matcher == MobEquipmentReloadListener.DifficultyLevel.GLOBAL) {
                    globalGroup = true;
                } else if (matcher == currentDifficulty) {
                    matches = true;
                    break;
                }
            }
            
            if (matches || globalGroup) {
                matchingDifficultyGroups.add(group);
            }
        }
        
        if (matchingDifficultyGroups.isEmpty()) return;
        
        MobEquipmentReloadListener.DifficultyGroup chosenDifficultyGroup = matchingDifficultyGroups.get(mob.getRandom().nextInt(matchingDifficultyGroups.size()));
        if (chosenDifficultyGroup == null) return;
        
        Holder<Biome> biomeHolder = mob.level().getBiome(mob.blockPosition());
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        
        List<MobEquipmentReloadListener.BiomeGroup> matchingBiomeGroups = new ArrayList<>();
        
        for (MobEquipmentReloadListener.BiomeGroup group : chosenDifficultyGroup.biomeGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.BiomeMatch matcher : group.matchers) {
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Global) {
                    globalGroup = true;
                }
                
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Id idMatch) {
                    if (biomeKey != null && biomeKey.location().equals(idMatch.id())) {
                        matches = true;
                        break;
                    }
                }
                
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Tag tagMatch) {
                    if (biomeHolder.tags().anyMatch(t -> t.location().equals(tagMatch.tag()))) {
                        matches = true;
                        break;
                    }
                }
            }
            
            if (matches || globalGroup) {
                matchingBiomeGroups.add(group);
            }
        }
        
        if (matchingBiomeGroups.isEmpty() && chosenDifficultyGroup.globalSets.isEmpty()) return;
        
        MobEquipmentReloadListener.BiomeGroup chosenBiomeGroup = null;
        
        if (!matchingBiomeGroups.isEmpty()) {
            chosenBiomeGroup = matchingBiomeGroups.get(
                    mob.getRandom().nextInt(matchingBiomeGroups.size())
            );
        }
        
        List<MobEquipmentReloadListener.EquipmentSet> candidateSets;
        Float biomeGroupChance;
        
        if (chosenBiomeGroup != null) {
            candidateSets = chosenBiomeGroup.sets;
            biomeGroupChance = chosenBiomeGroup.chance;
        } else {
            candidateSets = chosenDifficultyGroup.globalSets;
            biomeGroupChance = null;
        }
        
        float effectiveChance = hasOverride(biomeGroupChance) ? biomeGroupChance :
                hasOverride(chosenDifficultyGroup.chance) ? chosenDifficultyGroup.chance : entry.chance;
        
        if (mob.getRandom().nextFloat() > effectiveChance) return;
        
        //time-of-day / Y-level eligibility - a set with either restriction that doesn't currently
        //hold is excluded from the pool entirely, not just deprioritized
        long currentTime = mob.level().getDayTime();
        int mobY = mob.blockPosition().getY();
        
        List<MobEquipmentReloadListener.EquipmentSet> eligible = candidateSets.stream()
                .filter(s -> s.timeOfDay.matches(currentTime))
                .filter(s -> s.yLevel.matches(mobY))
                .toList();
        
        if (eligible.isEmpty()) return;
        
        MobEquipmentReloadListener.EquipmentSet chosenSet = pickWeightedSet(eligible, mob.getRandom());
        if (chosenSet == null) return;
        
        MobEquipmentSpawnUtil.applyEquipmentSet(mob, chosenSet);
    }
    
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains("MobArmoryLootTable")) return;
        
        ResourceLocation table = new ResourceLocation(tag.getString("MobArmoryLootTable"));
        LootTable loot = event.getEntity().level().getServer().getLootData().getLootTable(table);
        
        LootParams params = new LootParams.Builder((ServerLevel) event.getEntity().level())
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .create(LootContextParamSets.ENTITY);
        
        for (ItemStack stack : loot.getRandomItems(params)) {
            event.getDrops().add(
                    new ItemEntity(
                            mob.level(),
                            mob.getX(),
                            mob.getY(),
                            mob.getZ(),
                            stack
                    )
            );
        }
    }
    
    private static boolean hasOverride(Float value) {
        return value != null && value >= 0.0F;
    }
    
    private static MobEquipmentReloadListener.DifficultyLevel currentDifficulty(Level level) {
        if (level.getLevelData().isHardcore()) return MobEquipmentReloadListener.DifficultyLevel.HARDCORE;
        
        return switch (level.getDifficulty()) {
            case EASY, PEACEFUL -> MobEquipmentReloadListener.DifficultyLevel.EASY;
            case NORMAL -> MobEquipmentReloadListener.DifficultyLevel.NORMAL;
            case HARD -> MobEquipmentReloadListener.DifficultyLevel.HARD;
        };
    }
    
    private static MobEquipmentReloadListener.EquipmentSet pickWeightedSet(List<MobEquipmentReloadListener.EquipmentSet> sets, RandomSource random) {
        int totalWeight = sets.stream().mapToInt(s -> s.weight).sum();
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var set : sets) {
            cumulative += set.weight;
            if (roll < cumulative) return set;
        }
        return sets.get(sets.size() - 1);
    }
}
package com.estie.mobarmory.util;

import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

public final class MobEquipmentSpawnUtil {
    private MobEquipmentSpawnUtil() {}
    
    //applying a set to an already-existing mob
    public static void applyEquipmentSet(LivingEntity mob, MobEquipmentReloadListener.EquipmentSet set) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        
        for (var slotEntry : set.slots.entrySet()) {
            MobEquipmentReloadListener.WeightedItem chosen = pickWeightedItem(slotEntry.getValue(), mob.getRandom());
            if (chosen == null) continue;
            
            chosen.resolve();
            Item actual = chosen.item != null ? chosen.item : Items.AIR;
            ItemStack stack = new ItemStack(actual);
            
            if (chosen.enchant instanceof MobEquipmentReloadListener.EnchantData.Random rnd) {
                EnchantmentHelper.enchantItem(mob.getRandom(), stack, rnd.power(), true);
            }
            if (chosen.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined pre) {
                for (int i = 0; i < pre.ids().size(); i++) {
                    ResourceLocation id = new ResourceLocation(pre.ids().get(i));
                    Holder<Enchantment> holder = ForgeRegistries.ENCHANTMENTS.getHolder(id).orElse(null);
                    if (holder != null) stack.enchant(holder.value(), pre.levels().get(i));
                }
            }
            if (chosen.nbt != null) applyItemNbt(stack, chosen.nbt, chosen.itemId, mobId);
            
            mob.setItemSlot(slotEntry.getKey(), stack);
        }
        
        if (set.mobNbt != null) applyMobNbt(mob, set.mobNbt, mobId);
        if (set.lootTable != null) mob.getPersistentData().putString("MobArmoryLootTable", set.lootTable);
        
        for (MobEquipmentReloadListener.PotionEffectEntry pe : set.potionEffects) {
            ResourceLocation rl = ResourceLocation.tryParse(pe.effectId);
            MobEffect effect = rl != null ? ForgeRegistries.MOB_EFFECTS.getValue(rl) : null;
            if (effect != null) mob.addEffect(new MobEffectInstance(effect, pe.durationTicks, pe.amplifier));
            else MobArmory.LOGGER.warn("Unknown potion effect {} while equipping {}", pe.effectId, mobId);
        }
    }
    
    // matching sets
    
    public static boolean tryAddRandomMatchingSet(LivingEntity mob, BlockPos pos) {
        List<EquipmentSetContext> candidates = getAllSetsMatchingCriteria(mob, pos);
        if (candidates.isEmpty()) return false;
        
        EquipmentSetContext chosen = pickWeightedSet(candidates, mob.getRandom());
        if (chosen == null) return false;
        
        if (mob.getRandom().nextFloat() > chosen.chance()) return false;
        
        applyEquipmentSet(mob, chosen.equipmentSet());
        return true;
    }
    
    public static List<EquipmentSetContext> getAllSetsMatchingCriteria(LivingEntity mob, BlockPos pos) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) return Collections.emptyList();
        
        Level level = mob.level();
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return Collections.emptyList();
        
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(level);
        
        List<EquipmentSetContext> eligible = new ArrayList<>();
        
        Holder<Biome> biomeHolder = level.getBiome(pos);
        ResourceKey<Biome> biomeKey = biomeHolder.unwrapKey().orElse(null);
        
        for (MobEquipmentReloadListener.DifficultyGroup difficultyGroup : entry.difficultyGroups) {
            boolean difficultyMatches = false;
            boolean globalDifficulty = false;
            
            for (MobEquipmentReloadListener.DifficultyLevel matcher : difficultyGroup.matchers) {
                if (matcher == MobEquipmentReloadListener.DifficultyLevel.GLOBAL) globalDifficulty = true;
                else if (matcher == currentDifficulty) difficultyMatches = true;
            }
            
            if (!difficultyMatches && !globalDifficulty) continue;
            
            //effective chance inherited from the difficulty group. a biome group can override this below.
            float difficultyChance = hasOverride(difficultyGroup.chance) ? difficultyGroup.chance : entry.chance;
            
            for (MobEquipmentReloadListener.BiomeGroup biomeGroup : difficultyGroup.biomeGroups) {
                
                boolean biomeMatches = false;
                boolean globalBiome = false;
                
                for (MobEquipmentReloadListener.BiomeMatch matcher : biomeGroup.matchers) {
                    
                    if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Global) globalBiome = true;
                    
                    if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Id) {
                        MobEquipmentReloadListener.BiomeMatch.Id idMatch = (MobEquipmentReloadListener.BiomeMatch.Id) matcher;
                        if (biomeKey != null && biomeKey.location().equals(idMatch.id())) biomeMatches = true;
                    }
                    
                    if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Tag) {
                        MobEquipmentReloadListener.BiomeMatch.Tag tagMatch = (MobEquipmentReloadListener.BiomeMatch.Tag) matcher;
                        if (biomeHolder.tags().anyMatch(t -> t.location().equals(tagMatch.tag()))) biomeMatches = true;
                    }
                }
                
                if (!biomeMatches && !globalBiome) continue;
                
                float effectiveChance = hasOverride(biomeGroup.chance) ? biomeGroup.chance : difficultyChance;
                
                //add every eligible set from this biome group.
                for (MobEquipmentReloadListener.EquipmentSet set : biomeGroup.sets) {
                    if (!set.timeOfDay.matches(level.getDayTime())) continue;
                    if (!set.yLevel.matches(pos.getY())) continue;
                    
                    eligible.add(new EquipmentSetContext(set, effectiveChance));
                }
            }
            
            //global sets do not belong to a biome group, so they are added separately whenever the difficulty group matches.
            for (MobEquipmentReloadListener.EquipmentSet set : difficultyGroup.globalSets) {
                if (!set.timeOfDay.matches(level.getDayTime())) continue;
                if (!set.yLevel.matches(pos.getY())) continue;
                
                eligible.add(new EquipmentSetContext(set, difficultyChance));
            }
        }
        
        return eligible;
    }
    
    // set lookup
    
    public static Optional<EquipmentSetContext> getSetByName(String name) {
        for (MobEquipmentReloadListener.MobEquipmentEntry entry : MobEquipmentReloadListener.LOOKUP_FILES) {
            float entryChance = entry.chance;
            
            for (MobEquipmentReloadListener.DifficultyGroup difficultyGroup : entry.difficultyGroups) {
                float difficultyChance = hasOverride(difficultyGroup.chance) ? difficultyGroup.chance : entryChance;
                
                for (MobEquipmentReloadListener.BiomeGroup biomeGroup : difficultyGroup.biomeGroups) {
                    float effectiveChance = hasOverride(biomeGroup.chance) ? biomeGroup.chance : difficultyChance;
                    
                    for (MobEquipmentReloadListener.EquipmentSet set : biomeGroup.sets) {
                        if (name.equals(set.name)) return Optional.of(new EquipmentSetContext(set, effectiveChance));
                    }
                }
                
                for (MobEquipmentReloadListener.EquipmentSet set : difficultyGroup.globalSets) {
                    if (name.equals(set.name)) return Optional.of(new EquipmentSetContext(set, difficultyChance));
                }
            }
        }
        
        return Optional.empty();
    }
    
    //collecting sets (shared with EditScreenShared's preview cycler)
    
    public static List<MobEquipmentReloadListener.EquipmentSet> collectAllSets(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        for (var dg : entry.difficultyGroups) {
            for (var bg : dg.biomeGroups) all.addAll(bg.sets);
            all.addAll(dg.globalSets);
        }
        return all;
    }
    
    //ENTRIES-based pools (merged view)
    
    /** All sets under this mob's merged entry (every difficulty/biome group flattened, restrictions ignored). */
    public static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandomSetForMob(ResourceLocation mobId, RandomSource random) {
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return Optional.empty();
        return pickRandom(collectAllSets(entry), random);
    }
    
    /** Every set from every mob in ENTRIES - e.g. a skeleton could receive a zombie set. */
    public static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandomSetFromAnyMob(RandomSource random) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        for (var entry : MobEquipmentReloadListener.ENTRIES.values()) all.addAll(collectAllSets(entry));
        return pickRandom(all, random);
    }
    
    //LOOKUP_FILES-based pools (per file view)
    
    /** Only sets from one specific source file, e.g. "zombie_snowy". */
    public static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandomSetFromFile(String fileName, RandomSource random) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        for (var fileEntry : MobEquipmentReloadListener.LOOKUP_FILES) {
            if (fileName.equals(fileEntry.fileName)) all.addAll(collectAllSets(fileEntry));
        }
        return pickRandom(all, random);
    }
    
    //spawning with a specific set
    
    public static LivingEntity spawnMobWithSet(ServerLevel level, EntityType<? extends LivingEntity> type, MobEquipmentReloadListener.EquipmentSet set, Vec3 pos) {
        return spawnMobWithSet(level, type, set, pos, MobSpawnType.COMMAND);
    }
    
    public static LivingEntity spawnMobWithSet(ServerLevel level, ResourceLocation mobId, MobEquipmentReloadListener.EquipmentSet set, Vec3 pos, MobSpawnType spawnType) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
        if (!(type != null && LivingEntity.class.isAssignableFrom(type.getBaseClass()))) return null;
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> mobType = (EntityType<? extends LivingEntity>) type;
        return spawnMobWithSet(level, mobType, set, pos, spawnType);
    }
    
    public static LivingEntity spawnMobWithSet(ServerLevel level, EntityType<? extends LivingEntity> type, MobEquipmentReloadListener.EquipmentSet set, Vec3 pos, MobSpawnType spawnType) {
        LivingEntity mob = type.create(level);
        if (mob == null) return null;
        
        mob.moveTo(pos.x, pos.y, pos.z, mob.getYRot(), mob.getXRot());
        if (mob instanceof Mob mobMob) mobMob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), spawnType, null, null);
        applyEquipmentSet(mob, set);
        level.addFreshEntity(mob);
        return mob;
    }
    
    //pick + spawn in one call
    
    public static LivingEntity spawnMobWithRandomSet(ServerLevel level, ResourceLocation mobId, Vec3 pos, RandomSource random, MobSpawnType spawnType) {
        return pickRandomSetForMob(mobId, random)
                .map(set -> spawnMobWithSet(level, mobId, set, pos, spawnType))
                .orElse(null);
    }
    
    public static LivingEntity spawnMobWithRandomSetFromFile(ServerLevel level, ResourceLocation mobId, String fileName, Vec3 pos, RandomSource random, MobSpawnType spawnType) {
        return pickRandomSetFromFile(fileName, random)
                .map(set -> spawnMobWithSet(level, mobId, set, pos, spawnType))
                .orElse(null);
    }
    
    public static LivingEntity spawnMobWithRandomSetFromAnyMob(ServerLevel level, ResourceLocation mobId, Vec3 pos, RandomSource random, MobSpawnType spawnType) {
        return pickRandomSetFromAnyMob(random)
                .map(set -> spawnMobWithSet(level, mobId, set, pos, spawnType))
                .orElse(null);
    }
    
    //NBT helpers
    
    private static void applyItemNbt(ItemStack stack, String rawNbt, String itemId, ResourceLocation mobId) {
        try {
            String wrapped = rawNbt.trim().startsWith("{") ? rawNbt.trim() : "{" + rawNbt.trim() + "}";
            CompoundTag userTag = TagParser.parseTag(wrapped);
            CompoundTag existing = stack.getTag();
            stack.setTag(existing != null ? existing.merge(userTag) : userTag);
        } catch (Exception e) {
            MobArmory.LOGGER.warn("Failed to parse item NBT '{}' for {} on {}: {}", rawNbt, itemId, mobId, e.getMessage());
        }
    }
    
    private static void applyMobNbt(LivingEntity mob, String rawNbt, ResourceLocation mobId) {
        try {
            String wrapped = rawNbt.trim().startsWith("{") ? rawNbt.trim() : "{" + rawNbt.trim() + "}";
            CompoundTag userTag = TagParser.parseTag(wrapped);
            CompoundTag existing = mob.saveWithoutId(new CompoundTag());
            existing.merge(userTag);
            mob.load(existing);
        } catch (Exception e) {
            MobArmory.LOGGER.warn("Failed to parse mob NBT '{}' for {}: {}", rawNbt, mobId, e.getMessage());
        }
    }
    
    //private helpers
    
    private static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandom(List<MobEquipmentReloadListener.EquipmentSet> pool, RandomSource random) {
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }
    
    private static MobEquipmentReloadListener.WeightedItem pickWeightedItem(List<MobEquipmentReloadListener.WeightedItem> items, RandomSource random) {
        int totalWeight = items.stream().mapToInt(i -> i.weight).sum();
        if (totalWeight <= 0) return null;
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        for (var item : items) {
            cumulative += item.weight;
            if (roll < cumulative) return item;
        }
        return items.get(items.size() - 1);
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
    
    private static EquipmentSetContext pickWeightedSet(List<EquipmentSetContext> sets, RandomSource random) {
        int totalWeight = 0;
        
        for (EquipmentSetContext context : sets) {
            totalWeight += context.equipmentSet().weight;
        }
        
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (EquipmentSetContext context : sets) {
            cumulative += context.equipmentSet().weight;
            
            if (roll < cumulative) return context;
        }
        
        return sets.get(sets.size() - 1);
    }
}
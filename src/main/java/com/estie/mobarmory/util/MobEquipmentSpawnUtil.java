package com.estie.mobarmory.util;

import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Holder;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.*;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.biome.Biome;
import net.minecraft.world.level.levelgen.structure.Structure;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public final class MobEquipmentSpawnUtil {
    public static final String SPAWN_TYPE_TAG = "MobArmory_SpawnType";
    public static final String LOOT_TABLE = "MobArmoryLootTable";
    public static final String PROCESSED_TAG = "MobArmory_Processed";
    
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
        if (set.lootTable != null) mob.getPersistentData().putString(LOOT_TABLE, set.lootTable);
        
        for (MobEquipmentReloadListener.PotionEffectEntry pe : set.potionEffects) {
            ResourceLocation rl = ResourceLocation.tryParse(pe.effectId);
            MobEffect effect = rl != null ? ForgeRegistries.MOB_EFFECTS.getValue(rl) : null;
            if (effect != null) mob.addEffect(new MobEffectInstance(effect, pe.durationTicks, pe.amplifier));
            else MobArmory.LOGGER.warn("Unknown potion effect {} while equipping {}", pe.effectId, mobId);
        }
    }
    
    // matching sets
    
    public enum SpawnResult { UNCHANGED, EQUIPPED, REPLACED }
    
    public static SpawnResult processSpawn(LivingEntity mob, BlockPos pos) {
        ReplacementPool pool = new ReplacementPool();
        List<EquipmentSetContext> candidates = new ArrayList<>(collectMatches(mob, pos, pool));
        
        //a group with replacements but no usable set still replaces: empty set, that group's chance
        if (candidates.isEmpty() && !pool.isEmpty() && pool.chance != null) {
            candidates.add(new EquipmentSetContext(
                    new MobEquipmentReloadListener.EquipmentSet(null, 1, new EnumMap<>(EquipmentSlot.class)), pool.chance));
        }
        if (candidates.isEmpty()) return SpawnResult.UNCHANGED;
        
        EquipmentSetContext chosen = pickWeightedSet(candidates, mob.getRandom());
        if (chosen == null) return SpawnResult.UNCHANGED;
        if (mob.getRandom().nextFloat() > chosen.chance()) return SpawnResult.UNCHANGED;
        
        ResourceLocation replacementId = pool.isEmpty() ? null : pool.pick(mob.getRandom());
        ResourceLocation ownId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        //picking the mob's own id means "keep it": still equipped, just not replaced
        if (replacementId != null && !replacementId.equals(ownId)) {
            LivingEntity replacement = spawnReplacement(mob, replacementId, chosen.equipmentSet());
            
            if (replacement != null) {
                mob.discard();
                return SpawnResult.REPLACED;
            }
        }
        
        applyEquipmentSet(mob, chosen.equipmentSet());
        return SpawnResult.EQUIPPED;
    }
    
    public static void queueSpawnProcessing(LivingEntity mob, BlockPos pos) {
        long processAt = mob.level().getGameTime() + 1;
        PENDING_SPAWNS.add(new PendingSpawn(mob, pos.immutable(), processAt));
    }
    
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
        return collectMatches(mob, pos, null);
    }
    
    private static List<EquipmentSetContext> collectMatches(LivingEntity mob, BlockPos pos, ReplacementPool pool) {
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
                if (!dimensionMatches(biomeGroup.dimensions, level)) continue;
                if (!spawnTypeMatches(biomeGroup.spawnTypes, mob)) continue;
                if (!structureMatches(biomeGroup.structures, level, pos)) continue;
                
                float effectiveChance = hasOverride(biomeGroup.chance) ? biomeGroup.chance : difficultyChance;
                if (pool != null) pool.add(biomeGroup.replacements, effectiveChance);
                
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
        if (type == null) return null;
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
    
    private static boolean dimensionMatches(List<MobEquipmentReloadListener.DimensionMatch> matchers, Level level) {
        if (matchers.isEmpty()) return true;
        ResourceLocation dimension = level.dimension().location();
        for (MobEquipmentReloadListener.DimensionMatch matcher : matchers) {
            if (matcher instanceof MobEquipmentReloadListener.DimensionMatch.Global) return true;
            if (matcher instanceof MobEquipmentReloadListener.DimensionMatch.Id id && id.id().equals(dimension)) return true;
        }
        return false;
    }
    
    /** Jockey spawn type is highly inconsistent, often only affecting one entity or not
     * being set at all (chicken jockey receives the JOCKEY type, but spider jockey copies the type
     * from the originally spawned mob) so this type has to be handled separately by checking for the
     * entity's riders/mounts with a 1 tick delay after original spawn. MobSpawnType.JOCKEY is still used for validation and serialization.
     */
    private static boolean spawnTypeMatches(List<MobEquipmentReloadListener.SpawnTypeMatch> matchers, LivingEntity mob) {
        if (matchers.isEmpty()) return true;
        
        MobSpawnType actual = recordedSpawnType(mob);
        boolean hasPositive = false;
        boolean positiveMatched = false;
        
        boolean jockey = mob.isPassenger() || !mob.getPassengers().isEmpty();
        
        for (MobEquipmentReloadListener.SpawnTypeMatch m : matchers) {
            boolean same = m.type() == MobSpawnType.JOCKEY ? jockey : m.type() == actual;
            
            if (m.negated()) {
                if (same) return false;
            } else {
                hasPositive = true;
                if (same) positiveMatched = true;
            }
        }
        
        return !hasPositive || positiveMatched;
    }
    
    private static MobSpawnType recordedSpawnType(LivingEntity mob) {
        CompoundTag data = mob.getPersistentData();
        if (!data.contains(SPAWN_TYPE_TAG)) return null;
        try { return MobSpawnType.valueOf(data.getString(SPAWN_TYPE_TAG)); }
        catch (IllegalArgumentException e) { return null; }
    }
    
    private static LivingEntity spawnReplacement(LivingEntity original, ResourceLocation id, MobEquipmentReloadListener.EquipmentSet set) {
        if (!(original.level() instanceof ServerLevel level)) return null;
        
        if (!ForgeRegistries.ENTITY_TYPES.containsKey(id)) {
            MobArmory.LOGGER.warn("Unknown replacement mob {}", id);
            return null;
        }
        
        Entity created = ForgeRegistries.ENTITY_TYPES.getValue(id).create(level);
        if (!(created instanceof Mob replacement)) {
            if (created != null) created.discard();
            MobArmory.LOGGER.warn("Replacement {} is not a Mob, ignoring", id);
            return null;
        }
        
        List<Entity> riders = new ArrayList<>(original.getPassengers()); //original is the mount
        Entity mount = original.getVehicle();                            //original is the rider
        
        replacement.moveTo(original.getX(), original.getY(), original.getZ(), original.getYRot(), original.getXRot());
        replacement.setYHeadRot(original.getYHeadRot());
        
        MobSpawnType spawnType = recordedSpawnType(original);
        if (spawnType == null) spawnType = MobSpawnType.NATURAL;
        
        replacement.finalizeSpawn(level, level.getCurrentDifficultyAt(replacement.blockPosition()), spawnType, null, null);
        
        CompoundTag data = replacement.getPersistentData();
        data.putString(SPAWN_TYPE_TAG, spawnType.name());
        data.putBoolean(PROCESSED_TAG, true); //so its own join event doesn't roll again
        
        applyEquipmentSet(replacement, set);
        
        if (!level.addFreshEntity(replacement)) {
            MobArmory.LOGGER.warn("Could not spawn replacement {}", id);
            return null;
        }
        
        for (Entity passenger : new ArrayList<>(replacement.getPassengers())) {
            level.addFreshEntityWithPassengers(passenger);
        }

        for (Entity rider : riders) {
            rider.stopRiding();
            rider.startRiding(replacement, true);
        }

        if (mount != null) {
            original.stopRiding();
            replacement.startRiding(mount, true);
        }
        
        return replacement;
    }
    
    //a group matches when the position is inside a piece of any listed structure (empty list = anywhere)
    private static boolean structureMatches(List<ResourceLocation> structures, Level level, BlockPos pos) {
        if (structures.isEmpty()) return true;
        if (!(level instanceof ServerLevel serverLevel)) return false;
        for (ResourceLocation id : structures) {
            ResourceKey<Structure> key = ResourceKey.create(Registries.STRUCTURE, id);
            if (serverLevel.structureManager().getStructureWithPieceAt(pos, key).isValid()) return true;
        }
        return false;
    }
    
    private static final class ReplacementPool {
        final Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        Float chance; //effective chance of the first group that contributed anything
        
        void add(List<MobEquipmentReloadListener.MobReplacement> list, float groupChance) {
            if (list.isEmpty()) return;
            for (MobEquipmentReloadListener.MobReplacement r : list) weights.putIfAbsent(r.mob(), r.weight());
            if (chance == null) chance = groupChance;
        }
        
        boolean isEmpty() {
            return weights.isEmpty();
        }
        
        ResourceLocation pick(RandomSource random) {
            int total = 0;
            for (int w : weights.values()) total += w;
            if (total <= 0) return null;
            int roll = random.nextInt(total);
            int cumulative = 0;
            for (Map.Entry<ResourceLocation, Integer> e : weights.entrySet()) {
                cumulative += e.getValue();
                if (roll < cumulative) return e.getKey();
            }
            return null;
        }
    }
    
    public static void runPendingSpawns() {
        if (PENDING_SPAWNS.isEmpty()) return;
        
        List<PendingSpawn> due = new ArrayList<>();
        Iterator<PendingSpawn> iterator = PENDING_SPAWNS.iterator();
        while (iterator.hasNext()) {
            PendingSpawn pending = iterator.next();
            if (!(pending.mob.level() instanceof ServerLevel level)) { iterator.remove(); continue; }
            if (level.getGameTime() < pending.processAt) continue;
            iterator.remove();
            due.add(pending);
        }
        
        for (PendingSpawn pending : due) {
            LivingEntity mob = pending.mob;
            if (!mob.isAlive() || mob.isRemoved()) continue;
            processSpawn(mob, pending.pos);
        }
    }
    
    private static final List<PendingSpawn> PENDING_SPAWNS = new ArrayList<>();
    
    private static final class PendingSpawn {
        final LivingEntity mob;
        final BlockPos pos;
        final long processAt;
        
        PendingSpawn(LivingEntity mob, BlockPos pos, long processAt) {
            this.mob = mob;
            this.pos = pos;
            this.processAt = processAt;
        }
    }
}
package com.estie.mobarmory.util;

import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.JsonToNBT;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.DifficultyInstance;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.biome.Biome;
import net.minecraftforge.common.BiomeDictionary;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.*;

public final class MobEquipmentSpawnUtil {
    
    private MobEquipmentSpawnUtil() {}
    
    public static final String SPAWN_FLAG_TAG = "MobArmory_SpawnFlag"; //set on every mob that has been through the join handler
    public static final String SPAWN_TYPE_TAG = "MobArmory_SpawnType";  //how the mob spawned, see SpawnTypeDetector
    
    // applying a set to an already-existing mob
    public static void applyEquipmentSet(EntityLivingBase mob, MobEquipmentReloadListener.EquipmentSet set) {
        ResourceLocation mobId = getMobId(mob);
        
        for (Map.Entry<EntityEquipmentSlot, List<MobEquipmentReloadListener.WeightedItem>> slotEntry : set.slots.entrySet()) {
            MobEquipmentReloadListener.WeightedItem chosen = pickWeightedItem(slotEntry.getValue(), mob.getRNG());
            if (chosen == null) continue;
            
            chosen.resolve();
            
            Item actual = chosen.item != null ? chosen.item : net.minecraft.init.Items.AIR;
            ItemStack stack = new ItemStack(actual);
            
            if (chosen.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
                MobEquipmentReloadListener.EnchantData.Random rnd = (MobEquipmentReloadListener.EnchantData.Random) chosen.enchant;
                stack = EnchantmentHelper.addRandomEnchantment(mob.getRNG(), stack, rnd.power(), true);
            }
            
            if (chosen.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined) {
                MobEquipmentReloadListener.EnchantData.Predefined pre = (MobEquipmentReloadListener.EnchantData.Predefined) chosen.enchant;
                
                for (int i = 0; i < pre.ids().size(); i++) {
                    ResourceLocation id = new ResourceLocation(pre.ids().get(i));
                    Enchantment enchantment = ForgeRegistries.ENCHANTMENTS.getValue(id);
                    if (enchantment != null) stack.addEnchantment(enchantment, pre.levels().get(i));
                }
            }
            
            if (chosen.nbt != null) applyItemNbt(stack, chosen.nbt, chosen.itemId, mobId);
            mob.setItemStackToSlot(slotEntry.getKey(), stack);
        }
        
        if (set.mobNbt != null) applyMobNbt(mob, set.mobNbt, mobId);
        if (set.lootTable != null) mob.getEntityData().setString("MobArmoryLootTable", set.lootTable);
        
        for (MobEquipmentReloadListener.PotionEffectEntry pe : set.potionEffects) {
            ResourceLocation rl = new ResourceLocation(pe.effectId);
            Potion effect = Potion.REGISTRY.getObject(rl);
            
            if (effect != null) mob.addPotionEffect(new PotionEffect(effect, pe.durationTicks, pe.amplifier));
            else MobArmory.LOGGER.warn("Unknown potion effect {} while equipping {}", pe.effectId, mobId);
        }
    }
    
    // matching sets
    
    public static boolean tryAddRandomMatchingSet(EntityLivingBase mob, BlockPos pos) {
        List<EquipmentSetContext> candidates = getAllSetsMatchingCriteria(mob, pos);
        if (candidates.isEmpty()) return false;
        
        EquipmentSetContext chosen = pickWeightedSet(candidates, mob.getRNG());
        if (chosen == null) return false;
        
        if (mob.getRNG().nextFloat() > chosen.chance) return false;
        
        applyEquipmentSet(mob, chosen.equipmentSet);
        return true;
    }
    
    public enum SpawnResult { UNCHANGED, EQUIPPED, REPLACEMENT_QUEUED }

    public static SpawnResult processSpawnDelayed(EntityLivingBase mob, BlockPos pos) {
        ReplacementPool pool = new ReplacementPool();
        List<EquipmentSetContext> candidates = new ArrayList<>(collectMatches(mob, pos, pool, false));
        
        //a group with replacements but no usable set still replaces: empty set, that group's chance
        if (candidates.isEmpty() && !pool.isEmpty() && pool.chance != null) {
            candidates.add(new EquipmentSetContext(
                    new MobEquipmentReloadListener.EquipmentSet(null, 1,
                            new EnumMap<>(EntityEquipmentSlot.class)),
                    pool.chance));
        }
        if (candidates.isEmpty()) return SpawnResult.UNCHANGED;
        
        EquipmentSetContext chosen = pickWeightedSet(candidates, mob.getRNG());
        if (chosen == null) return SpawnResult.UNCHANGED;
        if (mob.getRNG().nextFloat() > chosen.chance) return SpawnResult.UNCHANGED;
        
        ResourceLocation replacementId = pool.isEmpty() ? null : pool.pick(mob.getRNG());
        ResourceLocation ownId = getMobId(mob);
        
        //picking the mob's own id means "keep it": still equipped, just not replaced
        if (replacementId != null && !replacementId.equals(ownId)) {
            PENDING.add(new PendingReplacement(mob, replacementId, chosen.equipmentSet));
            return SpawnResult.REPLACEMENT_QUEUED;
        }
        
        applyEquipmentSet(mob, chosen.equipmentSet);
        return SpawnResult.EQUIPPED;
    }
    
    public static void queueSpawnProcessing(EntityLivingBase mob, BlockPos pos) {
        if (!mob.getEntityData().hasKey(SPAWN_TYPE_TAG)) {
            String type = SpawnTypeDetector.detect();
            if (type != null) mob.getEntityData().setString(SPAWN_TYPE_TAG, type);
        }
        
        PENDING_SPAWNS.add(new PendingSpawn(mob, pos, mob.world.getTotalWorldTime() + 1));
    }
    
    public static List<EquipmentSetContext> getAllSetsMatchingCriteria(EntityLivingBase mob, BlockPos pos) {
        return collectMatches(mob, pos, null, false);
    }
    
    //pool may be null (callers that only want sets). detectSpawnType is only true inside the join event
    private static List<EquipmentSetContext> collectMatches(EntityLivingBase mob, BlockPos pos, ReplacementPool pool, boolean detectSpawnType) {
        ResourceLocation mobId = getMobId(mob);
        if (mobId == null) return Collections.emptyList();
        
        World world = mob.getEntityWorld();
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return Collections.emptyList();
        
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(world);
        SpawnTypeLookup spawnType = new SpawnTypeLookup(mob, detectSpawnType);
        
        List<EquipmentSetContext> eligible = new ArrayList<>();
        
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
                
                Biome biome = world.getBiome(pos);
                ResourceLocation biomeId = biome.getRegistryName();
                
                for (MobEquipmentReloadListener.BiomeMatch matcher : biomeGroup.matchers) {
                    
                    if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Global) globalBiome = true;
                    
                    if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Id) {
                        MobEquipmentReloadListener.BiomeMatch.Id idMatch = (MobEquipmentReloadListener.BiomeMatch.Id) matcher;
                        if (biomeId != null && biomeId.equals(idMatch.id())) biomeMatches = true;
                    }
                    
                    if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Tag) {
                        MobEquipmentReloadListener.BiomeMatch.Tag tagMatch = (MobEquipmentReloadListener.BiomeMatch.Tag) matcher;
                        BiomeDictionary.Type type = BiomeDictionary.Type.getType(tagMatch.tag());
                        if (BiomeDictionary.hasType(biome, type)) biomeMatches = true;
                    }
                }
                
                if (!biomeMatches && !globalBiome) continue;
                if (!dimensionMatches(biomeGroup.dimensions, world)) continue;
                if (!spawnTypeMatches(biomeGroup.spawnTypes, mob, spawnType)) continue;
                if (!biomeGroup.structures.isEmpty() && !StructureMatcher.isInsideAny(world, pos, biomeGroup.structures)) continue;
                
                float effectiveChance = hasOverride(biomeGroup.chance) ? biomeGroup.chance : difficultyChance;
                if (pool != null) pool.add(biomeGroup.replacements, effectiveChance);
                
                //add every eligible set from this biome group.
                for (MobEquipmentReloadListener.EquipmentSet set : biomeGroup.sets) {
                    if (!set.timeOfDay.matches(world.getWorldTime())) continue;
                    if (!set.yLevel.matches(pos.getY())) continue;
                    
                    eligible.add(new EquipmentSetContext(set, effectiveChance));
                }
            }
            
            //global sets do not belong to a biome group, so they are added separately whenever the difficulty group matches.
            for (MobEquipmentReloadListener.EquipmentSet set : difficultyGroup.globalSets) {
                if (!set.timeOfDay.matches(world.getWorldTime())) continue;
                if (!set.yLevel.matches(pos.getY())) continue;
                
                eligible.add(new EquipmentSetContext(set, difficultyChance));
            }
        }
        
        return eligible;
    }
    
    // dimension / spawn type matching
    
    //empty = no restriction; otherwise any matcher (or "global") is enough
    private static boolean dimensionMatches(List<MobEquipmentReloadListener.DimensionMatch> matchers, World world) {
        if (matchers.isEmpty()) return true;
        
        int dimension = world.provider.getDimension();
        for (MobEquipmentReloadListener.DimensionMatch matcher : matchers) {
            if (matcher instanceof MobEquipmentReloadListener.DimensionMatch.Global) return true;
            if (((MobEquipmentReloadListener.DimensionMatch.Id) matcher).dimension() == dimension) return true;
        }
        return false;
    }
    
    //any "!x" that matches excludes. if there are plain entries, one of them has to match.
    //a mob whose type is unknown only passes if every entry is a "!"
    private static boolean spawnTypeMatches(List<MobEquipmentReloadListener.SpawnTypeMatch> matchers, EntityLivingBase mob, SpawnTypeLookup lookup) {
        if (matchers.isEmpty()) return true;
        
        String actual = lookup.get(); //null = unknown
        boolean hasPositive = false;
        boolean positiveMatched = false;
        
        boolean jockey = mob.isRiding() || !mob.getPassengers().isEmpty();
        
        for (MobEquipmentReloadListener.SpawnTypeMatch matcher : matchers) {
            boolean same;
            
            if ("jockey".equals(matcher.type())) {
                same = jockey;
            } else {
                same = matcher.type().equals(actual);
            }
            
            if (matcher.negated()) {
                if (same) return false;
            } else {
                hasPositive = true;
                if (same) positiveMatched = true;
            }
        }
        return !hasPositive || positiveMatched;
    }
    
    private static String recordedSpawnType(EntityLivingBase mob) {
        return mob.getEntityData().hasKey(SPAWN_TYPE_TAG) ? mob.getEntityData().getString(SPAWN_TYPE_TAG) : null;
    }
    
    //reads the recorded type, or (inside the join event only) detects it from the stack and records it. only asks once
    private static final class SpawnTypeLookup {
        private final EntityLivingBase mob;
        private final boolean detect;
        private boolean resolved;
        private String value;
        
        SpawnTypeLookup(EntityLivingBase mob, boolean detect) {
            this.mob = mob;
            this.detect = detect;
        }
        
        String get() {
            if (!resolved) {
                resolved = true;
                value = recordedSpawnType(mob);
                if (value == null && detect) {
                    value = SpawnTypeDetector.detect();
                    if (value != null) mob.getEntityData().setString(SPAWN_TYPE_TAG, value);
                }
            }
            return value;
        }
    }
    
    // replacements
    
    //replacements from every matching group, merged. same id in several groups: the first one wins, later ones are dropped
    private static final class ReplacementPool {
        final Map<ResourceLocation, Integer> weights = new LinkedHashMap<>();
        Float chance; //effective chance of the first group that contributed anything
        
        void add(List<MobEquipmentReloadListener.MobReplacement> list, float groupChance) {
            if (list.isEmpty()) return;
            
            for (MobEquipmentReloadListener.MobReplacement r : list) {
                if (!weights.containsKey(r.mob())) weights.put(r.mob(), r.weight());
            }
            if (chance == null) chance = groupChance;
        }
        
        boolean isEmpty() {
            return weights.isEmpty();
        }
        
        ResourceLocation pick(Random random) {
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
    
    private static final class PendingReplacement {
        final EntityLivingBase original;
        final ResourceLocation replacementId;
        final MobEquipmentReloadListener.EquipmentSet set;
        
        PendingReplacement(EntityLivingBase original, ResourceLocation replacementId, MobEquipmentReloadListener.EquipmentSet set) {
            this.original = original;
            this.replacementId = replacementId;
            this.set = set;
        }
    }
    
    private static final List<PendingReplacement> PENDING = new ArrayList<>();
    
    /** Call once per server tick (end phase). By now riding relationships exist, so jockeys can be kept together. */
    public static void runPendingReplacements() {
        if (!PENDING_SPAWNS.isEmpty()) {
            Iterator<PendingSpawn> iterator = PENDING_SPAWNS.iterator();
            
            while (iterator.hasNext()) {
                PendingSpawn pending = iterator.next();
                
                if (pending.mob.world.getTotalWorldTime() < pending.processAt) continue;
                
                iterator.remove();
                
                EntityLivingBase mob = pending.mob;
                
                if (mob.isDead || !mob.addedToChunk || mob.world == null || mob.world.isRemote) continue;
                
                processSpawnDelayed(mob, pending.pos);
            }
        }
        
        List<PendingReplacement> batch = new ArrayList<>(PENDING);
        PENDING.clear();
        
        for (PendingReplacement pending : batch) {
            EntityLivingBase original = pending.original;
            if (original.isDead || !original.addedToChunk || original.world == null || original.world.isRemote) continue;
            
            spawnReplacement(original, pending.replacementId, pending.set);
        }
    }
    
    private static boolean spawnReplacement(EntityLivingBase original, ResourceLocation id, MobEquipmentReloadListener.EquipmentSet set) {
        World world = original.world;
        
        Class<? extends Entity> entityClass = EntityList.getClass(id);
        if (entityClass == null) {
            MobArmory.LOGGER.warn("Unknown replacement mob {}", id);
            return false;
        }
        
        Entity created = EntityList.newEntity(entityClass, world);
        if (!(created instanceof EntityLivingBase)) {
            if (created != null) created.setDead();
            MobArmory.LOGGER.warn("Replacement {} is not a living mob, ignoring", id);
            return false;
        }
        EntityLivingBase replacement = (EntityLivingBase) created;
        
        //jockeys: remember who was riding whom before anything gets moved
        List<Entity> riders = new ArrayList<>(original.getPassengers()); //original is the mount
        Entity mount = original.getRidingEntity();                       //original is the rider
        
        replacement.setLocationAndAngles(original.posX, original.posY, original.posZ, original.rotationYaw, original.rotationPitch);
        replacement.rotationYawHead = original.rotationYawHead;
        
        //so its own join event doesn't roll again, and it keeps the spawn type of the mob it replaces
        replacement.getEntityData().setBoolean(SPAWN_FLAG_TAG, true);
        String spawnType = recordedSpawnType(original);
        if (spawnType != null) replacement.getEntityData().setString(SPAWN_TYPE_TAG, spawnType);
        
        if (replacement instanceof EntityLiving) {
            ((EntityLiving) replacement).onInitialSpawn(world.getDifficultyForLocation(new BlockPos(replacement)), null);
        }
        applyEquipmentSet(replacement, set);
        
        if (!world.spawnEntity(replacement)) {
            MobArmory.LOGGER.warn("Could not spawn replacement {}", id);
            return false;
        }
        
        //replaced mount: its riders switch over to the replacement
        for (Entity rider : riders) {
            rider.dismountRidingEntity();
            rider.startRiding(replacement, true);
        }
        
        //replaced rider: free the seat, then climb onto the same mount (force = ignore the passenger limit)
        if (mount != null) {
            original.dismountRidingEntity();
            replacement.startRiding(mount, true);
        }
        
        original.setDead();
        return true;
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
    
    // collecting sets
    
    public static List<MobEquipmentReloadListener.EquipmentSet> collectAllSets(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        
        for (MobEquipmentReloadListener.DifficultyGroup dg : entry.difficultyGroups) {
            for (MobEquipmentReloadListener.BiomeGroup bg : dg.biomeGroups) {
                all.addAll(bg.sets);
            }
            
            all.addAll(dg.globalSets);
        }
        
        return all;
    }
    
    // ENTRIES-based pools
    
    public static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandomSetForMob(ResourceLocation mobId, Random random) {
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return Optional.empty();
        
        return pickRandom(collectAllSets(entry), random);
    }
    
    public static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandomSetFromAnyMob(Random random) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        for (MobEquipmentReloadListener.MobEquipmentEntry entry : MobEquipmentReloadListener.ENTRIES.values()) {
            all.addAll(collectAllSets(entry));
        }
        
        return pickRandom(all, random);
    }
    
    // LOOKUP_FILES-based pools
    
    public static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandomSetFromFile(String fileName, Random random) {
        List<MobEquipmentReloadListener.EquipmentSet> all = new ArrayList<>();
        for (MobEquipmentReloadListener.MobEquipmentEntry fileEntry : MobEquipmentReloadListener.LOOKUP_FILES) {
            if (fileName.equals(fileEntry.fileName)) all.addAll(collectAllSets(fileEntry));
        }
        
        return pickRandom(all, random);
    }
    
    // pick + spawn
    
    public static EntityLivingBase spawnMobWithSet(WorldServer world, ResourceLocation mobId, MobEquipmentReloadListener.EquipmentSet set, Vec3d pos) {
        Class<? extends Entity> entityClass = EntityList.getClass(mobId);
        if (entityClass == null) return null;
        
        Entity entity = EntityList.newEntity(entityClass, world);
        if (!(entity instanceof EntityLivingBase)) return null;
        
        EntityLivingBase mob = (EntityLivingBase) entity;
        mob.setPositionAndRotation(pos.x, pos.y, pos.z, mob.rotationYaw, mob.rotationPitch);
        
        DifficultyInstance difficulty = world.getDifficultyForLocation(new BlockPos(pos));
        if (mob instanceof EntityLiving) {
            EntityLiving living = (EntityLiving) mob;
            living.onInitialSpawn(difficulty, null);
        }
        applyEquipmentSet(mob, set);
        world.spawnEntity(mob);
        
        return mob;
    }
    
    public static EntityLivingBase spawnMobWithRandomSet(WorldServer world, ResourceLocation mobId, Vec3d pos, Random random) {
        Optional<MobEquipmentReloadListener.EquipmentSet> chosen = pickRandomSetForMob(mobId, random);
        return chosen.map(equipmentSet -> spawnMobWithSet(world, mobId, equipmentSet, pos)).orElse(null);
    }
    
    public static EntityLivingBase spawnMobWithRandomSetFromFile(WorldServer world, ResourceLocation mobId, String fileName, Vec3d pos, Random random) {
        Optional<MobEquipmentReloadListener.EquipmentSet> chosen = pickRandomSetFromFile(fileName, random);
        return chosen.map(equipmentSet -> spawnMobWithSet(world, mobId, equipmentSet, pos)).orElse(null);
    }
    
    public static EntityLivingBase spawnMobWithRandomSetFromAnyMob(WorldServer world, ResourceLocation mobId, Vec3d pos, Random random) {
        Optional<MobEquipmentReloadListener.EquipmentSet> chosen = pickRandomSetFromAnyMob(random);
        return chosen.map(equipmentSet -> spawnMobWithSet(world, mobId, equipmentSet, pos)).orElse(null);
    }
    
    // NBT helpers
    
    private static void applyItemNbt(ItemStack stack, String rawNbt, String itemId, ResourceLocation mobId) {
        try {
            String trimmed = rawNbt.trim();
            String wrapped = trimmed.startsWith("{") ? trimmed : "{" + trimmed + "}";
            
            NBTTagCompound userTag = JsonToNBT.getTagFromJson(wrapped);
            NBTTagCompound existing = stack.getTagCompound();
            
            if (existing != null) existing.merge(userTag);
            else stack.setTagCompound(userTag);
            
        } catch (Exception e) {
            MobArmory.LOGGER.warn("Failed to parse item NBT '{}' for {} on {}: {}", rawNbt, itemId, mobId, e.getMessage());
        }
    }
    
    private static void applyMobNbt(EntityLivingBase mob, String rawNbt, ResourceLocation mobId) {
        try {
            String trimmed = rawNbt.trim();
            String wrapped = trimmed.startsWith("{") ? trimmed : "{" + trimmed + "}";
            
            NBTTagCompound userTag = JsonToNBT.getTagFromJson(wrapped);
            NBTTagCompound existing = new NBTTagCompound();
            
            mob.writeToNBT(existing);
            existing.merge(userTag);
            mob.readFromNBT(existing);
            
        } catch (Exception e) {
            MobArmory.LOGGER.warn("Failed to parse mob NBT '{}' for {}: {}", rawNbt, mobId, e.getMessage());
        }
    }
    
    // private helpers
    
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
    
    private static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandom(List<MobEquipmentReloadListener.EquipmentSet> pool, Random random) {
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }
    
    private static MobEquipmentReloadListener.WeightedItem pickWeightedItem(List<MobEquipmentReloadListener.WeightedItem> items, Random random) {
        int totalWeight = 0;
        
        for (MobEquipmentReloadListener.WeightedItem item : items) {
            totalWeight += item.weight;
        }
        
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (MobEquipmentReloadListener.WeightedItem item : items) {
            cumulative += item.weight;
            if (roll < cumulative) return item;
        }
        
        return items.get(items.size() - 1);
    }
    
    private static EquipmentSetContext pickWeightedSet(List<EquipmentSetContext> sets, Random random) {
        int totalWeight = 0;
        
        for (EquipmentSetContext context : sets) {
            totalWeight += context.equipmentSet.weight;
        }
        
        if (totalWeight <= 0) return null;
        
        int roll = random.nextInt(totalWeight);
        int cumulative = 0;
        
        for (EquipmentSetContext context : sets) {
            cumulative += context.equipmentSet.weight;
            
            if (roll < cumulative) return context;
        }
        
        return sets.get(sets.size() - 1);
    }
    
    private static ResourceLocation getMobId(EntityLivingBase mob) {
        if (mob instanceof EntityPlayer) {
            return new ResourceLocation("minecraft", "player");
        }
        
        return EntityList.getKey(mob);
    }
    
    private static final List<PendingSpawn> PENDING_SPAWNS = new ArrayList<>();
    
    private static final class PendingSpawn {
        final EntityLivingBase mob;
        final BlockPos pos;
        final long processAt;
        
        PendingSpawn(EntityLivingBase mob, BlockPos pos, long processAt) {
            this.mob = mob;
            this.pos = pos;
            this.processAt = processAt;
        }
    }
}
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
    
    public static List<EquipmentSetContext> getAllSetsMatchingCriteria(EntityLivingBase mob, BlockPos pos) {
        ResourceLocation mobId = getMobId(mob);
        if (mobId == null) return Collections.emptyList();
        
        World world = mob.getEntityWorld();
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return Collections.emptyList();
        
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(world);
        
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
                
                float effectiveChance = hasOverride(biomeGroup.chance) ? biomeGroup.chance : difficultyChance;
                
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
}
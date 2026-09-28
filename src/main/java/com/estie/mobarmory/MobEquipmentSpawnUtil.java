package com.estie.mobarmory;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.enchantment.Enchantment;
import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
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
import net.minecraft.world.WorldServer;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.*;

public final class MobEquipmentSpawnUtil {
    
    private MobEquipmentSpawnUtil() {}
    
    // applying a set to an already-existing mob
    public static void applyEquipmentSet(EntityLiving mob, MobEquipmentReloadListener.EquipmentSet set) {
        ResourceLocation mobId = EntityList.getKey(mob);
        
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
    
    // spawning with a specific set
    public static EntityLiving spawnMobWithSet(WorldServer world, ResourceLocation mobId, MobEquipmentReloadListener.EquipmentSet set, Vec3d pos) {
        Class<? extends Entity> entityClass = EntityList.getClass(mobId);
        if (entityClass == null || !EntityLiving.class.isAssignableFrom(entityClass)) return null;
        
        Entity entity = EntityList.newEntity(entityClass, world);
        if (!(entity instanceof EntityLiving)) return null;
        
        EntityLiving mob = (EntityLiving) entity;
        mob.setPositionAndRotation(pos.x, pos.y, pos.z, mob.rotationYaw, mob.rotationPitch);
        
        DifficultyInstance difficulty = world.getDifficultyForLocation(new BlockPos(pos));
        mob.onInitialSpawn(difficulty, null);
        applyEquipmentSet(mob, set);
        world.spawnEntity(mob);
        
        return mob;
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
    
    public static Optional<MobEquipmentReloadListener.EquipmentSet>
    pickRandomSetFromAnyMob(Random random) {
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
    
    private static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandom(List<MobEquipmentReloadListener.EquipmentSet> pool, Random random) {
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }
    
    // pick + spawn
    
    public static EntityLiving spawnMobWithRandomSet(WorldServer world, ResourceLocation mobId, Vec3d pos, Random random) {
        Optional<MobEquipmentReloadListener.EquipmentSet> chosen = pickRandomSetForMob(mobId, random);
        return chosen.map(equipmentSet -> spawnMobWithSet(world, mobId, equipmentSet, pos)).orElse(null);
    }
    
    public static EntityLiving spawnMobWithRandomSetFromFile(WorldServer world, ResourceLocation mobId, String fileName, Vec3d pos, Random random) {
        Optional<MobEquipmentReloadListener.EquipmentSet> chosen = pickRandomSetFromFile(fileName, random);
        return chosen.map(equipmentSet -> spawnMobWithSet(world, mobId, equipmentSet, pos)).orElse(null);
    }
    
    public static EntityLiving spawnMobWithRandomSetFromAnyMob(WorldServer world, ResourceLocation mobId, Vec3d pos, Random random) {
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
    
    private static void applyMobNbt(EntityLiving mob, String rawNbt, ResourceLocation mobId) {
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
}
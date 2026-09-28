package com.estie.mobarmory;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.MobSpawnType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraft.world.item.enchantment.EnchantmentHelper;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public final class MobEquipmentSpawnUtil {
    private MobEquipmentSpawnUtil() {}
    
    //applying a set to an already-existing mob
    
    public static void applyEquipmentSet(Mob mob, MobEquipmentReloadListener.EquipmentSet set) {
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        
        for (var slotEntry : set.slots.entrySet()) {
            MobEquipmentReloadListener.WeightedItem chosen = pickWeightedItem(slotEntry.getValue(), mob.getRandom());
            if (chosen == null) continue;
            
            chosen.resolve();
            Item actual = chosen.item != null ? chosen.item : Items.AIR;
            ItemStack stack = new ItemStack(actual);
            
            if (chosen.enchant instanceof MobEquipmentReloadListener.EnchantData.Random rnd) {
                EnchantmentHelper.enchantItem(mob.getRandom(), stack, rnd.power(), false);
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
    
    //spawning with a specific set
    
    public static Mob spawnMobWithSet(ServerLevel level, EntityType<? extends Mob> type, MobEquipmentReloadListener.EquipmentSet set, Vec3 pos) {
        return spawnMobWithSet(level, type, set, pos, MobSpawnType.COMMAND);
    }
    
    public static Mob spawnMobWithSet(ServerLevel level, ResourceLocation mobId, MobEquipmentReloadListener.EquipmentSet set, Vec3 pos, MobSpawnType spawnType) {
        EntityType<?> type = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
        if (!(type != null && Mob.class.isAssignableFrom(type.getBaseClass()))) return null;
        @SuppressWarnings("unchecked")
        EntityType<? extends Mob> mobType = (EntityType<? extends Mob>) type;
        return spawnMobWithSet(level, mobType, set, pos, spawnType);
    }
    
    public static Mob spawnMobWithSet(ServerLevel level, EntityType<? extends Mob> type, MobEquipmentReloadListener.EquipmentSet set, Vec3 pos, MobSpawnType spawnType) {
        Mob mob = type.create(level);
        if (mob == null) return null;
        
        mob.moveTo(pos.x, pos.y, pos.z, mob.getYRot(), mob.getXRot());
        mob.finalizeSpawn(level, level.getCurrentDifficultyAt(mob.blockPosition()), spawnType, null, null);
        applyEquipmentSet(mob, set);
        level.addFreshEntity(mob);
        return mob;
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
    
    private static Optional<MobEquipmentReloadListener.EquipmentSet> pickRandom(
            List<MobEquipmentReloadListener.EquipmentSet> pool, RandomSource random) {
        return pool.isEmpty() ? Optional.empty() : Optional.of(pool.get(random.nextInt(pool.size())));
    }
    
    //pick + spawn in one call
    
    public static Mob spawnMobWithRandomSet(ServerLevel level, ResourceLocation mobId, Vec3 pos, RandomSource random, MobSpawnType spawnType) {
        return pickRandomSetForMob(mobId, random)
                .map(set -> spawnMobWithSet(level, mobId, set, pos, spawnType))
                .orElse(null);
    }
    
    public static Mob spawnMobWithRandomSetFromFile(ServerLevel level, ResourceLocation mobId, String fileName, Vec3 pos, RandomSource random, MobSpawnType spawnType) {
        return pickRandomSetFromFile(fileName, random)
                .map(set -> spawnMobWithSet(level, mobId, set, pos, spawnType))
                .orElse(null);
    }
    
    public static Mob spawnMobWithRandomSetFromAnyMob(ServerLevel level, ResourceLocation mobId, Vec3 pos, RandomSource random, MobSpawnType spawnType) {
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
    
    private static void applyMobNbt(Mob mob, String rawNbt, ResourceLocation mobId) {
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
}
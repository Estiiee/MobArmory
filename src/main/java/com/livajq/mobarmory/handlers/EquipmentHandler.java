package com.livajq.mobarmory.handlers;

import com.livajq.mobarmory.Config;
import com.livajq.mobarmory.MobArmory;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.core.Holder;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.TagParser;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.RandomSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.enchantment.Enchantment;
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
        if (!Config.enabled) return;
        if (!(event.getEntity() instanceof Mob mob)) return;
        if (event.getLevel().isClientSide()) return;
        if (event.loadedFromDisk()) return;
        
        ResourceLocation mobId = ForgeRegistries.ENTITY_TYPES.getKey(mob.getType());
        if (mobId == null) return;
        
        MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.ENTRIES.get(mobId);
        if (entry == null) return;
        
        MobEquipmentReloadListener.DifficultyLevel currentDifficulty = currentDifficulty(mob.level());
        MobEquipmentReloadListener.DifficultyGroup chosenDifficultyGroup = null;
        
        for (MobEquipmentReloadListener.DifficultyGroup group : entry.difficultyGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.DifficultyLevel matcher : group.matchers) {
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
        
        MobEquipmentReloadListener.BiomeGroup chosenBiomeGroup = null;
        
        for (MobEquipmentReloadListener.BiomeGroup group : chosenDifficultyGroup.biomeGroups) {
            boolean matches = false;
            boolean globalGroup = false;
            
            for (MobEquipmentReloadListener.BiomeMatch matcher : group.matchers) {
                if (matcher instanceof MobEquipmentReloadListener.BiomeMatch.Global) globalGroup = true;
                
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
                chosenBiomeGroup = group;
                break;
            }
        }
        
        List<MobEquipmentReloadListener.EquipmentSet> candidateSets;
        Float biomeGroupChance;
        
        if (chosenBiomeGroup != null) {
            candidateSets = chosenBiomeGroup.sets;
            biomeGroupChance = chosenBiomeGroup.chance;
        } else if (!chosenDifficultyGroup.globalSets.isEmpty()) {
            candidateSets = chosenDifficultyGroup.globalSets;
            biomeGroupChance = null;
        } else {
            return;
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
        
        for (var slotEntry : chosenSet.slots.entrySet()) {
            MobEquipmentReloadListener.WeightedItem chosen = pickWeightedItem(slotEntry.getValue(), mob.getRandom());
            
            if (chosen != null) {
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
        }
        
        if (chosenSet.mobNbt != null) applyMobNbt(mob, chosenSet.mobNbt, mobId);
        
        for (MobEquipmentReloadListener.PotionEffectEntry pe : chosenSet.potionEffects) {
            ResourceLocation rl = ResourceLocation.tryParse(pe.effectId);
            MobEffect effect = rl != null ? ForgeRegistries.MOB_EFFECTS.getValue(rl) : null;
            
            if (effect != null) mob.addEffect(new MobEffectInstance(effect, pe.durationTicks, pe.amplifier));
            else MobArmory.LOGGER.warn("Unknown potion effect {} while equipping {}", pe.effectId, mobId);
        }
    }
    
    //merges onto whatever the stack already has (e.g. enchants applied moments earlier) rather
    //than replacing its NBT outright
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
    
    //standard vanilla technique - the same save/merge/load /data merge entity itself uses
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
    
    private static boolean hasOverride(Float value) {
        return value != null && value != 0.0F;
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
    
    private static MobEquipmentReloadListener.WeightedItem pickWeightedItem(List<MobEquipmentReloadListener.WeightedItem> items, RandomSource random) {
        int totalWeight = items.stream().mapToInt(s -> s.weight).sum();
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
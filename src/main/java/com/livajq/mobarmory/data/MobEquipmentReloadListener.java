package com.livajq.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.livajq.mobarmory.MobArmory;
import net.minecraft.core.Holder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.enchantment.Enchantment;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class MobEquipmentReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    public static Map<ResourceLocation, MobEquipmentEntry> ENTRIES = Map.of();
    
    private static final Map<String, EquipmentSlot> SLOT_KEYS = Map.of(
            "head", EquipmentSlot.HEAD,
            "chest", EquipmentSlot.CHEST,
            "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET,
            "mainhand", EquipmentSlot.MAINHAND,
            "offhand", EquipmentSlot.OFFHAND
    );
    
    public MobEquipmentReloadListener() {
        super(GSON, "mob_equipment");
    }
    
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        Map<ResourceLocation, MobEquipmentEntry> parsed = new HashMap<>();
        
        for (var entry : resources.entrySet()) {
            try {
                JsonObject json = entry.getValue().getAsJsonObject();
                ResourceLocation mobId = new ResourceLocation(GsonHelper.getAsString(json, "mob"));
                float chance = GsonHelper.getAsFloat(json, "chance", 1.0F);
                
                List<BiomeGroup> biomeGroups = new ArrayList<>();
                List<EquipmentSet> globalSets = new ArrayList<>();
                
                //optional biomes entry to apply different equipment sets per biome(s)
                if (json.has("biomes")) {
                    JsonArray biomeArr = json.getAsJsonArray("biomes");
                    
                    for (JsonElement biomeEl : biomeArr) {
                        JsonObject biomeObj = biomeEl.getAsJsonObject();
                        
                        //matchers
                        List<BiomeMatch> matchers = new ArrayList<>();
                        JsonArray matchArr = biomeObj.getAsJsonArray("match");
                        
                        for (JsonElement mEl : matchArr) {
                            String raw = mEl.getAsString();
                            
                            //add tags for #, regular biome id otherwise
                            if (raw.equals("global")) matchers.add(new BiomeMatch.Global());
                            else if (raw.startsWith("#")) matchers.add(new BiomeMatch.Tag(new ResourceLocation(raw.substring(1))));
                            else matchers.add(new BiomeMatch.Id(new ResourceLocation(raw)));
                        }
                        
                        //equipment sets
                        List<EquipmentSet> sets = new ArrayList<>();
                        
                        if (biomeObj.has("sets")) {
                            JsonArray setArr = biomeObj.getAsJsonArray("sets");
                            for (JsonElement setEl : setArr) {
                                sets.add(parseSet(setEl.getAsJsonObject(), entry.getKey()));
                            }
                        }
                        
                        //implicit single set: biomeObj itself is the set
                        else sets.add(parseSet(biomeObj, entry.getKey()));
                        
                        biomeGroups.add(new BiomeGroup(matchers, sets));
                    }
                }
        
                if (biomeGroups.isEmpty()) {
                    if (json.has("sets")) {
                        JsonArray setArr = json.getAsJsonArray("sets");
                        for (JsonElement setEl : setArr) {
                            globalSets.add(parseSet(setEl.getAsJsonObject(), entry.getKey()));
                        }
                    }
                    //implicit single global set
                    else globalSets.add(parseSet(json, entry.getKey()));
                }
                
                //if (parsed.containsKey(mobId)) {
                //    MobArmory.LOGGER.warn("Duplicate mob_equipment entry for {} — file {} overwrites a previous one", mobId, entry.getKey());
                //}
                parsed.put(mobId, new MobEquipmentEntry(mobId, chance, biomeGroups, globalSets));
                
            } catch (Exception e) {
                MobArmory.LOGGER.error("Failed to parse mob_equipment entry {}", entry.getKey(), e);
            }
        }
        
        ENTRIES = Map.copyOf(parsed);
        MobArmory.LOGGER.info("Loaded {} mob equipment entries", ENTRIES.size());
    }
    
    private EquipmentSet parseSet(JsonObject json, ResourceLocation sourceKey) {
        int weight = GsonHelper.getAsInt(json, "weight", 1);
        Map<EquipmentSlot, List<WeightedItem>> slots = new EnumMap<>(EquipmentSlot.class);
        
        for (var slotKey : SLOT_KEYS.entrySet()) {
            if (!json.has(slotKey.getKey())) continue;
            
            JsonArray arr = GsonHelper.getAsJsonArray(json, slotKey.getKey());
            List<WeightedItem> items = new ArrayList<>();
            
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                ResourceLocation itemId = new ResourceLocation(GsonHelper.getAsString(obj, "item"));
                int itemWeight = GsonHelper.getAsInt(obj, "weight", 1);
                
                Item item = ForgeRegistries.ITEMS.getValue(itemId);
                if (item == null) {
                    MobArmory.LOGGER.warn("Unknown item {} in mob_equipment entry {}", itemId, sourceKey);
                    continue;
                }
                
                EnchantData enchant = null;
                
                //optional enchants added per item, either randomly with specified enchanting power or predefined
                if (obj.has("enchant")) {
                    JsonObject ench = obj.getAsJsonObject("enchant");
                    String type = GsonHelper.getAsString(ench, "type");
                    
                    if (type.equals("random")) {
                        int power = GsonHelper.getAsInt(ench, "power", 30);
                        enchant = new EnchantData.Random(power);
                    }
                    
                    else if (type.equals("predefined")) {
                        List<Holder<Enchantment>> enchants = new ArrayList<>();
                        List<Integer> levels = new ArrayList<>();
                        
                        JsonArray list = ench.getAsJsonArray("list");
                        for (JsonElement enchEl : list) {
                            JsonObject enchObj = enchEl.getAsJsonObject();
                            ResourceLocation enchId = new ResourceLocation(GsonHelper.getAsString(enchObj, "id"));
                            int level = GsonHelper.getAsInt(enchObj, "level");
                            
                            Holder<Enchantment> holder = ForgeRegistries.ENCHANTMENTS.getHolder(enchId).orElse(null);
                            if (holder == null) {
                                MobArmory.LOGGER.warn("Unknown enchantment {} in {}", enchId, sourceKey);
                                continue;
                            }
                            
                            enchants.add(holder);
                            levels.add(level);
                        }
                        
                        enchant = new EnchantData.Predefined(enchants, levels);
                    }
                }
                
                items.add(new WeightedItem(item, itemWeight, enchant));
            }
            
            if (!items.isEmpty()) slots.put(slotKey.getValue(), items);
        }
        
        return new EquipmentSet(weight, slots);
    }
    
    public static class MobEquipmentEntry {
        public final ResourceLocation mob;
        public final float chance;
  
        public final List<BiomeGroup> biomeGroups;
        public final List<EquipmentSet> globalSets;
        
        public MobEquipmentEntry(ResourceLocation mob, float chance, List<BiomeGroup> biomeGroups, List<EquipmentSet> globalSets) {
            this.mob = mob;
            this.chance = chance;
            this.biomeGroups = biomeGroups;
            this.globalSets = globalSets;
        }
    }
    
    public record BiomeGroup(List<BiomeMatch> matchers, List<EquipmentSet> sets) {}
    
    public sealed interface BiomeMatch {
        record Tag(ResourceLocation tag) implements BiomeMatch {}
        record Id(ResourceLocation id) implements BiomeMatch {}
        record Global() implements BiomeMatch {}
    }
    
    public sealed interface EnchantData {
        record Random(int power) implements EnchantData {}
        record Predefined(List<Holder<Enchantment>> enchants, List<Integer> levels) implements EnchantData {}
    }
    
    public record EquipmentSet(int weight, Map<EquipmentSlot, List<WeightedItem>> slots) {}
    public record WeightedItem(Item item, int weight, EnchantData enchant) {}
}
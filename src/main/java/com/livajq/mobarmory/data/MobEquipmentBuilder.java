package com.livajq.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.livajq.mobarmory.MobArmory;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class MobEquipmentBuilder {
    
    private ResourceLocation mob;
    private float chance = 1.0f;
    
    private final List<BiomeGroupBuilder> biomeGroups = new ArrayList<>();
    
    public static MobEquipmentBuilder mob(String id) {
        MobEquipmentBuilder b = new MobEquipmentBuilder();
        b.mob = new ResourceLocation(id);
        return b;
    }
    
    public MobEquipmentBuilder chance(float chance) {
        this.chance = chance;
        return this;
    }

    public BiomeGroupBuilder biomeGroup() {
        BiomeGroupBuilder g = new BiomeGroupBuilder(this);
        biomeGroups.add(g);
        return g;
    }
    
    public void createFile(String fileName) {
        if (FMLLoader.isProduction()) {
            MobArmory.LOGGER.warn("Attempted to create a mob_equipment file outside of dev environment");
            return;
        }
        try {
            JsonObject json = buildJson();

            //output in run/data
            Path dir = Paths.get("data", MobArmory.MODID, "mob_equipment");
            Files.createDirectories(dir);

            Path file = dir.resolve(fileName + ".json");
        
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonString = gson.toJson(json);
            
            Files.writeString(file, jsonString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
        } catch (IOException e) {
            throw new RuntimeException("Failed to create mob equipment file: " + fileName, e);
        }
    }
    
    private JsonObject buildJson() {
        JsonObject root = new JsonObject();
        
        root.addProperty("mob", mob.toString());
        root.addProperty("chance", chance);
        
        if (!biomeGroups.isEmpty()) {
            JsonArray biomeArr = new JsonArray();
            for (BiomeGroupBuilder g : biomeGroups) {
                biomeArr.add(g.toJson());
            }
            root.add("biomes", biomeArr);
            return root;
        }
        
        return root;
    }
    
    public static class BiomeGroupBuilder {
        
        private final MobEquipmentBuilder parent;
        
        private final List<String> matchers = new ArrayList<>();
        private final List<EquipmentSetBuilder> sets = new ArrayList<>();
        
        public BiomeGroupBuilder(MobEquipmentBuilder parent) {
            this.parent = parent;
        }
        
        //biome matchers
        public BiomeGroupBuilder match(String raw) {
            matchers.add(raw);
            return this;
        }
        
        public BiomeGroupBuilder matches(String... raws) {
            Collections.addAll(matchers, raws);
            return this;
        }
        
        //use to skip biome restrictions.
        //can be mixed with match(), in which case specific groups take priority and global acts as a fallback for all other biomes
        public BiomeGroupBuilder global() {
            matchers.add("global");
            return this;
        }
        
        //equipment set entry
        public EquipmentSetBuilder set() {
            EquipmentSetBuilder s = new EquipmentSetBuilder(this);
            sets.add(s);
            return s;
        }
        
        public MobEquipmentBuilder endBiomeGroup() {
            return parent;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            
            JsonArray matchArr = new JsonArray();
            for (String m : matchers) matchArr.add(m);
            obj.add("match", matchArr);
            
            if (!sets.isEmpty()) {
                JsonArray setArr = new JsonArray();
                for (EquipmentSetBuilder s : sets) {
                    setArr.add(s.toJson());
                }
                obj.add("sets", setArr);
            }
            
            return obj;
        }
    }
    
    public static class EquipmentSetBuilder {
 
        private final BiomeGroupBuilder parentBiome;
        
        private int weight = 1;
        
        //slotName -> list of WeightedItemBuilder
        private final Map<String, List<WeightedItemBuilder>> slots = new HashMap<>();
 
        //biome group set
        public EquipmentSetBuilder(BiomeGroupBuilder parentBiome) {
            this.parentBiome = parentBiome;
        }
        
        public EquipmentSetBuilder weight(int w) {
            this.weight = w;
            return this;
        }
        
        //equipment slot entries
        public SlotBuilder slot(String slotName) {
            List<WeightedItemBuilder> list = slots.computeIfAbsent(slotName, k -> new ArrayList<>());
            return new SlotBuilder(this, list);
        }
        
        public BiomeGroupBuilder endSet() {
            return parentBiome;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            obj.addProperty("weight", weight);
            
            // Serialize slots
            for (var entry : slots.entrySet()) {
                String slotName = entry.getKey();
                List<WeightedItemBuilder> items = entry.getValue();
                
                JsonArray arr = new JsonArray();
                for (WeightedItemBuilder item : items) {
                    arr.add(item.toJson());
                }
                
                obj.add(slotName, arr);
            }
            
            return obj;
        }
    }
    
    public static class SlotBuilder {
        
        private final EquipmentSetBuilder parentSet;
        private final List<WeightedItemBuilder> items;
        
        public SlotBuilder(EquipmentSetBuilder parentSet, List<WeightedItemBuilder> items) {
            this.parentSet = parentSet;
            this.items = items;
        }
        
        public WeightedItemBuilder item(String itemId) {
            WeightedItemBuilder w = new WeightedItemBuilder(this, itemId);
            items.add(w);
            return w;
        }
     
        public EquipmentSetBuilder endSlot() {
            return parentSet;
        }
    }
    
    public static class WeightedItemBuilder {
        
        private final SlotBuilder parentSlot;
        
        private final String itemId;
        private int weight = 1;
        
        private EnchantBuilder enchantBuilder = null;
        
        public WeightedItemBuilder(SlotBuilder parentSlot, String itemId) {
            this.parentSlot = parentSlot;
            this.itemId = itemId;
        }
        
        public WeightedItemBuilder weight(int w) {
            this.weight = w;
            return this;
        }

        public EnchantBuilder randomEnchant() {
            this.enchantBuilder = new EnchantBuilder(this, true);
            return enchantBuilder;
        }
        
        public EnchantBuilder predefinedEnchant() {
            this.enchantBuilder = new EnchantBuilder(this, false);
            return enchantBuilder;
        }
        
        public SlotBuilder endItem() {
            return parentSlot;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            
            obj.addProperty("item", itemId);
            obj.addProperty("weight", weight);
            
            if (enchantBuilder != null) {
                obj.add("enchant", enchantBuilder.toJson());
            }
            
            return obj;
        }
    }
    
    public static class EnchantBuilder {
        
        private final WeightedItemBuilder parentItem;
        
        private final boolean isRandom;
        
        private int randomPower = 30;
        
        private final List<String> predefinedIds = new ArrayList<>();
        private final List<Integer> predefinedLevels = new ArrayList<>();
        
        public EnchantBuilder(WeightedItemBuilder parentItem, boolean isRandom) {
            this.parentItem = parentItem;
            this.isRandom = isRandom;
        }
        
        //used for random only
        public EnchantBuilder power(int p) {
            if (isRandom) {
                this.randomPower = p;
            }
            return this;
        }

        public EnchantBuilder addPredefined(String id, int level) {
            if (!isRandom) {
                predefinedIds.add(id);
                predefinedLevels.add(level);
            }
            return this;
        }
        
        public WeightedItemBuilder endEnchant() {
            return parentItem;
        }

        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            
            if (isRandom) {
                obj.addProperty("type", "random");
                obj.addProperty("power", randomPower);
            } else {
                obj.addProperty("type", "predefined");
                
                JsonArray arr = new JsonArray();
                for (int i = 0; i < predefinedIds.size(); i++) {
                    JsonObject enchObj = new JsonObject();
                    enchObj.addProperty("id", predefinedIds.get(i));
                    enchObj.addProperty("level", predefinedLevels.get(i));
                    arr.add(enchObj);
                }
                
                obj.add("list", arr);
            }
            
            return obj;
        }
    }
}
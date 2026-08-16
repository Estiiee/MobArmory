package com.livajq.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.livajq.mobarmory.Config;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.*;

public class MobEquipmentBuilder {
    
    private ResourceLocation mob;
    private float chance = 1.0f;
    
    private final List<DifficultyGroupBuilder> difficultyGroups = new ArrayList<>();
    
    public static MobEquipmentBuilder mob(String id) {
        MobEquipmentBuilder b = new MobEquipmentBuilder();
        b.mob = new ResourceLocation(id);
        return b;
    }
    
    public MobEquipmentBuilder chance(float chance) {
        this.chance = chance;
        return this;
    }
    
    public DifficultyGroupBuilder difficultyGroup() {
        DifficultyGroupBuilder g = new DifficultyGroupBuilder(this);
        difficultyGroups.add(g);
        return g;
    }
    
    public SaveResult createFile(String fileName) {
        Path dir = FMLPaths.GAMEDIR.get().resolve(Config.outputDirectory).normalize();
        Path file = dir.resolve(fileName + ".json").normalize();
        
        try {
            JsonObject json = buildJson();
            
            Files.createDirectories(dir);
            
            if (!file.startsWith(dir)) {
                return new SaveResult(false, file, new IllegalArgumentException("Invalid file name"));
            }
            
            Gson gson = new GsonBuilder().setPrettyPrinting().create();
            String jsonString = gson.toJson(json);
            
            Files.writeString(file, jsonString, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING);
            
            return new SaveResult(true, file, null);
            
        } catch (Exception e) {
            return new SaveResult(false, file, e);
        }
    }
    
    private JsonObject buildJson() {
        JsonObject root = new JsonObject();
        
        root.addProperty("mob", mob.toString());
        root.addProperty("chance", chance);
        
        if (!difficultyGroups.isEmpty()) {
            JsonArray diffArr = new JsonArray();
            for (DifficultyGroupBuilder g : difficultyGroups) {
                diffArr.add(g.toJson());
            }
            root.add("difficulties", diffArr);
        }
        
        return root;
    }
    
    public static class DifficultyGroupBuilder {
        
        private final MobEquipmentBuilder parent;
        
        private final List<String> matchers = new ArrayList<>();
        private Float chance = null;
        private final List<BiomeGroupBuilder> biomeGroups = new ArrayList<>();
        
        public DifficultyGroupBuilder(MobEquipmentBuilder parent) {
            this.parent = parent;
        }
        
        public DifficultyGroupBuilder easy() {
            matchers.add("easy");
            return this;
        }
        
        public DifficultyGroupBuilder normal() {
            matchers.add("normal");
            return this;
        }
        
        public DifficultyGroupBuilder hard() {
            matchers.add("hard");
            return this;
        }
        
        public DifficultyGroupBuilder hardcore() {
            matchers.add("hardcore");
            return this;
        }
        
        //difficulty matchers: "easy" / "normal" / "hard" / "hardcore"
        public DifficultyGroupBuilder match(String raw) {
            matchers.add(raw);
            return this;
        }
        
        public DifficultyGroupBuilder matches(String... raws) {
            Collections.addAll(matchers, raws);
            return this;
        }
        
        //use to skip difficulty restrictions.
        //can be mixed with match(), in which case specific groups take priority and global acts as a fallback for all other difficulties
        public DifficultyGroupBuilder global() {
            matchers.add("global");
            return this;
        }
        
        //overrides the mob-level chance as the default for biome groups in this difficulty group
        //that don't specify their own chance
        public DifficultyGroupBuilder chance(float chance) {
            this.chance = chance;
            return this;
        }
        
        public BiomeGroupBuilder biomeGroup() {
            BiomeGroupBuilder g = new BiomeGroupBuilder(this);
            biomeGroups.add(g);
            return g;
        }
        
        public MobEquipmentBuilder endDifficultyGroup() {
            return parent;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            
            JsonArray matchArr = new JsonArray();
            for (String m : matchers) matchArr.add(m);
            obj.add("match", matchArr);
            
            if (chance != null) obj.addProperty("chance", chance);
            
            if (!biomeGroups.isEmpty()) {
                JsonArray biomeArr = new JsonArray();
                for (BiomeGroupBuilder g : biomeGroups) {
                    biomeArr.add(g.toJson());
                }
                obj.add("biomes", biomeArr);
            }
            
            return obj;
        }
    }
    
    public static class BiomeGroupBuilder {
        
        private final DifficultyGroupBuilder parent;
        
        private final List<String> matchers = new ArrayList<>();
        private Float chance = null;
        private final List<EquipmentSetBuilder> sets = new ArrayList<>();
        
        public BiomeGroupBuilder(DifficultyGroupBuilder parent) {
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
        
        //overrides the difficulty group's/mob's chance for this specific biome group
        //e.g. cold biomes 50% frozen gear, hot biomes 30% fire gear
        public BiomeGroupBuilder chance(float chance) {
            this.chance = chance;
            return this;
        }
        
        //equipment set entry
        public EquipmentSetBuilder set() {
            EquipmentSetBuilder s = new EquipmentSetBuilder(this);
            sets.add(s);
            return s;
        }
        
        public DifficultyGroupBuilder endBiomeGroup() {
            return parent;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            
            JsonArray matchArr = new JsonArray();
            for (String m : matchers) matchArr.add(m);
            obj.add("match", matchArr);
            
            if (chance != null) obj.addProperty("chance", chance);
            
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
        
        private String name = null;
        private int weight = 1;
        private String mobNbt = null;
        private final List<PotionEffectBuilder> potionEffects = new ArrayList<>();
        private long timeOfDayMin = -1, timeOfDayMax = -1; // -1 = unset
        private String yComparator = null;
        private Integer yValue = null;
        
        //slotName -> list of WeightedItemBuilder
        private final Map<String, List<WeightedItemBuilder>> slots = new HashMap<>();
        
        //biome group set
        public EquipmentSetBuilder(BiomeGroupBuilder parentBiome) {
            this.parentBiome = parentBiome;
        }
        
        public EquipmentSetBuilder name(String name) {
            this.name = name;
            return this;
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
        
        public EquipmentSetBuilder mobNbt(String nbt) {
            this.mobNbt = nbt;
            return this;
        }
        
        public EquipmentSetBuilder potionEffect(String effectId, int durationTicks, int amplifier) {
            potionEffects.add(new PotionEffectBuilder(effectId, durationTicks, amplifier));
            return this;
        }
        
        public EquipmentSetBuilder timeOfDay(long min, long max) {
            this.timeOfDayMin = min;
            this.timeOfDayMax = max;
            return this;
        }
        
        public EquipmentSetBuilder yLevel(String comparator, int value) {
            this.yComparator = comparator;
            this.yValue = value;
            return this;
        }
        
        public BiomeGroupBuilder endSet() {
            return parentBiome;
        }
        
        public JsonObject toJson() {
            JsonObject obj = new JsonObject();
            
            if (name != null) obj.addProperty("name", name);
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
            
            if (mobNbt != null) obj.addProperty("mob_nbt", mobNbt);
            
            if (!potionEffects.isEmpty()) {
                JsonArray arr = new JsonArray();
                for (PotionEffectBuilder pe : potionEffects) arr.add(pe.toJson());
                obj.add("potion_effects", arr);
            }
            
            if (timeOfDayMin >= 0) {
                JsonObject t = new JsonObject();
                t.addProperty("min", timeOfDayMin);
                t.addProperty("max", timeOfDayMax);
                obj.add("time_of_day", t);
            }
            
            if (yComparator != null) {
                JsonObject y = new JsonObject();
                y.addProperty("comparator", yComparator);
                y.addProperty("value", yValue);
                obj.add("y_level", y);
            }
            
            return obj;
        }
    }
    
    private static class PotionEffectBuilder {
        private final String effectId;
        private final int duration, amplifier;
        
        PotionEffectBuilder(String effectId, int duration, int amplifier) {
            this.effectId = effectId;
            this.duration = duration;
            this.amplifier = amplifier;
        }
        
        JsonObject toJson() {
            JsonObject o = new JsonObject();
            o.addProperty("effect", effectId);
            o.addProperty("duration", duration);
            o.addProperty("amplifier", amplifier);
            return o;
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
        private String nbt = null;
        
        private EnchantBuilder enchantBuilder = null;
        
        public WeightedItemBuilder(SlotBuilder parentSlot, String itemId) {
            this.parentSlot = parentSlot;
            this.itemId = itemId;
        }
        
        public WeightedItemBuilder weight(int w) {
            this.weight = w;
            return this;
        }
        
        public WeightedItemBuilder nbt(String nbt) {
            this.nbt = nbt;
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
            
            if (enchantBuilder != null) obj.add("enchant", enchantBuilder.toJson());
            if (nbt != null) obj.addProperty("nbt", nbt);
            
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
    
    public static class SaveResult {
        public final boolean success;
        public final Path path;
        public final Exception error;
        
        public SaveResult(boolean success, Path path, Exception error) {
            this.success = success;
            this.path = path;
            this.error = error;
        }
    }
    
}
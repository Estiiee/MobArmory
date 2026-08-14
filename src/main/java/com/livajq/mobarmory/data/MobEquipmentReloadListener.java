package com.livajq.mobarmory.data;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.livajq.mobarmory.MobArmory;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.server.packs.resources.SimpleJsonResourceReloadListener;
import net.minecraft.util.GsonHelper;
import net.minecraft.util.profiling.ProfilerFiller;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.*;

public class MobEquipmentReloadListener extends SimpleJsonResourceReloadListener {
    private static final Gson GSON = new Gson();
    private static final String DIRECTORY = "mob_equipment";
    public static Map<ResourceLocation, MobEquipmentEntry> ENTRIES = Map.of();
    
    //command-only, unmerged view: one MobEquipmentEntry per source file (fileName set), instead of
    //one merged entry per mob (fileName null, as in ENTRIES). lets /mobarmory listmobsets show
    //every "zombie_snowy", "zombie_hardcore" etc. separately for inspection/editing.
    public static List<MobEquipmentEntry> LOOKUP_FILES = new ArrayList<>();
    
    private static final Map<String, EquipmentSlot> SLOT_KEYS = Map.of(
            "head", EquipmentSlot.HEAD,
            "chest", EquipmentSlot.CHEST,
            "legs", EquipmentSlot.LEGS,
            "feet", EquipmentSlot.FEET,
            "mainhand", EquipmentSlot.MAINHAND,
            "offhand", EquipmentSlot.OFFHAND
    );
    
    public MobEquipmentReloadListener() {
        super(GSON, DIRECTORY);
    }
    
    @Override
    protected void apply(Map<ResourceLocation, JsonElement> resources, ResourceManager manager, ProfilerFiller profiler) {
        
        //group JSONs by mob ID. no override branch here - any two files targeting the same mob merge
        Map<ResourceLocation, List<JsonFile>> grouped = new HashMap<>();
        
        for (var entry : resources.entrySet()) {
            ResourceLocation fileId = entry.getKey();
            JsonObject json = entry.getValue().getAsJsonObject();
            
            ResourceLocation mobId;
            try {
                mobId = new ResourceLocation(GsonHelper.getAsString(json, "mob"));
            } catch (Exception e) {
                MobArmory.LOGGER.debug("Skipping mob_equipment file {} - no valid 'mob' field", fileId);
                continue;
            }
            
            grouped.computeIfAbsent(mobId, k -> new ArrayList<>())
                    .add(new JsonFile(fileId, json));
        }
        
        Map<ResourceLocation, MobEquipmentEntry> parsed = new HashMap<>();
        List<MobEquipmentEntry> lookupFiles = new ArrayList<>();
        
        for (var mobEntry : grouped.entrySet()) {
            ResourceLocation mobId = mobEntry.getKey();
            List<JsonFile> files = mobEntry.getValue();
            
            //deterministic order regardless of HashMap iteration - matters because biome/set
            //matching within a merged difficulty group is order-dependent (first match-or-global wins)
            files.sort(Comparator.comparing(f -> f.fileId().toString()));
            
            float mobChance = 1.0F;
            Map<List<DifficultyLevel>, DifficultyGroup> merged = new LinkedHashMap<>();
            
            for (JsonFile file : files) {
                try {
                    JsonObject json = file.json();
                    
                    //each file's own declared chance, independent of the merged mob-level value below
                    float fileChance = GsonHelper.getAsFloat(json, "chance", 1.0F);
                    mobChance = GsonHelper.getAsFloat(json, "chance", mobChance);
                    
                    List<DifficultyGroup> groups;
                    
                    if (json.has("difficulties")) {
                        JsonArray diffArr = json.getAsJsonArray("difficulties");
                        groups = new ArrayList<>();
                        for (JsonElement diffEl : diffArr) {
                            groups.add(parseDifficultyGroup(diffEl.getAsJsonObject(), file.fileId()));
                        }
                    } else {
                        GroupBody body = parseGroupBody(json, file.fileId());
                        groups = List.of(
                                new DifficultyGroup(
                                        List.of(DifficultyLevel.GLOBAL),
                                        null,
                                        body.biomeGroups(),
                                        body.globalSets()
                                )
                        );
                    }
                    
                    //command-only, unmerged: this file's own contribution kept as-is, alongside the merge below.
                    //fileName is the file's stripped path id (e.g. "zombie_snowy") - what listmobsets displays
                    lookupFiles.add(new MobEquipmentEntry(file.fileId().getPath(), mobId, fileChance, groups));
                    
                    for (DifficultyGroup g : groups) {
                        
                        //key = matchers as a set, so ["hard","hardcore"] and ["hardcore","hard"]
                        //merge into the same group instead of staying separate
                        List<DifficultyLevel> key = g.matchers.stream().sorted().toList();
                        
                        DifficultyGroup existing = merged.get(key);
                        
                        if (existing == null) {
                            merged.put(key, g);
                        } else {
                            Float mergedChance = g.chance != null ? g.chance : existing.chance;
                            
                            List<BiomeGroup> mergedBiomes = new ArrayList<>(existing.biomeGroups);
                            mergedBiomes.addAll(g.biomeGroups);
                            
                            List<EquipmentSet> mergedSets = new ArrayList<>(existing.globalSets);
                            mergedSets.addAll(g.globalSets);
                            
                            merged.put(key, new DifficultyGroup(existing.matchers, mergedChance, mergedBiomes, mergedSets));
                        }
                    }
                } catch (Exception e) {
                    MobArmory.LOGGER.error("Failed to parse mob_equipment entry {}", file.fileId(), e);
                }
            }
            
            parsed.put(mobId, new MobEquipmentEntry(null, mobId, mobChance, new ArrayList<>(merged.values())));
        }
        
        ENTRIES = Map.copyOf(parsed);
        LOOKUP_FILES = List.copyOf(lookupFiles);
        
        MobArmory.LOGGER.info("Loaded {} mob equipment entries", ENTRIES.size());
    }
    
    private record JsonFile(ResourceLocation fileId, JsonObject json) {}
    
    //editor round-trip entry point: builds a single MobEquipmentEntry straight from one JSON blob
    //(same schema as the datapack files), no multi-file merging - used when the client hands back
    //an edited entry, or when the server hands one to the client to open in the editor.
    public static MobEquipmentEntry fromJson(String fileName, JsonObject json) {
        ResourceLocation mob = json.has("mob") ? new ResourceLocation(json.get("mob").getAsString()) : null;
        float chance = GsonHelper.getAsFloat(json, "chance", 1.0F);
        ResourceLocation logKey = new ResourceLocation(MobArmory.MODID, "editor-transfer");
        
        List<DifficultyGroup> groups = new ArrayList<>();
        if (json.has("difficulties")) {
            for (JsonElement diffEl : json.getAsJsonArray("difficulties")) {
                groups.add(parseDifficultyGroup(diffEl.getAsJsonObject(), logKey));
            }
        }
        
        return new MobEquipmentEntry(fileName, mob, chance, groups);
    }
    
    //reverse of fromJson: same schema, entry -> JsonObject. fileName is deliberately excluded -
    //it's packet/transport metadata, not part of the file's own content.
    public static JsonObject toJson(MobEquipmentEntry entry) {
        JsonObject root = new JsonObject();
        if (entry.mob != null) root.addProperty("mob", entry.mob.toString());
        root.addProperty("chance", entry.chance);
        
        if (!entry.difficultyGroups.isEmpty()) {
            JsonArray diffArr = new JsonArray();
            for (DifficultyGroup g : entry.difficultyGroups) diffArr.add(toJson(g));
            root.add("difficulties", diffArr);
        }
        
        return root;
    }
    
    private static JsonObject toJson(DifficultyGroup group) {
        JsonObject obj = new JsonObject();
        
        JsonArray matchArr = new JsonArray();
        for (DifficultyLevel d : group.matchers) matchArr.add(d.name().toLowerCase(Locale.ROOT));
        obj.add("match", matchArr);
        
        if (group.chance != null) obj.addProperty("chance", group.chance);
        
        if (!group.biomeGroups.isEmpty()) {
            JsonArray biomeArr = new JsonArray();
            for (BiomeGroup bg : group.biomeGroups) biomeArr.add(toJson(bg));
            obj.add("biomes", biomeArr);
        } else if (!group.globalSets.isEmpty()) {
            JsonArray setArr = new JsonArray();
            for (EquipmentSet s : group.globalSets) setArr.add(toJson(s));
            obj.add("sets", setArr);
        }
        
        return obj;
    }
    
    private static JsonObject toJson(BiomeGroup group) {
        JsonObject obj = new JsonObject();
        
        JsonArray matchArr = new JsonArray();
        for (BiomeMatch m : group.matchers) matchArr.add(biomeMatchToString(m));
        obj.add("match", matchArr);
        
        if (group.chance != null) obj.addProperty("chance", group.chance);
        
        JsonArray setArr = new JsonArray();
        for (EquipmentSet s : group.sets) setArr.add(toJson(s));
        obj.add("sets", setArr);
        
        return obj;
    }
    
    public static String biomeMatchToString(BiomeMatch match) {
        if (match instanceof BiomeMatch.Global) return "global";
        if (match instanceof BiomeMatch.Tag t) return "#" + t.tag();
        if (match instanceof BiomeMatch.Id i) return i.id().toString();
        throw new IllegalStateException("Unknown BiomeMatch: " + match);
    }
  
    public static BiomeMatch parseBiomeMatch(String raw) {
        if (raw.equals("global")) return new BiomeMatch.Global();
        else if (raw.startsWith("#")) return new BiomeMatch.Tag(new ResourceLocation(raw.substring(1)));
        else return new BiomeMatch.Id(new ResourceLocation(raw));
    }
    
    private static JsonObject toJson(EquipmentSet set) {
        JsonObject obj = new JsonObject();
        obj.addProperty("weight", set.weight);
        
        for (var slotEntry : set.slots.entrySet()) {
            String slotKey = SLOT_KEYS.entrySet().stream()
                    .filter(e -> e.getValue() == slotEntry.getKey())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow();
            
            JsonArray arr = new JsonArray();
            for (WeightedItem item : slotEntry.getValue()) arr.add(toJson(item));
            obj.add(slotKey, arr);
        }
        
        return obj;
    }
    
    private static JsonObject toJson(WeightedItem item) {
        JsonObject obj = new JsonObject();
        
        //should never be null for a real registered Item, but a config-driven registry lookup
        //failing silently is worse than an obviously-wrong fallback value
        obj.addProperty("item", item.itemId);
        obj.addProperty("weight", item.weight);
        
        if (item.enchant != null) obj.add("enchant", toJson(item.enchant));
        
        return obj;
    }
    
    private static JsonObject toJson(EnchantData enchant) {
        JsonObject obj = new JsonObject();
        
        if (enchant instanceof EnchantData.Random r) {
            obj.addProperty("type", "random");
            obj.addProperty("power", r.power());
        } else if (enchant instanceof EnchantData.Predefined p) {
            obj.addProperty("type", "predefined");
            
            JsonArray arr = new JsonArray();
            for (int i = 0; i < p.ids().size(); i++) {
                JsonObject e = new JsonObject();
                e.addProperty("id", p.ids().get(i));
                e.addProperty("level", p.levels().get(i));
                arr.add(e);
            }
            obj.add("list", arr);
        }
        
        return obj;
    }
    
    private static DifficultyGroup parseDifficultyGroup(JsonObject json, ResourceLocation sourceKey) {
        List<DifficultyLevel> matchers = new ArrayList<>();
        JsonArray matchArr = json.getAsJsonArray("match");
        
        for (JsonElement mEl : matchArr) {
            matchers.add(DifficultyLevel.fromString(mEl.getAsString(), sourceKey));
        }
        
        Float groupChance = json.has("chance") ? json.get("chance").getAsFloat() : null;
        
        GroupBody body = parseGroupBody(json, sourceKey);
        return new DifficultyGroup(matchers, groupChance, body.biomeGroups(), body.globalSets());
    }
    
    //shared by a difficulty group and by the top-level fallback (no "difficulties" key):
    //either a "biomes" array, or a "sets" array / implicit single set with no biome restriction
    private static GroupBody parseGroupBody(JsonObject json, ResourceLocation sourceKey) {
        List<BiomeGroup> biomeGroups = new ArrayList<>();
        List<EquipmentSet> globalSets = new ArrayList<>();
        
        if (json.has("biomes")) {
            JsonArray biomeArr = json.getAsJsonArray("biomes");
            
            for (JsonElement biomeEl : biomeArr) {
                biomeGroups.add(parseBiomeGroup(biomeEl.getAsJsonObject(), sourceKey));
            }
        } else {
            if (json.has("sets")) {
                JsonArray setArr = json.getAsJsonArray("sets");
                for (JsonElement setEl : setArr) {
                    globalSets.add(parseSet(setEl.getAsJsonObject(), sourceKey));
                }
            }
            //implicit single global set: the object itself is the set
            else globalSets.add(parseSet(json, sourceKey));
        }
        
        return new GroupBody(biomeGroups, globalSets);
    }
    
    private static BiomeGroup parseBiomeGroup(JsonObject biomeObj, ResourceLocation sourceKey) {
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
        
        //optional per-biome-group chance, overrides the difficulty group's/mob's chance
        Float groupChance = biomeObj.has("chance") ? biomeObj.get("chance").getAsFloat() : null;
        
        //equipment sets
        List<EquipmentSet> sets = new ArrayList<>();
        
        if (biomeObj.has("sets")) {
            JsonArray setArr = biomeObj.getAsJsonArray("sets");
            for (JsonElement setEl : setArr) {
                sets.add(parseSet(setEl.getAsJsonObject(), sourceKey));
            }
        }
        //implicit single set: biomeObj itself is the set
        else sets.add(parseSet(biomeObj, sourceKey));
        
        return new BiomeGroup(matchers, groupChance, sets);
    }
    
    private static EquipmentSet parseSet(JsonObject json, ResourceLocation sourceKey) {
        String name = json.has("name") ? json.get("name").getAsString() : null;
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
                        List<String> ids = new ArrayList<>();
                        List<Integer> levels = new ArrayList<>();
                        
                        JsonArray list = ench.getAsJsonArray("list");
                        for (JsonElement enchEl : list) {
                            JsonObject enchObj = enchEl.getAsJsonObject();
                            String enchId = GsonHelper.getAsString(enchObj, "id");
                            int level = GsonHelper.getAsInt(enchObj, "level");
                            
                            ids.add(enchId);
                            levels.add(level);
                        }
                        
                        enchant = new EnchantData.Predefined(ids, levels);
                    }
                }
                
                String rawId = GsonHelper.getAsString(obj, "item");
                items.add(new WeightedItem(rawId, itemWeight, enchant));
            }
            
            if (!items.isEmpty()) slots.put(slotKey.getValue(), items);
        }
        
        return new EquipmentSet(name, weight, slots);
    }
    
    public static class MobEquipmentEntry {
        //null for merged entries in ENTRIES, set to the source file's name (e.g. "zombie_snowy")
        //for the unmerged per-file entries in LOOKUP_FILES
        public String fileName;
        public ResourceLocation mob;
        public float chance;
        public List<DifficultyGroup> difficultyGroups;
        
        public MobEquipmentEntry(String fileName, ResourceLocation mob, float chance, List<DifficultyGroup> difficultyGroups) {
            this.fileName = fileName;
            this.mob = mob;
            this.chance = chance;
            this.difficultyGroups = difficultyGroups;
        }
    }
    
    //chance: optional override; null means "fall through to whatever's less specific"
    public static class DifficultyGroup {
        public List<DifficultyLevel> matchers;
        public Float chance; // nullable
        public List<BiomeGroup> biomeGroups;
        public List<EquipmentSet> globalSets;
        
        public DifficultyGroup(List<DifficultyLevel> matchers,
                               Float chance,
                               List<BiomeGroup> biomeGroups,
                               List<EquipmentSet> globalSets) {
            this.matchers = matchers;
            this.chance = chance;
            this.biomeGroups = biomeGroups;
            this.globalSets = globalSets;
        }
    }
    
    public enum DifficultyLevel {
        EASY("easy"),
        NORMAL("normal"),
        HARD("hard"),
        HARDCORE("hardcore"),
        GLOBAL("global");
        
        private final String name;
        
        DifficultyLevel(String name) {
            this.name = name;
        }
        
        public static DifficultyLevel fromString(String raw, ResourceLocation sourceKey) {
            if (raw.equalsIgnoreCase("global")) return GLOBAL;
            
            try {
                return DifficultyLevel.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException e) {
                MobArmory.LOGGER.warn("Unknown difficulty '{}' in mob_equipment entry {}, treating as global", raw, sourceKey);
                return GLOBAL;
            }
        }
        
        public String toString() {
            return name;
        }
    }
    
    public static class BiomeGroup {
        public List<BiomeMatch> matchers;
        public Float chance; // nullable
        public List<EquipmentSet> sets;
        
        public BiomeGroup(List<BiomeMatch> matchers,
                          Float chance,
                          List<EquipmentSet> sets) {
            this.matchers = matchers;
            this.chance = chance;
            this.sets = sets;
        }
    }
    
    public sealed interface BiomeMatch {
        record Tag(ResourceLocation tag) implements BiomeMatch {}
        record Id(ResourceLocation id) implements BiomeMatch {}
        record Global() implements BiomeMatch {}
    }
    
    public static class WeightedItem {
        public String itemId;
        public Item item;
        public int weight;
        public EnchantData enchant;
        
        public WeightedItem(String itemId, int weight, EnchantData enchant) {
            this.itemId = itemId;
            this.weight = weight;
            this.enchant = enchant;
            this.item = null;
        }
        
        public void resolve() {
            this.item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        }
    }
    
    public sealed interface EnchantData {
        record Random(int power) implements EnchantData {}
        record Predefined(List<String> ids, List<Integer> levels) implements EnchantData {}
    }
    
    public static class EquipmentSet {
        public String name;
        public int weight;
        public Map<EquipmentSlot, List<WeightedItem>> slots;
        
        public EquipmentSet(String name, int weight, Map<EquipmentSlot, List<WeightedItem>> slots) {
            this.name = name;
            this.weight = weight;
            this.slots = slots;
        }
    }
    
    private record GroupBody(List<BiomeGroup> biomeGroups, List<EquipmentSet> globalSets) {}
}
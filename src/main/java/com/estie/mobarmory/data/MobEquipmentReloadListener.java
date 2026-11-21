package com.estie.mobarmory.data;

import com.estie.mobarmory.Config;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonPrimitive;
import com.estie.mobarmory.MobArmory;
import com.google.gson.JsonParser;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.util.JsonUtils;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class MobEquipmentReloadListener {
    public static Map<ResourceLocation, MobEquipmentEntry> ENTRIES = Collections.emptyMap();
    
    //command-only, unmerged view: one MobEquipmentEntry per source file (fileName set), instead of
    //one merged entry per mob (fileName null, as in ENTRIES). lets /mobarmory listmobsets show
    //every "zombie_snowy", "zombie_hardcore" etc. separately for inspection/editing.
    public static List<MobEquipmentEntry> LOOKUP_FILES = new ArrayList<>();
    
    private static final Map<String, EntityEquipmentSlot> SLOT_KEYS = new LinkedHashMap<>();
    static {
        SLOT_KEYS.put("head", EntityEquipmentSlot.HEAD);
        SLOT_KEYS.put("chest", EntityEquipmentSlot.CHEST);
        SLOT_KEYS.put("legs", EntityEquipmentSlot.LEGS);
        SLOT_KEYS.put("feet", EntityEquipmentSlot.FEET);
        SLOT_KEYS.put("mainhand", EntityEquipmentSlot.MAINHAND);
        SLOT_KEYS.put("offhand", EntityEquipmentSlot.OFFHAND);
    }
    
    //1.12.2 has no datapacks, so files are read straight from Config.getReadDirectory() instead of
    //data/<namespace>/mob_equipment. subfolders are walked like the datapack loader did, and a file's id
    //is its relative path without ".json" (e.g. "zombie_snowy" or "undead/zombie_snowy")
    public static void reload() {
        File dir = Config.getReadDirectory();
        
        if (!dir.exists()) {
            dir.mkdirs();
            copyExampleFile(dir);
        }
        
        Path root = dir.toPath();
        List<Path> paths;
        try (Stream<Path> walk = Files.walk(root)) {
            paths = walk.filter(p -> Files.isRegularFile(p) && p.getFileName().toString().endsWith(".json"))
                    .collect(Collectors.toList());
        } catch (IOException e) {
            MobArmory.LOGGER.error("Failed to scan mob_equipment directory {}", root, e);
            return;
        }
        
        Map<ResourceLocation, JsonElement> resources = new HashMap<>();
        
        for (Path path : paths) {
            String relative = root.relativize(path).toString().replace('\\', '/');
            relative = relative.substring(0, relative.length() - ".json".length());
            
            try (Reader reader = Files.newBufferedReader(path, StandardCharsets.UTF_8)) {
                JsonElement element = new JsonParser().parse(reader);
                if (!element.isJsonObject()) {
                    MobArmory.LOGGER.warn("Skipping mob_equipment file {} - not a JSON object", path);
                    continue;
                }
                resources.put(new ResourceLocation(MobArmory.MODID, relative), element);
            } catch (Exception e) {
                MobArmory.LOGGER.error("Failed to read mob_equipment file {}", path, e);
            }
        }
        
        apply(resources);
    }
    
    //only runs when the read directory didn't exist yet, so deleting the example afterwards sticks.
    //expects the file at src/main/resources/assets/mobarmory/mob_equipment/zombie_example.json
    private static void copyExampleFile(File dir) {
        String resource = "/assets/" + MobArmory.MODID + "/mob_equipment/zombie_example.json";
        try (InputStream in = MobEquipmentReloadListener.class.getResourceAsStream(resource)) {
            if (in == null) return;
            Files.copy(in, new File(dir, "zombie_example.json").toPath());
        } catch (IOException e) {
            MobArmory.LOGGER.warn("Could not copy example mob_equipment file", e);
        }
    }
    
    //for edit purposes only. upon saving in the editor, adds this file to the lookup list so it can be returned to at any point
    //does not affect ENTRIES so the .json still has to be moved to the read folder with a game restart for mobs to spawn with it
    public static void addOrReplaceLookupFile(MobEquipmentEntry entry) {
        if (entry == null || entry.fileName == null) return;
        
        List<MobEquipmentEntry> updated = new ArrayList<>(LOOKUP_FILES);
        
        for (int i = 0; i < updated.size(); i++) {
            MobEquipmentEntry existing = updated.get(i);
            
            if (entry.fileName.equals(existing.fileName)) {
                updated.set(i, entry);
                LOOKUP_FILES = Collections.unmodifiableList(updated);
                return;
            }
        }
        
        updated.add(entry);
        LOOKUP_FILES = Collections.unmodifiableList(updated);
    }
    
    private static void apply(Map<ResourceLocation, JsonElement> resources) {
        
        //every file is parsed once, in a fixed order, then handed to each mob it lists. sorted by file id so
        //first-wins values (top-level chance, difficulty group chance, replacement weights/chance) resolve the
        //same way every load. sets themselves are pooled, so their order doesn't matter
        List<ResourceLocation> fileIds = new ArrayList<>(resources.keySet());
        fileIds.sort(Comparator.comparing(ResourceLocation::toString));
        
        Map<ResourceLocation, List<ParsedFile>> grouped = new HashMap<>();
        List<MobEquipmentEntry> lookupFiles = new ArrayList<>();
        
        for (ResourceLocation fileId : fileIds) {
            ParsedFile file = parseFile(fileId, resources.get(fileId));
            if (file == null) continue;
            
            //command-only, unmerged: one entry per source file (all its mobs), alongside the per-mob merge below.
            //fileName is the file's stripped path id (e.g. "zombie_snowy") - what listmobsets displays
            lookupFiles.add(MobEquipmentEntry.of(file.fileId().getPath(), file.mobs(), file.chance(), file.groups()));
            
            if (file.fileId().getPath().equals("zombie_example") && !Config.spawnExampleZombie) continue;
            
            //no override branch here - any two files targeting the same mob merge
            for (ResourceLocation mobId : file.mobs()) {
                grouped.computeIfAbsent(mobId, k -> new ArrayList<>()).add(file);
            }
        }
        
        Map<ResourceLocation, MobEquipmentEntry> parsed = new HashMap<>();
        
        for (Map.Entry<ResourceLocation, List<ParsedFile>> mobEntry : grouped.entrySet()) {
            ResourceLocation mobId = mobEntry.getKey();
            
            float mobChance = -1.0F;
            Map<List<DifficultyLevel>, DifficultyGroup> merged = new LinkedHashMap<>();
            
            for (ParsedFile file : mobEntry.getValue()) {
                //each file's own declared chance, independent of the merged mob-level value below
                if (mobChance < 0.0F && file.chance() >= 0.0F) mobChance = file.chance();
                
                for (DifficultyGroup g : file.groups()) {
                    
                    //key = matchers as a set, so ["hard","hardcore"] and ["hardcore","hard"]
                    //merge into the same group instead of staying separate
                    List<DifficultyLevel> key = g.matchers.stream().sorted().collect(Collectors.toList());
                    
                    DifficultyGroup existing = merged.get(key);
                    
                    if (existing == null) {
                        merged.put(key, g);
                    } else {
                        //similar to top level chance, the first to define the chance for given difficulty group wins
                        Float mergedChance = existing.chance != null ? existing.chance : g.chance;
                        
                        List<BiomeGroup> mergedBiomes = new ArrayList<>(existing.biomeGroups);
                        mergedBiomes.addAll(g.biomeGroups);
                        
                        List<EquipmentSet> mergedSets = new ArrayList<>(existing.globalSets);
                        mergedSets.addAll(g.globalSets);
                        
                        merged.put(key, new DifficultyGroup(existing.matchers, mergedChance, mergedBiomes, mergedSets));
                    }
                }
            }
            
            //the top level chance is determined by the first in order file defining a non-negative value
            //-1 is used for skipping files from defining a top level chance. if no file defines any chance for a given mob,
            //0 is used (mob disabled). however, even with it set to 0 mobs can still spawn with sets if their respective
            //biome/difficulty groups define their own non-zero chances
            if (mobChance < 0.0F) mobChance = 0.0F;
            parsed.put(mobId, new MobEquipmentEntry(null, mobId, mobChance, new ArrayList<>(merged.values())));
        }
        
        ENTRIES = Collections.unmodifiableMap(parsed);
        LOOKUP_FILES = Collections.unmodifiableList(lookupFiles);
        
        MobArmory.LOGGER.info("Loaded {} mob equipment entries", ENTRIES.size());
    }
    
    //null = skipped (no valid mob, or failed to parse; already logged)
    private static ParsedFile parseFile(ResourceLocation fileId, JsonElement element) {
        List<ResourceLocation> mobs = Collections.emptyList();
        if (element.isJsonObject()) {
            try {
                mobs = parseMobIds(element.getAsJsonObject());
            } catch (Exception ignored) {
                //handled below like a missing field
            }
        }
        if (mobs.isEmpty()) {
            MobArmory.LOGGER.debug("Skipping mob_equipment file {} - no valid 'mob' field", fileId);
            return null;
        }
        
        JsonObject json = element.getAsJsonObject();
        try {
            float chance = JsonUtils.getFloat(json, "chance", -1.0F);
            
            List<DifficultyGroup> groups;
            if (json.has("difficulties")) {
                groups = new ArrayList<>();
                for (JsonElement diffEl : json.getAsJsonArray("difficulties")) {
                    groups.add(parseDifficultyGroup(diffEl.getAsJsonObject(), fileId));
                }
            } else {
                GroupBody body = parseGroupBody(json, fileId);
                groups = Collections.singletonList(new DifficultyGroup(
                        Collections.singletonList(DifficultyLevel.GLOBAL), null, body.biomeGroups(), body.globalSets()));
            }
            return new ParsedFile(fileId, mobs, chance, groups);
        } catch (Exception e) {
            MobArmory.LOGGER.error("Failed to parse mob_equipment entry {}", fileId, e);
            return null;
        }
    }
    
    //"mob" is either one id string (the original format, still what single-mob files are written as) or an array of ids.
//duplicates are dropped so a file can't merge into the same mob twice
    private static List<ResourceLocation> parseMobIds(JsonObject json) {
        JsonElement el = json.get("mob");
        if (el == null) return new ArrayList<>();
        
        Set<ResourceLocation> out = new LinkedHashSet<>();
        if (el.isJsonArray()) {
            for (JsonElement e : el.getAsJsonArray()) out.add(new ResourceLocation(e.getAsString()));
        } else {
            out.add(new ResourceLocation(el.getAsString()));
        }
        return new ArrayList<>(out);
    }
    
    private static class ParsedFile {
        private final ResourceLocation fileId;
        private final List<ResourceLocation> mobs;
        private final float chance;
        private final List<DifficultyGroup> groups;
        
        ParsedFile(ResourceLocation fileId, List<ResourceLocation> mobs, float chance, List<DifficultyGroup> groups) {
            this.fileId = fileId;
            this.mobs = mobs;
            this.chance = chance;
            this.groups = groups;
        }
        
        ResourceLocation fileId() { return fileId; }
        List<ResourceLocation> mobs() { return mobs; }
        float chance() { return chance; }
        List<DifficultyGroup> groups() { return groups; }
    }
    
    //editor round-trip entry point: builds a single MobEquipmentEntry straight from one JSON blob
    //(same schema as the datapack files), no multi-file merging - used when the client hands back
    //an edited entry, or when the server hands one to the client to open in the editor.
    public static MobEquipmentEntry fromJson(String fileName, JsonObject json) {
        List<ResourceLocation> mobs = parseMobIds(json);
        float chance = JsonUtils.getFloat(json, "chance", -1.0F);
        ResourceLocation logKey = new ResourceLocation(MobArmory.MODID, "editor-transfer");
        
        List<DifficultyGroup> groups = new ArrayList<>();
        if (json.has("difficulties")) {
            for (JsonElement diffEl : json.getAsJsonArray("difficulties")) {
                groups.add(parseDifficultyGroup(diffEl.getAsJsonObject(), logKey));
            }
        }
        
        return MobEquipmentEntry.of(fileName, mobs, chance, groups);
    }
    
    //reverse of fromJson: same schema, entry -> JsonObject. fileName is deliberately excluded -
    //it's packet/transport metadata, not part of the file's own content.
    public static JsonObject toJson(MobEquipmentEntry entry) {
        JsonObject root = new JsonObject();
        //one mob stays a plain string so old stuff works
        if (entry.mobs.size() == 1) {
            root.addProperty("mob", entry.mobs.get(0).toString());
        } else if (entry.mobs.size() > 1) {
            JsonArray mobArr = new JsonArray();
            for (ResourceLocation m : entry.mobs) mobArr.add(new JsonPrimitive(m.toString()));
            root.add("mob", mobArr);
        }
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
        
        if (!group.dimensions.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (DimensionMatch d : group.dimensions) arr.add(new JsonPrimitive(dimensionMatchToString(d)));
            obj.add("dimensions", arr);
        }
        
        if (!group.spawnTypes.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (SpawnTypeMatch t : group.spawnTypes) arr.add(new JsonPrimitive(spawnTypeMatchToString(t)));
            obj.add("spawn_types", arr);
        }
        
        if (!group.structures.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (String name : group.structures) arr.add(new JsonPrimitive(name));
            obj.add("structures", arr);
        }
        
        if (!group.replacements.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (MobReplacement r : group.replacements) {
                JsonObject o = new JsonObject();
                o.addProperty("mob", r.mob().toString());
                o.addProperty("weight", r.weight());
                arr.add(o);
            }
            obj.add("replacements", arr);
        }
        
        JsonArray setArr = new JsonArray();
        for (EquipmentSet s : group.sets) setArr.add(toJson(s));
        obj.add("sets", setArr);
        
        return obj;
    }
    
    public static String biomeMatchToString(BiomeMatch match) {
        if (match instanceof BiomeMatch.Global) return "global";
        if (match instanceof BiomeMatch.Tag) return ((BiomeMatch.Tag) match).tag();
        if (match instanceof BiomeMatch.Id) return ((BiomeMatch.Id) match).id().toString();
        throw new IllegalStateException("Unknown BiomeMatch: " + match);
    }
    
    public static BiomeMatch parseBiomeMatch(String raw) {
        if (raw.equalsIgnoreCase("global")) return new BiomeMatch.Global();
        else if (raw.indexOf(':') >= 0) return new BiomeMatch.Id(new ResourceLocation(raw));
        else return new BiomeMatch.Tag(raw);
    }
    
    private static JsonObject toJson(EquipmentSet set) {
        JsonObject obj = new JsonObject();
        if (set.name != null) obj.addProperty("name", set.name);
        obj.addProperty("weight", set.weight);
        
        for (Map.Entry<EntityEquipmentSlot, List<WeightedItem>> slotEntry : set.slots.entrySet()) {
            String slotKey = SLOT_KEYS.entrySet().stream()
                    .filter(e -> e.getValue() == slotEntry.getKey())
                    .map(Map.Entry::getKey)
                    .findFirst()
                    .orElseThrow(() -> new IllegalStateException("No key for slot " + slotEntry.getKey()));
            
            JsonArray arr = new JsonArray();
            for (WeightedItem item : slotEntry.getValue()) arr.add(toJson(item));
            obj.add(slotKey, arr);
        }
        
        if (set.mobNbt != null) obj.addProperty("mob_nbt", set.mobNbt);
        if (set.lootTable != null) obj.addProperty("loot_table", set.lootTable);
        
        if (!set.potionEffects.isEmpty()) {
            JsonArray arr = new JsonArray();
            for (PotionEffectEntry pe : set.potionEffects) {
                JsonObject o = new JsonObject();
                o.addProperty("effect", pe.effectId);
                o.addProperty("duration", pe.durationTicks);
                o.addProperty("amplifier", pe.amplifier);
                arr.add(o);
            }
            obj.add("potion_effects", arr);
        }
        
        boolean timeUnrestricted = set.timeOfDay.minTicks == 0 && set.timeOfDay.maxTicks == 24000;
        if (!timeUnrestricted) {
            JsonObject t = new JsonObject();
            t.addProperty("min", set.timeOfDay.minTicks);
            t.addProperty("max", set.timeOfDay.maxTicks);
            obj.add("time_of_day", t);
        }
        
        boolean yUnrestricted = set.yLevel.comparator == YComparator.LT && set.yLevel.value == 350;
        if (!yUnrestricted) {
            JsonObject y = new JsonObject();
            y.addProperty("comparator", set.yLevel.comparator.symbol);
            y.addProperty("value", set.yLevel.value);
            obj.add("y_level", y);
        }
        
        return obj;
    }
    
    private static JsonObject toJson(WeightedItem item) {
        JsonObject obj = new JsonObject();
        obj.addProperty("item", item.itemId);
        obj.addProperty("weight", item.weight);
        if (item.nbt != null) obj.addProperty("nbt", item.nbt);
        if (item.enchant != null) obj.add("enchant", toJson(item.enchant));
        return obj;
    }
    
    private static JsonObject toJson(EnchantData enchant) {
        JsonObject obj = new JsonObject();
        
        if (enchant instanceof EnchantData.Random) {
            EnchantData.Random r = (EnchantData.Random) enchant;
            obj.addProperty("type", "random");
            obj.addProperty("power", r.power());
        } else if (enchant instanceof EnchantData.Predefined) {
            EnchantData.Predefined p = (EnchantData.Predefined) enchant;
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
            
            //strings with colon (minecraft:plains) go to single biomes, without are treated as "tags"
            if (raw.equalsIgnoreCase("global")) matchers.add(new BiomeMatch.Global());
            else if (raw.indexOf(':') >= 0) matchers.add(new BiomeMatch.Id(new ResourceLocation(raw)));
            else matchers.add(new BiomeMatch.Tag(raw));
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
        
        //optional extra matchers, all empty = no restriction
        List<DimensionMatch> dimensions = new ArrayList<>();
        if (biomeObj.has("dimensions")) {
            for (JsonElement el : biomeObj.getAsJsonArray("dimensions")) dimensions.add(parseDimensionMatch(el.getAsString()));
        }
        
        List<SpawnTypeMatch> spawnTypes = new ArrayList<>();
        if (biomeObj.has("spawn_types")) {
            for (JsonElement el : biomeObj.getAsJsonArray("spawn_types")) spawnTypes.add(parseSpawnTypeMatch(el.getAsString()));
        }
        
        List<String> structures = new ArrayList<>();
        if (biomeObj.has("structures")) {
            for (JsonElement el : biomeObj.getAsJsonArray("structures")) structures.add(el.getAsString().trim());
        }
        
        List<MobReplacement> replacements = new ArrayList<>();
        if (biomeObj.has("replacements")) {
            for (JsonElement el : biomeObj.getAsJsonArray("replacements")) {
                JsonObject o = el.getAsJsonObject();
                replacements.add(new MobReplacement(
                        new ResourceLocation(JsonUtils.getString(o, "mob")),
                        Math.max(1, JsonUtils.getInt(o, "weight", 1))));
            }
        }
        
        return new BiomeGroup(matchers, groupChance, sets, dimensions, spawnTypes, structures, replacements);
    }
    
    private static EquipmentSet parseSet(JsonObject json, ResourceLocation sourceKey) {
        String name = json.has("name") ? json.get("name").getAsString() : null;
        int weight = JsonUtils.getInt(json, "weight", 1);
        Map<EntityEquipmentSlot, List<WeightedItem>> slots = new EnumMap<>(EntityEquipmentSlot.class);
        
        for (Map.Entry<String, EntityEquipmentSlot> slotKey : SLOT_KEYS.entrySet()) {
            if (!json.has(slotKey.getKey())) continue;
            
            JsonArray arr = JsonUtils.getJsonArray(json, slotKey.getKey());
            List<WeightedItem> items = new ArrayList<>();
            
            for (JsonElement el : arr) {
                JsonObject obj = el.getAsJsonObject();
                int itemWeight = JsonUtils.getInt(obj, "weight", 1);
                String itemNbt = obj.has("nbt") ? obj.get("nbt").getAsString() : null;
                
                EnchantData enchant = null;
                
                if (obj.has("enchant")) {
                    JsonObject ench = obj.getAsJsonObject("enchant");
                    String type = JsonUtils.getString(ench, "type");
                    
                    if (type.equals("random")) {
                        enchant = new EnchantData.Random(JsonUtils.getInt(ench, "power", 30));
                    } else if (type.equals("predefined")) {
                        List<String> ids = new ArrayList<>();
                        List<Integer> levels = new ArrayList<>();
                        
                        for (JsonElement enchEl : ench.getAsJsonArray("list")) {
                            JsonObject enchObj = enchEl.getAsJsonObject();
                            ids.add(JsonUtils.getString(enchObj, "id"));
                            levels.add(JsonUtils.getInt(enchObj, "level"));
                        }
                        
                        enchant = new EnchantData.Predefined(ids, levels);
                    }
                }
                
                items.add(new WeightedItem(JsonUtils.getString(obj, "item"), itemWeight, enchant, itemNbt));
            }
            
            if (!items.isEmpty()) slots.put(slotKey.getValue(), items);
        }
        
        EquipmentSet set = new EquipmentSet(name, weight, slots);
        
        set.mobNbt = json.has("mob_nbt") ? json.get("mob_nbt").getAsString() : null;
        set.lootTable = json.has("loot_table") ? json.get("loot_table").getAsString() : null;
        
        if (json.has("potion_effects")) {
            for (JsonElement el : json.getAsJsonArray("potion_effects")) {
                JsonObject o = el.getAsJsonObject();
                set.potionEffects.add(new PotionEffectEntry(
                        JsonUtils.getString(o, "effect"),
                        JsonUtils.getInt(o, "duration", 600),
                        JsonUtils.getInt(o, "amplifier", 0)
                ));
            }
        }
        
        long timeMin = 0, timeMax = 24000;
        if (json.has("time_of_day")) {
            JsonObject t = json.getAsJsonObject("time_of_day");
            timeMin = t.has("min") ? t.get("min").getAsLong() : 0;
            timeMax = t.has("max") ? t.get("max").getAsLong() : 24000;
        }
        set.timeOfDay = new TimeRange(timeMin, timeMax);
        
        YComparator comparator = YComparator.LT;
        int yValue = 350;
        if (json.has("y_level")) {
            JsonObject y = json.getAsJsonObject("y_level");
            comparator = YComparator.fromSymbol(JsonUtils.getString(y, "comparator", "<"));
            yValue = JsonUtils.getInt(y, "value", 350);
        }
        set.yLevel = new YLevelCondition(comparator, yValue);
        
        return set;
    }
    
    public static class MobEquipmentEntry {
        //null for merged entries in ENTRIES, set to the source file's name (e.g. "zombie_snowy")
        //for the unmerged per-file entries in LOOKUP_FILES
        public String fileName;
        //first of mobs (or null when empty); what the preview and everything single-mob reads. change via setMobs, not directly
        public ResourceLocation mob;
        //every mob this entry applies to: exactly one for merged ENTRIES entries, one or more for file entries
        public List<ResourceLocation> mobs;
        public float chance;
        public List<DifficultyGroup> difficultyGroups;
        
        public MobEquipmentEntry(String fileName, ResourceLocation mob, float chance, List<DifficultyGroup> difficultyGroups) {
            this.fileName = fileName;
            this.chance = chance;
            this.difficultyGroups = difficultyGroups;
            setMobs(mob == null ? Collections.<ResourceLocation>emptyList() : Collections.singletonList(mob));
        }
        
        //factory instead of an overload, so existing new MobEquipmentEntry(x, null, ...) calls don't become ambiguous
        public static MobEquipmentEntry of(String fileName, List<ResourceLocation> mobs, float chance, List<DifficultyGroup> difficultyGroups) {
            MobEquipmentEntry e = new MobEquipmentEntry(fileName, (ResourceLocation) null, chance, difficultyGroups);
            e.setMobs(mobs);
            return e;
        }
        
        public void setMobs(List<ResourceLocation> newMobs) {
            this.mobs = new ArrayList<>(newMobs);
            this.mob = newMobs.isEmpty() ? null : newMobs.get(0);
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
        public List<DimensionMatch> dimensions;   //empty = any dimension
        public List<SpawnTypeMatch> spawnTypes;   //empty = any spawn type
        public List<String> structures;           //empty = anywhere. raw names, no ids here
        public List<MobReplacement> replacements; //empty = keep the spawned mob
        
        public BiomeGroup(List<BiomeMatch> matchers, Float chance, List<EquipmentSet> sets) {
            this(matchers, chance, sets, new ArrayList<>(), new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        
        public BiomeGroup(List<BiomeMatch> matchers, Float chance, List<EquipmentSet> sets, List<DimensionMatch> dimensions) {
            this(matchers, chance, sets, dimensions, new ArrayList<>(), new ArrayList<>(), new ArrayList<>());
        }
        
        public BiomeGroup(List<BiomeMatch> matchers, Float chance, List<EquipmentSet> sets, List<DimensionMatch> dimensions, List<SpawnTypeMatch> spawnTypes) {
            this(matchers, chance, sets, dimensions, spawnTypes, new ArrayList<>(), new ArrayList<>());
        }
        
        public BiomeGroup(List<BiomeMatch> matchers, Float chance, List<EquipmentSet> sets, List<DimensionMatch> dimensions, List<SpawnTypeMatch> spawnTypes, List<String> structures, List<MobReplacement> replacements) {
            this.matchers = matchers;
            this.chance = chance;
            this.sets = sets;
            this.dimensions = dimensions;
            this.spawnTypes = spawnTypes;
            this.structures = structures;
            this.replacements = replacements;
        }
    }
    
    //dimensions are plain ints in 1.12.2: 0 overworld, -1 nether, 1 end, anything else is a modded dimension.
    //json accepts "global", an int ("-1" or -1), or the three vanilla ids so files stay portable with 1.20.1
    public interface DimensionMatch {
        final class Id implements DimensionMatch {
            private final int dimension;
            
            public Id(int dimension) { this.dimension = dimension; }
            public int dimension() { return dimension; }
            
            @Override public boolean equals(Object o) { return o instanceof Id && dimension == ((Id) o).dimension; }
            @Override public int hashCode() { return dimension; }
        }
        
        final class Global implements DimensionMatch {
            @Override public boolean equals(Object o) { return o instanceof Global; }
            @Override public int hashCode() { return 2; }
        }
    }
    
    //"natural", "!spawner" etc. names come from SpawnTypes.KNOWN (1.12.2 has no spawn type enum).
    //a leading "!" excludes. realistically this will only work for spawner vs non-spawner types and
    //more specific spawns have to be handled with code integration
    public static class SpawnTypeMatch {
        private final String type;
        private final boolean negated;
        
        public SpawnTypeMatch(String type, boolean negated) {
            this.type = type;
            this.negated = negated;
        }
        
        public String type() { return type; }
        public boolean negated() { return negated; }
        
        @Override public boolean equals(Object o) {
            return o instanceof SpawnTypeMatch && type.equals(((SpawnTypeMatch) o).type) && negated == ((SpawnTypeMatch) o).negated;
        }
        @Override public int hashCode() { return type.hashCode() * 31 + (negated ? 1 : 0); }
    }
    
    //one weighted option of "replace the spawned mob with..."
    public static class MobReplacement {
        private final ResourceLocation mob;
        private final int weight;
        
        public MobReplacement(ResourceLocation mob, int weight) {
            this.mob = mob;
            this.weight = weight;
        }
        
        public ResourceLocation mob() { return mob; }
        public int weight() { return weight; }
        
        @Override public boolean equals(Object o) {
            return o instanceof MobReplacement && mob.equals(((MobReplacement) o).mob) && weight == ((MobReplacement) o).weight;
        }
        @Override public int hashCode() { return mob.hashCode() * 31 + weight; }
    }
    
    public static String dimensionMatchToString(DimensionMatch match) {
        if (match instanceof DimensionMatch.Global) return "global";
        
        int dim = ((DimensionMatch.Id) match).dimension();
        if (dim == 0) return "minecraft:overworld";
        if (dim == -1) return "minecraft:the_nether";
        if (dim == 1) return "minecraft:the_end";
        return String.valueOf(dim);
    }
    
    public static DimensionMatch parseDimensionMatch(String raw) {
        String s = raw.trim();
        if (s.equalsIgnoreCase("global")) return new DimensionMatch.Global();
        if (s.equalsIgnoreCase("minecraft:overworld")) return new DimensionMatch.Id(0);
        if (s.equalsIgnoreCase("minecraft:the_nether")) return new DimensionMatch.Id(-1);
        if (s.equalsIgnoreCase("minecraft:the_end")) return new DimensionMatch.Id(1);
        
        try {
            return new DimensionMatch.Id(Integer.parseInt(s));
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid dimension '" + raw + "' (use a dimension number, e.g. -1)");
        }
    }
    
    public static String spawnTypeMatchToString(SpawnTypeMatch match) {
        return (match.negated() ? "!" : "") + match.type();
    }
    
    public static SpawnTypeMatch parseSpawnTypeMatch(String raw) {
        String s = raw.trim();
        boolean negated = s.startsWith("!");
        String name = (negated ? s.substring(1) : s).trim().toLowerCase(Locale.ROOT);
        
        if (!SpawnTypes.isKnown(name)) throw new IllegalArgumentException("Unknown spawn type '" + raw + "'");
        return new SpawnTypeMatch(name, negated);
    }
    
    public interface BiomeMatch {
        final class Tag implements BiomeMatch {
            private final String tag;
            
            public Tag(String  tag) { this.tag = tag; }
            public String tag() { return tag; }
            
            @Override public boolean equals(Object o) { return o instanceof Tag && tag.equals(((Tag) o).tag); }
            @Override public int hashCode() { return tag.hashCode(); }
        }
        
        final class Id implements BiomeMatch {
            private final ResourceLocation id;
            
            public Id(ResourceLocation id) { this.id = id; }
            public ResourceLocation id() { return id; }
            
            @Override public boolean equals(Object o) { return o instanceof Id && id.equals(((Id) o).id); }
            @Override public int hashCode() { return id.hashCode(); }
        }
        
        final class Global implements BiomeMatch {
            @Override public boolean equals(Object o) { return o instanceof Global; }
            @Override public int hashCode() { return 1; }
        }
    }
    
    public static class WeightedItem {
        public String itemId;
        public Item item;
        public int weight;
        public EnchantData enchant;
        public String nbt; // optional raw SNBT (braces optional), merged onto the ItemStack at spawn
        
        public WeightedItem(String itemId, int weight, EnchantData enchant) {
            this(itemId, weight, enchant, null);
        }
        
        public WeightedItem(String itemId, int weight, EnchantData enchant, String nbt) {
            this.itemId = itemId;
            this.weight = weight;
            this.enchant = enchant;
            this.nbt = nbt;
            this.item = null;
        }
        
        public void resolve() {
            this.item = ForgeRegistries.ITEMS.getValue(new ResourceLocation(itemId));
        }
    }
    
    public interface EnchantData {
        final class Random implements EnchantData {
            private final int power;
            
            public Random(int power) { this.power = power; }
            public int power() { return power; }
        }
        
        final class Predefined implements EnchantData {
            private final List<String> ids;
            private final List<Integer> levels;
            
            public Predefined(List<String> ids, List<Integer> levels) {
                this.ids = ids;
                this.levels = levels;
            }
            
            public List<String> ids() { return ids; }
            public List<Integer> levels() { return levels; }
        }
    }
    
    public static class EquipmentSet {
        public String name;
        public int weight;
        public String lootTable;
        public Map<EntityEquipmentSlot, List<WeightedItem>> slots;
        
        public String mobNbt;
        public List<PotionEffectEntry> potionEffects = new ArrayList<>();
        
        public TimeRange timeOfDay = new TimeRange(0, 24000);
        public YLevelCondition yLevel = new YLevelCondition(YComparator.LT, 350);
        
        public EquipmentSet(String name, int weight, Map<EntityEquipmentSlot, List<WeightedItem>> slots) {
            this.name = name;
            this.weight = weight;
            this.slots = slots;
        }
    }
    
    public static class PotionEffectEntry {
        public String effectId;
        public int durationTicks;
        public int amplifier;
        
        public PotionEffectEntry(String effectId, int durationTicks, int amplifier) {
            this.effectId = effectId;
            this.durationTicks = durationTicks;
            this.amplifier = amplifier;
        }
    }
    
    public static class TimeRange {
        public long minTicks; // 0-24000
        public long maxTicks;
        
        public TimeRange(long minTicks, long maxTicks) {
            this.minTicks = minTicks;
            this.maxTicks = maxTicks;
        }
        
        //min > max means the range wraps past midnight (e.g. 22000 -> 2000 covers deep night into dawn)
        public boolean matches(long currentTicks) {
            long t = currentTicks % 24000;
            if (minTicks <= maxTicks) return t >= minTicks && t <= maxTicks;
            else return t >= minTicks || t <= maxTicks;
        }
    }
    
    public enum YComparator {
        LT("<"), LTE("<="), EQ("="), GTE(">="), GT(">");
        
        public final String symbol;
        YComparator(String symbol) { this.symbol = symbol; }
        
        public static YComparator fromSymbol(String raw) {
            for (YComparator c : values()) if (c.symbol.equals(raw)) return c;
            return EQ;
        }
    }
    
    public static class YLevelCondition {
        public YComparator comparator;
        public int value;
        
        public YLevelCondition(YComparator comparator, int value) {
            this.comparator = comparator;
            this.value = value;
        }
        
        public boolean matches(int y) {
            switch (comparator) {
                case LT: return y < value;
                case LTE: return y <= value;
                case EQ: return y == value;
                case GTE: return y >= value;
                case GT: return y > value;
                default: return false;
            }
        }
    }
    
    public static String ticksToTimeString(long ticks) {
        long t = ((ticks % 24000) + 24000) % 24000;
        int hour = (int) (((t / 1000) + 6) % 24);
        int minute = (int) ((t % 1000) * 60 / 1000);
        return String.format("%02d:%02d", hour, minute);
    }
    
    public static long timeStringToTicks(String raw) {
        String[] parts = raw.trim().split(":");
        int hour = Integer.parseInt(parts[0].trim());
        int minute = parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 0;
        if (hour < 0 || hour > 23 || minute < 0 || minute > 59) throw new IllegalArgumentException("Invalid time");
        return ((hour - 6 + 24) % 24) * 1000L + Math.round(minute * 1000L / 60.0);
    }
    
    public static YLevelCondition parseYLevel(String raw) {
        String s = raw.trim();
        YComparator comp;
        String numPart;
        
        if (s.startsWith("<=")) { comp = YComparator.LTE; numPart = s.substring(2); }
        else if (s.startsWith(">=")) { comp = YComparator.GTE; numPart = s.substring(2); }
        else if (s.startsWith("<")) { comp = YComparator.LT; numPart = s.substring(1); }
        else if (s.startsWith(">")) { comp = YComparator.GT; numPart = s.substring(1); }
        else if (s.startsWith("=")) { comp = YComparator.EQ; numPart = s.substring(1); }
        else { comp = YComparator.EQ; numPart = s; }
        
        return new YLevelCondition(comp, Integer.parseInt(numPart.trim()));
    }
    
    public static String yLevelToString(YLevelCondition c) {
        return c.comparator.symbol + c.value;
    }
    
    public static boolean isYLevelUnrestricted(YLevelCondition y) {
        return y.comparator == YComparator.LT && y.value == 350;
    }
    
    public static TimeRange parseTimeRange(String raw) {
        String[] parts = raw.split("-");
        if (parts.length != 2) throw new IllegalArgumentException("Expected HH:MM-HH:MM");
        return new TimeRange(timeStringToTicks(parts[0].trim()), timeStringToTicks(parts[1].trim()));
    }
    
    public static String timeRangeToString(TimeRange t) {
        return ticksToTimeString(t.minTicks) + "-" + ticksToTimeString(t.maxTicks);
    }
    
    public static boolean isTimeUnrestricted(TimeRange t) {
        return t.minTicks == 0 && t.maxTicks == 24000;
    }
    
    private static class GroupBody {
        private final List<BiomeGroup> biomeGroups;
        private final List<EquipmentSet> globalSets;
        
        GroupBody(List<BiomeGroup> biomeGroups, List<EquipmentSet> globalSets) {
            this.biomeGroups = biomeGroups;
            this.globalSets = globalSets;
        }
        
        List<BiomeGroup> biomeGroups() { return biomeGroups; }
        List<EquipmentSet> globalSets() { return globalSets; }
    }
}
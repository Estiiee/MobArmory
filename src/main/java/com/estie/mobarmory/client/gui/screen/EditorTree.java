package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.screen.EditorNode.Action;
import com.estie.mobarmory.client.gui.screen.EditorNode.Field;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.data.MobEquipmentReloadListener.BiomeGroup;
import com.estie.mobarmory.data.MobEquipmentReloadListener.BiomeMatch;
import com.estie.mobarmory.data.MobEquipmentReloadListener.DifficultyGroup;
import com.estie.mobarmory.data.MobEquipmentReloadListener.DifficultyLevel;
import com.estie.mobarmory.data.MobEquipmentReloadListener.DimensionMatch;
import com.estie.mobarmory.data.MobEquipmentReloadListener.EnchantData;
import com.estie.mobarmory.data.MobEquipmentReloadListener.EquipmentSet;
import com.estie.mobarmory.data.MobEquipmentReloadListener.MobEquipmentEntry;
import com.estie.mobarmory.data.MobEquipmentReloadListener.MobReplacement;
import com.estie.mobarmory.data.MobEquipmentReloadListener.PotionEffectEntry;
import com.estie.mobarmory.data.MobEquipmentReloadListener.SpawnTypeMatch;
import com.estie.mobarmory.data.MobEquipmentReloadListener.TimeRange;
import com.estie.mobarmory.data.MobEquipmentReloadListener.WeightedItem;
import com.estie.mobarmory.data.MobEquipmentReloadListener.YComparator;
import com.estie.mobarmory.data.MobEquipmentReloadListener.YLevelCondition;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.MobSpawnType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Turns a MobEquipmentEntry into a EditorNode tree:
 * <p>
 *   mob
 *   +- difficulty group        (levels, chance)
 *      +- biome group          (biomes, dimensions, chance)
 *         +- equipment set     (name, weight, loot table, mob nbt, time, y level)
 *            +- potion effects -> effect (id, duration, amplifier)
 *            +- head/chest/legs/feet/mainhand/offhand -> item (id, weight, nbt, enchant)
 * <p>
 * All edits mutate the entry directly. No rendering in here.
 */
public final class EditorTree {
    
    private static final EquipmentSlot[] SLOTS = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS,
            EquipmentSlot.FEET, EquipmentSlot.MAINHAND, EquipmentSlot.OFFHAND
    };
    
    /** What the model needs from the screen. */
    public interface Hooks {
        /** Ask for a single line of text, then call onConfirm (used by the "+ Add" rows). */
        void prompt(String title, String initial, Predicate<String> validator, String warning, Consumer<String> onConfirm);
        
        /** Structure changed (add/delete/cycle): rebuild and select the node with this key (null keeps selection). */
        void changed(Object selectKey);
        
        /** The mob id changed: rebuild the preview entity. */
        void mobChanged();
    }
    
    private record FolderKey(Object owner, String name) {}
    
    private record SlotKey(EquipmentSet set, EquipmentSlot slot) {}
    
    private EditorTree() {}
    
    //normalization
    
    /**
     * Makes everything the editor touches mutable and folds "global sets" (sets directly on a
     * difficulty group, no biome restriction) into a biome group matching "global", so the tree
     * only needs one shape. Call once when an entry is opened.
     */
    public static void normalize(MobEquipmentEntry entry) {
        entry.difficultyGroups = new ArrayList<>(entry.difficultyGroups);
        
        for (DifficultyGroup g : entry.difficultyGroups) {
            g.matchers = new ArrayList<>(g.matchers);
            g.biomeGroups = new ArrayList<>(g.biomeGroups);
            
            if (g.globalSets != null && !g.globalSets.isEmpty()) {
                List<BiomeMatch> any = new ArrayList<>();
                any.add(new BiomeMatch.Global());
                g.biomeGroups.add(new BiomeGroup(any, null, new ArrayList<>(g.globalSets)));
            }
            g.globalSets = new ArrayList<>();
            
            for (BiomeGroup bg : g.biomeGroups) {
                bg.matchers = new ArrayList<>(bg.matchers);
                bg.sets = new ArrayList<>(bg.sets);
                bg.dimensions = new ArrayList<>(bg.dimensions);
                bg.spawnTypes = new ArrayList<>(bg.spawnTypes);
                bg.structures = new ArrayList<>(bg.structures);
                bg.replacements = new ArrayList<>(bg.replacements);
                
                for (EquipmentSet s : bg.sets) {
                    s.potionEffects = new ArrayList<>(s.potionEffects);
                    Map<EquipmentSlot, List<WeightedItem>> copy = new EnumMap<>(EquipmentSlot.class);
                    for (Map.Entry<EquipmentSlot, List<WeightedItem>> e : s.slots.entrySet()) {
                        copy.put(e.getKey(), new ArrayList<>(e.getValue()));
                    }
                    s.slots = copy;
                }
            }
        }
    }
    
    //tree
    
    public static EditorNode build(MobEquipmentEntry entry, Hooks h) {
        EditorNode root = new EditorNode(null, entry,
                () -> "Mob: " + (entry.mob != null ? entry.mob.toString() : "(none)"),
                () -> entry.chance >= 0 ? pct(entry.chance) : "");
        
        root.fields.add(Field.text("Mob ID",
                        () -> entry.mob != null ? entry.mob.toString() : "",
                        v -> {
                            ResourceLocation id = ResourceLocation.tryParse(normalizeId(v.trim()));
                            if (v.isBlank() || id == null) return "Invalid id";
                            entry.mob = id;
                            h.mobChanged();
                            return null;
                        })
                .warn(v -> EditScreenShared.mobExists(normalizeId(v.trim())) ? null : "Warning: entity type not found"));
        
        root.fields.add(Field.text("Chance",
                        () -> String.valueOf(entry.chance),
                        v -> {
                            Float f = parseFloat(v);
                            if (f == null) return "Not a number";
                            entry.chance = Mth.clamp(f, -1.0F, 1.0F);
                            return null;
                        })
                .hint("-1 = not set"));
        
        for (DifficultyGroup g : entry.difficultyGroups) {
            root.children.add(difficultyNode(root, entry, g, h));
        }
        root.children.add(addRow(root, "+ Add difficulty group", () -> {
            List<DifficultyLevel> levels = new ArrayList<>();
            levels.add(DifficultyLevel.GLOBAL);
            DifficultyGroup g = new DifficultyGroup(levels, -1.0F, new ArrayList<>(), new ArrayList<>());
            entry.difficultyGroups.add(g);
            h.changed(g);
        }));
        return root;
    }
    
    private static EditorNode difficultyNode(EditorNode parent, MobEquipmentEntry entry, DifficultyGroup g, Hooks h) {
        EditorNode n = new EditorNode(parent, g,
                () -> "Difficulty: " + g.matchers.stream().map(DifficultyLevel::toString).collect(Collectors.joining(", ")),
                () -> chanceSummary(g.chance));
        
        DifficultyLevel[] levels = DifficultyLevel.values();
        List<String> levelNames = Arrays.stream(levels)
                .map(l -> Character.toUpperCase(l.toString().charAt(0)) + l.toString().substring(1))
                .collect(Collectors.toList());
        n.fields.add(Field.toggles("Levels", levelNames,
                i -> g.matchers.contains(levels[i]),
                i -> {
                    if (!g.matchers.remove(levels[i])) g.matchers.add(levels[i]);
                    if (g.matchers.isEmpty()) g.matchers.add(DifficultyLevel.GLOBAL); // never leave a group matching nothing
                    h.changed(g); //recolors the buttons
                }));
        
        n.fields.add(Field.text("Chance",
                () -> g.chance == null ? "" : String.valueOf(g.chance),
                optionalChance(c -> g.chance = c)).hint("inherit"));
        
        n.actions.add(new Action("Delete difficulty group",
                "Delete this difficulty group and everything in it?",
                () -> {
                    entry.difficultyGroups.remove(g);
                    h.changed(parent.key);
                }));
        
        for (BiomeGroup bg : g.biomeGroups) {
            n.children.add(biomeNode(n, g, bg, h));
        }
        n.children.add(addRow(n, "+ Add biome group", () -> {
            List<BiomeMatch> any = new ArrayList<>();
            any.add(new BiomeMatch.Global());
            BiomeGroup bg = new BiomeGroup(any, -1.0F, new ArrayList<>());
            g.biomeGroups.add(bg);
            h.changed(bg);
        }));
        return n;
    }
    
    private static EditorNode biomeNode(EditorNode parent, DifficultyGroup g, BiomeGroup bg, Hooks h) {
        EditorNode n = new EditorNode(parent, bg,
                () -> "Biomes: " + bg.matchers.stream().map(MobEquipmentReloadListener::biomeMatchToString).collect(Collectors.joining(", ")),
                () -> biomeSummary(bg));
        
        n.fields.add(Field.multiline("Biomes", "Biomes (one per line: global, #tag or minecraft:id)",
                        () -> bg.matchers.stream().map(MobEquipmentReloadListener::biomeMatchToString).collect(Collectors.joining("\n")),
                        v -> {
                            List<BiomeMatch> parsed = new ArrayList<>();
                            for (String tok : splitList(v)) {
                                try {
                                    parsed.add(MobEquipmentReloadListener.parseBiomeMatch(tok));
                                } catch (Exception e) {
                                    return "Invalid matcher '" + tok + "'";
                                }
                            }
                            if (parsed.isEmpty()) return "At least one (use 'global' for any biome)";
                            bg.matchers.clear();
                            bg.matchers.addAll(parsed);
                            return null;
                        },
                        v -> splitList(v).stream().allMatch(EditScreenShared::biomeMatchValid), "Warning: unknown biome or tag")
                .buttonText(v -> listButton(v, "biome", "(none)")));
        
        n.fields.add(Field.multiline("Dimensions", "Dimensions (one per line: minecraft:the_nether, ...; empty = any)",
                        () -> dimensionsText(bg),
                        v -> {
                            List<DimensionMatch> parsed = new ArrayList<>();
                            for (String tok : splitList(v)) {
                                try {
                                    parsed.add(MobEquipmentReloadListener.parseDimensionMatch(tok));
                                } catch (Exception e) {
                                    return "Invalid dimension '" + tok + "'";
                                }
                            }
                            bg.dimensions.clear();
                            bg.dimensions.addAll(parsed);
                            return null;
                        },
                        v -> true, "")
                .buttonText(v -> listButton(v, "dimension", "(any)")));
        
        n.fields.add(Field.multiline("Spawn types", "Spawn types (e.g. natural, spawner, structure), ! excludes, empty = all", //(" + SPAWN_TYPE_NAMES + ")",
                        () -> bg.spawnTypes.stream().map(MobEquipmentReloadListener::spawnTypeMatchToString).collect(Collectors.joining("\n")),
                        v -> {
                            List<SpawnTypeMatch> parsed = new ArrayList<>();
                            for (String tok : splitList(v)) {
                                try {
                                    parsed.add(MobEquipmentReloadListener.parseSpawnTypeMatch(tok));
                                } catch (IllegalArgumentException e) {
                                    return "Unknown spawn type '" + tok + "'";
                                }
                            }
                            bg.spawnTypes.clear();
                            bg.spawnTypes.addAll(parsed);
                            return null;
                        },
                        v -> splitList(v).stream().allMatch(EditorTree::validSpawnType), "Warning: unknown spawn type")
                .buttonText(v -> listButton(v, "rule", "(any)")));
        
        n.fields.add(Field.multiline("Structures", "Structures (one per line, e.g. minecraft:pillager_outpost; empty = anywhere)",
                        () -> bg.structures.stream().map(ResourceLocation::toString).collect(Collectors.joining("\n")),
                        v -> {
                            List<ResourceLocation> parsed = new ArrayList<>();
                            for (String tok : splitList(v)) {
                                ResourceLocation id = ResourceLocation.tryParse(normalizeId(tok));
                                if (id == null) return "Invalid structure id '" + tok + "'";
                                parsed.add(id);
                            }
                            bg.structures.clear();
                            bg.structures.addAll(parsed);
                            return null;
                        },
                        v -> true, "")
                .buttonText(v -> listButton(v, "structure", "(anywhere)")));
        
        n.fields.add(Field.multiline("Replace with", "Replace the mob with (one per line: mob_id;weight, weight optional)",
                        () -> bg.replacements.stream().map(r -> r.mob() + ";" + r.weight()).collect(Collectors.joining("\n")),
                        v -> {
                            List<MobReplacement> parsed = new ArrayList<>();
                            for (String tok : splitList(tightenSemicolons(v))) {
                                String idPart = tok;
                                int weight = 1;
                                int semi = tok.lastIndexOf(';');
                                if (semi >= 0) {
                                    idPart = tok.substring(0, semi);
                                    Integer w = parseInt(tok.substring(semi + 1), 1);
                                    if (w == null) return "Weight must be 1 or more in '" + tok + "'";
                                    weight = w;
                                }
                                ResourceLocation id = ResourceLocation.tryParse(normalizeId(idPart));
                                if (idPart.isBlank() || id == null) return "Invalid mob id '" + idPart + "'";
                                parsed.add(new MobReplacement(id, weight));
                            }
                            bg.replacements.clear();
                            bg.replacements.addAll(parsed);
                            return null;
                        },
                        v -> splitList(tightenSemicolons(v)).stream().allMatch(tok ->
                                EditScreenShared.mobExists(normalizeId(tok.contains(";") ? tok.substring(0, tok.lastIndexOf(';')) : tok))),
                        "Warning: unknown mob id")
                .buttonText(v -> listButton(v, "option", "(keep mob)")));
        
        n.fields.add(Field.text("Chance",
                () -> bg.chance == null ? "" : String.valueOf(bg.chance),
                optionalChance(c -> bg.chance = c)).hint("inherit"));
        
        n.actions.add(new Action("Delete biome group",
                "Delete this biome group and all its sets?",
                () -> {
                    g.biomeGroups.remove(bg);
                    h.changed(parent.key);
                }));
        
        for (EquipmentSet set : bg.sets) {
            n.children.add(setNode(n, bg, set, h));
        }
        n.children.add(addRow(n, "+ Add set", () -> {
            EquipmentSet set = new EquipmentSet(null, 1, new EnumMap<>(EquipmentSlot.class));
            bg.sets.add(set);
            h.changed(set);
        }));
        return n;
    }
    
    private static EditorNode setNode(EditorNode parent, BiomeGroup bg, EquipmentSet set, Hooks h) {
        EditorNode n = new EditorNode(parent, set,
                () -> set.name != null && !set.name.isBlank() ? set.name : "Set " + (bg.sets.indexOf(set) + 1),
                () -> setSummary(set));
        
        n.fields.add(Field.text("Name",
                () -> set.name != null ? set.name : "",
                v -> {
                    set.name = v.isBlank() ? null : v;
                    return null;
                }).hint("optional"));
        
        n.fields.add(Field.text("Weight",
                () -> String.valueOf(set.weight),
                v -> {
                    Integer w = parseInt(v, 1);
                    if (w == null) return "Whole number, 1 or more";
                    set.weight = w;
                    return null;
                }));
        
        n.fields.add(Field.text("Loot table",
                () -> set.lootTable != null ? set.lootTable : "",
                v -> {
                    set.lootTable = v.isBlank() ? null : v.trim();
                    return null;
                }).hint("none"));
        
        n.fields.add(Field.multiline("Mob NBT", "Mob NBT (e.g. CustomName: '{\"text\":\"Boss\"}')",
                () -> set.mobNbt != null ? set.mobNbt : "",
                v -> {
                    set.mobNbt = v.isBlank() ? null : v;
                    return null;
                }, EditScreenShared::nbtValid, "Warning: invalid NBT syntax"));
        
        n.fields.add(Field.text("Time",
                        () -> MobEquipmentReloadListener.isTimeUnrestricted(set.timeOfDay)
                                ? "" : MobEquipmentReloadListener.timeRangeToString(set.timeOfDay),
                        v -> {
                            try {
                                set.timeOfDay = v.isBlank()
                                        ? new TimeRange(0, 24000)
                                        : MobEquipmentReloadListener.parseTimeRange(v);
                                return null;
                            } catch (Exception e) {
                                return "Use HH:MM-HH:MM";
                            }
                        })
                .hint("always (e.g. 18:00-06:00)"));
        
        n.fields.add(Field.text("Y level",
                        () -> MobEquipmentReloadListener.isYLevelUnrestricted(set.yLevel)
                                ? "" : MobEquipmentReloadListener.yLevelToString(set.yLevel),
                        v -> {
                            try {
                                set.yLevel = v.isBlank()
                                        ? new YLevelCondition(YComparator.LT, 350)
                                        : MobEquipmentReloadListener.parseYLevel(v);
                                return null;
                            } catch (Exception e) {
                                return "Use e.g. <64, >=0, 40";
                            }
                        })
                .hint("always (e.g. <64)"));
        
        n.actions.add(new Action("Delete set", "Delete this set?", () -> {
            bg.sets.remove(set);
            h.changed(parent.key);
        }));
        
        n.children.add(potionFolder(n, set, h));
        for (EquipmentSlot slot : SLOTS) {
            n.children.add(slotNode(n, set, slot, h));
        }
        return n;
    }
    
    private static EditorNode potionFolder(EditorNode parent, EquipmentSet set, Hooks h) {
        EditorNode n = new EditorNode(parent, new FolderKey(set, "potions"),
                () -> "Potion effects (" + set.potionEffects.size() + ")", () -> "");
        
        for (PotionEffectEntry pe : set.potionEffects) {
            n.children.add(effectNode(n, set, pe, h));
        }
        n.children.add(addRow(n, "+ Add potion effect", () -> h.prompt(
                "Add potion effect (e.g. minecraft:speed)", "",
                v -> EditScreenShared.effectExists(normalizeId(v.trim())), "Warning: effect not found",
                raw -> {
                    PotionEffectEntry pe = new PotionEffectEntry(normalizeId(raw.trim()), 600, 0);
                    set.potionEffects.add(pe);
                    h.changed(pe);
                })));
        return n;
    }
    
    private static EditorNode effectNode(EditorNode parent, EquipmentSet set, PotionEffectEntry pe, Hooks h) {
        EditorNode n = new EditorNode(parent, pe,
                () -> stripNamespace(pe.effectId),
                () -> (pe.durationTicks / 20) + "s" + (pe.amplifier > 0 ? "  amp " + pe.amplifier : ""));
        
        n.fields.add(Field.text("Effect",
                        () -> pe.effectId,
                        v -> {
                            ResourceLocation id = ResourceLocation.tryParse(normalizeId(v.trim()));
                            if (v.isBlank() || id == null) return "Invalid id";
                            pe.effectId = id.toString();
                            return null;
                        })
                .warn(v -> EditScreenShared.effectExists(normalizeId(v.trim())) ? null : "Warning: effect not found"));
        
        n.fields.add(Field.text("Duration",
                        () -> String.valueOf(pe.durationTicks),
                        v -> {
                            Integer d = parseInt(v, 1);
                            if (d == null) return "Ticks, 1 or more";
                            pe.durationTicks = d;
                            return null;
                        })
                .hint("ticks (20 = 1s)"));
        
        n.fields.add(Field.text("Amplifier",
                () -> String.valueOf(pe.amplifier),
                v -> {
                    Integer a = parseInt(v, 0);
                    if (a == null) return "0 or more";
                    pe.amplifier = a;
                    return null;
                }));
        
        n.actions.add(new Action("Delete effect", null, () -> {
            set.potionEffects.remove(pe);
            h.changed(parent.key);
        }));
        return n;
    }
    
    private static EditorNode slotNode(EditorNode parent, EquipmentSet set, EquipmentSlot slot, Hooks h) {
        List<WeightedItem> items = set.slots.get(slot);
        
        EditorNode n = new EditorNode(parent, new SlotKey(set, slot),
                () -> slot.getName() + " (" + (set.slots.get(slot) == null ? 0 : set.slots.get(slot).size()) + ")",
                () -> "");
        
        if (items != null) {
            for (WeightedItem item : items) {
                n.children.add(itemNode(n, set, slot, item, h));
            }
        }
        n.children.add(addRow(n, "+ Add item", () -> h.prompt(
                "Add item (e.g. minecraft:iron_sword)", "",
                v -> EditScreenShared.itemExists(normalizeId(v.trim())), "Warning: item not found",
                raw -> {
                    WeightedItem wi = new WeightedItem(normalizeId(raw.trim()), 1, null);
                    set.slots.computeIfAbsent(slot, k -> new ArrayList<>()).add(wi);
                    h.changed(wi);
                })));
        return n;
    }
    
    private static EditorNode itemNode(EditorNode parent, EquipmentSet set, EquipmentSlot slot, WeightedItem item, Hooks h) {
        EditorNode n = new EditorNode(parent, item,
                () -> stripNamespace(item.itemId),
                () -> itemSummary(item));
        
        n.fields.add(Field.text("Item",
                        () -> item.itemId,
                        v -> {
                            ResourceLocation id = ResourceLocation.tryParse(normalizeId(v.trim()));
                            if (v.isBlank() || id == null) return "Invalid id";
                            item.itemId = id.toString();
                            return null;
                        })
                .warn(v -> EditScreenShared.itemExists(normalizeId(v.trim())) ? null : "Warning: item not found"));
        
        n.fields.add(Field.text("Weight",
                () -> String.valueOf(item.weight),
                v -> {
                    Integer w = parseInt(v, 1);
                    if (w == null) return "Whole number, 1 or more";
                    item.weight = w;
                    return null;
                }));
        
        n.fields.add(Field.multiline("NBT", "Item NBT (e.g. DifficultyMode: \"expert\")",
                () -> item.nbt != null ? item.nbt : "",
                v -> {
                    item.nbt = v.isBlank() ? null : v;
                    return null;
                }, EditScreenShared::nbtValid, "Warning: invalid NBT syntax"));
        
        n.fields.add(Field.cycle("Enchant", () -> enchantMode(item), () -> {
            if (item.enchant == null) {
                item.enchant = new EnchantData.Random(30);
            } else if (item.enchant instanceof EnchantData.Random) {
                item.enchant = new EnchantData.Predefined(new ArrayList<>(), new ArrayList<>());
            } else {
                item.enchant = null;
            }
            h.changed(item); //the fields below depend on the mode
        }));
        
        if (item.enchant instanceof EnchantData.Random) {
            n.fields.add(Field.text("Power",
                    () -> item.enchant instanceof EnchantData.Random r ? String.valueOf(r.power()) : "30",
                    v -> {
                        Integer p = parseInt(v, 1);
                        if (p == null) return "Whole number, 1 or more";
                        item.enchant = new EnchantData.Random(p);
                        return null;
                    }));
        } else if (item.enchant instanceof EnchantData.Predefined) {
            n.fields.add(Field.multiline("Enchants", "Enchants (one per line: sharpness:5, minecraft:unbreaking:3)",
                            () -> predefinedText(item),
                            v -> {
                                List<String> ids = new ArrayList<>();
                                List<Integer> levels = new ArrayList<>();
                                for (String tok : splitList(v)) {
                                    String id = tok;
                                    int level = 1;
                                    int colon = tok.lastIndexOf(':');
                                    if (colon > 0) {
                                        try {
                                            level = Integer.parseInt(tok.substring(colon + 1));
                                            id = tok.substring(0, colon);
                                        } catch (NumberFormatException ignored) {
                                            //no level suffix: the colon was the namespace separator
                                        }
                                    }
                                    if (level < 1) return "Level must be 1 or more in '" + tok + "'";
                                    if (ResourceLocation.tryParse(normalizeId(id)) == null) return "Invalid id '" + id + "'";
                                    ids.add(normalizeId(id));
                                    levels.add(level);
                                }
                                item.enchant = new EnchantData.Predefined(ids, levels);
                                return null;
                            },
                            v -> {
                                for (String tok : splitList(v)) {
                                    int colon = tok.lastIndexOf(':');
                                    String id = colon > 0 && isInt(tok.substring(colon + 1)) ? tok.substring(0, colon) : tok;
                                    if (!EditScreenShared.enchantExists(normalizeId(id))) return false;
                                }
                                return true;
                            }, "Warning: unknown enchantment")
                    .buttonText(v -> listButton(v, "enchant", "(none)")));
        }
        
        n.actions.add(new Action("Delete item", null, () -> {
            List<WeightedItem> list = set.slots.get(slot);
            if (list != null) {
                list.remove(item);
                //a slot key only exists in the map while it has items (parseSet relies on that)
                if (list.isEmpty()) set.slots.remove(slot);
            }
            h.changed(parent.key);
        }));
        return n;
    }
    
    //helpers
    
    private static EditorNode addRow(EditorNode parent, String text, Runnable run) {
        EditorNode n = new EditorNode(parent, new FolderKey(parent.key, "add"), () -> text, () -> "");
        n.activate = run;
        return n;
    }
    
    private static EditorNode.Setter optionalChance(Consumer<Float> apply) {
        return v -> {
            if (v.isBlank()) {
                apply.accept(null);
                return null;
            }
            Float f = parseFloat(v);
            if (f == null) return "Not a number";
            apply.accept(Mth.clamp(f, -1.0F, 1.0F));
            return null;
        };
    }
    
    private static String dimensionsText(BiomeGroup bg) {
        return bg.dimensions.stream().map(MobEquipmentReloadListener::dimensionMatchToString).collect(Collectors.joining("\n"));
    }
    
    private static String dimensionsSummary(BiomeGroup bg) {
        return bg.dimensions.stream().map(MobEquipmentReloadListener::dimensionMatchToString).collect(Collectors.joining(", "));
    }
    
    private static final String SPAWN_TYPE_NAMES = Arrays.stream(MobSpawnType.values())
            .map(t -> t.name().toLowerCase(Locale.ROOT))
            .collect(Collectors.joining(", "));
    
    private static boolean validSpawnType(String token) {
        try {
            MobEquipmentReloadListener.parseSpawnTypeMatch(token);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /** Row summary for a biome group: chance (if overridden), dimensions, spawn types. */
    private static String biomeSummary(BiomeGroup bg) {
        List<String> parts = new ArrayList<>();
        if (EditScreenShared.hasOverride(bg.chance)) parts.add(pct(bg.chance));
        if (!bg.dimensions.isEmpty()) parts.add("in " + dimensionsSummary(bg));
        if (!bg.spawnTypes.isEmpty()) {
            parts.add(bg.spawnTypes.stream().map(MobEquipmentReloadListener::spawnTypeMatchToString).collect(Collectors.joining(",")));
        }
        if (!bg.structures.isEmpty()) {
            parts.add("at " + bg.structures.stream().map(r -> stripNamespace(r.toString())).collect(Collectors.joining(",")));
        }
        if (!bg.replacements.isEmpty()) {
            parts.add("-> " + bg.replacements.stream().map(r -> stripNamespace(r.mob().toString())).collect(Collectors.joining("/")));
        }
        return String.join("  ", parts);
    }
    
    /** Button text for the one-per-line list popups. */
    private static String listButton(String value, String noun, String whenEmpty) {
        int count = splitList(value).size();
        if (count == 0) return whenEmpty + " - edit...";
        return count + " " + noun + (count == 1 ? "" : "s") + " - edit...";
    }
    
    private static String chanceSummary(Float chance) {
        return EditScreenShared.hasOverride(chance) ? pct(chance) : "";
    }
    
    private static String pct(float chance) {
        return Math.round(chance * 100) + "%";
    }
    
    private static String setSummary(EquipmentSet set) {
        StringBuilder sb = new StringBuilder("w").append(set.weight);
        if (!MobEquipmentReloadListener.isTimeUnrestricted(set.timeOfDay)) {
            sb.append("  ").append(MobEquipmentReloadListener.timeRangeToString(set.timeOfDay));
        }
        if (!MobEquipmentReloadListener.isYLevelUnrestricted(set.yLevel)) {
            sb.append("  y").append(MobEquipmentReloadListener.yLevelToString(set.yLevel));
        }
        return sb.toString();
    }
    
    private static String itemSummary(WeightedItem item) {
        StringBuilder sb = new StringBuilder("w").append(item.weight);
        if (item.enchant instanceof EnchantData.Random r) sb.append("  ench random ").append(r.power());
        else if (item.enchant instanceof EnchantData.Predefined p) sb.append("  ench x").append(p.ids().size());
        if (item.nbt != null) sb.append("  nbt");
        return sb.toString();
    }
    
    private static String enchantMode(WeightedItem item) {
        if (item.enchant == null) return "None";
        return item.enchant instanceof EnchantData.Random ? "Random" : "Predefined";
    }
    
    private static String predefinedText(WeightedItem item) {
        if (!(item.enchant instanceof EnchantData.Predefined p)) return "";
        List<String> parts = new ArrayList<>();
        for (int i = 0; i < p.ids().size(); i++) {
            parts.add(p.ids().get(i) + ":" + p.levels().get(i));
        }
        return String.join("\n", parts);
    }
    
    private static String tightenSemicolons(String v) {
        return v.replaceAll("\\s*;\\s*", ";");
    }
    
    private static List<String> splitList(String v) {
        List<String> out = new ArrayList<>();
        for (String s : v.split("[,\\s]+")) {
            if (!s.isBlank()) out.add(s.trim());
        }
        return out;
    }
    
    private static String normalizeId(String raw) {
        return raw.contains(":") ? raw : "minecraft:" + raw;
    }
    
    private static String stripNamespace(String id) {
        if (id == null) return "(none)";
        return id.startsWith("minecraft:") ? id.substring("minecraft:".length()) : id;
    }
    
    private static boolean isInt(String s) {
        try {
            Integer.parseInt(s);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }
    
    private static Integer parseInt(String v, int min) {
        try {
            int n = Integer.parseInt(v.trim());
            return n >= min ? n : null;
        } catch (NumberFormatException e) {
            return null;
        }
    }
    
    private static Float parseFloat(String v) {
        try {
            return Float.parseFloat(v.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
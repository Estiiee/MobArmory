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

import com.estie.mobarmory.util.StructureMatcher;
import net.minecraft.entity.EntityList;
import net.minecraft.init.Blocks;
import net.minecraft.init.Items;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemMonsterPlacer;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraftforge.fml.common.registry.ForgeRegistries;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Arrays;
import java.util.EnumMap;
import java.util.List;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Turns a MobEquipmentEntry into an EditorNode tree:
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
    
    private static final EntityEquipmentSlot[] SLOTS = {
            EntityEquipmentSlot.HEAD, EntityEquipmentSlot.CHEST, EntityEquipmentSlot.LEGS,
            EntityEquipmentSlot.FEET, EntityEquipmentSlot.MAINHAND, EntityEquipmentSlot.OFFHAND
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
    
    private static final int COLOR_DIFFICULTY = 0xFF9A8A;
    private static final int COLOR_BIOME = 0x8FE08F;
    private static final int COLOR_SET = 0xFFE066;
    private static final int COLOR_POTION = 0xC9A8FF;
    private static final int COLOR_SLOT = 0xB4C0D4;
    
    private static final class FolderKey {
        private final Object owner;
        private final String name;
        
        FolderKey(Object owner, String name) {
            this.owner = owner;
            this.name = name;
        }
        
        @Override public boolean equals(Object o) {
            return o instanceof FolderKey && Objects.equals(owner, ((FolderKey) o).owner) && name.equals(((FolderKey) o).name);
        }
        
        @Override public int hashCode() {
            return Objects.hashCode(owner) * 31 + name.hashCode();
        }
    }
    
    private static final class SlotKey {
        private final EquipmentSet set;
        private final EntityEquipmentSlot slot;
        
        SlotKey(EquipmentSet set, EntityEquipmentSlot slot) {
            this.set = set;
            this.slot = slot;
        }
        
        @Override public boolean equals(Object o) {
            return o instanceof SlotKey && set == ((SlotKey) o).set && slot == ((SlotKey) o).slot;
        }
        
        @Override public int hashCode() {
            return System.identityHashCode(set) * 31 + slot.ordinal();
        }
    }
    
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
                    Map<EntityEquipmentSlot, List<WeightedItem>> copy = new EnumMap<EntityEquipmentSlot, List<WeightedItem>>(EntityEquipmentSlot.class);
                    for (Map.Entry<EntityEquipmentSlot, List<WeightedItem>> e : s.slots.entrySet()) {
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
        root.icon = () -> mobIcon(entry);
        
        root.fields.add(Field.text("Mob ID",
                        () -> entry.mob != null ? entry.mob.toString() : "",
                        v -> {
                            ResourceLocation id = tryParse(normalizeId(v.trim()));
                            if (isBlank(v) || id == null) return "Invalid id";
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
                            entry.chance = MathHelper.clamp(f, -1.0F, 1.0F);
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
        n.color = COLOR_DIFFICULTY;
        n.icon = () -> new ItemStack(Items.IRON_SWORD);
        
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
        n.color = COLOR_BIOME;
        n.icon = () -> new ItemStack(Blocks.GRASS);
        
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
        
        n.fields.add(Field.multiline("Dimensions", "Dimensions, one per line: 0 overworld, -1 nether, 1 end (empty = any)",
                        () -> dimensionsText(bg),
                        v -> {
                            List<DimensionMatch> parsed = new ArrayList<DimensionMatch>();
                            for (String tok : splitList(v)) {
                                try {
                                    parsed.add(MobEquipmentReloadListener.parseDimensionMatch(tok));
                                } catch (IllegalArgumentException e) {
                                    return "Invalid dimension '" + tok + "' (use a number)";
                                }
                            }
                            bg.dimensions.clear();
                            bg.dimensions.addAll(parsed);
                            return null;
                        },
                        v -> true, "")
                .buttonText(v -> listButton(v, "dimension", "(any)")));
        
        n.fields.add(Field.multiline("Spawn types", "Spawn types, ! excludes (e.g. natural, spawner, structure; empty = all)",
                        () -> bg.spawnTypes.stream().map(MobEquipmentReloadListener::spawnTypeMatchToString).collect(Collectors.joining("\n")),
                        v -> {
                            List<SpawnTypeMatch> parsed = new ArrayList<SpawnTypeMatch>();
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
        
        n.fields.add(Field.multiline("Structures", "Structures, one per line (e.g. Village, Temple, or a mod's structure name)",
                        () -> String.join("\n", bg.structures),
                        v -> {
                            List<String> parsed = new ArrayList<String>(splitList(v));
                            bg.structures.clear();
                            bg.structures.addAll(parsed);
                            return null;
                        },
                        v -> splitList(v).stream().allMatch(EditorTree::structureKnown), "Warning: not a 1.12.2 structure name")
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
                                ResourceLocation id = tryParse(normalizeId(idPart));
                                if (isBlank(idPart) || id == null) return "Invalid mob id '" + idPart + "'";
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
            EquipmentSet set = new EquipmentSet(null, 1, new EnumMap<EntityEquipmentSlot, List<WeightedItem>>(EntityEquipmentSlot.class));
            bg.sets.add(set);
            h.changed(set);
        }));
        return n;
    }
    
    private static EditorNode setNode(EditorNode parent, BiomeGroup bg, EquipmentSet set, Hooks h) {
        EditorNode n = new EditorNode(parent, set,
                () -> set.name != null && !isBlank(set.name) ? set.name : "Set " + (bg.sets.indexOf(set) + 1),
                () -> setSummary(set));
        n.color = COLOR_SET;
        n.icon = () -> firstItem(set);
        
        n.fields.add(Field.text("Name",
                () -> set.name != null ? set.name : "",
                v -> {
                    set.name = isBlank(v) ? null : v;
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
                    set.lootTable = isBlank(v) ? null : v.trim();
                    return null;
                }).hint("none"));
        
        n.fields.add(Field.multiline("Mob NBT", "Mob NBT (e.g. CustomName: '{\"text\":\"Boss\"}')",
                () -> set.mobNbt != null ? set.mobNbt : "",
                v -> {
                    set.mobNbt = isBlank(v) ? null : v;
                    return null;
                }, EditScreenShared::nbtValid, "Warning: invalid NBT syntax"));
        
        n.fields.add(Field.text("Time",
                        () -> MobEquipmentReloadListener.isTimeUnrestricted(set.timeOfDay)
                                ? "" : MobEquipmentReloadListener.timeRangeToString(set.timeOfDay),
                        v -> {
                            try {
                                set.timeOfDay = isBlank(v)
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
                                set.yLevel = isBlank(v)
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
        for (EntityEquipmentSlot slot : SLOTS) {
            n.children.add(slotNode(n, set, slot, h));
        }
        return n;
    }
    
    private static EditorNode potionFolder(EditorNode parent, EquipmentSet set, Hooks h) {
        EditorNode n = new EditorNode(parent, new FolderKey(set, "potions"),
                () -> "Potion effects (" + set.potionEffects.size() + ")", () -> "");
        n.color = COLOR_POTION;
        n.icon = () -> new ItemStack(Items.POTIONITEM);
        
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
        n.color = COLOR_POTION;
        n.icon = () -> new ItemStack(Items.POTIONITEM);
        
        n.fields.add(Field.text("Effect",
                        () -> pe.effectId,
                        v -> {
                            ResourceLocation id = tryParse(normalizeId(v.trim()));
                            if (isBlank(v) || id == null) return "Invalid id";
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
    
    private static EditorNode slotNode(EditorNode parent, EquipmentSet set, EntityEquipmentSlot slot, Hooks h) {
        List<WeightedItem> items = set.slots.get(slot);
        
        EditorNode n = new EditorNode(parent, new SlotKey(set, slot),
                () -> slot.getName() + " (" + (set.slots.get(slot) == null ? 0 : set.slots.get(slot).size()) + ")",
                () -> "");
        n.color = COLOR_SLOT;
        n.icon = () -> {
            List<WeightedItem> list = set.slots.get(slot);
            return list != null && !list.isEmpty() ? stackOf(list.get(0).itemId) : ItemStack.EMPTY;
        };
        
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
    
    private static EditorNode itemNode(EditorNode parent, EquipmentSet set, EntityEquipmentSlot slot, WeightedItem item, Hooks h) {
        EditorNode n = new EditorNode(parent, item,
                () -> stripNamespace(item.itemId),
                () -> itemSummary(item));
        n.icon = () -> stackOf(item.itemId);
        
        n.fields.add(Field.text("Item",
                        () -> item.itemId,
                        v -> {
                            ResourceLocation id = tryParse(normalizeId(v.trim()));
                            if (isBlank(v) || id == null) return "Invalid id";
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
                    item.nbt = isBlank(v) ? null : v;
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
                    () -> item.enchant instanceof EnchantData.Random ? String.valueOf(((EnchantData.Random) item.enchant).power()) : "30",
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
                                    if (tryParse(normalizeId(id)) == null) return "Invalid id '" + id + "'";
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
    
    //preview + icons
    
    /** Sets the preview cycles through for this selection: inside a set it pins that set, above it everything below. */
    public static List<EquipmentSet> previewSets(EditorNode selected) {
        for (EditorNode p = selected; p != null; p = p.parent) {
            if (p.key instanceof EquipmentSet) return Collections.singletonList((EquipmentSet) p.key);
        }
        List<EquipmentSet> out = new ArrayList<>();
        collectSets(selected, out);
        return out;
    }
    
    private static void collectSets(EditorNode node, List<EquipmentSet> out) {
        if (node.key instanceof EquipmentSet) {
            out.add((EquipmentSet) node.key);
            return;
        }
        for (EditorNode c : node.children) collectSets(c, out);
    }
    
    /**
     * Mobs the preview cycles through: empty when nothing is replaced, otherwise the original mob
     * first, then the replacements of the selected group (or of every group below the selection).
     */
    public static List<ResourceLocation> previewMobs(EditorNode selected) {
        EditorNode group = null;
        for (EditorNode p = selected; p != null; p = p.parent) {
            if (p.key instanceof BiomeGroup) {
                group = p;
                break;
            }
        }
        
        LinkedHashSet<ResourceLocation> replacements = new LinkedHashSet<>();
        collectReplacements(group != null ? group : selected, replacements);
        if (replacements.isEmpty()) return Collections.<ResourceLocation>emptyList();
        
        EditorNode top = selected;
        while (top.parent != null) top = top.parent;
        
        LinkedHashSet<ResourceLocation> mobs = new LinkedHashSet<>();
        if (top.key instanceof MobEquipmentEntry && ((MobEquipmentEntry) top.key).mob != null) {
            mobs.add(((MobEquipmentEntry) top.key).mob);
        }
        mobs.addAll(replacements);
        return new ArrayList<>(mobs);
    }
    
    private static void collectReplacements(EditorNode node, LinkedHashSet<ResourceLocation> out) {
        if (node.key instanceof BiomeGroup) {
            for (MobReplacement r : ((BiomeGroup) node.key).replacements) out.add(r.mob());
            return;
        }
        for (EditorNode c : node.children) collectReplacements(c, out);
    }
    
    //spawn egg when the mob has one, otherwise a zombie head
    private static ItemStack mobIcon(MobEquipmentEntry entry) {
        if (entry.mob != null && EntityList.ENTITY_EGGS.containsKey(entry.mob)) {
            ItemStack egg = new ItemStack(Items.SPAWN_EGG);
            ItemMonsterPlacer.applyEntityIdToItemStack(egg, entry.mob);
            return egg;
        }
        return new ItemStack(Items.SKULL, 1, 2);
    }
    
    //unknown ids show a barrier, like the preview does
    private static ItemStack stackOf(String itemId) {
        ResourceLocation id = itemId == null ? null : tryParse(itemId);
        if (id == null || !ForgeRegistries.ITEMS.containsKey(id)) return new ItemStack(Blocks.BARRIER);
        return new ItemStack(ForgeRegistries.ITEMS.getValue(id));
    }
    
    private static ItemStack firstItem(EquipmentSet set) {
        for (EntityEquipmentSlot slot : SLOTS) {
            List<WeightedItem> list = set.slots.get(slot);
            if (list != null && !list.isEmpty()) return stackOf(list.get(0).itemId);
        }
        return new ItemStack(Items.IRON_CHESTPLATE); //empty set
    }
    
    //helpers
    
    private static final Pattern ID_PATTERN = Pattern.compile("([a-z0-9_.-]+:)?[a-z0-9/._-]+");
    
    //1.12.2 has no ResourceLocation.tryParse, and its constructor accepts anything
    private static ResourceLocation tryParse(String s) {
        if (s == null || !ID_PATTERN.matcher(s).matches()) return null;
        return new ResourceLocation(s);
    }
    
    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
    
    private static EditorNode addRow(EditorNode parent, String text, Runnable run) {
        EditorNode n = new EditorNode(parent, new FolderKey(parent.key, "add"), () -> text, () -> "");
        n.activate = run;
        return n;
    }
    
    private static EditorNode.Setter optionalChance(Consumer<Float> apply) {
        return v -> {
            if (isBlank(v)) {
                apply.accept(null);
                return null;
            }
            Float f = parseFloat(v);
            if (f == null) return "Not a number";
            apply.accept(MathHelper.clamp(f, -1.0F, 1.0F));
            return null;
        };
    }
    
    private static String dimensionsText(BiomeGroup bg) {
        return bg.dimensions.stream().map(MobEquipmentReloadListener::dimensionMatchToString).collect(Collectors.joining("\n"));
    }
    
    private static String dimensionsSummary(BiomeGroup bg) {
        return bg.dimensions.stream().map(MobEquipmentReloadListener::dimensionMatchToString).collect(Collectors.joining(", "));
    }
    
    //1.12.2 has no structure registry. only 1.20.1 style "minecraft:" ids can be checked: they have to map onto a
    //vanilla structure name. anything else could be a mod's structure name, so no warning
    private static boolean structureKnown(String token) {
        if (token.toLowerCase().startsWith("minecraft:")) return StructureMatcher.isVanilla(token);
        return true;
    }
    
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
        if (item.enchant instanceof EnchantData.Random) {
            sb.append("  ench random ").append(((EnchantData.Random) item.enchant).power());
        } else if (item.enchant instanceof EnchantData.Predefined) {
            sb.append("  ench x").append(((EnchantData.Predefined) item.enchant).ids().size());
        }
        if (item.nbt != null) sb.append("  nbt");
        return sb.toString();
    }
    
    private static String enchantMode(WeightedItem item) {
        if (item.enchant == null) return "None";
        return item.enchant instanceof EnchantData.Random ? "Random" : "Predefined";
    }
    
    private static String predefinedText(WeightedItem item) {
        if (!(item.enchant instanceof EnchantData.Predefined)) return "";
        EnchantData.Predefined p = (EnchantData.Predefined) item.enchant;
        List<String> parts = new ArrayList<String>();
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
            if (!isBlank(s)) out.add(s.trim());
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
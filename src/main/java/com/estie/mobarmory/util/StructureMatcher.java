package com.estie.mobarmory.util;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import net.minecraft.world.gen.ChunkProviderServer;
import net.minecraft.world.gen.structure.MapGenStructureData;

import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * "Is this position inside a structure called X". 1.12.2 has no structure registry, a structure is just a
 * name. Two paths:
 *  1. the vanilla names (Village, Mineshaft, Stronghold, Monument, Mansion, Temple, Fortress, EndCity)
 *     go through the chunk generator's own check
 *  2. any other name is looked up in the structure data the world saves for every structure generator
 *     built on vanilla's MapGenStructure. The names are the data file names under the world's data folder
 *     (Village.dat = "Village"); mod structures that use the same base class show up there too.
 * <p>
 * Structures a mod places by writing blocks directly (most dungeon mods) have no saved identity at all and
 * can't be matched this way.
 */
public final class StructureMatcher {
    private StructureMatcher() {}
    
    private static final Set<String> VANILLA = new HashSet<>(Arrays.asList(
            "Village", "Mineshaft", "Stronghold", "Monument", "Mansion", "Temple", "Fortress", "EndCity"));
    
    private static final Map<String, String> ALIASES = new HashMap<>();
    
    private static void alias(String name, String... ids) {
        for (String id : ids) ALIASES.put(id, name);
    }
    
    static {
        alias("Village", "minecraft:village");
        alias("Mineshaft", "minecraft:mineshaft");
        alias("Stronghold", "minecraft:stronghold");
        alias("Monument", "minecraft:monument", "minecraft:ocean_monument");
        alias("Mansion", "minecraft:mansion", "minecraft:woodland_mansion");
        alias("Fortress", "minecraft:fortress", "minecraft:nether_fortress");
        alias("EndCity", "minecraft:end_city");
        //desert pyramids, jungle temples, swamp huts and igloos are all "Temple" in 1.12.2
        alias("Temple", "minecraft:temple", "minecraft:desert_pyramid", "minecraft:jungle_pyramid",
                "minecraft:jungle_temple", "minecraft:swamp_hut", "minecraft:igloo");
    }

    public static String resolve(String raw) {
        String alias = ALIASES.get(raw.trim().toLowerCase(Locale.ROOT));
        return alias != null ? alias : raw.trim();
    }
    
    public static boolean isInsideAny(World world, BlockPos pos, List<String> names) {
        for (String name : names) {
            if (isInside(world, pos, name)) return true;
        }
        return false;
    }
    
    public static boolean isInside(World world, BlockPos pos, String raw) {
        String name = resolve(raw);
        if (name.isEmpty()) return false;
        
        try {
            if (VANILLA.contains(name) && world.getChunkProvider() instanceof ChunkProviderServer) {
                ChunkProviderServer provider = (ChunkProviderServer) world.getChunkProvider();
                if (provider.isInsideStructure(world, name, pos)) return true;
            }
            return insideSavedStructure(world, pos, name);
        } catch (Exception e) {
            return false;
        }
    }
    
    //saved data layout: "Features" -> one compound per structure start -> "Children" -> pieces with a "BB" int array
    //(minX, minY, minZ, maxX, maxY, maxZ). a position counts when it is inside any piece, like the vanilla check
    private static boolean insideSavedStructure(World world, BlockPos pos, String name) {
        Object loaded = world.getPerWorldStorage().getOrLoadData(MapGenStructureData.class, name);
        if (!(loaded instanceof MapGenStructureData)) return false;
        
        NBTTagCompound features = ((MapGenStructureData) loaded).getTagCompound().getCompoundTag("Features");
        for (String key : features.getKeySet()) {
            NBTTagList children = features.getCompoundTag(key).getTagList("Children", 10);
            
            for (int i = 0; i < children.tagCount(); i++) {
                int[] bb = children.getCompoundTagAt(i).getIntArray("BB");
                if (bb.length != 6) continue;
                
                if (pos.getX() >= bb[0] && pos.getX() <= bb[3]
                        && pos.getY() >= bb[1] && pos.getY() <= bb[4]
                        && pos.getZ() >= bb[2] && pos.getZ() <= bb[5]) {
                    return true;
                }
            }
        }
        return false;
    }
}
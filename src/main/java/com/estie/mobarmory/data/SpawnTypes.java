package com.estie.mobarmory.data;

import java.util.*;

/**
 * Every spawn type name the json accepts in "spawn_types". 1.12.2 has no MobSpawnType enum
 * <p>
 * The names are the 1.20.1 ones so files stay consistent. Names the handler can't
 * detect in 1.12.2 simply never match (and "!name" never excludes anything).
 * <p>
 * Mutable in case of custom implementation.
 * <p>
 * Confirmed as working: natural, spawner, spawn_egg, structure, command, jockey
 */
public final class SpawnTypes {
    private SpawnTypes() {}
    
    public static final Set<String> KNOWN = new LinkedHashSet<>(Arrays.asList(
            "natural",
            "spawner",
            "chunk_generation",
            "structure",
            "triggered",
            "breeding",
            "mob_summoned",
            "jockey",
            "event",
            "conversion",
            "reinforcement",
            "patrol",
            "bucket",
            "spawn_egg",
            "command",
            "dispenser",
            "transformation"
    ));
    
    public static boolean isKnown(String name) {
        return KNOWN.contains(name);
    }
}
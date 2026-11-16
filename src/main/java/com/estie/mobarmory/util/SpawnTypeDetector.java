package com.estie.mobarmory.util;

import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.data.SpawnTypes;

import java.util.ArrayList;
import java.util.List;

/**
 * Works out how a mob came into the world by looking at who is calling: it walks the stack from the
 * entity join event upwards and the nearest frame that matches a rule decides. Call it from inside
 * the join event (where the spawning code is still on the stack).
 * <p>
 * Rules are class name prefixes (class names are readable at runtime, method names are SRG "func_" names
 * outside the dev environment, so method rules list both). The table below is the one place to edit:
 *  - first matching rule wins, rules are checked nearest frame first and in list order within a frame
 *  - "also" rules need a deeper frame with that prefix too (egg vs dispenser, natural vs chunk generation)
 *  - the type has to be a name from SpawnTypes.KNOWN
 *  - anything unmatched (other mods' spawners and so on) is unknown (null)
 * <p>
 * Set DEBUG to true to log the detected type together with the top of the stack for every detection,
 * which is the quickest way to find the frames to add a rule for.
 */
public final class SpawnTypeDetector {
    private SpawnTypeDetector() {}
    
    public static boolean DEBUG = true;
    
    private static final int MAX_FRAMES = 64;
    
    private static final class Rule {
        final String classPrefix;
        final String[] methods;   //null = any method
        final String alsoPrefix;  //null = no deeper frame needed
        final String type;
        
        Rule(String classPrefix, String[] methods, String alsoPrefix, String type) {
            this.classPrefix = classPrefix;
            this.methods = methods;
            this.alsoPrefix = alsoPrefix;
            this.type = type;
        }
    }
    
    private static final List<Rule> RULES = new ArrayList<>();
    
    private static void rule(String classPrefix, String type) {
        RULES.add(new Rule(classPrefix, null, null, type));
    }
    
    private static void rule(String classPrefix, String alsoPrefix, String type) {
        RULES.add(new Rule(classPrefix, null, alsoPrefix, type));
    }
    
    private static void ruleMethod(String classPrefix, String type, String... methods) {
        RULES.add(new Rule(classPrefix, methods, null, type));
    }
    
    static {
        //mobs placed by structure pieces while a structure generates (witches in huts, villagers, ...)
        rule("net.minecraft.world.gen.structure.", "structure");
        
        //commands and command blocks
        rule("net.minecraft.command.server.CommandSummon", "command");
        
        //spawn eggs, and dispensers firing them (the dispenser frames sit deeper than the egg item)
        rule("net.minecraft.item.ItemMonsterPlacer", "net.minecraft.dispenser.", "dispenser");
        rule("net.minecraft.item.ItemMonsterPlacer", "spawn_egg");
        
        //mob spawner blocks
        rule("net.minecraft.tileentity.MobSpawnerBaseLogic", "spawner");
        
        //jockeys are created inside the mob's onInitialSpawn
        ruleMethod("net.minecraft.entity.monster.EntitySpider", "jockey", "onInitialSpawn", "func_180482_a");
        ruleMethod("net.minecraft.entity.monster.EntityZombie", "jockey", "onInitialSpawn", "func_180482_a");
        
        //zombie reinforcements
        ruleMethod("net.minecraft.entity.monster.EntityZombie", "reinforcement", "attackEntityFrom", "func_70097_a");
        
        //animals breeding
        rule("net.minecraft.entity.ai.EntityAIMate", "breeding");
        
        //a mob turning into another
        rule("net.minecraft.entity.monster.EntityZombieVillager", "conversion");
        
        //built or triggered by the player: wither, golems, silverfish blocks, endermites from pearls
        rule("net.minecraft.block.BlockSkull", "triggered");
        rule("net.minecraft.block.BlockPumpkin", "triggered");
        rule("net.minecraft.block.BlockSilverfish", "triggered");
        rule("net.minecraft.entity.item.EntityEnderPearl", "triggered");
        
        //natural spawning and chunk generation spawning share the spawner class; generation has the chunk generator below it
        rule("net.minecraft.world.WorldEntitySpawner", "net.minecraft.world.gen.", "chunk_generation");
        rule("net.minecraft.world.WorldEntitySpawner", "natural");
        
        for (Rule r : RULES) {
            if (!SpawnTypes.isKnown(r.type)) {
                MobArmory.LOGGER.warn("SpawnTypeDetector rule for {} uses unknown spawn type '{}' (add it to SpawnTypes.KNOWN)", r.classPrefix, r.type);
            }
        }
    }
    
    /** @return a name from SpawnTypes.KNOWN, or null when nothing matched */
    public static String detect() {
        StackTraceElement[] frames = new Throwable().getStackTrace();
        int limit = Math.min(frames.length, MAX_FRAMES);
        
        String found = null;
        search:
        for (int i = 0; i < limit; i++) {
            String className = frames[i].getClassName();
            if (className.startsWith("com.estie.mobarmory.") || className.startsWith("java.") || className.startsWith("sun.")) continue;
            
            for (Rule rule : RULES) {
                if (!className.startsWith(rule.classPrefix)) continue;
                if (rule.methods != null && !contains(rule.methods, frames[i].getMethodName())) continue;
                if (rule.alsoPrefix != null && !anyFrameBelow(frames, i + 1, limit, rule.alsoPrefix)) continue;
                
                found = rule.type;
                break search;
            }
        }
        
        if (DEBUG) {
            StringBuilder top = new StringBuilder();
            for (int i = 0; i < Math.min(limit, 14); i++) {
                top.append("\n    ").append(frames[i].getClassName()).append('.').append(frames[i].getMethodName());
            }
            MobArmory.LOGGER.info("Spawn type detected: {} {}", found == null ? "(unknown)" : found, top);
        }
        return found;
    }
    
    private static boolean anyFrameBelow(StackTraceElement[] frames, int from, int limit, String prefix) {
        for (int i = from; i < limit; i++) {
            if (frames[i].getClassName().startsWith(prefix)) return true;
        }
        return false;
    }
    
    private static boolean contains(String[] values, String value) {
        for (String v : values) {
            if (v.equals(value)) return true;
        }
        return false;
    }
}
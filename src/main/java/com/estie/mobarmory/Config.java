package com.estie.mobarmory;

import net.minecraftforge.common.config.Configuration;
import net.minecraftforge.fml.client.event.ConfigChangedEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

import java.io.File;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public final class Config {
    
    private static Configuration CONFIG;

    private static final String CATEGORY_GENERAL = "general";
    private static final String DEFAULT_OUTPUT_DIRECTORY = "output";
    private static final String DEFAULT_READ_DIRECTORY = "read";
    
    public static String outputDirectory;
    public static String readDirectory;
    
    public static boolean enabled;
    public static boolean clientAccessible;
    public static boolean spawnExampleZombie;

    public static void init(File configDir) {
        File mobArmoryDir = new File(configDir, MobArmory.MODID);
        
        if (!mobArmoryDir.exists() && !mobArmoryDir.mkdirs()) {
            MobArmory.LOGGER.warn("Could not create MobArmory config directory: {}", mobArmoryDir);
        }
        
        CONFIG = new Configuration(new File(mobArmoryDir, "mobarmory.cfg"));
        load();
    }
    
    public static void load() {
        enabled = CONFIG.getBoolean(
                "enabled",
                CATEGORY_GENERAL,
                true,
                "Use to enable or disable mob equipment altogether"
        );
        
        clientAccessible = CONFIG.getBoolean(
                "clientAccessible",
                CATEGORY_GENERAL,
                true,
                
                "Whether clients can access server's mob equipment entries without operator permissions.",
                "This option does not allow altering any server side data, all copies are created locally," +
                "but it allows viewing and modifying server's mob equipment data on the client"
        );
        
        outputDirectory = CONFIG.getString(
                "outputDirectory",
                CATEGORY_GENERAL,
                DEFAULT_OUTPUT_DIRECTORY,
                "Where generated files should be placed, relative to the MobArmory config directory."
        );
        
        readDirectory = CONFIG.getString(
                "readDirectory",
                CATEGORY_GENERAL,
                DEFAULT_READ_DIRECTORY,
                "Where mob equipment files should be read from, relative to the MobArmory config directory."
        );
        
        spawnExampleZombie = CONFIG.getBoolean(
                "spawnExampleZombie",
                CATEGORY_GENERAL,
                false,
                "Should the game use the 'zombie_example' file for spawning mobs.",
                "By default false as it's only meant to work as a demonstration"
        );
        
        if (CONFIG.hasChanged()) {
            CONFIG.save();
        }
    }

    public static File getOutputDirectory() {
        return new File(CONFIG.getConfigFile().getParentFile(), outputDirectory);
    }
    
    public static File getReadDirectory() {
        return new File(CONFIG.getConfigFile().getParentFile(), readDirectory);
    }
    
    @SubscribeEvent
    public static void onConfigChanged(ConfigChangedEvent.OnConfigChangedEvent event) {
        if (!MobArmory.MODID.equals(event.getModID())) return;
        load();
    }
}
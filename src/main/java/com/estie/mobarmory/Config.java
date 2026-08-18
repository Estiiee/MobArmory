package com.estie.mobarmory;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.event.config.ModConfigEvent;

@Mod.EventBusSubscriber(modid = MobArmory.MODID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class Config {
    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();
    public static final ForgeConfigSpec SPEC;
    
    // =========================================================
    // Definitions
    // =========================================================
    
    private static final ForgeConfigSpec.ConfigValue<String> OUTPUT_DIRECTORY;
    
    private static final ForgeConfigSpec.BooleanValue ENABLED;
    private static final ForgeConfigSpec.BooleanValue CLIENT_ACCESSIBLE;
    
    static {
        BUILDER.push("General");
        
        ENABLED = BUILDER.comment("Use to enable or disable mob equipment altogether").define("enabled", true);
        
        CLIENT_ACCESSIBLE = BUILDER.comment("Whether clients can access server's mob equipment entries without operator permissions",
                "This option does not allow altering any server side data, all copies are created locally, but it allows viewing and modifying server's mob equipment data on the client")
                .define("clientAccessible", true);
        
        OUTPUT_DIRECTORY = BUILDER.comment("Where generated files should be placed, starting from the root").define("outputDirectory", "mob_armory/output");
        
        BUILDER.pop();
        SPEC = BUILDER.build();
    }
    
    // =========================================================
    // Runtime values
    // =========================================================
    
    public static String outputDirectory;
    
    public static boolean enabled;
    public static boolean clientAccessible;
    
    // =========================================================
    // Sync
    // =========================================================
    
    @SubscribeEvent
    static void onLoad(final ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) return;
        
        enabled = ENABLED.get();
        clientAccessible = CLIENT_ACCESSIBLE.get();
        outputDirectory = OUTPUT_DIRECTORY.get();
    }
    
    // =========================================================
    // Helpers
    // =========================================================
    
}
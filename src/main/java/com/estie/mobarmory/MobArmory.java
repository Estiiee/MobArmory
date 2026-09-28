package com.estie.mobarmory;

import com.estie.mobarmory.command.MobArmoryCommands;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.event.FMLPreInitializationEvent;
import net.minecraftforge.fml.common.event.FMLServerStartingEvent;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Mod(modid = MobArmory.MODID, name = MobArmory.NAME, version = MobArmory.VERSION,
        acceptedMinecraftVersions = "[1.12.2]")
public class MobArmory {
    public static final String MODID = "mobarmory";
    public static final String NAME = "MobArmory";
    public static final String VERSION = "0.1.0";
    public static final Logger LOGGER = LogManager.getLogger(MODID);
    
    @Mod.Instance(MODID)
    public static MobArmory instance;
    
    @Mod.EventHandler
    public void preInit(FMLPreInitializationEvent event) {
        PacketHandler.register();
    }
    
    @Mod.EventHandler
    public void serverStarting(FMLServerStartingEvent event) {
        //MobEquipmentReloadListener.reload();
        event.registerServerCommand(new MobArmoryCommands());
    }
}
package com.estie.mobarmory;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import com.mojang.logging.LogUtils;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.AddReloadListenerEvent;
import net.minecraftforge.eventbus.api.IEventBus;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.registries.RegisterEvent;
import org.slf4j.Logger;

@Mod(MobArmory.MODID)
public class MobArmory {
    public static final String MODID = "mobarmory";
    public static final String NAME = "MobArmory";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public MobArmory(FMLJavaModLoadingContext context) {
        IEventBus modEventBus = context.getModEventBus();
        IEventBus forgeEventBus = MinecraftForge.EVENT_BUS;
        modEventBus.addListener(this::commonSetup);
        modEventBus.addListener(this::onRegister);
        forgeEventBus.addListener(this::reloadListener);
        
        context.registerConfig(ModConfig.Type.COMMON, Config.SPEC, "mobarmory.toml");
    }
    
    private void commonSetup(final FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            PacketHandler.register();
            //MobEquipmentList.init();
        });
    }
    
    private void reloadListener(AddReloadListenerEvent event) {
        event.addListener(new MobEquipmentReloadListener());
    }
    
    private void onRegister(RegisterEvent event) {}
    
    @Mod.EventBusSubscriber(modid = MODID, bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static class ClientModEvents {
        
        @SubscribeEvent
        public static void onClientSetup(FMLClientSetupEvent event) {
            event.enqueueWork(() -> {
            
            });
        }
    }
}
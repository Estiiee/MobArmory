package com.estie.mobarmory.handlers;

import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.packet.LoadMobEquipmentEntryPacket;
import com.estie.mobarmory.packet.OpenEditScreenPacket;
import com.estie.mobarmory.packet.OpenLookupScreenPacket;
import net.minecraftforge.fml.common.network.NetworkRegistry;
import net.minecraftforge.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import net.minecraftforge.fml.relauncher.Side;

public class PacketHandler {
    public static SimpleNetworkWrapper INSTANCE;
    
    public static void register() {
        INSTANCE = NetworkRegistry.INSTANCE.newSimpleChannel(MobArmory.MODID);
        
        int id = 0;
        INSTANCE.registerMessage(OpenLookupScreenPacket.Handler.class, OpenLookupScreenPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(OpenEditScreenPacket.Handler.class, OpenEditScreenPacket.class, id++, Side.CLIENT);
        INSTANCE.registerMessage(LoadMobEquipmentEntryPacket.Handler.class, LoadMobEquipmentEntryPacket.class, id++, Side.SERVER);
    }
}
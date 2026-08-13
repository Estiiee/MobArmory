package com.livajq.mobarmory.handlers;

import com.livajq.mobarmory.MobArmory;
import com.livajq.mobarmory.packet.LoadMobEquipmentEntryPacket;
import com.livajq.mobarmory.packet.OpenEditScreenPacket;
import com.livajq.mobarmory.packet.OpenLookupScreenPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class PacketHandler {
    public static final String PROTOCOL_VERSION = "1";
    public static SimpleChannel INSTANCE;
    
    public static void register() {
        INSTANCE = NetworkRegistry.newSimpleChannel(
                new ResourceLocation(MobArmory.MODID, "main"),
                () -> PROTOCOL_VERSION,
                PROTOCOL_VERSION::equals,
                PROTOCOL_VERSION::equals
        );
        
        int id = 0;
        
        INSTANCE.registerMessage(id++, OpenLookupScreenPacket.class, OpenLookupScreenPacket::encode, OpenLookupScreenPacket::decode, OpenLookupScreenPacket::handle);
        INSTANCE.registerMessage(id++, LoadMobEquipmentEntryPacket.class, LoadMobEquipmentEntryPacket::encode, LoadMobEquipmentEntryPacket::decode, LoadMobEquipmentEntryPacket::handle);
        INSTANCE.registerMessage(id++, OpenEditScreenPacket.class, OpenEditScreenPacket::encode, OpenEditScreenPacket::decode, OpenEditScreenPacket::handle);
    }
}
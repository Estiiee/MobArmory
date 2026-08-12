package com.livajq.mobarmory.handlers;

import com.livajq.mobarmory.MobArmory;
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
    }
}
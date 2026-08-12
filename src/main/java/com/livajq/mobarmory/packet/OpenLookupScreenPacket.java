package com.livajq.mobarmory.packet;

import com.livajq.mobarmory.client.gui.screen.LookupScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class OpenLookupScreenPacket {
    
    private final List<String> fileNames;
    
    public OpenLookupScreenPacket(List<String> fileNames) {
        this.fileNames = fileNames;
    }
    
    public static void encode(OpenLookupScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.fileNames.size());
        for (String name : msg.fileNames) {
            buf.writeUtf(name);
        }
    }
    
    public static OpenLookupScreenPacket decode(FriendlyByteBuf buf) {
        int size = buf.readVarInt();
        List<String> list = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            list.add(buf.readUtf());
        }
        return new OpenLookupScreenPacket(list);
    }
    
    public static void handle(OpenLookupScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            Minecraft.getInstance().setScreen(new LookupScreen(msg.fileNames));
        });
        ctx.get().setPacketHandled(true);
    }
}
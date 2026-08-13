package com.livajq.mobarmory.packet;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.livajq.mobarmory.client.gui.screen.EditScreenMain;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class OpenEditScreenPacket {
    
    private final String fileName;
    private final String json;
    
    public OpenEditScreenPacket(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        this.fileName = entry.fileName;
        this.json = MobEquipmentReloadListener.toJson(entry).toString();
    }
    
    private OpenEditScreenPacket(String fileName, String json) {
        this.fileName = fileName;
        this.json = json;
    }
    
    public static void encode(OpenEditScreenPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.fileName != null);
        if (msg.fileName != null) buf.writeUtf(msg.fileName);
        buf.writeUtf(msg.json, 32767);
    }
    
    public static OpenEditScreenPacket decode(FriendlyByteBuf buf) {
        String fileName = buf.readBoolean() ? buf.readUtf() : null;
        String json = buf.readUtf(32767);
        return new OpenEditScreenPacket(fileName, json);
    }
    
    public static void handle(OpenEditScreenPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            JsonObject obj = JsonParser.parseString(msg.json).getAsJsonObject();
            MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.fromJson(msg.fileName, obj);
            Minecraft.getInstance().setScreen(new EditScreenMain(entry));
        });
        ctx.get().setPacketHandled(true);
    }
}
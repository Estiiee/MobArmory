package com.estie.mobarmory.packet;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.function.Supplier;

public class LoadMobEquipmentEntryPacket {
    private final String fileName; // null = create blank
    
    public LoadMobEquipmentEntryPacket(String fileName) {
        this.fileName = fileName;
    }
    
    public static void encode(LoadMobEquipmentEntryPacket msg, FriendlyByteBuf buf) {
        buf.writeBoolean(msg.fileName != null);
        if (msg.fileName != null) buf.writeUtf(msg.fileName);
    }
    
    public static LoadMobEquipmentEntryPacket decode(FriendlyByteBuf buf) {
        String fileName = buf.readBoolean() ? buf.readUtf() : null;
        return new LoadMobEquipmentEntryPacket(fileName);
    }
    
    public static void handle(LoadMobEquipmentEntryPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            
            MobEquipmentReloadListener.MobEquipmentEntry toEdit;
            
            if (msg.fileName == null) toEdit = new MobEquipmentReloadListener.MobEquipmentEntry(null, null, 1.0f, new ArrayList<>());
           
            else {
                toEdit = MobEquipmentReloadListener.LOOKUP_FILES.stream()
                        .filter(e -> msg.fileName.equals(e.fileName))
                        .findFirst()
                        .orElse(null);
                
                if (toEdit == null) {
                    player.sendSystemMessage(Component.literal("Could not find entry: " + msg.fileName));
                    return;
                }
            }
            
            PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenEditScreenPacket(toEdit));
        });
        ctx.get().setPacketHandled(true);
    }
}
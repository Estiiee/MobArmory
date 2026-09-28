package com.estie.mobarmory.packet;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.text.TextComponentString;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;

import java.util.ArrayList;

public class LoadMobEquipmentEntryPacket implements IMessage {
    private String fileName; // null = create blank
    
    public LoadMobEquipmentEntryPacket() {}
    
    public LoadMobEquipmentEntryPacket(String fileName) {
        this.fileName = fileName;
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(fileName != null);
        if (fileName != null) ByteBufUtils.writeUTF8String(buf, fileName);
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        fileName = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
    }
    
    public static class Handler implements IMessageHandler<LoadMobEquipmentEntryPacket, IMessage> {
        @Override
        public IMessage onMessage(LoadMobEquipmentEntryPacket msg, MessageContext ctx) {
            EntityPlayerMP player = ctx.getServerHandler().player;
            player.getServerWorld().addScheduledTask(() -> {
                MobEquipmentReloadListener.MobEquipmentEntry toEdit;
                
                if (msg.fileName == null) {
                    toEdit = new MobEquipmentReloadListener.MobEquipmentEntry(null, null, 1.0f, new ArrayList<>());
                } else {
                    toEdit = MobEquipmentReloadListener.LOOKUP_FILES.stream()
                            .filter(e -> msg.fileName.equals(e.fileName))
                            .findFirst()
                            .orElse(null);
                    
                    if (toEdit == null) {
                        player.sendMessage(new TextComponentString("Could not find entry: " + msg.fileName));
                        return;
                    }
                }
                
                PacketHandler.INSTANCE.sendTo(new OpenEditScreenPacket(toEdit), player);
            });
            return null;
        }
    }
}
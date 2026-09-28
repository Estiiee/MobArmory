package com.estie.mobarmory.packet;

import com.estie.mobarmory.client.gui.screen.LookupScreen;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.util.ArrayList;
import java.util.List;

public class OpenLookupScreenPacket implements IMessage {
    private List<String> fileNames = new ArrayList<>();
    
    public OpenLookupScreenPacket() {}
    
    public OpenLookupScreenPacket(List<String> fileNames) {
        this.fileNames = fileNames;
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        ByteBufUtils.writeVarInt(buf, fileNames.size(), 5);
        for (String name : fileNames) ByteBufUtils.writeUTF8String(buf, name);
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        int size = ByteBufUtils.readVarInt(buf, 5);
        fileNames = new ArrayList<>(size);
        for (int i = 0; i < size; i++) fileNames.add(ByteBufUtils.readUTF8String(buf));
    }
    
    public static class Handler implements IMessageHandler<OpenLookupScreenPacket, IMessage> {
        @SideOnly(Side.CLIENT)
        @Override
        public IMessage onMessage(OpenLookupScreenPacket msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> mc.displayGuiScreen(new LookupScreen(msg.fileNames)));
            return null;
        }
    }
}
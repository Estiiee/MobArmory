package com.estie.mobarmory.packet;

import com.estie.mobarmory.client.gui.screen.EditScreenMain;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import io.netty.buffer.ByteBuf;
import net.minecraft.client.Minecraft;
import net.minecraftforge.fml.common.network.ByteBufUtils;
import net.minecraftforge.fml.common.network.simpleimpl.IMessage;
import net.minecraftforge.fml.common.network.simpleimpl.IMessageHandler;
import net.minecraftforge.fml.common.network.simpleimpl.MessageContext;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import java.nio.charset.StandardCharsets;

public class OpenEditScreenPacket implements IMessage {
    private String fileName;
    private String json;
    
    public OpenEditScreenPacket() {}
    
    public OpenEditScreenPacket(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        this.fileName = entry.fileName;
        this.json = MobEquipmentReloadListener.toJson(entry).toString();
    }
    
    @Override
    public void toBytes(ByteBuf buf) {
        buf.writeBoolean(fileName != null);
        if (fileName != null) ByteBufUtils.writeUTF8String(buf, fileName);
        byte[] bytes = json.getBytes(StandardCharsets.UTF_8);
        buf.writeInt(bytes.length);
        buf.writeBytes(bytes);
    }
    
    @Override
    public void fromBytes(ByteBuf buf) {
        fileName = buf.readBoolean() ? ByteBufUtils.readUTF8String(buf) : null;
        byte[] bytes = new byte[buf.readInt()];
        buf.readBytes(bytes);
        json = new String(bytes, StandardCharsets.UTF_8);
    }
    
    public static class Handler implements IMessageHandler<OpenEditScreenPacket, IMessage> {
        @SideOnly(Side.CLIENT)
        @Override
        public IMessage onMessage(OpenEditScreenPacket msg, MessageContext ctx) {
            Minecraft mc = Minecraft.getMinecraft();
            mc.addScheduledTask(() -> {
                JsonObject obj = new JsonParser().parse(msg.json).getAsJsonObject();
                MobEquipmentReloadListener.MobEquipmentEntry entry = MobEquipmentReloadListener.fromJson(msg.fileName, obj);
                mc.displayGuiScreen(new EditScreenMain(entry));
            });
            return null;
        }
    }
}
package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.handlers.PacketHandler;
import com.estie.mobarmory.packet.LoadMobEquipmentEntryPacket;
import net.minecraft.client.Minecraft;

public class LookupListWidget extends SimpleEntryList<String> {
    
    public LookupListWidget(Minecraft mc, int left, int top, int width, int height, int itemHeight) {
        super(mc, left, top, width, height, itemHeight);
    }
    
    @Override
    protected void renderEntry(String fileName, int index, int x, int y, int rowWidth, boolean hovered) {
        mc.fontRenderer.drawString(fileName, x + 4, y + 4, 0xFFFFFF);
    }
    
    @Override
    protected void onEntryClicked(String fileName, int index) {
        PacketHandler.INSTANCE.sendToServer(new LoadMobEquipmentEntryPacket(fileName));
    }
}
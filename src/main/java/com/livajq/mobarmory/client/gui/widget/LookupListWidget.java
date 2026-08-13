package com.livajq.mobarmory.client.gui.widget;

import com.livajq.mobarmory.handlers.PacketHandler;
import com.livajq.mobarmory.packet.LoadMobEquipmentEntryPacket;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSelectionList;
import net.minecraft.client.gui.narration.NarrationElementOutput;

public class LookupListWidget extends AbstractSelectionList<LookupListWidget.Entry> {
    
    public LookupListWidget(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    @Override
    public void updateNarration(NarrationElementOutput narration) {}
    
    public static class Entry extends AbstractSelectionList.Entry<Entry> {
        
        private final String fileName;
        
        public Entry(String fileName) {
            this.fileName = fileName;
        }
        
        
        @Override
        public void render(GuiGraphics gfx, int index, int y, int x, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            gfx.drawString(Minecraft.getInstance().font, fileName, x + 4, y + 4, 0xFFFFFF);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            PacketHandler.INSTANCE.sendToServer(new LoadMobEquipmentEntryPacket(fileName));
            return true;
        }
    }
}

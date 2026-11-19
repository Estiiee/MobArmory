package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.handlers.PacketHandler;
import com.estie.mobarmory.packet.LoadMobEquipmentEntryPacket;
import net.minecraft.client.gui.GuiScreen;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class LookupScreen extends GuiScreen {
    
    private final List<String> fileNames;
    private final List<String> sorted;
    private int scrollOffset = 0;
    
    private static final int TOP = 32;
    private static final int BOTTOM_MARGIN = 32;
    private static final int ITEM_HEIGHT = 20;
    
    public LookupScreen(List<String> fileNames) {
        this.fileNames = fileNames;
        this.sorted = new ArrayList<>(fileNames);
        this.sorted.sort(String::compareTo);
    }
    
    @Override
    public void initGui() {
        scrollOffset = 0;
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        
        drawCenteredString(
                fontRenderer,
                "MobArmory Lookup",
                width / 2,
                12,
                0xFFFFFF
        );
        
        int bottom = height - BOTTOM_MARGIN;
        int visibleHeight = bottom - TOP;
        int visibleEntries = visibleHeight / ITEM_HEIGHT;
        
        int maxScroll = Math.max(0, sorted.size() - visibleEntries);
        scrollOffset = Math.min(scrollOffset, maxScroll);
        
        for (int i = 0; i < visibleEntries; i++) {
            int index = i + scrollOffset;
            
            if (index >= sorted.size()) break;
            
            int y = TOP + i * ITEM_HEIGHT;
            String fileName = sorted.get(index);
            
            boolean hovered = mouseX >= 0 && mouseX < width && mouseY >= y && mouseY < y + ITEM_HEIGHT;
            
            if (hovered) drawRect(0, y, width, y + ITEM_HEIGHT, 0x55FFFFFF);
            fontRenderer.drawString(fileName, 4, y + 4, 0xFFFFFF);
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        super.mouseClicked(mouseX, mouseY, mouseButton);
        if (mouseButton != 0) return;
        if (mouseY < TOP || mouseY >= height - BOTTOM_MARGIN) return;
        
        int index = (mouseY - TOP) / ITEM_HEIGHT + scrollOffset;
        
        if (index >= 0 && index < sorted.size()) PacketHandler.INSTANCE.sendToServer(new LoadMobEquipmentEntryPacket(sorted.get(index)));
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        
        int wheel = org.lwjgl.input.Mouse.getEventDWheel();
        
        if (wheel == 0)
            return;
        
        int mouseX = org.lwjgl.input.Mouse.getEventX() * width / mc.displayWidth;
        int mouseY = height - org.lwjgl.input.Mouse.getEventY() * height / mc.displayHeight - 1;
        
        if (mouseX < 0 || mouseX >= width
                || mouseY < TOP || mouseY >= height - BOTTOM_MARGIN)
            return;
        
        int visibleEntries = (height - BOTTOM_MARGIN - TOP) / ITEM_HEIGHT;
        int maxScroll = Math.max(0, sorted.size() - visibleEntries);
        
        if (wheel > 0) scrollOffset--;
        else scrollOffset++;
        
        scrollOffset = Math.max(0, Math.min(scrollOffset, maxScroll));
    }
}
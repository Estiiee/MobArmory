package com.estie.mobarmory.client.gui.widget;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Gui;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public abstract class SimpleEntryList<T> extends Gui {
    protected final Minecraft mc;
    protected final int left, top, width, height, itemHeight;
    public final List<T> entries = new ArrayList<>();
    protected int scrollOffset = 0;
    
    public SimpleEntryList(Minecraft mc, int left, int top, int width, int height, int itemHeight) {
        this.mc = mc;
        this.left = left;
        this.top = top;
        this.width = width;
        this.height = height;
        this.itemHeight = itemHeight;
    }
    
    protected abstract void renderEntry(T entry, int index, int x, int y, int rowWidth, boolean hovered);
    protected abstract void onEntryClicked(T entry, int index);
    
    public void render(int mouseX, int mouseY) {
        drawRect(left, top, left + width, top + height, 0x88000000);
        
        int visibleCount = height / itemHeight;
        int maxOffset = Math.max(0, entries.size() - visibleCount);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxOffset);
        
        for (int i = 0; i < visibleCount && (i + scrollOffset) < entries.size(); i++) {
            int index = i + scrollOffset;
            int rowTop = top + i * itemHeight;
            boolean hovered = mouseX >= left && mouseX < left + width && mouseY >= rowTop && mouseY < rowTop + itemHeight;
            
            if (hovered) drawRect(left, rowTop, left + width, rowTop + itemHeight, 0x55FFFFFF);
            
            renderEntry(entries.get(index), index, left, rowTop, width, hovered);
        }
    }
    
    public boolean mouseClicked(int mouseX, int mouseY, int button) {
        if (mouseX < left || mouseX >= left + width || mouseY < top || mouseY >= top + height) return false;
        
        int visibleCount = height / itemHeight;
        int relativeRow = (mouseY - top) / itemHeight;
        int index = relativeRow + scrollOffset;
        
        if (relativeRow < visibleCount && index < entries.size()) {
            onEntryClicked(entries.get(index), index);
            return true;
        }
        return false;
    }
    
    public void mouseScrolled(int amount) {
        scrollOffset -= amount;
    }
}
package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.client.gui.widget.LookupListWidget;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

public class LookupScreen extends Screen {
    
    private final List<String> fileNames;
    private LookupListWidget list;
    
    public LookupScreen(List<String> fileNames) {
        super(Component.literal("MobArmory Lookup"));
        this.fileNames = fileNames;
    }
    
    @Override
    protected void init() {
        list = new LookupListWidget(
                this.minecraft,
                this.width,
                this.height,
                32,
                this.height - 32,
                20
        );
        
        this.addWidget(list);
        
        List<String> sorted = new ArrayList<>(fileNames);
        sorted.sort(String::compareTo);
        
        for (String fileName : sorted) {
            list.children().add(new LookupListWidget.Entry(fileName));
        }
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
    }
    
    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return list.mouseClicked(x, y, button) || super.mouseClicked(x, y, button);
    }
}

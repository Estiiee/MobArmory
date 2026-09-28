package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.LookupListWidget;
import net.minecraft.client.gui.GuiScreen;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class LookupScreen extends GuiScreen {
    private final List<String> fileNames;
    private LookupListWidget list;
    
    public LookupScreen(List<String> fileNames) {
        this.fileNames = fileNames;
    }
    
    @Override
    public void initGui() {
        this.list = new LookupListWidget(
                this.mc,
                20,
                32,
                this.width - 40,
                this.height - 64,
                20
        );
        
        List<String> sorted = new ArrayList<>(this.fileNames);
        Collections.sort(sorted);
        this.list.entries.addAll(sorted);
    }
    
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        this.drawDefaultBackground();
        
        if (this.list != null) {
            this.list.render(mouseX, mouseY);
        }
        
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    
    @Override
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (this.list != null && this.list.mouseClicked(mouseX, mouseY, mouseButton)) {
            return;
        }
        
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        
        if (this.list != null) {
            this.list.mouseScrolled(Mouse.getEventDWheel());
        }
    }
}
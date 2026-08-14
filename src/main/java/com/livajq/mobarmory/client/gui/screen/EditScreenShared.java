package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

public final class EditScreenShared {
    
    public static void renderHeader(GuiGraphics gfx, Font font, MobEquipmentReloadListener.MobEquipmentEntry entry, int screenWidth, int previewSize, String screenName) {
        
        // --- TOP CENTER: FILE NAME ---
        String fileLabel = entry.fileName != null ? entry.fileName : "(unnamed file)";
        gfx.drawCenteredString(font, fileLabel, screenWidth / 2, 15, 0xFFFFFF);
        
        // --- SCREEN NAME UNDER FILE NAME ---
        gfx.drawCenteredString(font, screenName, screenWidth / 2, 30, 0x55FF55);
        
        // --- RIGHT SIDE PREVIEW PANEL ---
        int previewX = screenWidth - previewSize - 20;
        int previewY = 60;
        
        String mobLabel = entry.mob != null ? entry.mob.toString() : "(no mob chosen)";
        gfx.drawCenteredString(font, mobLabel, previewX + previewSize / 2, previewY - 12, 0xAAAAAA);
        
        gfx.fill(previewX, previewY, previewX + previewSize, previewY + previewSize, 0xFF333333);
        gfx.drawCenteredString(font, "Mob Preview",
                previewX + previewSize / 2,
                previewY + previewSize / 2 - 4,
                0xFFFFFF);
    }
    
    public static boolean hasOverride(Float value) {
        return value != null && value != 0.0F;
    }
}

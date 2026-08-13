package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentBuilder;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;

public class EditScreenMain extends Screen {
    
    public final MobEquipmentReloadListener.MobEquipmentEntry entry;
   
    private MobEquipmentBuilder builder;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenMain(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        super(Component.literal(entry.fileName != null ? "Edit: " + entry.fileName : "New Mob Equipment Entry"));
        this.entry = entry;
        updateBuilder();
    }
    
    @Override
    protected void init() {
        
        // --- LEFT SIDE BUTTONS ---
        int leftX = 20;
        int y = 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Mob ID"),
                btn -> {
                    this.minecraft.setScreen(new TextInputScreen(this, "Set Mob ID", entry.mob != null ? entry.mob.toString() : "", value -> {
                        try {
                            entry.mob = new ResourceLocation(value);
                            updateBuilder();
                        } catch (Exception ignored) {}
                    }));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Chance"),
                btn -> {
                    this.minecraft.setScreen(new TextInputScreen(this, "Set Chance (0.0 - 1.0)", "" + entry.chance, value -> {
                        try {
                            float f = Float.parseFloat(value);
                            entry.chance = Mth.clamp(f, 0f, 1f);
                            updateBuilder();
                        } catch (Exception ignored) {}
                    }));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Difficulty Groups"),
                btn -> this.minecraft.setScreen(new EditScreenDifficultyGroups(this))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Save"),
                btn -> {
                    String initial = entry.fileName != null ? entry.fileName : "";
                    this.minecraft.setScreen(new TextInputScreen(
                            this,
                            "Save As...",
                            initial,
                            this::onSaveNameEntered
                    ));
                }
        ).bounds(this.width / 2 - 50, this.height - 40, 100, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, font, entry, width, PREVIEW_SIZE, "Main");
        
        int infoX = this.width - PREVIEW_SIZE - 20 + PREVIEW_SIZE / 2;
        int infoY = 60 + PREVIEW_SIZE;
        
        // --- INFO UNDER PREVIEW ---
        float chance = entry.chance;
        String chanceLabel = "Chance: " + (int)(chance * 100) + "%";
        gfx.drawCenteredString(this.font, chanceLabel,
                infoX,
                infoY + 12,
                0xFFFFFF);
        
        int groupCount = entry.difficultyGroups.size();
        String groupLabel = groupCount + " difficulty group" + (groupCount == 1 ? "" : "s");
        gfx.drawCenteredString(this.font, groupLabel,
                infoX,
                infoY + 26,
                0xAAAAAA);
    }
    
    private void updateBuilder() {
        MobEquipmentBuilder b = MobEquipmentBuilder
                .mob(entry.mob != null ? entry.mob.toString() : "")
                .chance(entry.chance);
        
        for (MobEquipmentReloadListener.DifficultyGroup group : entry.difficultyGroups) {
            MobEquipmentBuilder.DifficultyGroupBuilder dg = b.difficultyGroup();
            
            // matchers
            for (MobEquipmentReloadListener.DifficultyLevel lvl : group.matchers) {
                switch (lvl) {
                    case EASY -> dg.easy();
                    case NORMAL -> dg.normal();
                    case HARD -> dg.hard();
                    case HARDCORE -> dg.hardcore();
                    case GLOBAL -> dg.global();
                }
            }
            
            // chance override
            if (group.chance != null) {
                dg.chance(group.chance);
            }
            
            // biome groups (later)
            // equipment sets (later)
            
            dg.endDifficultyGroup();
        }
        
        this.builder = b;
    }
    
    
    private void onSaveNameEntered(String name) {
        entry.fileName = name;
        this.minecraft.setScreen(new EditScreenMain(entry));
    }
}

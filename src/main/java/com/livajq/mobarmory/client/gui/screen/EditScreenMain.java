package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentBuilder;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

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
                    String initial = this.entry.fileName != null ? this.entry.fileName : "";
                    this.minecraft.setScreen(new TextInputScreen(
                            this,
                            "Save As...",
                            initial,
                            name -> {
                                this.entry.fileName = name;
                                this.saveToFile();
                                this.minecraft.setScreen(null);
                            }
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
    
    public void saveToFile() {
        updateBuilder();
        MobEquipmentBuilder.SaveResult result = builder.createFile(entry.fileName);
        
        if (result.success) minecraft.player.displayClientMessage(Component.literal("Saved mob equipment to: " + result.path), false);
        else minecraft.player.displayClientMessage(Component.literal("Failed to save: " + result.error.getMessage()), false);
    }
    
    
    private void updateBuilder() {
        
        MobEquipmentBuilder b = MobEquipmentBuilder
                .mob(entry.mob != null ? entry.mob.toString() : "")
                .chance(entry.chance);
        
        for (MobEquipmentReloadListener.DifficultyGroup difficultyGroup : entry.difficultyGroups) {
            
            MobEquipmentBuilder.DifficultyGroupBuilder dg = b.difficultyGroup();
            
            //difficulty matchers
            for (MobEquipmentReloadListener.DifficultyLevel lvl : difficultyGroup.matchers) {
                switch (lvl) {
                    case EASY -> dg.easy();
                    case NORMAL -> dg.normal();
                    case HARD -> dg.hard();
                    case HARDCORE -> dg.hardcore();
                    case GLOBAL -> dg.global();
                }
            }
            
            if (EditScreenShared.hasOverride(difficultyGroup.chance)) {
                dg.chance(difficultyGroup.chance);
            }
            
            //biome groups
            for (MobEquipmentReloadListener.BiomeGroup biomeGroup : difficultyGroup.biomeGroups) {
                
                MobEquipmentBuilder.BiomeGroupBuilder bg = dg.biomeGroup();
                
                for (MobEquipmentReloadListener.BiomeMatch match : biomeGroup.matchers) {
                    bg.match(MobEquipmentReloadListener.biomeMatchToString(match));
                }
                
                if (EditScreenShared.hasOverride(biomeGroup.chance)) {
                    bg.chance(biomeGroup.chance);
                }
                
                //equipment sets
                for (MobEquipmentReloadListener.EquipmentSet set : biomeGroup.sets) {
                    
                    MobEquipmentBuilder.EquipmentSetBuilder sb = bg.set();
                    
                    if (set.name != null) sb.name(set.name);
                    sb.weight(set.weight);
                    
                    //slots
                    for (var slotEntry : set.slots.entrySet()) {
                        
                        String slotName = slotEntry.getKey().getName();
                        MobEquipmentBuilder.SlotBuilder slb = sb.slot(slotName);
                        
                        for (MobEquipmentReloadListener.WeightedItem wi : slotEntry.getValue()) {
                            
                            MobEquipmentBuilder.WeightedItemBuilder wib = slb.item(wi.itemId)
                                    .weight(wi.weight);
                            
                            if (wi.enchant instanceof MobEquipmentReloadListener.EnchantData.Random rnd) {
                                wib.randomEnchant().power(rnd.power()).endEnchant();
                            }
                            
                            if (wi.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined pre) {
                                MobEquipmentBuilder.EnchantBuilder eb = wib.predefinedEnchant();
                                for (int i = 0; i < pre.ids().size(); i++) {
                                    eb.addPredefined(pre.ids().get(i), pre.levels().get(i));
                                }
                                eb.endEnchant();
                            }
                            
                            wib.endItem();
                        }
                        
                        slb.endSlot();
                    }
                    
                    sb.endSet();
                }
                
                bg.endBiomeGroup();
            }
            
            dg.endDifficultyGroup();
        }
        
        this.builder = b;
    }
    
    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> {
                        if (confirmed) this.minecraft.setScreen(null);
                        else this.minecraft.setScreen(this);
                    },
                    Component.literal("Exit Editor"),
                    Component.literal("Are you sure you want to exit? Unsaved changes will be lost.")
            ));
            return true;
        }
        
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}

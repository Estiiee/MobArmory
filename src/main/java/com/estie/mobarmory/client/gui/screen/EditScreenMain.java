package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentBuilder;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiYesNo;
import net.minecraft.inventory.EntityEquipmentSlot;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.MathHelper;
import net.minecraft.util.text.TextComponentString;
import org.lwjgl.input.Keyboard;
import org.lwjgl.input.Mouse;

import java.io.IOException;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class EditScreenMain extends GuiScreen {
    public final MobEquipmentReloadListener.MobEquipmentEntry entry;
    
    private MobEquipmentBuilder builder;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenMain(MobEquipmentReloadListener.MobEquipmentEntry entry) {
        this.entry = entry;
        updateBuilder();
    }
    
    @Override
    public void initGui() {
        EditScreenShared.rebuildPreviewEntity(entry, this.mc.world);
        
        // --- LEFT SIDE BUTTONS ---
        int leftX = 20;
        int y = 40;
        
        this.buttonList.add(new GuiButton(
                0,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Mob ID"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                1,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Chance"
        ));
        y += 24;
        
        this.buttonList.add(new GuiButton(
                2,
                leftX,
                y,
                LEFT_PANEL_WIDTH,
                20,
                "Difficulty Groups"
        ));
        
        this.buttonList.add(new GuiButton(
                3,
                this.width / 2 - 50,
                this.height - 40,
                100,
                20,
                "Save"
        ));
    }
    
    @Override
    protected void actionPerformed(GuiButton button) throws IOException {
        switch (button.id) {
            case 0:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Mob ID",
                        entry.mob != null ? entry.mob.toString() : "",
                        value -> {
                            try {
                                entry.mob = new ResourceLocation(value);
                                updateBuilder();
                                EditScreenShared.rebuildPreviewEntity(
                                        entry,
                                        this.mc.world
                                );
                            } catch (Exception ignored) {
                            }
                        },
                        EditScreenShared::mobExists,
                        "Warning: entity type not found",
                        false
                ));
                break;
            
            case 1:
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Set Chance (-1.0 - 1.0)",
                        "" + entry.chance,
                        value -> {
                            try {
                                float f = Float.parseFloat(value);
                                entry.chance = MathHelper.clamp(f, -1f, 1f);
                                updateBuilder();
                            } catch (Exception ignored) {
                            }
                        }
                ));
                break;
            
            case 2:
                this.mc.displayGuiScreen(
                        new EditScreenDifficultyGroups(this)
                );
                break;
            
            case 3:
                String initial = entry.fileName != null
                        ? entry.fileName
                        : "";
                
                this.mc.displayGuiScreen(new TextInputScreen(
                        this,
                        "Save As...",
                        initial,
                        name -> {
                            entry.fileName = name;
                            saveToFile();
                            this.mc.displayGuiScreen(null);
                        }
                ));
                break;
        }
    }
    
    @Override
    public void drawScreen(
            int mouseX,
            int mouseY,
            float partialTicks) {
        
        this.drawDefaultBackground();
        
        super.drawScreen(mouseX, mouseY, partialTicks);
        
        EditScreenShared.renderHeader(
                this.mc,
                entry,
                this.width,
                PREVIEW_SIZE,
                Collections.singletonList(
                        EditScreenShared.current("Main")
                )
        );
        
        int infoX = this.width
                - PREVIEW_SIZE
                - 20
                + PREVIEW_SIZE / 2;
        
        int infoY = 60 + PREVIEW_SIZE;
        
        // --- INFO UNDER PREVIEW ---
        float chance = entry.chance;
        
        String val = entry.chance < 0.0F ? "Not Set" : (int)(chance * 100) + "%";
        String chanceLabel = "Chance: " + val;
        
        this.drawCenteredString(
                this.fontRenderer,
                chanceLabel,
                infoX,
                infoY + 12,
                0xFFFFFF
        );
        
        int groupCount = entry.difficultyGroups.size();
        
        String groupLabel =
                groupCount
                        + " difficulty group"
                        + (groupCount == 1 ? "" : "s");
        
        this.drawCenteredString(
                this.fontRenderer,
                groupLabel,
                infoX,
                infoY + 26,
                0xAAAAAA
        );
    }
    
    public void saveToFile() {
        updateBuilder();
        
        MobEquipmentBuilder.SaveResult result =
                builder.createFile(entry.fileName);
        
        if (result.success) {
            this.mc.player.sendStatusMessage(
                    new TextComponentString(
                            "Saved mob equipment to: " + result.path
                    ),
                    false
            );
        } else {
            this.mc.player.sendStatusMessage(
                    new TextComponentString(
                            "Failed to save: "
                                    + result.error.getMessage()
                    ),
                    false
            );
        }
    }
    
    private void updateBuilder() {
        MobEquipmentBuilder b =
                MobEquipmentBuilder
                        .mob(entry.mob != null
                                ? entry.mob.toString()
                                : "")
                        .chance(entry.chance);
        
        for (MobEquipmentReloadListener.DifficultyGroup difficultyGroup
                : entry.difficultyGroups) {
            
            MobEquipmentBuilder.DifficultyGroupBuilder dg =
                    b.difficultyGroup();
            
            for (MobEquipmentReloadListener.DifficultyLevel lvl
                    : difficultyGroup.matchers) {
                
                switch (lvl) {
                    case EASY:
                        dg.easy();
                        break;
                    
                    case NORMAL:
                        dg.normal();
                        break;
                    
                    case HARD:
                        dg.hard();
                        break;
                    
                    case HARDCORE:
                        dg.hardcore();
                        break;
                    
                    case GLOBAL:
                        dg.global();
                        break;
                }
            }
            
            if (EditScreenShared.hasOverride(
                    difficultyGroup.chance)) {
                dg.chance(difficultyGroup.chance);
            }
            
            for (MobEquipmentReloadListener.BiomeGroup biomeGroup
                    : difficultyGroup.biomeGroups) {
                
                MobEquipmentBuilder.BiomeGroupBuilder bg =
                        dg.biomeGroup();
                
                for (MobEquipmentReloadListener.BiomeMatch match
                        : biomeGroup.matchers) {
                    
                    bg.match(
                            MobEquipmentReloadListener
                                    .biomeMatchToString(match)
                    );
                }
                
                if (EditScreenShared.hasOverride(
                        biomeGroup.chance)) {
                    bg.chance(biomeGroup.chance);
                }
                
                for (MobEquipmentReloadListener.EquipmentSet set
                        : biomeGroup.sets) {
                    
                    MobEquipmentBuilder.EquipmentSetBuilder sb =
                            bg.set();
                    
                    if (set.name != null) {
                        sb.name(set.name);
                    }
                    
                    sb.weight(set.weight);
                    
                    if (set.lootTable != null) {
                        sb.lootTable(set.lootTable);
                    }
                    
                    for (Map.Entry<EntityEquipmentSlot, List<MobEquipmentReloadListener.WeightedItem>> slotEntry : set.slots.entrySet()) {
                        
                        String slotName = slotEntry.getKey().getName();
                        
                        MobEquipmentBuilder.SlotBuilder slb = sb.slot(slotName);
                        
                        for (MobEquipmentReloadListener.WeightedItem wi : slotEntry.getValue()) {
                            
                            MobEquipmentBuilder.WeightedItemBuilder wib = slb.item(wi.itemId).weight(wi.weight);
                            
                            if (wi.nbt != null) {
                                wib.nbt(wi.nbt);
                            }
                            
                            if (wi.enchant instanceof MobEquipmentReloadListener.EnchantData.Random) {
                                
                                MobEquipmentReloadListener.EnchantData.Random rnd =
                                        (MobEquipmentReloadListener.EnchantData.Random) wi.enchant;
                                
                                wib.randomEnchant()
                                        .power(rnd.power())
                                        .endEnchant();
                            }
                            
                            if (wi.enchant
                                    instanceof MobEquipmentReloadListener.EnchantData.Predefined) {
                                
                                MobEquipmentReloadListener.EnchantData.Predefined pre =
                                        (MobEquipmentReloadListener.EnchantData.Predefined) wi.enchant;
                                
                                MobEquipmentBuilder.EnchantBuilder eb = wib.predefinedEnchant();
                                
                                for (int i = 0;
                                     i < pre.ids().size();
                                     i++) {
                                    
                                    eb.addPredefined(
                                            pre.ids().get(i),
                                            pre.levels().get(i)
                                    );
                                }
                                
                                eb.endEnchant();
                            }
                            
                            wib.endItem();
                        }
                        
                        slb.endSlot();
                    }
                    
                    if (set.mobNbt != null) {
                        sb.mobNbt(set.mobNbt);
                    }
                    
                    for (MobEquipmentReloadListener.PotionEffectEntry pe : set.potionEffects) {
                        
                        sb.potionEffect(
                                pe.effectId,
                                pe.durationTicks,
                                pe.amplifier
                        );
                    }
                    
                    boolean timeUnrestricted = set.timeOfDay.minTicks == 0 && set.timeOfDay.maxTicks == 24000;
                    
                    if (!timeUnrestricted) {
                        sb.timeOfDay(
                                set.timeOfDay.minTicks,
                                set.timeOfDay.maxTicks
                        );
                    }
                    
                    boolean yUnrestricted =
                            set.yLevel.comparator
                                    == MobEquipmentReloadListener.YComparator.LT
                                    && set.yLevel.value == 350;
                    
                    if (!yUnrestricted) {
                        sb.yLevel(
                                set.yLevel.comparator.symbol,
                                set.yLevel.value
                        );
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
    public boolean doesGuiPauseGame() {
        return false;
    }
    
    @Override
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (keyCode == Keyboard.KEY_ESCAPE) {
            this.mc.displayGuiScreen(new GuiYesNo(
                    (confirmed, id) -> {
                        if (confirmed) {
                            this.mc.displayGuiScreen(null);
                        } else {
                            this.mc.displayGuiScreen(
                                    EditScreenMain.this
                            );
                        }
                    },
                    "Exit Editor",
                    "Are you sure you want to exit? Unsaved changes will be lost.",
                    0
            ));
            return;
        }
        
        super.keyTyped(typedChar, keyCode);
    }
    
    @Override
    protected void mouseClicked(
            int mouseX,
            int mouseY,
            int mouseButton) throws IOException {
        
        if (EditScreenShared.breadcrumbClicked(
                mouseX,
                mouseY)) {
            return;
        }
        
        if (EditScreenShared.mouseClicked(
                mouseX,
                mouseY,
                mouseButton)) {
            return;
        }
        
        super.mouseClicked(mouseX, mouseY, mouseButton);
    }
    
    @Override
    protected void mouseClickMove(
            int mouseX,
            int mouseY,
            int clickedMouseButton,
            long timeSinceLastClick) {
        
        if (EditScreenShared.mouseDragged(
                mouseX,
                mouseY)) {
            return;
        }
        
        super.mouseClickMove(
                mouseX,
                mouseY,
                clickedMouseButton,
                timeSinceLastClick
        );
    }
    
    @Override
    protected void mouseReleased(
            int mouseX,
            int mouseY,
            int state) {
        
        if (EditScreenShared.mouseReleased()) {
            return;
        }
        
        super.mouseReleased(mouseX, mouseY, state);
    }
    
    @Override
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        
        EditScreenShared.mouseScrolled(
                Mouse.getEventX(),
                Mouse.getEventY(),
                Mouse.getEventDWheel()
        );
    }
}
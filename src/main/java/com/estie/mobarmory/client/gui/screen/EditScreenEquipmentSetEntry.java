package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;

public class EditScreenEquipmentSetEntry extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenEquipmentSetEntry(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                                       MobEquipmentReloadListener.BiomeGroup biomeGroup, MobEquipmentReloadListener.EquipmentSet set) {
        super(Component.literal("Edit Equipment Set"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
    }
    
    @Override
    protected void init() {
        int leftX = 20;
        int rightX = leftX + LEFT_PANEL_WIDTH + 10;
        
        AtomicInteger leftCount = new AtomicInteger(0);
        AtomicInteger rightCount = new AtomicInteger(0);
        
        Consumer<Button.Builder> addLeft = builder -> {
            this.addRenderableWidget(builder
                    .bounds(leftX, 40 + leftCount.getAndIncrement() * 24, LEFT_PANEL_WIDTH, 20)
                    .build());
        };
        
        Consumer<Button.Builder> addRight = builder -> {
            this.addRenderableWidget(builder
                    .bounds(rightX, 40 + rightCount.getAndIncrement() * 24, LEFT_PANEL_WIDTH, 20)
                    .build());
        };
        
        //LEFT COLUMN
        
        addLeft.accept(Button.builder(
                Component.literal("Name"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Set Name (identifier only, optional)",
                        set.name != null ? set.name : "",
                        value -> {
                            set.name = value.isBlank() ? null : value;
                            this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set));
                        }
                ))
        ));
        
        addLeft.accept(Button.builder(
                Component.literal("Weight"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Set Weight (relative pick chance)",
                        "" + set.weight,
                        value -> {
                            try {
                                set.weight = Math.max(1, Integer.parseInt(value));
                            } catch (Exception ignored) {}
                        }
                ))
        ));
        
        addLeft.accept(Button.builder(
                Component.literal("Slots"),
                btn -> this.minecraft.setScreen(new EditScreenSlots(main, difficultyGroup, biomeGroup, set))
        ));
        
        // NEW: Loot Table
        addLeft.accept(Button.builder(
                Component.literal("Loot Table"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Loot Table (e.g. mobarmory:blaze_inferno_loot)",
                        set.lootTable != null ? set.lootTable : "",
                        value -> {
                            set.lootTable = value.isBlank() ? null : value;
                            this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set));
                        }
                ))
        ));
        
        addLeft.accept(Button.builder(
                Component.literal("Mob NBT"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Mob NBT (e.g. CustomName: '{\"text\":\"Boss\"}')",
                        set.mobNbt != null ? set.mobNbt : "",
                        value -> {
                            set.mobNbt = value.isBlank() ? null : value;
                            this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set));
                        },
                        EditScreenShared::nbtValid, "Warning: invalid NBT syntax", true
                ))
        ));
        
        //RIGHT COLUMN
        
        addRight.accept(Button.builder(
                Component.literal("Potion Effects (" + set.potionEffects.size() + ")"),
                btn -> this.minecraft.setScreen(new EditScreenPotionEffects(main, difficultyGroup, biomeGroup, set))
        ));
        
        addRight.accept(Button.builder(
                Component.literal("Time of Day"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Time of Day (e.g. 18:00-6:00, blank = always)",
                        MobEquipmentReloadListener.isTimeUnrestricted(set.timeOfDay)
                                ? ""
                                : MobEquipmentReloadListener.timeRangeToString(set.timeOfDay),
                        value -> {
                            try {
                                set.timeOfDay = value.isBlank()
                                        ? new MobEquipmentReloadListener.TimeRange(0, 24000)
                                        : MobEquipmentReloadListener.parseTimeRange(value);
                            } catch (Exception ignored) {}
                            this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set));
                        },
                        EditScreenShared::timeRangeValid, "Warning: invalid format (use HH:MM-HH:MM)", true
                ))
        ));
        
        addRight.accept(Button.builder(
                Component.literal("Y Level"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Y Level (e.g. <64, >=0, 40; blank = always)",
                        MobEquipmentReloadListener.isYLevelUnrestricted(set.yLevel)
                                ? ""
                                : MobEquipmentReloadListener.yLevelToString(set.yLevel),
                        value -> {
                            try {
                                set.yLevel = value.isBlank()
                                        ? new MobEquipmentReloadListener.YLevelCondition(
                                        MobEquipmentReloadListener.YComparator.LT, 350)
                                        : MobEquipmentReloadListener.parseYLevel(value);
                            } catch (Exception ignored) {}
                            this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set));
                        },
                        EditScreenShared::yLevelValid, "Warning: invalid format (e.g. <64, >=0, 40)", true
                ))
        ));
        
        addRight.accept(Button.builder(
                Component.literal("Delete Set"),
                btn -> {
                    biomeGroup.sets.remove(set);
                    this.minecraft.setScreen(new EditScreenEquipmentSets(main, difficultyGroup, biomeGroup));
                }
        ));
        
        addRight.accept(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenEquipmentSets(main, difficultyGroup, biomeGroup))
        ));
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, font, main.entry, width, PREVIEW_SIZE, List.of(
                EditScreenShared.crumbMain(main.entry),
                EditScreenShared.crumbDifficultyGroup(main, difficultyGroup),
                EditScreenShared.crumbBiomeGroup(main, difficultyGroup, biomeGroup),
                EditScreenShared.current(set.name != null ? set.name : "Equipment Set")));
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int infoY = 60 + PREVIEW_SIZE + 12;
        
        gfx.drawCenteredString(this.font, "Name: " + (set.name != null ? set.name : "(unnamed)"),
                previewX + PREVIEW_SIZE / 2, infoY, 0xFFFFFF);
        
        gfx.drawCenteredString(this.font, "Weight: " + set.weight,
                previewX + PREVIEW_SIZE / 2, infoY + 14, 0xAAAAAA);
        
        int itemCount = set.slots.values().stream().mapToInt(List::size).sum();
        gfx.drawCenteredString(this.font, "Items: " + itemCount + " across " + set.slots.size() + " slot(s)",
                previewX + PREVIEW_SIZE / 2, infoY + 28, 0xAAAAAA);
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
    
    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (EditScreenShared.breadcrumbClicked(mouseX, mouseY)) return true;
        if (EditScreenShared.mouseClicked(mouseX, mouseY, button)) return true;
        return super.mouseClicked(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (EditScreenShared.mouseDragged(mouseX, mouseY, button, dragX, dragY)) return true;
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }
    
    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (EditScreenShared.mouseReleased(mouseX, mouseY, button)) return true;
        return super.mouseReleased(mouseX, mouseY, button);
    }
    
    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (EditScreenShared.mouseScrolled(mouseX, mouseY, delta)) return true;
        return super.mouseScrolled(mouseX, mouseY, delta);
    }
}
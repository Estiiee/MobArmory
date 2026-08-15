package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import org.lwjgl.glfw.GLFW;

import java.util.List;

public class EditScreenDifficultyGroupEntry extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    public EditScreenDifficultyGroupEntry(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup) {
        super(Component.literal("Edit Difficulty Group"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
    }
    
    @Override
    protected void init() {
        int leftX = 20;
        int y = 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Matcher"),
                btn -> this.minecraft.setScreen(new EditScreenDifficultyMatcher(main, difficultyGroup))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Set Chance"),
                btn -> {
                    this.minecraft.setScreen(new TextInputScreen(
                            this,
                            "Set Chance (0.0 - 1.0)",
                            "" + difficultyGroup.chance,
                            value -> {
                                try {
                                    float f = Float.parseFloat(value);
                                    difficultyGroup.chance = Mth.clamp(f, 0f, 1f);
                                } catch (Exception ignored) {}
                            }
                    ));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Biome Groups"),
                btn -> this.minecraft.setScreen(new EditScreenBiomeGroups(main, difficultyGroup))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Delete Group"),
                btn -> {
                    main.entry.difficultyGroups.remove(difficultyGroup);
                    this.minecraft.setScreen(new EditScreenDifficultyGroups(main));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        y += 24;
    
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenDifficultyGroups(main))
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        EditScreenShared.renderHeader(gfx, font, main.entry, width, PREVIEW_SIZE, List.of(
                EditScreenShared.crumbMain(main.entry),
                EditScreenShared.current("Difficulty Group")));
        
        int previewX = this.width - PREVIEW_SIZE - 20;
        int previewY = 60;
        int infoY = previewY + PREVIEW_SIZE + 12;

        StringBuilder stringBuilder = new StringBuilder();
        for (MobEquipmentReloadListener.DifficultyLevel difficultyLevel : difficultyGroup.matchers) {
            stringBuilder.append(difficultyLevel.toString()).append(" ");
        }
        
        String raw = "Matchers: " + stringBuilder;

        List<FormattedCharSequence> lines = this.font.split(Component.literal(raw), PREVIEW_SIZE);
        
        int dy = 0;
        for (FormattedCharSequence line : lines) {
            gfx.drawCenteredString(this.font,
                    line,
                    previewX + PREVIEW_SIZE / 2,
                    infoY + dy,
                    0xFFFFFF);
            dy += this.font.lineHeight;
        }
        
        int usedHeight = dy;
        
        gfx.drawCenteredString(this.font,
                "Chance: " + (int)((EditScreenShared.hasOverride(difficultyGroup.chance) ? difficultyGroup.chance : main.entry.chance) * 100) + "%",
                previewX + PREVIEW_SIZE / 2,
                infoY + usedHeight + 4,
                0xAAAAAA);
        
        gfx.drawCenteredString(this.font,
                "Biome Groups: " + difficultyGroup.biomeGroups.size(),
                previewX + PREVIEW_SIZE / 2,
                infoY + usedHeight + 18,
                0xAAAAAA);
        
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

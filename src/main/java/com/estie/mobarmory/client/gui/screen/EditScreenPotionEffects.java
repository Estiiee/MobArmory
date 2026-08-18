package com.estie.mobarmory.client.gui.screen;

import com.estie.mobarmory.client.gui.widget.PotionEffectList;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class EditScreenPotionEffects extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private static final int LEFT_PANEL_WIDTH = 120;
    private PotionEffectList list;
    
    public EditScreenPotionEffects(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                                   MobEquipmentReloadListener.BiomeGroup biomeGroup, MobEquipmentReloadListener.EquipmentSet set) {
        super(Component.literal("Potion Effects"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
    }
    
    @Override
    protected void init() {
        list = new PotionEffectList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
        for (MobEquipmentReloadListener.PotionEffectEntry pe : set.potionEffects) {
            list.children().add(new PotionEffectList.Entry(pe, main, difficultyGroup, biomeGroup, set));
        }
        this.addWidget(list);
        
        int leftX = 20;
        int rightX = this.width - LEFT_PANEL_WIDTH - 20;
        int y = this.height - 40;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Effect"),
                btn -> {
                    set.potionEffects.add(new MobEquipmentReloadListener.PotionEffectEntry("minecraft:speed", 600, 0));
                    this.minecraft.setScreen(new EditScreenPotionEffects(main, difficultyGroup, biomeGroup, set));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenEquipmentSetEntry(main, difficultyGroup, biomeGroup, set))
        ).bounds(rightX, y, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "Potion Effects", this.width / 2, 15, 0xFFFFFF);
    }
    
    @Override
    public boolean shouldCloseOnEsc() { return false; }
    
    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            this.minecraft.setScreen(new ConfirmScreen(
                    confirmed -> this.minecraft.setScreen(confirmed ? null : this),
                    Component.literal("Exit Editor"),
                    Component.literal("Are you sure you want to exit? Unsaved changes will be lost.")
            ));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }
}
package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.client.gui.widget.BiomeMatcherList;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

public class EditScreenBiomeMatchers extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private BiomeMatcherList list;
    
    private static final int LEFT_PANEL_WIDTH = 200;
    
    public EditScreenBiomeMatchers(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup) {
        super(Component.literal("Edit Matchers"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
    }
    
    @Override
    protected void init() {
        this.list = new BiomeMatcherList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
        
        for (MobEquipmentReloadListener.BiomeMatch match : biomeGroup.matchers) {
            list.children().add(new BiomeMatcherList.Entry(biomeGroup, match,
                    () -> this.minecraft.setScreen(new EditScreenBiomeMatchers(main, difficultyGroup, biomeGroup))));
        }
        
        this.addWidget(list);
        
        int leftX = 20;
        int btnWidth = LEFT_PANEL_WIDTH / 2 - 5;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Matcher"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Add Matcher (global / #tag / minecraft:biome_id)",
                        "",
                        value -> {
                            try {
                                biomeGroup.matchers.add(MobEquipmentReloadListener.parseBiomeMatch(value));
                            } catch (Exception ignored) {}
                            this.minecraft.setScreen(new EditScreenBiomeMatchers(main, difficultyGroup, biomeGroup));
                        }
                ))
        ).bounds(leftX, this.height - 40, btnWidth, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenBiomeGroupEntry(main, difficultyGroup, biomeGroup))
        ).bounds(leftX + btnWidth + 10, this.height - 40, btnWidth, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        
        gfx.drawCenteredString(this.font, "Biome matchers", this.width / 2, 15, 0xFFFFFF);
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
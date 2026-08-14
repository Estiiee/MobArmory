package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.client.gui.widget.EnchantList;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;

public class EditScreenEnchants extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EquipmentSlot slot;
    private final MobEquipmentReloadListener.WeightedItem item;
    
    private static final int LEFT_PANEL_WIDTH = 120;
    private static final int PREVIEW_SIZE = 100;
    
    private EnchantList list;
    
    public EditScreenEnchants(EditScreenMain main,
                              MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                              MobEquipmentReloadListener.BiomeGroup biomeGroup,
                              MobEquipmentReloadListener.EquipmentSet set,
                              EquipmentSlot slot,
                              MobEquipmentReloadListener.WeightedItem item) {
        super(Component.literal("Predefined Enchantments"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
        this.item = item;
    }
    
    @Override
    protected void init() {

        if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined p) {
            this.list = new EnchantList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
            
            for (int i = 0; i < p.ids().size(); i++) {
                list.children().add(new EnchantList.Entry(
                        p.ids().get(i),
                        p.levels().get(i),
                        main, difficultyGroup, biomeGroup, set, slot, item, i
                ));
            }
            
            this.addWidget(list);
        }
        
        int leftX = 20;
        int rightX = this.width - LEFT_PANEL_WIDTH - 20;
        int y = this.height - 40;
    
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Entry"),
                btn -> {
                    MobEquipmentReloadListener.EnchantData.Predefined p;
                    
                    if (item.enchant instanceof MobEquipmentReloadListener.EnchantData.Predefined pre) {
                        p = pre;
                    } else {
                        p = new MobEquipmentReloadListener.EnchantData.Predefined(
                                new ArrayList<>(), new ArrayList<>()
                        );
                        item.enchant = p;
                    }
                    
                    p.ids().add("minecraft:unbreaking");
                    p.levels().add(1);
                    
                    this.minecraft.setScreen(new EditScreenEnchants(
                            main, difficultyGroup, biomeGroup, set, slot, item
                    ));
                }
        ).bounds(leftX, y, LEFT_PANEL_WIDTH, 20).build());
      
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenEnchantEntry(
                        main, difficultyGroup, biomeGroup, set, slot, item
                ))
        ).bounds(rightX, y, LEFT_PANEL_WIDTH, 20).build());
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        if (list != null) list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, "Enchantments", this.width / 2, 15, 0xFFFFFF);
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
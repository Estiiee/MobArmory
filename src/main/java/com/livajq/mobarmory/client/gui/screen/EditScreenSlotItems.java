package com.livajq.mobarmory.client.gui.screen;

import com.livajq.mobarmory.client.gui.widget.WeightedItemList;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import org.lwjgl.glfw.GLFW;

import java.util.ArrayList;
import java.util.List;

public class EditScreenSlotItems extends Screen {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    private final EquipmentSlot slot;
    private WeightedItemList list;
    
    private static final int LEFT_PANEL_WIDTH = 200;
    
    public EditScreenSlotItems(EditScreenMain main, MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
                               MobEquipmentReloadListener.BiomeGroup biomeGroup, MobEquipmentReloadListener.EquipmentSet set, EquipmentSlot slot) {
        super(Component.literal(EditScreenSlots.slotLabel(slot) + " items"));
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
        this.slot = slot;
    }
    
    @Override
    protected void init() {
        this.list = new WeightedItemList(this.minecraft, this.width, this.height, 40, this.height - 60, 20);
        
        for (MobEquipmentReloadListener.WeightedItem item : set.slots.getOrDefault(slot, List.of())) {
            list.children().add(new WeightedItemList.Entry(item, main, difficultyGroup, biomeGroup, set, slot));
        }
        
        this.addWidget(list);
        
        int leftX = 20;
        int rightX = this.width - LEFT_PANEL_WIDTH - 20;
        int btnWidth = LEFT_PANEL_WIDTH;
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Add Item"),
                btn -> this.minecraft.setScreen(new TextInputScreen(
                        this,
                        "Add Item (e.g. minecraft:iron_sword)",
                        "",
                        value -> {
                            String raw = value.trim();
                            if (!raw.contains(":")) raw = "minecraft:" + raw;
                            
                            MobEquipmentReloadListener.WeightedItem newItem =
                                    new MobEquipmentReloadListener.WeightedItem(raw, 1, null);
                            
                            set.slots.computeIfAbsent(slot, s -> new ArrayList<>()).add(newItem);
                            
                            this.minecraft.setScreen(new EditScreenSlotItems(
                                    main, difficultyGroup, biomeGroup, set, slot
                            ));
                        }
                ))
        ).bounds(leftX, this.height - 40, btnWidth, 20).build());
        
        this.addRenderableWidget(Button.builder(
                Component.literal("Back"),
                btn -> this.minecraft.setScreen(new EditScreenSlots(main, difficultyGroup, biomeGroup, set))
        ).bounds(rightX, this.height - 40, btnWidth, 20).build());
        
    }
    
    @Override
    public void render(GuiGraphics gfx, int mouseX, int mouseY, float partialTick) {
        this.renderBackground(gfx);
        list.render(gfx, mouseX, mouseY, partialTick);
        super.render(gfx, mouseX, mouseY, partialTick);
        gfx.drawCenteredString(this.font, EditScreenSlots.slotLabel(slot) + " items", this.width / 2, 15, 0xFFFFFF);
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
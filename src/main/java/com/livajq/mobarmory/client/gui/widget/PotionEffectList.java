package com.livajq.mobarmory.client.gui.widget;

import com.livajq.mobarmory.client.gui.screen.EditScreenMain;
import com.livajq.mobarmory.client.gui.screen.EditScreenPotionEffects;
import com.livajq.mobarmory.client.gui.screen.PotionEffectTextInputScreen;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ObjectSelectionList;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public class PotionEffectList extends ObjectSelectionList<PotionEffectList.Entry> {
    
    public PotionEffectList(Minecraft mc, int width, int height, int top, int bottom, int itemHeight) {
        super(mc, width, height, top, bottom, itemHeight);
    }
    
    public static class Entry extends ObjectSelectionList.Entry<Entry> {
        
        private final MobEquipmentReloadListener.PotionEffectEntry effect;
        private final EditScreenMain main;
        private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
        private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
        private final MobEquipmentReloadListener.EquipmentSet set;
        private final String label;
        
        public Entry(MobEquipmentReloadListener.PotionEffectEntry effect, EditScreenMain main,
                     MobEquipmentReloadListener.DifficultyGroup difficultyGroup, MobEquipmentReloadListener.BiomeGroup biomeGroup,
                     MobEquipmentReloadListener.EquipmentSet set) {
            this.effect = effect;
            this.main = main;
            this.difficultyGroup = difficultyGroup;
            this.biomeGroup = biomeGroup;
            this.set = set;
            this.label = effect.effectId + " (" + effect.durationTicks + "t, amp " + effect.amplifier + ")";
        }
        
        @Override
        public void render(GuiGraphics gfx, int index, int top, int left, int width, int height, int mouseX, int mouseY, boolean hovered, float partialTick) {
            int color = hovered ? 0xFFFFA0 : 0xFFFFFF;
            gfx.drawString(Minecraft.getInstance().font, label, left + 4, top + 6, color);
        }
        
        @Override
        public boolean mouseClicked(double mouseX, double mouseY, int button) {
            Screen parent = new EditScreenPotionEffects(main, difficultyGroup, biomeGroup, set);
            
            Minecraft.getInstance().setScreen(new PotionEffectTextInputScreen(
                    parent, "Edit Potion Effect",
                    effect.effectId, String.valueOf(effect.durationTicks), String.valueOf(effect.amplifier),
                    (newId, newDuration, newAmplifier) -> {
                        try {
                            effect.effectId = newId;
                            effect.durationTicks = Math.max(1, Integer.parseInt(newDuration));
                            effect.amplifier = Math.max(0, Integer.parseInt(newAmplifier));
                        } catch (Exception ignored) {}
                    },
                    () -> set.potionEffects.remove(effect)
            ));
            return true;
        }
        
        @Override
        public Component getNarration() {
            return Component.literal(label);
        }
    }
}
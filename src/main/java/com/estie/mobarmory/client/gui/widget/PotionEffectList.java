package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.client.gui.screen.EditScreenMain;
import com.estie.mobarmory.client.gui.screen.EditScreenPotionEffects;
import com.estie.mobarmory.client.gui.screen.PotionEffectTextInputScreen;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;

public class PotionEffectList extends SimpleEntryList<MobEquipmentReloadListener.PotionEffectEntry> {
    
    private final EditScreenMain main;
    private final MobEquipmentReloadListener.DifficultyGroup difficultyGroup;
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final MobEquipmentReloadListener.EquipmentSet set;
    
    public PotionEffectList(
            Minecraft mc,
            int left,
            int top,
            int width,
            int height,
            int itemHeight,
            EditScreenMain main,
            MobEquipmentReloadListener.DifficultyGroup difficultyGroup,
            MobEquipmentReloadListener.BiomeGroup biomeGroup,
            MobEquipmentReloadListener.EquipmentSet set) {
        
        super(mc, left, top, width, height, itemHeight);
        
        this.main = main;
        this.difficultyGroup = difficultyGroup;
        this.biomeGroup = biomeGroup;
        this.set = set;
    }
    
    @Override
    protected void renderEntry(
            MobEquipmentReloadListener.PotionEffectEntry effect,
            int index,
            int x,
            int y,
            int rowWidth,
            boolean hovered) {
        
        String label = effect.effectId
                + " (" + effect.durationTicks
                + "t, amp " + effect.amplifier + ")";
        
        int color = hovered ? 0xFFFFA0 : 0xFFFFFF;
        
        this.mc.fontRenderer.drawString(
                label,
                x + 4,
                y + 6,
                color
        );
    }
    
    @Override
    protected void onEntryClicked(
            MobEquipmentReloadListener.PotionEffectEntry effect,
            int index) {
        
        final GuiScreen parent = new EditScreenPotionEffects(
                main,
                difficultyGroup,
                biomeGroup,
                set
        );
        
        this.mc.displayGuiScreen(new PotionEffectTextInputScreen(
                parent,
                "Edit Potion Effect",
                effect.effectId,
                String.valueOf(effect.durationTicks),
                String.valueOf(effect.amplifier),
                (newId, newDuration, newAmplifier) -> {
                    try {
                        effect.effectId = newId;
                        effect.durationTicks = Math.max(
                                1,
                                Integer.parseInt(newDuration)
                        );
                        effect.amplifier = Math.max(
                                0,
                                Integer.parseInt(newAmplifier)
                        );
                    } catch (Exception ignored) {
                    }
                },
                () -> set.potionEffects.remove(effect)
        ));
    }
}
package com.estie.mobarmory.client.gui.widget;

import com.estie.mobarmory.data.MobEquipmentReloadListener;
import net.minecraft.client.Minecraft;

public class BiomeMatcherList extends SimpleEntryList<MobEquipmentReloadListener.BiomeMatch> {
    
    private final MobEquipmentReloadListener.BiomeGroup biomeGroup;
    private final Runnable onChanged;
    
    public BiomeMatcherList(Minecraft mc, int left, int top, int width, int height, int itemHeight,
                            MobEquipmentReloadListener.BiomeGroup biomeGroup, Runnable onChanged) {
        super(mc, left, top, width, height, itemHeight);
        this.biomeGroup = biomeGroup;
        this.onChanged = onChanged;
    }
    
    @Override
    protected void renderEntry(MobEquipmentReloadListener.BiomeMatch match, int index, int x, int y, int rowWidth, boolean hovered) {
        String label = MobEquipmentReloadListener.biomeMatchToString(match);
        mc.fontRenderer.drawString(label, x + 4, y + 6, 0xFFFFFF);
        mc.fontRenderer.drawString("[x]", x + rowWidth - 20, y + 6, 0xFF5555);
    }
    
    @Override
    protected void onEntryClicked(MobEquipmentReloadListener.BiomeMatch match, int index) {
        biomeGroup.matchers.remove(match);
        if (biomeGroup.matchers.isEmpty()) biomeGroup.matchers.add(new MobEquipmentReloadListener.BiomeMatch.Global());
        onChanged.run();
    }
}
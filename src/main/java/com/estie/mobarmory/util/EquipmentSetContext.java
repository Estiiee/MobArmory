package com.estie.mobarmory.util;

import com.estie.mobarmory.data.MobEquipmentReloadListener;

public class EquipmentSetContext {
    public MobEquipmentReloadListener.EquipmentSet equipmentSet;
    public float chance;
    
    public EquipmentSetContext(MobEquipmentReloadListener.EquipmentSet equipmentSet, float chance) {
        this.equipmentSet = equipmentSet;
        this.chance = chance;
    }
}
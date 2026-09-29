package com.estie.mobarmory.handlers;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.util.MobEquipmentSpawnUtil;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.stats.Stats;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.storage.loot.LootParams;
import net.minecraft.world.level.storage.loot.LootTable;
import net.minecraft.world.level.storage.loot.parameters.LootContextParamSets;
import net.minecraft.world.level.storage.loot.parameters.LootContextParams;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public class EquipmentHandler {
    
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (!Config.enabled) return;
        if (!(event.getEntity() instanceof LivingEntity mob)) return;
        if (event.getLevel().isClientSide()) return;
        if (event.loadedFromDisk()) return;
        
        //players seem to always return false on loadedFromDisk() so separate handling
        if (mob instanceof ServerPlayer player) {
            if (player.getPersistentData().getBoolean("MobArmory_SpawnFlag")) return;
            int deaths = player.getStats().getValue(Stats.CUSTOM.get(Stats.DEATHS));
            if (deaths > 0 && Config.playerSingleUse) return;
            
            player.getPersistentData().putBoolean("MobArmory_SpawnFlag", true);
            MobEquipmentSpawnUtil.tryAddRandomMatchingSet(player, player.blockPosition());
            return;
        }
        
        MobEquipmentSpawnUtil.tryAddRandomMatchingSet(mob, mob.blockPosition());
    }
    
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        if (!(event.getEntity() instanceof Mob mob)) return;
        
        CompoundTag tag = mob.getPersistentData();
        if (!tag.contains("MobArmoryLootTable")) return;
        
        ResourceLocation table = new ResourceLocation(tag.getString("MobArmoryLootTable"));
        LootTable loot = event.getEntity().level().getServer().getLootData().getLootTable(table);
        
        LootParams params = new LootParams.Builder((ServerLevel) event.getEntity().level())
                .withParameter(LootContextParams.THIS_ENTITY, mob)
                .withParameter(LootContextParams.ORIGIN, mob.position())
                .withParameter(LootContextParams.DAMAGE_SOURCE, event.getSource())
                .create(LootContextParamSets.ENTITY);
        
        for (ItemStack stack : loot.getRandomItems(params)) {
            event.getDrops().add(
                    new ItemEntity(
                            mob.level(),
                            mob.getX(),
                            mob.getY(),
                            mob.getZ(),
                            stack
                    )
            );
        }
    }
}
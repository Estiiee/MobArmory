package com.estie.mobarmory.handlers;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.util.MobEquipmentSpawnUtil;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.stats.StatList;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.WorldServer;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.event.entity.EntityJoinWorldEvent;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.common.eventhandler.SubscribeEvent;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public class EquipmentHandler {
    
    @SubscribeEvent
    public static void onEntityJoinWorld(EntityJoinWorldEvent event) {
        if (!Config.enabled) return;
        if (!(event.getEntity() instanceof EntityLivingBase)) return;
        if (event.getWorld().isRemote) return;
        
        EntityLivingBase mob = (EntityLivingBase) event.getEntity();
        
        if (mob.getEntityData().getBoolean("MobArmory_SpawnFlag")) return;
        mob.getEntityData().setBoolean("MobArmory_SpawnFlag", true);
        
        if (mob instanceof EntityPlayerMP) {
            EntityPlayerMP player = (EntityPlayerMP) mob;
            
            int deaths = player.getStatFile().readStat(StatList.DEATHS);
            if (deaths > 0 && Config.playerSingleUse) return;
        }
        
        MobEquipmentSpawnUtil.tryAddRandomMatchingSet(mob, mob.getPosition());
    }
    
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        EntityLivingBase mob = event.getEntityLiving();
        if (!(mob.world instanceof WorldServer)) return;
        if (!mob.getEntityData().hasKey("MobArmoryLootTable")) return;
        
        String lootTableId = mob.getEntityData().getString("MobArmoryLootTable");
        
        ResourceLocation tableId = new ResourceLocation(lootTableId);
        
        WorldServer world = (WorldServer) mob.world;
        
        LootTable loot = world.getLootTableManager().getLootTableFromLocation(tableId);
        
        LootContext context = new LootContext(0.0F, world, world.getLootTableManager(), mob, null, event.getSource());
        
        for (ItemStack stack : loot.generateLootForPools(world.rand, context)) {
            event.getDrops().add(new EntityItem(world, mob.posX, mob.posY, mob.posZ, stack));
        }
    }
}
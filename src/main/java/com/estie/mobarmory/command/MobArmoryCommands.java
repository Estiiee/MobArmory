package com.estie.mobarmory.command;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.MobArmory;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import com.estie.mobarmory.packet.OpenEditScreenPacket;
import com.estie.mobarmory.packet.OpenLookupScreenPacket;
import com.estie.mobarmory.util.EquipmentSetContext;
import com.estie.mobarmory.util.MobEquipmentSpawnUtil;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.arguments.ResourceLocationArgument;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Mod.EventBusSubscriber(modid = MobArmory.MODID)
public class MobArmoryCommands {
    
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        register(event.getDispatcher());
    }
    
    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        
        dispatcher.register(
                Commands.literal("mobarmory")
                        .then(Commands.literal("createnew")
                                .executes(ctx -> createnew(ctx.getSource()))
                        )
                        
                        .then(Commands.literal("lookup")
                                .requires(src -> src.getServer().isSingleplayer() || Config.clientAccessible)
                                .executes(ctx -> lookup(ctx.getSource()))
                        )
                        
                        .then(Commands.literal("spawnFromLookup")
                                .then(Commands.argument("fileName", StringArgumentType.string())
                                        .then(Commands.argument("mobId", ResourceLocationArgument.id())
                                                .executes(ctx -> spawnFromLookup(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "fileName"),
                                                        ResourceLocationArgument.getId(ctx, "mobId")
                                                ))
                                        )
                                )
                        )
                        .then(Commands.literal("spawnFromSetName")
                                .then(Commands.argument("setName", StringArgumentType.string())
                                        .then(Commands.argument("mobId", ResourceLocationArgument.id())
                                                .executes(ctx -> spawnFromSetName(
                                                        ctx.getSource(),
                                                        StringArgumentType.getString(ctx, "setName"),
                                                        ResourceLocationArgument.getId(ctx, "mobId")
                                                ))
                                        )
                                )
                        )
        );
    }
    
    private static int createnew(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be used by a player"));
            return 0;
        }
        
        MobEquipmentReloadListener.MobEquipmentEntry blank =
                new MobEquipmentReloadListener.MobEquipmentEntry(null, null, -1.0f, new ArrayList<>());
        
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenEditScreenPacket(blank));
        
        src.sendSuccess(() -> Component.literal("Created new entry"), false);
        return 1;
    }
    
    private static int lookup(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be used by a player"));
            return 0;
        }
        
        List<String> fileNames = MobEquipmentReloadListener.LOOKUP_FILES.stream().map(e -> e.fileName).toList();
        
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenLookupScreenPacket(fileNames));
        
        src.sendSuccess(() -> Component.literal("Opening lookup window"), false);
        return 1;
    }
    
    private static int spawnFromLookup(CommandSourceStack src, String fileName, ResourceLocation mobId) {
        ServerPlayer player = src.getPlayer();
        
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be used by a player"));
            return 0;
        }
        
        MobEquipmentReloadListener.MobEquipmentEntry entry =
                MobEquipmentReloadListener.LOOKUP_FILES.stream()
                        .filter(e -> fileName.equals(e.fileName))
                        .findFirst()
                        .orElse(null);
        
        if (entry == null) {
            src.sendFailure(Component.literal("No lookup file found: " + fileName));
            return 0;
        }
        
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
        
        if (entityType == null) {
            src.sendFailure(Component.literal("Unknown mob ID: " + mobId));
            return 0;
        }
        
        ServerLevel level = (ServerLevel) player.level();
        RandomSource random = player.getRandom();
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) entityType;
        
        Optional<MobEquipmentReloadListener.EquipmentSet> set = MobEquipmentSpawnUtil.pickRandomSetFromFile(fileName, random);
        set.ifPresent(equipmentSet -> MobEquipmentSpawnUtil.spawnMobWithSet(level, livingType, equipmentSet, player.getPosition(1)));
        
        return 1;
    }
    
    private static int spawnFromSetName(CommandSourceStack src, String setName, ResourceLocation mobId) {
        ServerPlayer player = src.getPlayer();
        
        if (player == null) {
            src.sendFailure(Component.literal("This command can only be used by a player"));
            return 0;
        }
        
        EntityType<?> entityType = ForgeRegistries.ENTITY_TYPES.getValue(mobId);
        
        if (entityType == null) {
            src.sendFailure(Component.literal("Unknown mob ID: " + mobId));
            return 0;
        }
        
        @SuppressWarnings("unchecked")
        EntityType<? extends LivingEntity> livingType = (EntityType<? extends LivingEntity>) entityType;
        
        Optional<EquipmentSetContext> context = MobEquipmentSpawnUtil.getSetByName(setName);
        
        if (context.isEmpty()) {
            src.sendFailure(Component.literal("No equipment set found: " + setName));
            return 0;
        }
        
        ServerLevel level = (ServerLevel) player.level();
        MobEquipmentSpawnUtil.spawnMobWithSet(level, livingType, context.get().equipmentSet(), player.getPosition(1));
        
        return 1;
    }
}

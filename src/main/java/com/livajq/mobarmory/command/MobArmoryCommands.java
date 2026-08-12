package com.livajq.mobarmory.command;

import com.livajq.mobarmory.Config;
import com.livajq.mobarmory.MobArmory;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import com.livajq.mobarmory.handlers.PacketHandler;
import com.livajq.mobarmory.packet.OpenLookupScreenPacket;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

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
                                .then(Commands.argument("filename", StringArgumentType.string())
                                        .executes(ctx -> {
                                            String fileName = StringArgumentType.getString(ctx, "filename");
                                            return createnew(ctx.getSource(), fileName);
                                        })
                                )
                        )
                        
                        .then(Commands.literal("lookup")
                                .requires(src -> src.getServer().isSingleplayer() || Config.clientAccessible)
                                .executes(ctx -> lookup(ctx.getSource()))
                        )
        );
    }
    
    private static int createnew(CommandSourceStack src, String fileName) {
        
        MobEquipmentReloadListener.MobEquipmentEntry entry =
                new MobEquipmentReloadListener.MobEquipmentEntry(
                        fileName,
                        null,
                        1.0f,
                        new ArrayList<>()
                );
        
        MobEquipmentReloadListener.LOOKUP_FILES.add(entry);
        
        src.sendSuccess(() -> Component.literal("Created new entry: " + fileName), false);
        
        return 1;
    }
    
    private static int lookup(CommandSourceStack src) {
        ServerPlayer player = src.getPlayer();
        List<String> fileNames = MobEquipmentReloadListener.LOOKUP_FILES.stream().map(e -> e.fileName).toList();
        
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenLookupScreenPacket(fileNames));
        
        src.sendSuccess(() -> Component.literal("Opening lookup window"), false);
        return 1;
    }
}

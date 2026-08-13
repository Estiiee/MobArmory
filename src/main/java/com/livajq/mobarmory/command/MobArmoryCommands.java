package com.livajq.mobarmory.command;

import com.livajq.mobarmory.Config;
import com.livajq.mobarmory.MobArmory;
import com.livajq.mobarmory.data.MobEquipmentReloadListener;
import com.livajq.mobarmory.handlers.PacketHandler;
import com.livajq.mobarmory.packet.OpenEditScreenPacket;
import com.livajq.mobarmory.packet.OpenLookupScreenPacket;
import com.mojang.brigadier.CommandDispatcher;
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
                                .executes(ctx -> createnew(ctx.getSource()))
                        )
                        
                        .then(Commands.literal("lookup")
                                .requires(src -> src.getServer().isSingleplayer() || Config.clientAccessible)
                                .executes(ctx -> lookup(ctx.getSource()))
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
                new MobEquipmentReloadListener.MobEquipmentEntry(null, null, 1.0f, new ArrayList<>());
        
        PacketHandler.INSTANCE.send(PacketDistributor.PLAYER.with(() -> player), new OpenEditScreenPacket(blank));
        
        src.sendSuccess(() -> Component.literal("Created new entry"), false);
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

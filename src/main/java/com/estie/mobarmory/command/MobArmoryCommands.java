package com.estie.mobarmory.command;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import com.estie.mobarmory.packet.OpenEditScreenPacket;
import com.estie.mobarmory.packet.OpenLookupScreenPacket;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.text.TextComponentString;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class MobArmoryCommands extends CommandBase {
    
    @Override
    public String getName() {
        return "mobarmory";
    }
    
    @Override
    public String getUsage(ICommandSender sender) {
        return "/mobarmory <createnew|lookup>";
    }
    
    @Override
    public void execute(MinecraftServer server, ICommandSender sender, String[] args) throws CommandException {
        if (args.length == 0) {
            throw new WrongUsageException(getUsage(sender));
        }
        
        if ("createnew".equals(args[0])) {
            createNew(sender);
        } else if ("lookup".equals(args[0])) {
            lookup(sender);
        } else {
            throw new WrongUsageException(getUsage(sender));
        }
    }
    
    private void createNew(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        
        MobEquipmentReloadListener.MobEquipmentEntry blank =
                new MobEquipmentReloadListener.MobEquipmentEntry(
                        null,
                        null,
                        -1.0f,
                        new ArrayList<>()
                );
        
        PacketHandler.INSTANCE.sendTo(
                new OpenEditScreenPacket(blank),
                player
        );
        
        sender.sendMessage(new TextComponentString("Created new entry"));
    }
    
    private void lookup(ICommandSender sender) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        
        if (!Config.clientAccessible && !sender.canUseCommand(2, getName())) {
            throw new CommandException("You must be an operator to use this command.");
        }
        
        List<String> fileNames = MobEquipmentReloadListener.LOOKUP_FILES
                .stream()
                .map(e -> e.fileName)
                .collect(Collectors.toList());
        
        PacketHandler.INSTANCE.sendTo(
                new OpenLookupScreenPacket(fileNames),
                player
        );
        
        sender.sendMessage(new TextComponentString("Opening lookup window"));
    }
    
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
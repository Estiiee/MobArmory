package com.estie.mobarmory.command;

import com.estie.mobarmory.Config;
import com.estie.mobarmory.data.MobEquipmentReloadListener;
import com.estie.mobarmory.handlers.PacketHandler;
import com.estie.mobarmory.packet.OpenEditScreenPacket;
import com.estie.mobarmory.packet.OpenLookupScreenPacket;
import com.estie.mobarmory.util.EquipmentSetContext;
import com.estie.mobarmory.util.MobEquipmentSpawnUtil;
import net.minecraft.command.CommandBase;
import net.minecraft.command.CommandException;
import net.minecraft.command.ICommandSender;
import net.minecraft.command.WrongUsageException;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.text.TextComponentString;
import net.minecraft.world.WorldServer;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class MobArmoryCommands extends CommandBase {
    
    @Override
    public String getName() {
        return "mobarmory";
    }
    
    @Override
    public String getUsage(ICommandSender sender) {
        return "/mobarmory <createnew|lookup|spawnFromLookup|spawnFromSetName>";
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
        } else if ("spawnFromLookup".equals(args[0])) {
            if (args.length < 3) {
                throw new WrongUsageException("/mobarmory spawnFromLookup <fileName> <mobId>");
            }
            
            spawnFromLookup(sender, args[1], args[2]);
        } else if ("spawnFromSetName".equals(args[0])) {
            if (args.length < 3) {
                throw new WrongUsageException("/mobarmory spawnFromSetName <setName> <mobId>");
            }
            
            spawnFromSetName(sender, args[1], args[2]);
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
    
    private void spawnFromLookup(ICommandSender sender, String fileName, String mobId) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        
        MobEquipmentReloadListener.MobEquipmentEntry entry =
                MobEquipmentReloadListener.LOOKUP_FILES.stream()
                        .filter(e -> fileName.equals(e.fileName))
                        .findFirst()
                        .orElse(null);
        
        if (entry == null) {
            throw new CommandException("No lookup file found: " + fileName);
        }
        
        ResourceLocation mobIdRL = new ResourceLocation(mobId);
        
        EntityLivingBase entity = MobEquipmentSpawnUtil.spawnMobWithSet(
                (WorldServer) player.world,
                mobIdRL,
                MobEquipmentSpawnUtil.pickRandomSetFromFile(
                        fileName,
                        player.getRNG()
                ).orElse(null),
                player.getPositionVector()
        );
        
        if (entity == null) {
            throw new CommandException("Failed to spawn " + mobId);
        }
    }
    
    private void spawnFromSetName(ICommandSender sender, String setName, String mobId) throws CommandException {
        EntityPlayerMP player = getCommandSenderAsPlayer(sender);
        
        ResourceLocation mobIdRL = new ResourceLocation(mobId);
        
        Optional<EquipmentSetContext> context = MobEquipmentSpawnUtil.getSetByName(setName);
        
        if (!context.isPresent()) {
            throw new CommandException("No equipment set found: " + setName);
        }
        
        EntityLivingBase entity = MobEquipmentSpawnUtil.spawnMobWithSet(
                (WorldServer) player.world,
                mobIdRL,
                context.get().equipmentSet,
                player.getPositionVector()
        );
        
        if (entity == null) {
            throw new CommandException("Failed to spawn " + mobId);
        }
    }
    
    @Override
    public List<String> getTabCompletions(MinecraftServer server, ICommandSender sender, String[] args, @Nullable BlockPos targetPos) {
        if (args.length == 1) {
            return getListOfStringsMatchingLastWord(
                    args,
                    "createnew",
                    "lookup",
                    "spawnFromLookup",
                    "spawnFromSetName"
            );
        }
        
        return Collections.emptyList();
    }
    
    @Override
    public int getRequiredPermissionLevel() {
        return 0;
    }
}
package com.lycoris.loconautics.registry;

import com.lycoris.loconautics.config.command.ConfigCommand;
import com.lycoris.loconautics.content.bearingaxle.AxleDebugCommand;
import com.lycoris.loconautics.content.cerisene.CeriseneDebugCommand;
import com.lycoris.loconautics.content.hose.rope.HoseDebugCommand;
import com.lycoris.loconautics.content.knuckle.KnuckleTuning;
import com.lycoris.loconautics.content.trainlookup.commands.FindCommand;
import com.lycoris.loconautics.content.trainlookup.commands.HistoryCommand;
import com.lycoris.loconautics.content.trainlookup.commands.RemoveCommand;
import com.lycoris.loconautics.content.trainlookup.commands.RetrieveCommand;
import com.lycoris.loconautics.content.trainlookup.commands.TrainArguments;
import com.lycoris.loconautics.foundation.debug.SteerTrace;
import com.lycoris.loconautics.trainphysics.debug.GhostConsistTrace;
import com.lycoris.loconautics.trainphysics.debug.GhostGapTrace;
import com.lycoris.loconautics.trainphysics.debug.GraphDump;
import com.lycoris.loconautics.trainphysics.debug.IsTrainCommand;
import com.lycoris.loconautics.trainphysics.debug.LengthTrace;
import com.lycoris.loconautics.trainphysics.debug.PoseTweakCommand;
import com.lycoris.loconautics.trainphysics.debug.PurgeTrainCommand;
import com.lycoris.loconautics.trainphysics.debug.RailDebug;
import com.lycoris.loconautics.trainphysics.debug.SpawnCartCommand;
import com.lycoris.loconautics.trainphysics.debug.TeleportDebug;
import com.lycoris.loconautics.trainphysics.debug.force.ForceDebugCommand;
import com.lycoris.loconautics.trainphysics.portal.PortalTrace;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;

@EventBusSubscriber(modid = "loconautics")
public final class LoconauticsCommands {
    private static final int LOOKUP_PERMISSION = 4;
    private static final int DEBUG_PERMISSION = 2;

    private LoconauticsCommands() {
    }

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        LiteralArgumentBuilder<CommandSourceStack> debug = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("dbgloco")
            .requires(source -> source.hasPermission(2));
        RailDebug.addBranches(debug);
        SpawnCartCommand.addBranch(debug);
        TeleportDebug.addBranch(debug);
        KnuckleTuning.addBranch(debug);
        SteerTrace.addBranch(debug);
        PortalTrace.addBranch(debug);
        AxleDebugCommand.addBranch(debug);
        CeriseneDebugCommand.addBranch(debug);
        GraphDump.addBranch(debug);
        PurgeTrainCommand.addBranch(debug);
        IsTrainCommand.addBranch(debug);
        PoseTweakCommand.addBranch(debug);
        LengthTrace.addBranch(debug);
        GhostGapTrace.addBranch(debug);
        GhostConsistTrace.addBranch(debug);
        ForceDebugCommand.addBranch(debug);
        HoseDebugCommand.addBranch(debug);
        ConfigCommand.addBranch(debug);
        ConfigCommand.addResetBranch(debug);
        event.getDispatcher().register(debug);
        LiteralArgumentBuilder<CommandSourceStack> root = (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("loconautics")
            .requires(source -> source.hasPermission(2));
        root.then(lookup(RetrieveCommand.build()));
        root.then(lookup(FindCommand.build()));
        root.then(lookup(HistoryCommand.build()));
        root.then(lookup(RemoveCommand.build()));
        root.then(
            lookup(
                (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal("teleport")
                    .then(Commands.argument("train", StringArgumentType.greedyString()).suggests(TrainArguments::suggestTrain).executes(FindCommand::teleport))
            )
        );
        event.getDispatcher().register(root);
    }

    private static LiteralArgumentBuilder<CommandSourceStack> lookup(LiteralArgumentBuilder<CommandSourceStack> branch) {
        return (LiteralArgumentBuilder<CommandSourceStack>)branch.requires(source -> source.hasPermission(4));
    }
}

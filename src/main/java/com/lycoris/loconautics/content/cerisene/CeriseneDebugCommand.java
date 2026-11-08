package com.lycoris.loconautics.content.cerisene;

import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.HitResult.Type;

public final class CeriseneDebugCommand {
    private static final double REACH = 8.0;

    private CeriseneDebugCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(Commands.literal("cerisene").executes(ctx -> heat(((CommandSourceStack)ctx.getSource()).getPlayerOrException())));
    }

    private static int heat(ServerPlayer player) {
        HitResult hit = player.pick(8.0, 0.0F, false);
        if (hit instanceof BlockHitResult blockHit && hit.getType() == Type.BLOCK) {
            BlockState state = player.level().getBlockState(blockHit.getBlockPos());
            if (!(state.getBlock() instanceof CeriseneBlock)) {
                player.sendSystemMessage(Component.literal("not a Cerisene Block"));
                return 0;
            } else {
                int heat = (Integer)state.getValue(CeriseneBlock.HEAT);
                boolean source = CeriseneHeat.heatSourceNear(player.level(), blockHit.getBlockPos());
                boolean water = CeriseneHeat.waterAdjacent(player.level(), blockHit.getBlockPos());
                player.sendSystemMessage(
                    Component.literal(
                        "cerisene heat " + heat + "/4" + (source ? ", heat source in range" : ", no heat source") + (water ? ", water adjacent" : "")
                    )
                );
                return heat;
            }
        } else {
            player.sendSystemMessage(Component.literal("no block under the crosshair"));
            return 0;
        }
    }
}

package com.lycoris.loconautics.trainphysics.debug;

import com.lycoris.loconautics.trainphysics.PoseTweak;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.spawn.SableTrainSpawner;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

public final class PoseTweakCommand {
    private PoseTweakCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
            ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal(
                                            "pose"
                                        )
                                        .then(angle("roll", PoseTweak::setRoll)))
                                    .then(angle("pitch", PoseTweak::setPitch)))
                                .then(angle("yaw", PoseTweak::setYaw)))
                            .then(
                                Commands.literal("offset")
                                    .then(
                                        Commands.argument("x", DoubleArgumentType.doubleArg(-32.0, 32.0))
                                            .then(
                                                Commands.argument("y", DoubleArgumentType.doubleArg(-32.0, 32.0))
                                                    .then(Commands.argument("z", DoubleArgumentType.doubleArg(-32.0, 32.0)).executes(PoseTweakCommand::offset))
                                            )
                                    )
                            ))
                        .then(
                            Commands.literal("lerp")
                                .then(
                                    Commands.argument("speed", DoubleArgumentType.doubleArg(0.0, 1.0))
                                        .executes(ctx -> edit(ctx, tweak -> tweak.setLerpSpeed(DoubleArgumentType.getDouble(ctx, "speed"))))
                                )
                        ))
                    .then(Commands.literal("reset").executes(ctx -> edit(ctx, PoseTweak::reset))))
                .executes(PoseTweakCommand::show)
        );
    }

    private static LiteralArgumentBuilder<CommandSourceStack> angle(String name, BiConsumer<PoseTweak, Double> setter) {
        return (LiteralArgumentBuilder<CommandSourceStack>)Commands.literal(name)
            .then(
                Commands.argument("degrees", DoubleArgumentType.doubleArg(-360.0, 360.0))
                    .executes(ctx -> edit(ctx, tweak -> setter.accept(tweak, DoubleArgumentType.getDouble(ctx, "degrees"))))
            );
    }

    private static int offset(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return edit(
            ctx,
            tweak -> tweak.setOffset(DoubleArgumentType.getDouble(ctx, "x"), DoubleArgumentType.getDouble(ctx, "y"), DoubleArgumentType.getDouble(ctx, "z"))
        );
    }

    private static int show(CommandContext<CommandSourceStack> ctx) throws CommandSyntaxException {
        return edit(ctx, tweak -> {});
    }

    private static int edit(CommandContext<CommandSourceStack> ctx, Consumer<PoseTweak> change) throws CommandSyntaxException {
        ServerPlayer player = ((CommandSourceStack)ctx.getSource()).getPlayerOrException();
        SableTrain train = SableTrainSpawner.pickLookedAtTrain(player);
        if (train == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("no train under the crosshair"));
            return 0;
        } else {
            change.accept(train.poseTweak());
            String state = train.poseTweak().toString();
            String note = train.isDerailed() ? ", derailed so it applies on the next rerail" : "";
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("sub level pose: " + state + note), false);
            return 1;
        }
    }
}

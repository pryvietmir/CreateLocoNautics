package com.lycoris.loconautics.trainphysics.visual;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.FloatArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;

@OnlyIn(Dist.CLIENT)
public final class BogeyTurnTweakCommand {
    private BogeyTurnTweakCommand() {
    }

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(
            (LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("bogey")
                        .then(
                            Commands.literal("add")
                                .then(
                                    Commands.argument("gain", FloatArgumentType.floatArg())
                                        .executes(
                                            ctx -> report((CommandSourceStack)ctx.getSource(), BogeyTurnTweak.add(FloatArgumentType.getFloat(ctx, "gain")))
                                        )
                                )
                        ))
                    .then(
                        Commands.literal("remove")
                            .then(
                                Commands.argument("gain", FloatArgumentType.floatArg())
                                    .executes(ctx -> report((CommandSourceStack)ctx.getSource(), BogeyTurnTweak.add(-FloatArgumentType.getFloat(ctx, "gain"))))
                            )
                    ))
                .then(Commands.literal("current").executes(ctx -> report((CommandSourceStack)ctx.getSource(), BogeyTurnTweak.gain())))
        );
    }

    private static int report(CommandSourceStack source, float gain) {
        source.sendSuccess(() -> Component.literal(String.format("bogey turn gain: %+.0f%% of the bogey's own turn angle", gain * 100.0F)), false);
        return 1;
    }
}

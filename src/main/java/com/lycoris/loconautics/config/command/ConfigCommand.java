package com.lycoris.loconautics.config.command;

import com.lycoris.loconautics.config.LoconauticsConfig;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.context.CommandContext;
import com.mojang.brigadier.suggestion.Suggestions;
import com.mojang.brigadier.suggestion.SuggestionsBuilder;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.Map.Entry;
import java.util.concurrent.CompletableFuture;
import net.minecraft.ChatFormatting;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.ClickEvent.Action;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.Range;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

public final class ConfigCommand {
    private ConfigCommand() {
    }

    public static void addBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
            Commands.literal("config")
                .then(
                    ((RequiredArgumentBuilder)Commands.argument("group", StringArgumentType.word())
                            .suggests(ConfigCommand::suggestGroups)
                            .executes(ctx -> listGroup(ctx)))
                        .then(
                            ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("key", StringArgumentType.word())
                                        .suggests(ConfigCommand::suggestKeys)
                                        .executes(ctx -> printValue(ctx)))
                                    .then(Commands.literal("default").executes(ctx -> resetValue(ctx))))
                                .then(Commands.argument("value", StringArgumentType.greedyString()).executes(ConfigCommand::setValue))
                        )
                )
        );
    }

    public static void addResetBranch(LiteralArgumentBuilder<CommandSourceStack> root) {
        root.then(
            ((LiteralArgumentBuilder)((LiteralArgumentBuilder)((LiteralArgumentBuilder)Commands.literal("configreset").executes(ctx -> promptResetAll(ctx)))
                        .then(Commands.literal("confirm").executes(ctx -> confirmResetAll(ctx))))
                    .then(Commands.literal("cancel").executes(ctx -> cancelReset(ctx))))
                .then(
                    Commands.argument("group", StringArgumentType.word())
                        .suggests(ConfigCommand::suggestGroups)
                        .then(
                            ((RequiredArgumentBuilder)((RequiredArgumentBuilder)Commands.argument("key", StringArgumentType.word())
                                        .suggests(ConfigCommand::suggestKeys)
                                        .executes(ctx -> promptReset(ctx)))
                                    .then(Commands.literal("confirm").executes(ctx -> resetValue(ctx))))
                                .then(Commands.literal("cancel").executes(ctx -> cancelReset(ctx)))
                        )
                )
        );
    }

    private static CompletableFuture<Suggestions> suggestGroups(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        allGroups().keySet().forEach(builder::suggest);
        return builder.buildFuture();
    }

    private static CompletableFuture<Suggestions> suggestKeys(CommandContext<CommandSourceStack> ctx, SuggestionsBuilder builder) {
        String group = StringArgumentType.getString(ctx, "group");
        Map<String, ConfigWalker.Entry> entries = allGroups().get(group);
        if (entries != null) {
            entries.forEach((k, e) -> {
                Object def = ConfigWalker.getOrDefault(() -> (String)e.spec().getDefault(), "?", e.flatKey());
                builder.suggest(k, Component.literal("[" + e.typeName() + "]  default: " + def).withStyle(ChatFormatting.GRAY));
            });
        }

        return builder.buildFuture();
    }

    private static int listGroup(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        Map<String, ConfigWalker.Entry> entries = allGroups().get(group);
        if (entries == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[config] unknown group \"" + group + "\"").withStyle(ChatFormatting.RED));
            return 0;
        } else {
            ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[config] " + group + " keys:").withStyle(ChatFormatting.AQUA), false);
            entries.forEach(
                (k, e) -> {
                    Object current = e.value().get();
                    Object def = ConfigWalker.getOrDefault(() -> (String)e.spec().getDefault(), "?", e.flatKey());
                    ((CommandSourceStack)ctx.getSource())
                        .sendSuccess(
                            () -> Component.literal(k + " ")
                                .withStyle(ChatFormatting.WHITE)
                                .append(Component.literal("[" + e.typeName() + "] ").withStyle(ChatFormatting.GRAY))
                                .append(Component.literal("current:" + current + " ").withStyle(ChatFormatting.YELLOW))
                                .append(Component.literal("default:" + def).withStyle(ChatFormatting.DARK_GRAY)),
                            false
                        );
                }
            );
            return 1;
        }
    }

    private static int printValue(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        String key = StringArgumentType.getString(ctx, "key");
        ConfigWalker.Entry entry = resolve(ctx, group, key);
        if (entry == null) {
            return 0;
        }

        Object current = entry.value().get();
        Object def = ConfigWalker.getOrDefault(() -> (String)entry.spec().getDefault(), "?", entry.flatKey());
        ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
                () -> Component.literal(key + " ")
                    .withStyle(ChatFormatting.WHITE)
                    .append(Component.literal("[" + entry.typeName() + "] ").withStyle(ChatFormatting.GRAY))
                    .append(Component.literal("current:" + current + " ").withStyle(ChatFormatting.YELLOW))
                    .append(Component.literal("default:" + def).withStyle(ChatFormatting.DARK_GRAY)),
                false
            );
        return 1;
    }

    private static int setValue(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        String key = StringArgumentType.getString(ctx, "key");
        String rawVal = StringArgumentType.getString(ctx, "value");
        ConfigWalker.Entry entry = resolve(ctx, group, key);
        if (entry == null) {
            return 0;
        }

        ValueSpec spec = entry.spec();
        Range<?> range = spec.getRange();
        Class<?> clazz = range != null ? range.getClazz() : spec.getClazz();

        Object parsed;
        try {
            parsed = parse(rawVal, clazz);
        } catch (IllegalArgumentException e) {
            ((CommandSourceStack)ctx.getSource())
                .sendFailure(Component.literal("[config] invalid value \"" + rawVal + "\" for type " + entry.typeName()).withStyle(ChatFormatting.RED));
            return 0;
        }

        if (!spec.test(parsed)) {
            String bound = range != null ? " (allowed " + range.getMin() + " to " + range.getMax() + ")" : "";
            ((CommandSourceStack)ctx.getSource())
                .sendFailure(Component.literal("[config] " + rawVal + " is out of range" + bound).withStyle(ChatFormatting.RED));
            return 0;
        } else {
            entry.value().set(parsed);
            specFor(group).save();
            Object now = entry.value().get();
            ((CommandSourceStack)ctx.getSource())
                .sendSuccess(
                    () -> Component.literal("[config] ")
                        .withStyle(ChatFormatting.AQUA)
                        .append(Component.literal(group + " " + key + " set to ").withStyle(ChatFormatting.WHITE))
                        .append(Component.literal(String.valueOf(now)).withStyle(ChatFormatting.GREEN)),
                    true
                );
            return 1;
        }
    }

    private static int resetValue(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        String key = StringArgumentType.getString(ctx, "key");
        ConfigWalker.Entry entry = resolve(ctx, group, key);
        if (entry == null) {
            return 0;
        }

        Object def = entry.spec().getDefault();
        entry.value().set(def);
        specFor(group).save();
        ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
                () -> Component.literal("[config] ")
                    .withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(group + " " + key + " reset to default: ").withStyle(ChatFormatting.WHITE))
                    .append(Component.literal(String.valueOf(def)).withStyle(ChatFormatting.YELLOW)),
                true
            );
        return 1;
    }

    private static int promptReset(CommandContext<CommandSourceStack> ctx) {
        String group = StringArgumentType.getString(ctx, "group");
        String key = StringArgumentType.getString(ctx, "key");
        ConfigWalker.Entry entry = resolve(ctx, group, key);
        if (entry == null) {
            return 0;
        } else {
            Object current = entry.value().get();
            Object def = entry.spec().getDefault();
            if (Objects.equals(current, def)) {
                ((CommandSourceStack)ctx.getSource())
                    .sendSuccess(() -> Component.literal("[config] " + key + " is already at default (" + def + ")").withStyle(ChatFormatting.GRAY), false);
                return 1;
            } else {
                String confirmCmd = "/configreset " + group + " " + key + " confirm";
                String cancelCmd = "/configreset " + group + " " + key + " cancel";
                MutableComponent yes = Component.literal("[Yes]")
                    .withStyle(
                        style -> style.withColor(ChatFormatting.GREEN)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(Action.RUN_COMMAND, confirmCmd))
                            .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to confirm reset")))
                    );
                MutableComponent no = Component.literal("[No]")
                    .withStyle(
                        style -> style.withColor(ChatFormatting.RED)
                            .withBold(true)
                            .withClickEvent(new ClickEvent(Action.RUN_COMMAND, cancelCmd))
                            .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to cancel")))
                    );
                ((CommandSourceStack)ctx.getSource())
                    .sendSuccess(() -> Component.literal("[config] Reset " + group + " " + key + "?").withStyle(ChatFormatting.AQUA), false);
                ((CommandSourceStack)ctx.getSource())
                    .sendSuccess(
                        () -> Component.literal(key + " ")
                            .withStyle(ChatFormatting.WHITE)
                            .append(Component.literal(String.valueOf(current)).withStyle(ChatFormatting.YELLOW))
                            .append(Component.literal(" -> ").withStyle(ChatFormatting.GRAY))
                            .append(Component.literal(String.valueOf(def)).withStyle(ChatFormatting.GREEN)),
                        false
                    );
                ((CommandSourceStack)ctx.getSource())
                    .sendSuccess(() -> yes.copy().append(Component.literal(" | ").withStyle(ChatFormatting.GRAY)).append(no.copy()), false);
                return 1;
            }
        }
    }

    private static int promptResetAll(CommandContext<CommandSourceStack> ctx) {
        List<String[]> diffs = new ArrayList<>();

        for (Entry<String, Map<String, ConfigWalker.Entry>> group : allGroups().entrySet()) {
            for (Entry<String, ConfigWalker.Entry> e : group.getValue().entrySet()) {
                ConfigWalker.Entry entry = e.getValue();
                Object current = entry.value().get();
                Object def = entry.spec().getDefault();
                if (!Objects.equals(current, def)) {
                    diffs.add(new String[]{group.getKey(), e.getKey(), String.valueOf(current), String.valueOf(def), entry.typeName()});
                }
            }
        }

        if (diffs.isEmpty()) {
            ((CommandSourceStack)ctx.getSource())
                .sendSuccess(() -> Component.literal("[config] Everything is already at default.").withStyle(ChatFormatting.GRAY), false);
            return 1;
        }

        ((CommandSourceStack)ctx.getSource())
            .sendSuccess(
                () -> Component.literal("[config] Reset ALL " + diffs.size() + " non-default value(s) to default?").withStyle(ChatFormatting.AQUA), false
            );

        for (String[] d : diffs) {
            ((CommandSourceStack)ctx.getSource())
                .sendSuccess(
                    () -> Component.literal(d[0] + " " + d[1] + " ")
                        .withStyle(ChatFormatting.WHITE)
                        .append(Component.literal("[" + d[4] + "] ").withStyle(ChatFormatting.GRAY))
                        .append(Component.literal("current:" + d[2] + " ").withStyle(ChatFormatting.YELLOW))
                        .append(Component.literal("default:" + d[3]).withStyle(ChatFormatting.DARK_GRAY)),
                    false
                );
        }

        MutableComponent yes = Component.literal("[Yes]")
            .withStyle(
                style -> style.withColor(ChatFormatting.GREEN)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/dbgloco configreset confirm"))
                    .withHoverEvent(
                        new HoverEvent(
                            net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to reset all " + diffs.size() + " value(s)")
                        )
                    )
            );
        MutableComponent no = Component.literal("[No]")
            .withStyle(
                style -> style.withColor(ChatFormatting.RED)
                    .withBold(true)
                    .withClickEvent(new ClickEvent(Action.RUN_COMMAND, "/dbgloco configreset cancel"))
                    .withHoverEvent(new HoverEvent(net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT, Component.literal("Click to cancel")))
            );
        ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> yes.copy().append(Component.literal("  -  ").withStyle(ChatFormatting.GRAY)).append(no.copy()), false);
        return 1;
    }

    private static int confirmResetAll(CommandContext<CommandSourceStack> ctx) {
        Set<ModConfigSpec> touchedSpecs = new HashSet<>();
        int count = 0;

        for (Entry<String, Map<String, ConfigWalker.Entry>> group : allGroups().entrySet()) {
            for (Entry<String, ConfigWalker.Entry> e : group.getValue().entrySet()) {
                ConfigWalker.Entry entry = e.getValue();
                Object current = entry.value().get();
                Object def = entry.spec().getDefault();
                if (!Objects.equals(current, def)) {
                    entry.value().set(def);
                    touchedSpecs.add(specFor(group.getKey()));
                    count++;
                }
            }
        }

        touchedSpecs.forEach(ModConfigSpec::save);
        int finalCount = count;
        ((CommandSourceStack)ctx.getSource())
            .sendSuccess(() -> Component.literal("[config] Reset " + finalCount + " value(s) to default.").withStyle(ChatFormatting.GREEN), true);
        return count;
    }

    private static int cancelReset(CommandContext<CommandSourceStack> ctx) {
        ((CommandSourceStack)ctx.getSource()).sendSuccess(() -> Component.literal("[config] Reset cancelled.").withStyle(ChatFormatting.GRAY), false);
        return 1;
    }

    private static ConfigWalker.Entry resolve(CommandContext<CommandSourceStack> ctx, String group, String key) {
        Map<String, ConfigWalker.Entry> entries = allGroups().get(group);
        if (entries == null) {
            ((CommandSourceStack)ctx.getSource()).sendFailure(Component.literal("[config] unknown group \"" + group + "\"").withStyle(ChatFormatting.RED));
            return null;
        } else {
            ConfigWalker.Entry entry = entries.get(key);
            if (entry == null) {
                ((CommandSourceStack)ctx.getSource())
                    .sendFailure(Component.literal("[config] unknown key \"" + key + "\" in " + group).withStyle(ChatFormatting.RED));
                return null;
            } else {
                return entry;
            }
        }
    }

    private static Object parse(String raw, Class<?> clazz) {
        if (clazz == Boolean.class) {
            if (raw.equalsIgnoreCase("true")) {
                return true;
            } else if (raw.equalsIgnoreCase("false")) {
                return false;
            } else {
                throw new IllegalArgumentException();
            }
        } else {
            if (clazz == Integer.class) {
                return Integer.parseInt(raw);
            }

            if (clazz == Long.class) {
                return Long.parseLong(raw);
            }

            if (clazz == Double.class) {
                return Double.parseDouble(raw);
            }

            if (clazz == Float.class) {
                return Float.parseFloat(raw);
            }

            if (clazz != null && clazz.isEnum()) {
                for (Object constant : clazz.getEnumConstants()) {
                    if (constant.toString().equalsIgnoreCase(raw)) {
                        return constant;
                    }
                }

                throw new IllegalArgumentException();
            } else {
                return raw;
            }
        }
    }

    private static Map<String, Map<String, ConfigWalker.Entry>> allGroups() {
        Map<String, Map<String, ConfigWalker.Entry>> merged = new LinkedHashMap<>();
        merged.putAll(ConfigWalker.index(LoconauticsConfig.SERVER_SPEC));
        merged.putAll(ConfigWalker.index(LoconauticsConfig.CLIENT_SPEC));
        return merged;
    }

    private static ModConfigSpec specFor(String group) {
        return ConfigWalker.index(LoconauticsConfig.CLIENT_SPEC).containsKey(group) ? LoconauticsConfig.CLIENT_SPEC : LoconauticsConfig.SERVER_SPEC;
    }
}

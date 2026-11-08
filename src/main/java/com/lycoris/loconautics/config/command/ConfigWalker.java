package com.lycoris.loconautics.config.command;

import com.electronwill.nightconfig.core.UnmodifiableConfig;
import com.lycoris.loconautics.LoconauticsConstants;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.common.ModConfigSpec.ConfigValue;
import net.neoforged.neoforge.common.ModConfigSpec.Range;
import net.neoforged.neoforge.common.ModConfigSpec.ValueSpec;

public final class ConfigWalker {
    public static Map<String, Map<String, ConfigWalker.Entry>> index(ModConfigSpec configSpec) {
        Map<String, Map<String, ConfigWalker.Entry>> result = new LinkedHashMap<>();
        walk(configSpec.getValues(), configSpec.getSpec(), new ArrayList<>(), result);
        return result;
    }

    private static void walk(UnmodifiableConfig values, UnmodifiableConfig spec, List<String> path, Map<String, Map<String, ConfigWalker.Entry>> out) {
        values.valueMap().forEach((key, raw) -> {
            List<String> here = new ArrayList<>(path);
            here.add(key);
            if (raw instanceof ConfigValue<?> cv) {
                if (spec.valueMap().get(key) instanceof ValueSpec vs) {
                    ConfigWalker.Entry entry = new ConfigWalker.Entry(here, cv, vs);
                    out.computeIfAbsent(entry.group(), k -> new LinkedHashMap<>()).put(entry.flatKey(), entry);
                }
            } else if (raw instanceof UnmodifiableConfig nested && spec.valueMap().get(key) instanceof UnmodifiableConfig nestedSpec) {
                walk(nested, nestedSpec, here, out);
            }
        });
    }

    public static <T> T getOrDefault(Supplier<T> getter, T fallback, String key) {
        try {
            return getter.get();
        } catch (Exception e) {
            LoconauticsConstants.LOGGER.warn("Config read failed for {}, using fallback {}", new Object[]{key, fallback, e});
            return fallback;
        }
    }

    private ConfigWalker() {
    }

    public record Entry(List<String> path, ConfigValue<?> value, ValueSpec spec) {
        public String group() {
            return this.path.getFirst();
        }

        public String flatKey() {
            return String.join("_", this.path.subList(1, this.path.size()));
        }

        public String typeName() {
            Range<?> range = this.spec.getRange();
            Class<?> c = range != null ? range.getClazz() : this.spec.getClazz();
            if (c == null || c == Object.class) {
                return "Value";
            } else if (c == Boolean.class) {
                return "Boolean";
            } else if (c == Integer.class) {
                return "Integer";
            } else if (c == Long.class) {
                return "Long";
            } else if (c == Double.class) {
                return "Double";
            } else if (c == Float.class) {
                return "Float";
            } else {
                return c.isEnum()
                    ? "Enum(" + Arrays.stream(c.getEnumConstants()).map(Object::toString).reduce((a, b) -> a + "|" + b).orElse("") + ")"
                    : c.getSimpleName();
            }
        }
    }
}

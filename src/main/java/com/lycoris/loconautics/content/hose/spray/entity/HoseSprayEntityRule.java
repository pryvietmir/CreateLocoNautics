package com.lycoris.loconautics.content.hose.spray.entity;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.hose.spray.HoseSprayDamage;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.Holder.Reference;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.tags.TagKey;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import org.jetbrains.annotations.Nullable;

public record HoseSprayEntityRule(List<Predicate<EntityType<?>>> match, HoseSprayEntityRule.Action action) {
    public boolean matches(Entity entity) {
        if (this.match.isEmpty()) {
            return true;
        }

        for (Predicate<EntityType<?>> p : this.match) {
            if (p.test(entity.getType())) {
                return true;
            }
        }

        return false;
    }

    public static Predicate<EntityType<?>> matcher(String s) {
        if (s.startsWith("#")) {
            TagKey<EntityType<?>> tag = TagKey.create(Registries.ENTITY_TYPE, ResourceLocation.parse(s.substring(1)));
            return typex -> typex.builtInRegistryHolder().is(tag);
        }

        EntityType<?> type = (EntityType<?>)BuiltInRegistries.ENTITY_TYPE.getOptional(ResourceLocation.parse(s)).orElse(null);
        if (type == null) {
            LoconauticsConstants.LOGGER.error("[HoseSpray] unknown entity type {}", s);
        }

        return t -> t == type;
    }

    public static List<Predicate<EntityType<?>>> matchers(JsonArray arr) {
        List<Predicate<EntityType<?>>> out = new ArrayList<>();
        if (arr != null) {
            for (JsonElement e : arr) {
                out.add(matcher(e.getAsString()));
            }
        }

        return out;
    }

    public static HoseSprayEntityRule fromJson(JsonObject o) {
        List<Predicate<EntityType<?>>> match = matchers(o.has("match") ? o.getAsJsonArray("match") : null);
        if (o.has("mob_effect")) {
            ResourceLocation id = ResourceLocation.parse(o.get("mob_effect").getAsString());
            int dur = o.has("duration") ? o.get("duration").getAsInt() : 200;
            int amp = o.has("amplifier") ? o.get("amplifier").getAsInt() : 0;
            Reference<MobEffect> holder = (Reference<MobEffect>)BuiltInRegistries.MOB_EFFECT
                .getHolder(ResourceKey.create(Registries.MOB_EFFECT, id))
                .orElse(null);
            if (holder == null) {
                LoconauticsConstants.LOGGER.error("[HoseSpray] unknown mob_effect {}", id);
                return new HoseSprayEntityRule(match, (l, e, p, who) -> {});
            } else {
                HoseSprayEntityRule.Action a = (level, entity, power, attacker) -> {
                    if (entity instanceof LivingEntity living) {
                        living.addEffect(new MobEffectInstance(holder, dur, amp));
                    }
                };
                return new HoseSprayEntityRule(match, a);
            }
        } else if (o.has("damage")) {
            float amount = o.get("damage").getAsFloat();
            HoseSprayEntityRule.Action a = (level, entity, power, attacker) -> entity.hurt(
                HoseSprayDamage.of(level, HoseSprayDamage.Substance.UNKNOWN, attacker), amount
            );
            return new HoseSprayEntityRule(match, a);
        } else if (o.has("ignite")) {
            float seconds = o.get("ignite").getAsFloat();
            HoseSprayEntityRule.Action a = (level, entity, power, attacker) -> entity.setRemainingFireTicks((int)(seconds * 20.0F));
            return new HoseSprayEntityRule(match, a);
        } else {
            return new HoseSprayEntityRule(match, (l, e, p, who) -> {});
        }
    }

    public interface Action {
        void apply(ServerLevel var1, Entity var2, float var3, @Nullable LivingEntity var4);
    }
}

package com.lycoris.loconautics.content.hose.spray.interaction;

import com.lycoris.loconautics.content.hose.spray.entity.HoseSprayEntityRule;
import java.util.List;
import java.util.function.Predicate;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.neoforged.neoforge.fluids.FluidStack;
import org.jetbrains.annotations.Nullable;

public record CompositeSprayInteraction(@Nullable HoseSprayInteraction base, List<HoseSprayEntityRule> rules, List<Predicate<EntityType<?>>> blacklist)
    implements HoseSprayInteraction {
    @Override
    public boolean onBlock(ServerLevel level, BlockPos pos, BlockState state, Direction hitDir, FluidStack fluid, float power) {
        return this.base != null ? this.base.onBlock(level, pos, state, hitDir, fluid, power) : !state.isAir();
    }

    @Override
    public void onEntity(ServerLevel level, Entity entity, FluidStack fluid, float power, @Nullable LivingEntity attacker) {
        for (Predicate<EntityType<?>> b : this.blacklist) {
            if (b.test(entity.getType())) {
                return;
            }
        }

        if (this.base != null) {
            this.base.onEntity(level, entity, fluid, power, attacker);
        }

        for (HoseSprayEntityRule rule : this.rules) {
            if (rule.matches(entity)) {
                rule.action().apply(level, entity, power, attacker);
                break;
            }
        }
    }
}

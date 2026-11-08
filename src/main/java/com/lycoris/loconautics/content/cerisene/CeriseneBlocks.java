package com.lycoris.loconautics.content.cerisene;

import com.lycoris.loconautics.registry.LoconauticsRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.food.FoodProperties.Builder;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.BucketItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.Item.Properties;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.SoundType;
import net.minecraft.world.level.material.FlowingFluid;
import net.minecraft.world.level.material.Fluid;
import net.minecraft.world.level.material.MapColor;
import net.minecraft.world.level.material.PushReaction;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.fluids.FluidType;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Flowing;
import net.neoforged.neoforge.fluids.BaseFlowingFluid.Source;
import net.neoforged.neoforge.registries.DeferredHolder;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.NeoForgeRegistries.Keys;

public final class CeriseneBlocks {
    public static final DeferredRegister<FluidType> FLUID_TYPES = DeferredRegister.create(Keys.FLUID_TYPES, "loconautics");
    public static final DeferredRegister<Fluid> FLUIDS = DeferredRegister.create(Registries.FLUID, "loconautics");
    public static final DeferredHolder<Item, Item> RAW_CERISENE = LoconauticsRegistries.ITEMS
        .register(
            "raw_cerisene",
            () -> new Item(
                new Properties()
                    .stacksTo(64)
                    .food(
                        new Builder()
                            .nutrition(2)
                            .saturationModifier(0.2F)
                            .alwaysEdible()
                            .effect(() -> new MobEffectInstance(MobEffects.CONFUSION, 200), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.POISON, 100), 1.0F)
                            .effect(() -> new MobEffectInstance(MobEffects.GLOWING, 600), 1.0F)
                            .build()
                    )
            )
        );
    public static final DeferredHolder<Block, CeriseneBlock> CERISENE_BLOCK = LoconauticsRegistries.BLOCKS
        .register(
            "cerisene_block",
            () -> new CeriseneBlock(
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PINK)
                    .strength(0.8F)
                    .sound(SoundType.GLASS)
                    .lightLevel(state -> 7)
                    .noOcclusion()
                    .isValidSpawn((state, level, pos, type) -> false)
                    .isRedstoneConductor((state, level, pos) -> false)
                    .isSuffocating((state, level, pos) -> false)
                    .isViewBlocking((state, level, pos) -> false)
            )
        );
    public static final DeferredHolder<Item, BlockItem> CERISENE_BLOCK_ITEM = LoconauticsRegistries.ITEMS
        .register("cerisene_block", () -> new BlockItem((Block)CERISENE_BLOCK.get(), new Properties().stacksTo(64)));
    public static final DeferredHolder<FluidType, FluidType> CERISENE_TYPE = FLUID_TYPES.register(
        "cerisene",
        () -> new CeriseneFluidType(
            net.neoforged.neoforge.fluids.FluidType.Properties.create()
                .density(1200)
                .viscosity(1500)
                .temperature(300)
                .lightLevel(7)
                .canSwim(false)
                .canDrown(false)
                .supportsBoating(false)
        )
    );
    public static final DeferredHolder<Fluid, Source> CERISENE = FLUIDS.register("cerisene", () -> new Source(fluidProperties()));
    public static final DeferredHolder<Fluid, Flowing> CERISENE_FLOWING = FLUIDS.register("flowing_cerisene", () -> new Flowing(fluidProperties()));
    public static final DeferredHolder<Block, LiquidBlock> CERISENE_FLUID_BLOCK = LoconauticsRegistries.BLOCKS
        .register(
            "cerisene_fluid",
            () -> new LiquidBlock(
                (FlowingFluid)CERISENE.get(),
                net.minecraft.world.level.block.state.BlockBehaviour.Properties.of()
                    .mapColor(MapColor.COLOR_PINK)
                    .replaceable()
                    .noCollission()
                    .strength(100.0F)
                    .pushReaction(PushReaction.DESTROY)
                    .liquid()
                    .noLootTable()
                    .lightLevel(state -> 7)
            )
        );
    public static final DeferredHolder<Item, BucketItem> CERISENE_BUCKET = LoconauticsRegistries.ITEMS
        .register("cerisene_bucket", () -> new BucketItem((Fluid)CERISENE.get(), new Properties().craftRemainder(Items.BUCKET).stacksTo(1)));

    private CeriseneBlocks() {
    }

    private static net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties fluidProperties() {
        return new net.neoforged.neoforge.fluids.BaseFlowingFluid.Properties(CERISENE_TYPE, CERISENE, CERISENE_FLOWING)
            .bucket(CERISENE_BUCKET)
            .block(CERISENE_FLUID_BLOCK);
    }

    public static void register(IEventBus modBus) {
        FLUID_TYPES.register(modBus);
        FLUIDS.register(modBus);
    }
}

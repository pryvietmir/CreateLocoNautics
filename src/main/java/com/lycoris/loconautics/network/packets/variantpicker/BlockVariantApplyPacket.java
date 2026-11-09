package com.lycoris.loconautics.network.packets.variantpicker;

import com.lycoris.loconautics.network.RejectedPacketLog;
import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.variantpicker.BlockVariantRegistry;
import io.netty.buffer.ByteBuf;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.RegistryFriendlyByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload.Type;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.block.Block;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record BlockVariantApplyPacket(InteractionHand hand, ResourceLocation blockId) implements CustomPacketPayload {
    public static final Type<BlockVariantApplyPacket> TYPE = new Type(LoconauticsConstants.id("block_variant_apply"));
    private static final StreamCodec<ByteBuf, InteractionHand> HAND_CODEC = ByteBufCodecs.VAR_INT.map(i -> InteractionHand.values()[i], Enum::ordinal);
    public static final StreamCodec<RegistryFriendlyByteBuf, BlockVariantApplyPacket> STREAM_CODEC = StreamCodec.composite(
        HAND_CODEC, BlockVariantApplyPacket::hand, ResourceLocation.STREAM_CODEC, BlockVariantApplyPacket::blockId, BlockVariantApplyPacket::new
    );

    public Type<? extends CustomPacketPayload> type() {
        return TYPE;
    }

    public static void handle(BlockVariantApplyPacket packet, IPayloadContext context) {
        if (context.player() instanceof ServerPlayer player) {
            context.enqueueWork(() -> {
                ItemStack held = player.getItemInHand(packet.hand());
                if (!held.isEmpty()) {
                    Block current = Block.byItem(held.getItem());
                    if (BlockVariantRegistry.isKnownVariant(current)) {
                        Block chosen = (Block)BuiltInRegistries.BLOCK.getOptional(packet.blockId()).orElse(null);
                        // Fix: only allow switching within the SAME variant family, otherwise a forged
                        // packet could turn a stack of one block into a stack of any other known variant.
                        if (chosen != null && BlockVariantRegistry.isKnownVariant(chosen)) {
                            if (BlockVariantRegistry.locate(current).family() != BlockVariantRegistry.locate(chosen).family()) {
                                RejectedPacketLog.warn(
                                    player, "variant swap " + BuiltInRegistries.BLOCK.getKey(current) + " -> " + packet.blockId() + " (different families)"
                                );
                                return;
                            }

                            Item chosenItem = chosen.asItem();
                            if (chosenItem != Items.AIR && chosenItem != held.getItem()) {
                                player.setItemInHand(packet.hand(), new ItemStack(chosenItem, held.getCount()));
                            }
                        }
                    }
                }
            });
        }
    }
}

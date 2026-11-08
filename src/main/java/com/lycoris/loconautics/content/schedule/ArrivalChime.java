package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.sable.SubLevelBlocks;
import com.lycoris.loconautics.common.sable.SubLevelData;
import com.lycoris.loconautics.content.analogcontroller.TrainHorn;
import com.lycoris.loconautics.trainphysics.SableTrain;
import com.lycoris.loconautics.trainphysics.TrainConsist;
import com.simibubi.create.AllBlocks;
import com.simibubi.create.AllSoundEvents;
import com.simibubi.create.content.decoration.steamWhistle.WhistleBlock;
import com.simibubi.create.content.equipment.bell.AbstractBellBlockEntity;
import com.simibubi.create.content.trains.entity.ArrivalSoundQueue;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.level.block.BellBlock;
import net.minecraft.world.level.block.NoteBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.block.state.properties.NoteBlockInstrument;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public final class ArrivalChime {
    private static final float VOLUME = 5.0F;
    private final ServerLevel level;
    private final List<ArrivalChime.Source> sources;
    private final int lastStep;
    private int step;
    private int ticks;

    private ArrivalChime(ServerLevel level, List<ArrivalChime.Source> sources, int lastStep) {
        this.level = level;
        this.sources = sources;
        this.lastStep = lastStep;
    }

    @Nullable
    public static ArrivalChime start(SableTrain train, Vec3 heading) {
        ServerLevel level = train.level();
        UUID id = train.car() == null ? null : train.car().subLevelId();
        if (level != null && id != null) {
            List<UUID> subs = new ArrayList<>();
            List<BlockPos> positions = new ArrayList<>();
            List<Double> reaches = new ArrayList<>();

            for (UUID member : TrainConsist.connectedSubLevels(level, id)) {
                ServerSubLevel sub = SubLevelData.resolve(level, member);
                SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
                    if (ArrivalSoundQueue.isPlayable(state)) {
                        subs.add(member);
                        positions.add(pos.immutable());
                        reaches.add(sub.logicalPose().transformPosition(Vec3.atCenterOf(pos)).dot(heading));
                    }

                    return true;
                });
            }

            if (positions.isEmpty()) {
                return null;
            }

            double front = reaches.stream().mapToDouble(Double::doubleValue).max().orElse(0.0);
            List<ArrivalChime.Source> sources = new ArrayList<>();
            int lastStep = 0;

            for (int i = 0; i < positions.size(); i++) {
                int step = (int)Math.round(front - reaches.get(i));
                sources.add(new ArrivalChime.Source(subs.get(i), positions.get(i), step));
                lastStep = Math.max(lastStep, step);
            }

            return new ArrivalChime(level, sources, lastStep);
        } else {
            return null;
        }
    }

    public boolean tick() {
        if (this.ticks++ % 2 != 0) {
            return true;
        }

        for (ArrivalChime.Source source : this.sources) {
            if (source.step() == this.step) {
                this.play(source);
            }
        }

        return ++this.step <= this.lastStep;
    }

    private void play(ArrivalChime.Source source) {
        ServerSubLevel sub = SubLevelData.resolve(this.level, source.subLevel());
        if (sub != null && !sub.isRemoved()) {
            ServerLevel plotLevel = sub.getLevel();
            BlockState state = plotLevel.getBlockState(source.pos());
            Vec3 at = sub.logicalPose().transformPosition(Vec3.atCenterOf(source.pos()));
            if (state.getBlock() instanceof BellBlock) {
                SoundEvent bell = AllBlocks.HAUNTED_BELL.has(state) ? AllSoundEvents.HAUNTED_BELL_USE.getMainEvent() : SoundEvents.BELL_BLOCK;
                this.playAt(at, bell, 1.0F);
                swing(plotLevel, source.pos(), state);
            }

            if (state.getBlock() instanceof NoteBlock) {
                float pitch = (float)Math.pow(2.0, ((Integer)state.getValue(NoteBlock.NOTE) - 12) / 12.0);
                this.playAt(at, (SoundEvent)((NoteBlockInstrument)state.getValue(NoteBlock.INSTRUMENT)).getSoundEvent().value(), pitch);
            }

            if (state.getBlock() instanceof WhistleBlock) {
                TrainHorn.blow(this.level, source.pos(), true);
            }
        }
    }

    private static void swing(ServerLevel plotLevel, BlockPos pos, BlockState state) {
        Direction facing = (Direction)state.getValue(BellBlock.FACING);
        if (plotLevel.getBlockEntity(pos) instanceof AbstractBellBlockEntity bell) {
            bell.ring(plotLevel, pos, facing);
        } else {
            plotLevel.blockEvent(pos, state.getBlock(), 1, facing.get3DDataValue());
        }
    }

    private void playAt(Vec3 at, SoundEvent event, float pitch) {
        this.level.playSound(null, at.x, at.y, at.z, event, SoundSource.NEUTRAL, 5.0F, pitch);
    }

    private record Source(UUID subLevel, BlockPos pos, int step) {
    }
}

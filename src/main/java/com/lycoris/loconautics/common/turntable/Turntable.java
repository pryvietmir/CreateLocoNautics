package com.lycoris.loconautics.common.turntable;

import com.simibubi.create.content.trains.graph.TrackNodeLocation;
import java.util.UUID;
import net.minecraft.world.phys.Vec3;
import org.jetbrains.annotations.Nullable;

public interface Turntable {
    UUID plateSubLevelId();

    Vec3 turntableAxisWorld();

    Vec3 turntableAxisNormal();

    boolean turntablePowered();

    boolean turntableScheduleDriven();

    void turntableAlignTo(TrackNodeLocation var1, TrackNodeLocation var2);

    void turntableClearAlignment();

    @Nullable
    TrackNodeLocation turntableAlignTarget();

    boolean turntableAligned();

    boolean turntableSettled();
}

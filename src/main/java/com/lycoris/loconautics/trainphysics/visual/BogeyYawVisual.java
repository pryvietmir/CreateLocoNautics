package com.lycoris.loconautics.trainphysics.visual;

import com.simibubi.create.content.trains.bogey.AbstractBogeyBlockEntity;
import java.util.Objects;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class BogeyYawVisual {
    private static final String RAIL_OFF_X_KEY = "LoconauticsRailOffX";
    private static final String RAIL_OFF_Y_KEY = "LoconauticsRailOffY";
    private static final String RAIL_OFF_Z_KEY = "LoconauticsRailOffZ";
    private static final String BODY_CT_X_KEY = "LoconauticsBodyCounterX";
    private static final String BODY_CT_Y_KEY = "LoconauticsBodyCounterY";
    private static final String BODY_CT_Z_KEY = "LoconauticsBodyCounterZ";
    private static final String LINK_GRAPH_KEY = "LoconauticsRailGraph";
    private static final String LINK_LEAD_N1_KEY = "LoconauticsRailLeadN1";
    private static final String LINK_LEAD_N2_KEY = "LoconauticsRailLeadN2";
    private static final String LINK_LEAD_POS_KEY = "LoconauticsRailLeadPos";
    private static final String LINK_TRAIL_N1_KEY = "LoconauticsRailTrailN1";
    private static final String LINK_TRAIL_N2_KEY = "LoconauticsRailTrailN2";
    private static final String LINK_TRAIL_POS_KEY = "LoconauticsRailTrailPos";
    private static final String LINK_FLIP_KEY = "LoconauticsRailChordFlip";
    private static final String SWING_X_KEY = "LoconauticsWheelSwingX";
    private static final String SWING_Y_KEY = "LoconauticsWheelSwingY";
    private static final String SWING_Z_KEY = "LoconauticsWheelSwingZ";
    private static final String SWING_W_KEY = "LoconauticsWheelSwingW";

    private BogeyYawVisual() {
    }

    private static void sync(AbstractBogeyBlockEntity be) {
        be.setChanged();
        Level level = be.getLevel();
        if (level != null) {
            BlockPos pos = be.getBlockPos();
            BlockState state = level.getBlockState(pos);
            level.sendBlockUpdated(pos, state, state, 2);
        }
    }

    public static void clearRailBodyPose(AbstractBogeyBlockEntity be) {
        CompoundTag data = be.getBogeyData();
        if (data != null && data.contains("LoconauticsRailOffX")) {
            for (String key : new String[]{
                "LoconauticsRailOffX",
                "LoconauticsRailOffY",
                "LoconauticsRailOffZ",
                "LoconauticsBodyCounterX",
                "LoconauticsBodyCounterY",
                "LoconauticsBodyCounterZ"
            }) {
                data.remove(key);
            }

            setRailLink(be, null);
            be.setBogeyData(data);
            sync(be);
        }
    }

    public static Vector3f getRailLocalOffset(AbstractBogeyBlockEntity be, Vector3f dest) {
        CompoundTag data = be.getBogeyData();
        return data != null && data.contains("LoconauticsRailOffX")
            ? dest.set(data.getFloat("LoconauticsRailOffX"), data.getFloat("LoconauticsRailOffY"), data.getFloat("LoconauticsRailOffZ"))
            : dest.zero();
    }

    public static Vector3f getBodyCounter(AbstractBogeyBlockEntity be, Vector3f dest) {
        CompoundTag data = be.getBogeyData();
        return data != null && data.contains("LoconauticsBodyCounterX")
            ? dest.set(data.getFloat("LoconauticsBodyCounterX"), data.getFloat("LoconauticsBodyCounterY"), data.getFloat("LoconauticsBodyCounterZ"))
            : dest.zero();
    }

    public static Quaternionf getWheelSwing(AbstractBogeyBlockEntity be, Quaternionf dest) {
        CompoundTag data = be.getBogeyData();
        return data != null && data.contains("LoconauticsWheelSwingW")
            ? dest.set(
                data.getFloat("LoconauticsWheelSwingX"),
                data.getFloat("LoconauticsWheelSwingY"),
                data.getFloat("LoconauticsWheelSwingZ"),
                data.getFloat("LoconauticsWheelSwingW")
            )
            : null;
    }

    public static void setWheelSwing(AbstractBogeyBlockEntity be, Quaternionf swing) {
        CompoundTag data = be.getBogeyData();
        boolean present = data != null && data.contains("LoconauticsWheelSwingW");
        if (swing == null) {
            if (!present) {
                return;
            }

            for (String key : new String[]{"LoconauticsWheelSwingX", "LoconauticsWheelSwingY", "LoconauticsWheelSwingZ", "LoconauticsWheelSwingW"}) {
                data.remove(key);
            }
        } else {
            if (data == null) {
                data = new CompoundTag();
            }

            if (present
                && Math.abs(data.getFloat("LoconauticsWheelSwingX") - swing.x) < 8.0E-4F
                && Math.abs(data.getFloat("LoconauticsWheelSwingY") - swing.y) < 8.0E-4F
                && Math.abs(data.getFloat("LoconauticsWheelSwingZ") - swing.z) < 8.0E-4F
                && Math.abs(data.getFloat("LoconauticsWheelSwingW") - swing.w) < 8.0E-4F) {
                return;
            }

            data.putFloat("LoconauticsWheelSwingX", swing.x);
            data.putFloat("LoconauticsWheelSwingY", swing.y);
            data.putFloat("LoconauticsWheelSwingZ", swing.z);
            data.putFloat("LoconauticsWheelSwingW", swing.w);
        }

        be.setBogeyData(data);
        sync(be);
    }

    public static BogeyYawVisual.Link getRailLink(AbstractBogeyBlockEntity be) {
        CompoundTag data = be.getBogeyData();
        return data != null && data.hasUUID("LoconauticsRailGraph")
            ? new BogeyYawVisual.Link(
                data.getUUID("LoconauticsRailGraph"),
                data.getInt("LoconauticsRailLeadN1"),
                data.getInt("LoconauticsRailLeadN2"),
                data.getFloat("LoconauticsRailLeadPos"),
                data.getInt("LoconauticsRailTrailN1"),
                data.getInt("LoconauticsRailTrailN2"),
                data.getFloat("LoconauticsRailTrailPos"),
                data.getBoolean("LoconauticsRailChordFlip")
            )
            : null;
    }

    public static void setRailLink(AbstractBogeyBlockEntity be, BogeyYawVisual.Link link) {
        CompoundTag data = be.getBogeyData();
        if (data == null) {
            data = new CompoundTag();
        }

        if (link == null) {
            if (!data.hasUUID("LoconauticsRailGraph")) {
                return;
            }

            for (String key : new String[]{
                "LoconauticsRailGraph",
                "LoconauticsRailLeadN1",
                "LoconauticsRailLeadN2",
                "LoconauticsRailLeadPos",
                "LoconauticsRailTrailN1",
                "LoconauticsRailTrailN2",
                "LoconauticsRailTrailPos",
                "LoconauticsRailChordFlip"
            }) {
                data.remove(key);
            }
        } else {
            data.putUUID("LoconauticsRailGraph", link.graphId());
            data.putInt("LoconauticsRailLeadN1", link.leadNode1());
            data.putInt("LoconauticsRailLeadN2", link.leadNode2());
            data.putFloat("LoconauticsRailLeadPos", link.leadPos());
            data.putInt("LoconauticsRailTrailN1", link.trailNode1());
            data.putInt("LoconauticsRailTrailN2", link.trailNode2());
            data.putFloat("LoconauticsRailTrailPos", link.trailPos());
            data.putBoolean("LoconauticsRailChordFlip", link.chordFlipped());
        }

        be.setBogeyData(data);
    }

    public static void setRailPose(AbstractBogeyBlockEntity be, BogeyYawVisual.Link link, Vector3f offset, Vector3f counter) {
        CompoundTag data = be.getBogeyData();
        float curOx = 0.0F;
        float curOy = 0.0F;
        float curOz = 0.0F;
        if (data != null) {
            curOx = data.getFloat("LoconauticsRailOffX");
            curOy = data.getFloat("LoconauticsRailOffY");
            curOz = data.getFloat("LoconauticsRailOffZ");
        }

        if (!Objects.equals(link, getRailLink(be))
            || !(Math.abs(curOx - offset.x) < 8.0E-4F)
            || !(Math.abs(curOy - offset.y) < 8.0E-4F)
            || !(Math.abs(curOz - offset.z) < 8.0E-4F)
            || !(counter.distanceSquared(getBodyCounter(be, new Vector3f())) < 6.3999994E-7F)
            || data == null
            || !data.contains("LoconauticsRailOffX")) {
            setRailLink(be, link);
            data = be.getBogeyData();
            if (data == null) {
                data = new CompoundTag();
            }

            data.putFloat("LoconauticsRailOffX", offset.x);
            data.putFloat("LoconauticsRailOffY", offset.y);
            data.putFloat("LoconauticsRailOffZ", offset.z);
            data.putFloat("LoconauticsBodyCounterX", counter.x);
            data.putFloat("LoconauticsBodyCounterY", counter.y);
            data.putFloat("LoconauticsBodyCounterZ", counter.z);
            be.setBogeyData(data);
            sync(be);
        }
    }

    public record Link(UUID graphId, int leadNode1, int leadNode2, float leadPos, int trailNode1, int trailNode2, float trailPos, boolean chordFlipped) {
    }
}

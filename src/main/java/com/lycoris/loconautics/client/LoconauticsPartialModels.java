package com.lycoris.loconautics.client;

import com.lycoris.loconautics.LoconauticsConstants;
import com.lycoris.loconautics.content.conductorshat.ConductorsHats;
import com.lycoris.loconautics.content.controls.panel.PanelGlass;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyle;
import com.lycoris.loconautics.content.controls.panel.style.PanelStyles;
import com.simibubi.create.content.fluids.FluidTransportBehaviour.AttachmentTypes.ComponentPartials;
import dev.engine_room.flywheel.lib.model.baked.PartialModel;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import net.createmod.catnip.data.Iterate;
import net.createmod.catnip.lang.Lang;
import net.minecraft.core.Direction;
import net.minecraft.world.item.DyeColor;

public class LoconauticsPartialModels {
    public static final PartialModel TURNTABLE_COG = PartialModel.of(LoconauticsConstants.id("block/turntablebearing/cog"));
    public static final PartialModel TURNTABLE_SHAFT = PartialModel.of(LoconauticsConstants.id("block/turntablebearing/shaft"));
    public static final PartialModel CONTROLS_LEVER = PartialModel.of(LoconauticsConstants.id("block/analogcontroller/controls_lever"));
    public static final PartialModel GAUGE_BACK = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/back"));
    public static final PartialModel BODY = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/body"));
    public static final PartialModel SLIM_BODY = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/slim_body"));
    public static final PartialModel BIG_BODY = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/big_body"));
    public static final PartialModel PLATE = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/plate"));
    public static final PartialModel SLIM_PLATE = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/slim_plate"));
    public static final PartialModel GAUGE_FACE = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/face"));
    public static final PartialModel BUTTON_CAP = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/button_cap"));
    public static final PartialModel LEVER_HANDLE = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/lever_handle"));
    public static final PartialModel LEVER_KNOB = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/lever_knob"));
    public static final Map<String, LoconauticsPartialModels.StyleKit> LEVER_STYLE_KITS = new HashMap<>();
    public static final Map<String, PartialModel> BUTTON_STYLE_CAPS;
    public static final PartialModel LEVER_BULB;
    public static final PartialModel GLASS_BODY;
    public static final PartialModel GLASS_SLIM;
    public static final PartialModel GAUGE_BULB;
    public static final PartialModel SWITCH_BULB;
    public static final PartialModel SWITCH_PADDLE;
    public static final PartialModel PANEL_RECEIVER;
    public static final PartialModel PANEL_RECEIVER_POWERED;
    public static final PartialModel VALVE_BASE;
    public static final PartialModel VALVE_HANDLE;
    public static final PartialModel NEEDLE_LINEAR;
    public static final PartialModel NEEDLE_DEFAULT;
    public static final PartialModel NEEDLE_CLASSIC;
    public static final PartialModel NEEDLE_VERTICAL;
    public static final PartialModel SCREW_LINK_LINK;
    public static final PartialModel SCREW_LINK_KNOT;
    public static final PartialModel STEEL_CABLE_ROPE;
    public static final PartialModel STEEL_CABLE_KNOT;
    public static final PartialModel STEEL_CABLE_CONNECTOR_KNOT;
    public static final PartialModel HOSE_CONNECTOR_KNOT;
    public static final PartialModel STEEL_CABLE_WINCH_COIL;
    public static final PartialModel KNUCKLE_HAND;
    public static final PartialModel KNUCKLE_COUPLING;
    public static final PartialModel KNUCKLE_PIN;
    public static final PartialModel KNUCKLE_STRETCH_ARM;
    public static final Map<DyeColor, PartialModel> ENGINE_PISTONS;
    public static final Map<DyeColor, PartialModel> BUFFER_SLEEVES;
    public static final Map<DyeColor, PartialModel> BUFFER_RODS;
    public static final Map<DyeColor, PartialModel> BUFFER_HEADS;
    public static final PartialModel HOSE_STRAND;
    public static final PartialModel HOSE_KNOT;
    public static final PartialModel HATCH_FLUID_BASE;
    public static final PartialModel HATCH_FLUID_LID;
    public static final PartialModel FIREBOX_DOOR_LEFT;
    public static final PartialModel FIREBOX_DOOR_RIGHT;
    public static final PartialModel FIREBOX_LEVER_ARM;
    public static final PartialModel HOSE_WINCH_COIL;
    public static final PartialModel BARREL_PIPE_CONNECTOR;
    public static final PartialModel BOGEY_DRIVEN_TOP;
    public static final PartialModel BOGEY_SHAFTED;
    public static final PartialModel BOGEY_DRIVEN_SHAFTED;
    public static final PartialModel INDUSTRIAL_PIPE_CASING;
    public static final PartialModel RELOCATOR_DRAWHANDLE;
    public static final PartialModel RELOCATOR_LOWER_JAW;
    public static final PartialModel RELOCATOR_GREATER_JAW;
    public static final PartialModel RELOCATOR_OMNICOOB;
    public static final Map<ComponentPartials, Map<Direction, PartialModel>> INDUSTRIAL_PIPE_ATTACHMENTS;
    public static final Map<DyeColor, PartialModel> CONDUCTORS_HATS;
    public static final Map<String, PartialModel> PULL_BRAKE_WHEELS;
    public static final Map<String, PartialModel> PULL_BRAKE_CHAINS;

    public static PartialModel glass(PanelGlass glass) {
        return switch (glass) {
            case BODY -> GLASS_BODY;
            case SLIM -> GLASS_SLIM;
        };
    }

    public static PartialModel conductorsHat(long seed) {
        return CONDUCTORS_HATS.get(ConductorsHats.colorFor(seed));
    }

    public static void init() {
    }

    static {
        for (PanelStyle style : PanelStyles.LEVER.all()) {
            PanelStyle.ModelKit kit = style.kit();
            if (kit != null) {
                String path = "block/controls/gauge/lever_" + kit.name() + "_";
                LEVER_STYLE_KITS.put(
                    kit.name(),
                    new LoconauticsPartialModels.StyleKit(
                        PartialModel.of(LoconauticsConstants.id(path + "handle")),
                        PartialModel.of(LoconauticsConstants.id(path + "knob")),
                        kit.base() ? PartialModel.of(LoconauticsConstants.id(path + "base")) : null
                    )
                );
            }
        }

        BUTTON_STYLE_CAPS = new HashMap<>();

        for (PanelStyle style : PanelStyles.BUTTON.all()) {
            PanelStyle.ModelKit kit = style.kit();
            if (kit != null) {
                BUTTON_STYLE_CAPS.put(kit.name(), PartialModel.of(LoconauticsConstants.id("block/controls/gauge/button_" + kit.name() + "_cap")));
            }
        }

        LEVER_BULB = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/lever_bulb"));
        GLASS_BODY = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/glass_body"));
        GLASS_SLIM = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/glass_slim"));
        GAUGE_BULB = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/bulb"));
        SWITCH_BULB = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/switch_bulb"));
        SWITCH_PADDLE = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/switch_paddle"));
        PANEL_RECEIVER = PartialModel.of(LoconauticsConstants.id("block/controls/panel/receiver"));
        PANEL_RECEIVER_POWERED = PartialModel.of(LoconauticsConstants.id("block/controls/panel/receiver_powered"));
        VALVE_BASE = PartialModel.of(LoconauticsConstants.id("block/controls/valve/base"));
        VALVE_HANDLE = PartialModel.of(LoconauticsConstants.id("block/controls/valve/handle"));
        NEEDLE_LINEAR = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_linear"));
        NEEDLE_DEFAULT = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_default"));
        NEEDLE_CLASSIC = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_classic"));
        NEEDLE_VERTICAL = PartialModel.of(LoconauticsConstants.id("block/controls/gauge/needle_vertical"));
        SCREW_LINK_LINK = PartialModel.of(LoconauticsConstants.id("block/screwlink/link"));
        SCREW_LINK_KNOT = PartialModel.of(LoconauticsConstants.id("block/screwlink/knot"));
        STEEL_CABLE_ROPE = PartialModel.of(LoconauticsConstants.id("block/steelcable/rope"));
        STEEL_CABLE_KNOT = PartialModel.of(LoconauticsConstants.id("block/steelcable/knot"));
        STEEL_CABLE_CONNECTOR_KNOT = PartialModel.of(LoconauticsConstants.id("block/steelcable/connector_knot"));
        HOSE_CONNECTOR_KNOT = PartialModel.of(LoconauticsConstants.id("block/hose/connector_knot"));
        STEEL_CABLE_WINCH_COIL = PartialModel.of(LoconauticsConstants.id("block/steelcable/winch_coil"));
        KNUCKLE_HAND = PartialModel.of(LoconauticsConstants.id("block/knuckle/hand"));
        KNUCKLE_COUPLING = PartialModel.of(LoconauticsConstants.id("block/knuckle/coupling"));
        KNUCKLE_PIN = PartialModel.of(LoconauticsConstants.id("block/knuckle/pin"));
        KNUCKLE_STRETCH_ARM = PartialModel.of(LoconauticsConstants.id("block/knuckle/stretch_arm"));
        ENGINE_PISTONS = new EnumMap<>(DyeColor.class);

        for (DyeColor color : DyeColor.values()) {
            ENGINE_PISTONS.put(color, PartialModel.of(LoconauticsConstants.id("block/combustion/" + color.getSerializedName() + "/piston")));
        }

        BUFFER_SLEEVES = new EnumMap<>(DyeColor.class);
        BUFFER_RODS = new EnumMap<>(DyeColor.class);
        BUFFER_HEADS = new EnumMap<>(DyeColor.class);

        for (DyeColor color : DyeColor.values()) {
            String folder = "block/buffer/" + color.getSerializedName() + "/";
            BUFFER_SLEEVES.put(color, PartialModel.of(LoconauticsConstants.id(folder + "sleeve")));
            BUFFER_RODS.put(color, PartialModel.of(LoconauticsConstants.id(folder + "rod")));
            BUFFER_HEADS.put(color, PartialModel.of(LoconauticsConstants.id(folder + "head")));
        }

        HOSE_STRAND = PartialModel.of(LoconauticsConstants.id("block/hose/strand"));
        HOSE_KNOT = PartialModel.of(LoconauticsConstants.id("block/hose/knot"));
        HATCH_FLUID_BASE = PartialModel.of(LoconauticsConstants.id("block/hatch/fluid_hatch_gray"));
        HATCH_FLUID_LID = PartialModel.of(LoconauticsConstants.id("block/hatch/fluid_hatch_lid_gray"));
        FIREBOX_DOOR_LEFT = PartialModel.of(LoconauticsConstants.id("block/boiler/firebox/door_left"));
        FIREBOX_DOOR_RIGHT = PartialModel.of(LoconauticsConstants.id("block/boiler/firebox/door_right"));
        FIREBOX_LEVER_ARM = PartialModel.of(LoconauticsConstants.id("block/boiler/firebox/lever_arm"));
        HOSE_WINCH_COIL = PartialModel.of(LoconauticsConstants.id("block/hose/coil"));
        BARREL_PIPE_CONNECTOR = PartialModel.of(LoconauticsConstants.id("block/boiler/barrel/pipe_connector"));
        BOGEY_DRIVEN_TOP = PartialModel.of(LoconauticsConstants.id("block/bearingaxle/bogey/top"));
        BOGEY_SHAFTED = PartialModel.of(LoconauticsConstants.id("block/bearingaxle/bogey/shafted"));
        BOGEY_DRIVEN_SHAFTED = PartialModel.of(LoconauticsConstants.id("block/bearingaxle/bogey/driven_shafted"));
        INDUSTRIAL_PIPE_CASING = PartialModel.of(LoconauticsConstants.id("block/industrial/pipe/casing"));
        RELOCATOR_DRAWHANDLE = PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/drawhandle"));
        RELOCATOR_LOWER_JAW = PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/lower_jaw"));
        RELOCATOR_GREATER_JAW = PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/greater_jaw"));
        RELOCATOR_OMNICOOB = PartialModel.of(LoconauticsConstants.id("item/creative_train_relocator/omnicoob"));
        INDUSTRIAL_PIPE_ATTACHMENTS = new EnumMap<>(ComponentPartials.class);

        for (ComponentPartials type : ComponentPartials.values()) {
            Map<Direction, PartialModel> map = new HashMap<>();

            for (Direction d : Iterate.directions) {
                map.put(d, PartialModel.of(LoconauticsConstants.id("block/industrial/pipe/" + Lang.asId(type.name()) + "/" + Lang.asId(d.getSerializedName()))));
            }

            INDUSTRIAL_PIPE_ATTACHMENTS.put(type, map);
        }

        CONDUCTORS_HATS = new EnumMap<>(DyeColor.class);

        for (DyeColor color : DyeColor.values()) {
            CONDUCTORS_HATS.put(color, PartialModel.of(LoconauticsConstants.id("entity/conductors_hat/" + color.getSerializedName())));
        }

        PULL_BRAKE_WHEELS = new HashMap<>();
        PULL_BRAKE_CHAINS = new HashMap<>();
        List<String> names = new ArrayList<>(List.of("brass"));

        for (DyeColor color : DyeColor.values()) {
            names.add(color.getSerializedName());
        }

        for (String name : names) {
            String folder = "block/brakes/pull/" + name + "/";
            PULL_BRAKE_WHEELS.put(name, PartialModel.of(LoconauticsConstants.id(folder + "wheel")));
            PULL_BRAKE_CHAINS.put(name, PartialModel.of(LoconauticsConstants.id(folder + "chain")));
        }
    }

    public record StyleKit(PartialModel handle, PartialModel knob, PartialModel base) {
    }
}

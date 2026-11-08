package com.lycoris.loconautics.content.controls.panel.style;

public final class PanelStyles {
    public static final PanelStyleSet LEVER = new PanelStyleSet("lever");
    public static final PanelStyleSet SWITCH = new PanelStyleSet("switch");
    public static final PanelStyleSet GAUGE = new PanelStyleSet("gauge");
    public static final PanelStyleSet BUTTON = new PanelStyleSet("button");
    public static final int GAUGE_PLATE = GAUGE.registerOption("plate", true);
    public static final int GAUGE_LIGHT = GAUGE.registerOption("light", false);
    public static final int BUTTON_PLATE = BUTTON.registerOption("plate", true);
    public static final int BUTTON_LIGHT = BUTTON.registerOption("light", false);
    public static final int BUTTON_GLASS = BUTTON.registerOption("glass", false);
    public static final int LEVER_PLATE = LEVER.registerOption("plate", true);
    public static final int LEVER_LIGHT = LEVER.registerOption("light", false);
    public static final int SWITCH_PLATE = SWITCH.registerOption("plate", true);
    public static final int SWITCH_LIGHT = SWITCH.registerOption("light", false);
    public static final int SWITCH_GLASS = SWITCH.registerOption("glass", false);

    private PanelStyles() {
    }

    static {
        LEVER.register("default", null);
        LEVER.register("ball", new PanelStyle.ModelKit("ball", 5, 3, true));
        SWITCH.register("default", null);
        GAUGE.register("default", null);
        BUTTON.register("default", null);
        BUTTON.register("big", new PanelStyle.ModelKit("big", 12, 6, false), false);
    }
}

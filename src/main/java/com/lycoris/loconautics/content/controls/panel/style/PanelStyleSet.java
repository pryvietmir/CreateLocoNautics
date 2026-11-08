package com.lycoris.loconautics.content.controls.panel.style;

import java.util.ArrayList;
import java.util.List;
import net.minecraft.util.Mth;
import org.jetbrains.annotations.Nullable;

public final class PanelStyleSet {
    private final String partName;
    private final List<PanelStyle> styles = new ArrayList<>();
    private final List<PanelOption> options = new ArrayList<>();

    PanelStyleSet(String partName) {
        this.partName = partName;
    }

    void register(String id, @Nullable PanelStyle.ModelKit kit) {
        this.styles.add(new PanelStyle(id, kit));
    }

    void register(String id, @Nullable PanelStyle.ModelKit kit, boolean options) {
        this.styles.add(new PanelStyle(id, kit, options));
    }

    int registerOption(String id, boolean defaultOn) {
        this.options.add(new PanelOption(id, defaultOn));
        return this.options.size() - 1;
    }

    public int count() {
        return this.styles.size();
    }

    public int clamp(int index) {
        return Mth.clamp(index, 0, this.styles.size() - 1);
    }

    public List<PanelStyle> all() {
        return this.styles;
    }

    public PanelStyle get(int index) {
        return this.styles.get(this.clamp(index));
    }

    public String langKey(int index) {
        return "gui.loconautics.selector.style." + this.partName + "." + this.get(index).id();
    }

    public int optionCount() {
        return this.options.size();
    }

    public PanelOption option(int index) {
        return this.options.get(index);
    }

    public String optionLangKey(int index) {
        return "gui.loconautics.selector.option." + this.partName + "." + this.options.get(index).id();
    }

    public int defaultOptions() {
        int mask = 0;

        for (int i = 0; i < this.options.size(); i++) {
            if (this.options.get(i).defaultOn()) {
                mask |= 1 << i;
            }
        }

        return mask;
    }

    public int clampOptions(int mask) {
        return mask & (1 << this.options.size()) - 1;
    }

    public boolean hasOptions(int style) {
        return this.get(style).options();
    }

    public int effectiveOptions(int mask, int style) {
        return this.hasOptions(style) ? mask : 0;
    }

    public boolean isOn(int mask, int option) {
        return (mask & 1 << option) != 0;
    }

    public int toggle(int mask, int option) {
        return mask ^ 1 << option;
    }
}

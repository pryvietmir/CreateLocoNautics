package com.lycoris.loconautics.content.schedule;

import com.lycoris.loconautics.common.turntable.TurntableRegistry;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.annotations.Nullable;

final class ManeuverPlan {
    private final List<ManeuverPlan.Leg> legs = new ArrayList<>();
    private int index;

    int currentRel() {
        return this.index < this.legs.size() ? this.legs.get(this.index).rel() : 0;
    }

    void advance() {
        this.index++;
    }

    static boolean backsOut(TurntableRegistry.Exit hop) {
        return hop.movingHead().equals(hop.entryHead());
    }

    void record(@Nullable TurntableRegistry.Exit hop, int rel) {
        this.place(new ManeuverPlan.Leg(rel, hop, false));
        if (hop != null) {
            int after = !hop.entry() && backsOut(hop) ? -rel : rel;
            this.legs.add(new ManeuverPlan.Leg(after, null, false));
        }
    }

    void recordReversal(int rel) {
        this.place(new ManeuverPlan.Leg(rel, null, true));
        this.legs.add(new ManeuverPlan.Leg(-rel, null, false));
    }

    private void place(ManeuverPlan.Leg leg) {
        while (this.legs.size() <= this.index) {
            this.legs.add(leg);
        }

        this.legs.set(this.index, leg);

        while (this.legs.size() > this.index + 1) {
            this.legs.remove(this.legs.size() - 1);
        }
    }

    @Override
    public String toString() {
        StringBuilder text = new StringBuilder();

        for (int i = 0; i < this.legs.size(); i++) {
            ManeuverPlan.Leg leg = this.legs.get(i);
            text.append(i == this.index ? "[" : "")
                .append(leg.rel() > 0 ? "+" : "-")
                .append(leg.reversal() ? "r" : "")
                .append(leg.hop() == null ? "" : (leg.hop().entry() ? "e" : (backsOut(leg.hop()) ? "b" : "s")))
                .append(i == this.index ? "]" : "")
                .append(i + 1 < this.legs.size() ? " " : "");
        }

        return text.toString();
    }

    record Leg(int rel, @Nullable TurntableRegistry.Exit hop, boolean reversal) {
    }
}

package com.lycoris.loconautics.content.controls.panel;

public final class PanelGlassLid {
    public static final float SPEED = 2.0F;
    private static final float BEZIER_HANDLE = 0.1F;
    private static final int BEZIER_STEPS = 16;
    private static final PanelGlassLid.Key[] OPEN = new PanelGlassLid.Key[]{
        new PanelGlassLid.Key(0.0F, 0.0F, PanelGlassLid.Kind.LINEAR),
        new PanelGlassLid.Key(0.5F, 100.0F, PanelGlassLid.Kind.LINEAR),
        new PanelGlassLid.Key(0.75F, 100.0F, PanelGlassLid.Kind.CATMULL)
    };
    private static final PanelGlassLid.Key[] CLOSE = new PanelGlassLid.Key[]{
        new PanelGlassLid.Key(0.0F, 100.0F, PanelGlassLid.Kind.CATMULL),
        new PanelGlassLid.Key(0.25F, 100.0F, PanelGlassLid.Kind.BEZIER),
        new PanelGlassLid.Key(0.75F, 15.0F, PanelGlassLid.Kind.BEZIER),
        new PanelGlassLid.Key(1.0F, 0.0F, PanelGlassLid.Kind.CATMULL)
    };

    private PanelGlassLid() {
    }

    public static float angle(boolean opening, float seconds) {
        return sample(opening ? OPEN : CLOSE, seconds * 2.0F);
    }

    private static float sample(PanelGlassLid.Key[] keys, float time) {
        if (time <= keys[0].time) {
            return keys[0].value;
        }

        PanelGlassLid.Key last = keys[keys.length - 1];
        if (time >= last.time) {
            return last.value;
        }

        int i = 0;

        while (keys[i + 1].time <= time) {
            i++;
        }

        PanelGlassLid.Key before = keys[i];
        PanelGlassLid.Key after = keys[i + 1];
        float alpha = (time - before.time) / (after.time - before.time);
        if (before.kind != PanelGlassLid.Kind.CATMULL && after.kind != PanelGlassLid.Kind.CATMULL) {
            return before.kind != PanelGlassLid.Kind.BEZIER && after.kind != PanelGlassLid.Kind.BEZIER
                ? before.value + (after.value - before.value) * alpha
                : bezier(before, after, time);
        }

        float p0 = i > 0 ? keys[i - 1].value : before.value;
        float p3 = i + 2 < keys.length ? keys[i + 2].value : after.value;
        return catmull(p0, before.value, after.value, p3, alpha);
    }

    private static float catmull(float p0, float p1, float p2, float p3, float t) {
        float t2 = t * t;
        float t3 = t2 * t;
        return 0.5F * (2.0F * p1 + (p2 - p0) * t + (2.0F * p0 - 5.0F * p1 + 4.0F * p2 - p3) * t2 + (3.0F * p1 - p0 - 3.0F * p2 + p3) * t3);
    }

    private static float bezier(PanelGlassLid.Key before, PanelGlassLid.Key after, float time) {
        float x0 = before.time;
        float x1 = before.time + 0.1F;
        float x2 = after.time - 0.1F;
        float x3 = after.time;
        float low = 0.0F;
        float high = 1.0F;

        for (int step = 0; step < 16; step++) {
            float mid = (low + high) * 0.5F;
            if (cubic(x0, x1, x2, x3, mid) < time) {
                low = mid;
            } else {
                high = mid;
            }
        }

        float s = (low + high) * 0.5F;
        return cubic(before.value, before.value, after.value, after.value, s);
    }

    private static float cubic(float a, float b, float c, float d, float s) {
        float inverse = 1.0F - s;
        return inverse * inverse * inverse * a + 3.0F * inverse * inverse * s * b + 3.0F * inverse * s * s * c + s * s * s * d;
    }

    private record Key(float time, float value, PanelGlassLid.Kind kind) {
    }

    private enum Kind {
        LINEAR,
        CATMULL,
        BEZIER;
    }
}

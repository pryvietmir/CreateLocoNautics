package com.lycoris.loconautics.common.sable;

import dev.ryanhcode.sable.companion.math.BoundingBox3ic;
import dev.ryanhcode.sable.sublevel.SubLevel;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.core.BlockPos;
import org.joml.Vector3d;
import org.joml.Vector3dc;

public final class SubLevelOverlap {
    private static final double EPS = 1.0E-6;
    private static final double MIN_CROSS_LENGTH = 1.0E-4;

    private SubLevelOverlap() {
    }

    public static boolean intersects(SubLevel a, SubLevel b) {
        return test(a, b) != null;
    }

    public static SubLevelOverlap.Hit test(SubLevel a, SubLevel b) {
        return test(a, b, null, null);
    }

    public static SubLevelOverlap.Hit test(SubLevel a, SubLevel b, Vector3dc shiftA, Vector3dc shiftB) {
        if (a != null && b != null && a != b) {
            SubLevelOverlap.Box boxA = SubLevelOverlap.Box.of(a, shiftA);
            SubLevelOverlap.Box boxB = SubLevelOverlap.Box.of(b, shiftB);
            if (boxA != null && boxB != null) {
                if (boxA.center().distance(boxB.center()) > boxA.radius() + boxB.radius()) {
                    return null;
                } else {
                    return separatingAxis(boxA, boxB) == null ? null : deepestBlockHit(a, b, boxA, boxB, shiftA, shiftB);
                }
            } else {
                return null;
            }
        } else {
            return null;
        }
    }

    private static SubLevelOverlap.Hit deepestBlockHit(
        SubLevel a, SubLevel b, SubLevelOverlap.Box wholeA, SubLevelOverlap.Box wholeB, Vector3dc shiftA, Vector3dc shiftB
    ) {
        List<SubLevelOverlap.Box> blocksA = blocksTouching(a, shiftA, wholeB);
        if (blocksA.isEmpty()) {
            return null;
        }

        List<SubLevelOverlap.Box> blocksB = blocksTouching(b, shiftB, wholeA);
        SubLevelOverlap.Hit deepest = null;

        for (SubLevelOverlap.Box blockA : blocksA) {
            for (SubLevelOverlap.Box blockB : blocksB) {
                SubLevelOverlap.Hit hit = separatingAxis(blockA, blockB);
                if (hit != null && (deepest == null || hit.depth() > deepest.depth())) {
                    deepest = hit;
                }
            }
        }

        return deepest;
    }

    private static List<SubLevelOverlap.Box> blocksTouching(SubLevel sub, Vector3dc shift, SubLevelOverlap.Box other) {
        List<SubLevelOverlap.Box> touching = new ArrayList<>();
        SubLevelBlocks.forEachBlock(sub, (pos, state) -> {
            if (state.isAir()) {
                return true;
            }

            SubLevelOverlap.Box block = SubLevelOverlap.Box.ofBlock(sub, pos, shift);
            if (separatingAxis(block, other) != null) {
                touching.add(block);
            }

            return true;
        });
        return touching;
    }

    private static SubLevelOverlap.Hit separatingAxis(SubLevelOverlap.Box a, SubLevelOverlap.Box b) {
        double[][] r = new double[3][3];
        double[][] absR = new double[3][3];

        for (int i = 0; i < 3; i++) {
            for (int j = 0; j < 3; j++) {
                r[i][j] = a.axes()[i].dot(b.axes()[j]);
                absR[i][j] = Math.abs(r[i][j]) + 1.0E-6;
            }
        }

        Vector3d t = new Vector3d(b.center()).sub(a.center());
        double[] ta = new double[]{t.dot(a.axes()[0]), t.dot(a.axes()[1]), t.dot(a.axes()[2])};
        double best = Double.MAX_VALUE;
        Vector3d bestAxis = null;

        for (int i = 0; i < 3; i++) {
            double reach = b.half()[0] * absR[i][0] + b.half()[1] * absR[i][1] + b.half()[2] * absR[i][2];
            double overlap = a.half()[i] + reach - Math.abs(ta[i]);
            if (overlap <= 0.0) {
                return null;
            }

            if (overlap < best) {
                best = overlap;
                bestAxis = new Vector3d(a.axes()[i]).mul(ta[i] >= 0.0 ? 1.0 : -1.0);
            }
        }

        for (int j = 0; j < 3; j++) {
            double reach = a.half()[0] * absR[0][j] + a.half()[1] * absR[1][j] + a.half()[2] * absR[2][j];
            double along = ta[0] * r[0][j] + ta[1] * r[1][j] + ta[2] * r[2][j];
            double overlap = reach + b.half()[j] - Math.abs(along);
            if (overlap <= 0.0) {
                return null;
            }

            if (overlap < best) {
                best = overlap;
                bestAxis = new Vector3d(b.axes()[j]).mul(along >= 0.0 ? 1.0 : -1.0);
            }
        }

        for (int i = 0; i < 3; i++) {
            int i1 = (i + 1) % 3;
            int i2 = (i + 2) % 3;

            for (int j = 0; j < 3; j++) {
                int j1 = (j + 1) % 3;
                int j2 = (j + 2) % 3;
                double reachA = a.half()[i1] * absR[i2][j] + a.half()[i2] * absR[i1][j];
                double reachB = b.half()[j1] * absR[i][j2] + b.half()[j2] * absR[i][j1];
                double gap = Math.abs(ta[i2] * r[i1][j] - ta[i1] * r[i2][j]);
                if (reachA + reachB - gap <= 0.0) {
                    return null;
                }

                double length = Math.sqrt(Math.max(0.0, 1.0 - r[i][j] * r[i][j]));
                if (!(length < 1.0E-4)) {
                    double overlap = (reachA + reachB - gap) / length;
                    if (overlap < best) {
                        best = overlap;
                        Vector3d axis = new Vector3d(a.axes()[i]).cross(b.axes()[j]).normalize();
                        bestAxis = axis.mul(t.dot(axis) >= 0.0 ? 1.0 : -1.0);
                    }
                }
            }
        }

        return bestAxis == null ? null : new SubLevelOverlap.Hit(bestAxis, best);
    }

    private record Box(Vector3d center, Vector3d[] axes, double[] half, double radius) {
        static SubLevelOverlap.Box ofBlock(SubLevel sub, BlockPos pos, Vector3dc shift) {
            Vector3d[] axes = new Vector3d[]{
                SubLevelData.directionOf(sub, new Vector3d(1.0, 0.0, 0.0)),
                SubLevelData.directionOf(sub, new Vector3d(0.0, 1.0, 0.0)),
                SubLevelData.directionOf(sub, new Vector3d(0.0, 0.0, 1.0))
            };
            Vector3d center = SubLevelData.worldOf(sub, new Vector3d(pos.getX() + 0.5, pos.getY() + 0.5, pos.getZ() + 0.5));
            if (shift != null) {
                center.add(shift);
            }

            return new SubLevelOverlap.Box(center, axes, new double[]{0.5, 0.5, 0.5}, Math.sqrt(0.75));
        }

        static SubLevelOverlap.Box of(SubLevel sub, Vector3dc shift) {
            BoundingBox3ic bounds = sub.getPlot().getBoundingBox();
            double hx = (bounds.maxX() + 1 - bounds.minX()) * 0.5;
            double hy = (bounds.maxY() + 1 - bounds.minY()) * 0.5;
            double hz = (bounds.maxZ() + 1 - bounds.minZ()) * 0.5;
            if (!(hx <= 0.0) && !(hy <= 0.0) && !(hz <= 0.0)) {
                Vector3d local = new Vector3d(bounds.minX() + hx, bounds.minY() + hy, bounds.minZ() + hz);
                Vector3d[] axes = new Vector3d[]{
                    SubLevelData.directionOf(sub, new Vector3d(1.0, 0.0, 0.0)),
                    SubLevelData.directionOf(sub, new Vector3d(0.0, 1.0, 0.0)),
                    SubLevelData.directionOf(sub, new Vector3d(0.0, 0.0, 1.0))
                };
                Vector3d center = SubLevelData.worldOf(sub, local);
                if (shift != null) {
                    center.add(shift);
                }

                return new SubLevelOverlap.Box(center, axes, new double[]{hx, hy, hz}, Math.sqrt(hx * hx + hy * hy + hz * hz));
            } else {
                return null;
            }
        }
    }

    public record Hit(Vector3dc axis, double depth) {
    }
}

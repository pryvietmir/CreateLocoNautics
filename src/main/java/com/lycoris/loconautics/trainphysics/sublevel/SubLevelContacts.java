package com.lycoris.loconautics.trainphysics.sublevel;

import com.lycoris.loconautics.trainphysics.wheels.GhostWheelBodies;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintConfiguration;
import dev.ryanhcode.sable.api.physics.constraint.FreeConstraintHandle;
import dev.ryanhcode.sable.api.sublevel.ServerSubLevelContainer;
import dev.ryanhcode.sable.api.sublevel.SubLevelContainer;
import dev.ryanhcode.sable.sublevel.ServerSubLevel;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.minecraft.core.BlockPos;
import org.joml.Quaterniond;
import org.joml.Vector3d;

public final class SubLevelContacts {
    private static final Map<SubLevelContacts.Pair, SubLevelContacts.Contact> DISABLED = new HashMap<>();

    private SubLevelContacts() {
    }

    public static void clear() {
        DISABLED.clear();
    }

    public static boolean isDisabled(UUID a, UUID b) {
        SubLevelContacts.Contact contact = DISABLED.get(SubLevelContacts.Pair.of(a, b));
        return contact != null && contact.handle().isValid();
    }

    public static Set<SubLevelContacts.Pair> disabledPairs() {
        return Set.copyOf(DISABLED.keySet());
    }

    public static boolean disable(ServerSubLevel a, ServerSubLevel b) {
        if (a != null && b != null && a != b && !a.isRemoved() && !b.isRemoved()) {
            SubLevelContacts.Pair key = SubLevelContacts.Pair.of(a.getUniqueId(), b.getUniqueId());
            SubLevelContacts.Contact live = DISABLED.get(key);
            if (live != null && live.handle().isValid()) {
                return true;
            }

            ServerSubLevelContainer container = SubLevelContainer.getContainer(a.getLevel());
            if (container == null) {
                return false;
            }

            FreeConstraintConfiguration config = new FreeConstraintConfiguration(plotCenter(a), plotCenter(b), new Quaterniond());
            FreeConstraintHandle handle = (FreeConstraintHandle)container.physicsSystem().getPipeline().addConstraint(a, b, config);
            if (handle == null) {
                return false;
            }

            handle.setContactsEnabled(false);
            DISABLED.put(key, new SubLevelContacts.Contact(handle, a, b));
            return true;
        } else {
            return false;
        }
    }

    public static boolean disableCoupled(ServerSubLevel a, ServerSubLevel b) {
        boolean done = disable(a, b);
        ServerSubLevelContainer container = a == null ? null : SubLevelContainer.getContainer(a.getLevel());
        if (container != null && b != null) {
            List<ServerSubLevel> sidesA = withWheels(container, a);
            List<ServerSubLevel> sidesB = withWheels(container, b);

            for (ServerSubLevel x : sidesA) {
                for (ServerSubLevel y : sidesB) {
                    if (x != a || y != b) {
                        disable(x, y);
                    }
                }
            }

            return done;
        } else {
            return done;
        }
    }

    public static void enableCoupled(UUID a, UUID b) {
        List<UUID> sidesA = new ArrayList<>(GhostWheelBodies.wheelBodiesOf(a));
        List<UUID> sidesB = new ArrayList<>(GhostWheelBodies.wheelBodiesOf(b));
        sidesA.add(a);
        sidesB.add(b);

        for (UUID x : sidesA) {
            for (UUID y : sidesB) {
                enable(x, y);
            }
        }
    }

    private static List<ServerSubLevel> withWheels(ServerSubLevelContainer container, ServerSubLevel car) {
        List<ServerSubLevel> out = new ArrayList<>();
        out.add(car);

        for (UUID id : GhostWheelBodies.wheelBodiesOf(car.getUniqueId())) {
            if (container.getSubLevel(id) instanceof ServerSubLevel wheel && !wheel.isRemoved()) {
                out.add(wheel);
            }
        }

        return out;
    }

    public static void enable(UUID a, UUID b) {
        SubLevelContacts.Contact contact = DISABLED.remove(SubLevelContacts.Pair.of(a, b));
        if (contact != null && contact.handle().isValid()) {
            contact.handle().remove();
        }
    }

    public static void releaseAll(UUID body) {
        DISABLED.entrySet().removeIf(entry -> {
            if (!entry.getKey().first().equals(body) && !entry.getKey().second().equals(body)) {
                return false;
            }

            if (entry.getValue().handle().isValid()) {
                entry.getValue().handle().remove();
            }

            return true;
        });
    }

    public static void sweep() {
        DISABLED.values().removeIf(contact -> {
            if (!contact.handle().isValid()) {
                return true;
            }

            if (!contact.a().isRemoved() && !contact.b().isRemoved()) {
                return false;
            }

            contact.handle().remove();
            return true;
        });
    }

    private static Vector3d plotCenter(ServerSubLevel sub) {
        BlockPos center = sub.getPlot().getCenterBlock();
        return new Vector3d(center.getX() + 0.5, center.getY() + 0.5, center.getZ() + 0.5);
    }

    private record Contact(FreeConstraintHandle handle, ServerSubLevel a, ServerSubLevel b) {
    }

    public record Pair(UUID first, UUID second) {
        public static SubLevelContacts.Pair of(UUID a, UUID b) {
            return a.compareTo(b) <= 0 ? new SubLevelContacts.Pair(a, b) : new SubLevelContacts.Pair(b, a);
        }
    }
}

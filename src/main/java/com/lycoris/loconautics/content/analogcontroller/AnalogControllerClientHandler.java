package com.lycoris.loconautics.content.analogcontroller;

import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerDismountPacket;
import com.lycoris.loconautics.network.packets.analogcontroller.AnalogControllerInputPacket;
import com.lycoris.loconautics.trainphysics.drive.TrainPropulsion;
import com.lycoris.loconautics.trainphysics.state.SableTrainClientRegistry;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.blaze3d.platform.InputConstants.Key;
import com.mojang.blaze3d.platform.InputConstants.Type;
import com.simibubi.create.foundation.utility.ControlsUtil;
import dev.ryanhcode.sable.Sable;
import dev.ryanhcode.sable.sublevel.ClientSubLevel;
import java.util.Collection;
import java.util.HashSet;
import net.createmod.catnip.platform.CatnipServices;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.api.distmarker.OnlyIn;
import org.lwjgl.glfw.GLFW;

@OnlyIn(Dist.CLIENT)
public class AnalogControllerClientHandler {
    private static final int PACKET_RATE = 5;
    private static BlockPos mountedPos;
    private static Collection<Integer> currentlyPressed = new HashSet<>();
    private static int packetCooldown;

    public static void startControlling(BlockPos pos) {
        mountedPos = pos;
        currentlyPressed.clear();
        packetCooldown = 0;
        Minecraft.getInstance().player.displayClientMessage(Component.translatable("block.loconautics.analog_controller.start"), true);
    }

    public static void stopControllingClient() {
        if (mountedPos != null) {
            CatnipServices.NETWORK.sendToServer(new AnalogControllerDismountPacket(mountedPos));
            stopControlling();
        }
    }

    public static void stopControlling() {
        if (mountedPos != null) {
            ControlsUtil.getControls().forEach(kb -> kb.setDown(ControlsUtil.isActuallyPressed(kb)));
            if (!currentlyPressed.isEmpty()) {
                CatnipServices.NETWORK.sendToServer(new AnalogControllerInputPacket(currentlyPressed, false, mountedPos));
            }

            Minecraft.getInstance().player.displayClientMessage(Component.translatable("block.loconautics.analog_controller.stop"), true);
            mountedPos = null;
            currentlyPressed.clear();
            packetCooldown = 0;
        }
    }

    public static void clear() {
        mountedPos = null;
        currentlyPressed.clear();
        packetCooldown = 0;
    }

    public static boolean isControlling() {
        return mountedPos != null;
    }

    public static BlockPos getMountedPos() {
        return mountedPos;
    }

    public static AnalogControllerBlockEntity resolveMountedBlockEntity() {
        if (mountedPos == null) {
            return null;
        } else {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level == null) {
                return null;
            } else if (mc.level.getBlockEntity(mountedPos) instanceof AnalogControllerBlockEntity hostBe) {
                return hostBe;
            } else {
                ClientSubLevel sub = Sable.HELPER.getContainingClient(mountedPos);
                return sub != null && sub.getLevel().getBlockEntity(mountedPos) instanceof AnalogControllerBlockEntity subBe ? subBe : null;
            }
        }
    }

    public static double speedBarRatio(AnalogControllerBlockEntity be) {
        ClientSubLevel sub = Sable.HELPER.getContainingClient(be.getBlockPos());
        if (sub == null) {
            return 0.0;
        }

        SableTrainClientRegistry.TrainMarker marker = SableTrainClientRegistry.get(sub.getUniqueId());
        return marker == null ? 0.0 : (int)(speedFraction(marker.speed(), marker) * 18.0) / 18.0;
    }

    public static double speedFraction(double speed, SableTrainClientRegistry.TrainMarker marker) {
        double top = marker != null && marker.topSpeed() > 0.0 ? marker.topSpeed() : TrainPropulsion.bearingAxleCapBpt();
        return top <= 0.0 ? 0.0 : Math.min(1.0, Math.abs(speed) / top);
    }

    private static KeyMapping[] controlKeys() {
        Options o = Minecraft.getInstance().options;
        return new KeyMapping[]{o.keyUp, o.keyDown, o.keyLeft, o.keyRight, o.keyJump, o.keyShift, o.keySprint};
    }

    private static boolean isBoundKeyDown(long window, KeyMapping kb) {
        Key key = kb.getKey();
        return key.getType() == Type.MOUSE ? GLFW.glfwGetMouseButton(window, key.getValue()) == 1 : InputConstants.isKeyDown(window, key.getValue());
    }

    public static void tick() {
        if (mountedPos != null) {
            Minecraft mc = Minecraft.getInstance();
            if (mc.level != null && resolveMountedBlockEntity() == null && !(mc.level.getBlockState(mountedPos).getBlock() instanceof AnalogControllerBlock)) {
                stopControlling();
            } else {
                long window = mc.getWindow().getWindow();
                KeyMapping[] keys = controlKeys();
                HashSet<Integer> pressedKeys = new HashSet<>();

                for (int i = 0; i < keys.length; i++) {
                    if (isBoundKeyDown(window, keys[i])) {
                        pressedKeys.add(i);
                    }
                }

                HashSet<Integer> newKeys = new HashSet<>(pressedKeys);
                Collection<Integer> releasedKeys = new HashSet<>(currentlyPressed);
                newKeys.removeAll(currentlyPressed);
                releasedKeys.removeAll(pressedKeys);
                if (!releasedKeys.isEmpty()) {
                    CatnipServices.NETWORK.sendToServer(new AnalogControllerInputPacket(releasedKeys, false, mountedPos));
                }

                if (!newKeys.isEmpty()) {
                    CatnipServices.NETWORK.sendToServer(new AnalogControllerInputPacket(newKeys, true, mountedPos));
                    packetCooldown = 5;
                }

                if (packetCooldown <= 0 && !pressedKeys.isEmpty()) {
                    CatnipServices.NETWORK.sendToServer(new AnalogControllerInputPacket(pressedKeys, true, mountedPos));
                    packetCooldown = 5;
                }

                if (packetCooldown > 0) {
                    packetCooldown--;
                }

                currentlyPressed = pressedKeys;
                ControlsUtil.getControls().forEach(kb -> kb.setDown(false));
            }
        }
    }
}

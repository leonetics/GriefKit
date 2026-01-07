package com.griefkit.managers;

import com.griefkit.placement.PlacementStep;
import net.minecraft.item.BlockItem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

public class PlacementManager {

    public enum Fail {
        NO_PLAYER,
        NO_WORLD,
        NOT_REPLACEABLE,
        MAINHAND_NOT_BLOCKITEM
    }

    public record Result(boolean value, Fail fail) {
        public static Result ok() {
            return new Result(true, null);
        }
        public static Result fail(Fail fail) {
            return new Result(false, fail);
        }
    }


    public Result airplaceStep(MinecraftClient mc, PlacementStep step) {
        if (mc.player == null) return Result.fail(Fail.NO_PLAYER);
        if (mc.world == null) return Result.fail(Fail.NO_WORLD);

        if (!mc.world.getBlockState(step.pos).isReplaceable()) return Result.fail(Fail.NOT_REPLACEABLE);

        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            return Result.fail(Fail.MAINHAND_NOT_BLOCKITEM);
        }

        BlockPos supportPos = step.supportPos;
        Direction face = step.supportFace;

        Vec3d hitVec = Vec3d.ofCenter(supportPos);
        BlockHitResult bhr = new BlockHitResult(hitVec, face, supportPos, false);

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
        ));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2
        ));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN
        ));

        mc.player.swingHand(Hand.MAIN_HAND);
        return Result.ok();
    }
}

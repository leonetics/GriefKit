package com.griefkit.managers.placement;

import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.player.FindItemResult;
import meteordevelopment.meteorclient.utils.player.InvUtils;
import meteordevelopment.meteorclient.utils.world.BlockUtils;
import meteordevelopment.orbit.EventHandler;

import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.MinecraftClient;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

/**
 * shit placement manager. modules submit PlacementRequest's to this and it
 * handles:
 * - queueing
 * - validation (air, range, timeout)
 * - actual placing (hotbar selection + BlockUtils.place/airplace)
 */
public enum PlacementManager {
    INSTANCE;

    private static final int MAX_QUEUE_SIZE = 512; // no crazy shit
    private static final int MAX_PLACEMENTS_PER_TICK = 12;  // hard global cap, 12 sounds nice

    // queued requests + set to dedupe by position
    private final Deque<PlacementRequest> queue = new ArrayDeque<>();
    private final Set<BlockPos> queuedPositions = new HashSet<>();

    private final MinecraftClient mc = MinecraftClient.getInstance();

    public static void init() {
        meteordevelopment.meteorclient.MeteorClient.EVENT_BUS.subscribe(INSTANCE);
    }

    public void enqueue(PlacementRequest request) {
        if (request == null) return;
        if (queuedPositions.contains(request.pos)) return; // get rid of dupes

        // limit overall queue size so no crazy shit but i gotta be more intelligent abt this later
        if (queue.size() >= MAX_QUEUE_SIZE) {
            PlacementRequest old = queue.pollFirst();
            if (old != null) queuedPositions.remove(old.pos);
        }

        queue.addLast(request);
        queuedPositions.add(request.pos);
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;
        if (queue.isEmpty()) return;

        int placedThisTick = 0;

        while (!queue.isEmpty() && placedThisTick < MAX_PLACEMENTS_PER_TICK) {
            PlacementRequest req = queue.pollFirst();
            queuedPositions.remove(req.pos);

            if (!isStillValid(req)) continue;

            if (tryPlace(req)) { // actually place thingy
                placedThisTick++;
            }
        }
    }

    /** check range, timeout, air/replaceable, chunk loaded. */
    private boolean isStillValid(PlacementRequest req) {
        if (mc.player == null || mc.world == null) return false;

        if (req.isExpired()) return false;

        // chunk loaded?
        if (!mc.world.isChunkLoaded(req.pos.getX() >> 4, req.pos.getZ() >> 4)) return false;

        var state = mc.world.getBlockState(req.pos);
        // must still be air/replaceable or we drop it
        if (!state.isAir() && !state.isReplaceable()) return false;

        // distance check but ts kinda stupid
        Vec3d eye = mc.player.getCameraPosVec(1.0f);
        Vec3d center = Vec3d.ofCenter(req.pos);
        if (eye.squaredDistanceTo(center) > req.maxDistanceSq) return false;

        return true;
    }

    private boolean tryPlace(PlacementRequest req) {
        if (mc.player == null || mc.world == null) return false;

        // figure out what item we're placing
        FindItemResult fir;
        if (req.block != null) {
            fir = InvUtils.find(req.block.asItem());
        } else {
            // "any block" mode – any block item in hotbar
            fir = InvUtils.findInHotbar(stack -> stack.getItem() instanceof BlockItem);
        }

        if (!fir.found()) return false;

        // 2. choose placement method
        if (req.airplace) {
            return airplace(req, fir);
        } else {
            // meteor handles slot switch + legit rotation
            return BlockUtils.place(req.pos, fir, true, 0, true);
        }

    }

    private boolean airplace(PlacementRequest req, FindItemResult fir) {
        if (mc.player == null || mc.world == null || mc.player.networkHandler == null) return false;

        // make sure the correct item is in main hand before we start swapping offhand
        InvUtils.swap(fir.slot(), true); // meteor will switch to the right hotbar slot

        BlockPos target = req.pos;

        // --- support / hitVec logic --- skidded from my wither
        BlockPos supportPos;
        Direction face = Direction.UP;

        if (req.block == Blocks.WITHER_SKELETON_SKULL) { // edge case for withers, fix later bc i am shit coder
            // skull sits on top of the soul sand under it
            supportPos = target.down();
        } else {
            // default: airplace at the target position itself
            supportPos = target;
        }

        Vec3d hitVec = Vec3d.ofCenter(supportPos);

        BlockHitResult bhr = new BlockHitResult(
            hitVec,
            face,
            supportPos,
            false
        );

        var nh = mc.player.networkHandler;
        // swap mainhand <-> offhand, place from offhand, then swap back
        nh.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));

        nh.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND,
            bhr,
            mc.player.currentScreenHandler.getRevision() + 2
        ));

        nh.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND,
            BlockPos.ORIGIN,
            Direction.DOWN
        ));

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }
}

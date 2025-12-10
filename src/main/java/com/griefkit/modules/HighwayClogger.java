// leonetics 2025 - highway clogger for nether tunnels

package com.griefkit.modules;

import com.griefkit.GriefKit;
import com.griefkit.managers.placement.PlacementManager;
import com.griefkit.managers.placement.PlacementRequest;

import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import meteordevelopment.meteorclient.settings.DoubleSetting;

import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

import java.util.concurrent.ThreadLocalRandom;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashSet;
import java.util.Set;

public class HighwayClogger extends Module {
    private final SettingGroup sgGeneral = settings.getDefaultGroup();
    private final SettingGroup sgRender  = settings.createGroup("Render");

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many placement requests to send per tick.")
        .defaultValue(4)
        .min(1)
        .max(20)
        .build()
    );

    private final Setting<Double> density = sgGeneral.add(new DoubleSetting.Builder()
        .name("density")
        .description("Density of clog..")
        .defaultValue(0.50)
        .min(0.0)
        .max(1.0)
        .build()
    );

    private final Setting<Boolean> silent = sgGeneral.add(new BoolSetting.Builder()
        .name("silent")
        .description("Disable info/warn spam.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render queued clog blocks behind you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How to render the boxes.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Fill color for queued blocks.")
        .defaultValue(new SettingColor(255, 150, 0, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for queued blocks.")
        .defaultValue(new SettingColor(255, 150, 0, 255))
        .build()
    );

    // queue of positions to clog (module-local, not packets)
    private final Deque<BlockPos> queue = new ArrayDeque<>();
    private final Set<BlockPos> queuedSet = new HashSet<>();

    private BlockPos lastPlayerBlockPos = null;

    // optional per-module cap so this queue can't go insane
    private static final int MAX_LOCAL_QUEUE_SIZE = 512;

    public HighwayClogger() {
        super(GriefKit.CATEGORY, "highway-clogger", "Places blocks behind you as you walk through highways.");
    }

    @Override
    public void onActivate() {
        queue.clear();
        queuedSet.clear();
        lastPlayerBlockPos = null;

        if (!silent.get()) info("Highway Clogger enabled");
    }

    @Override
    public void onDeactivate() {
        queue.clear();
        queuedSet.clear();
        lastPlayerBlockPos = null;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (mc.player == null || mc.world == null) return;

        ClientPlayerEntity player = mc.player;
        BlockPos currentPos = player.getBlockPos();

        // as you move, enqueue blocks behind you in the direction of your yaw.
        if (lastPlayerBlockPos == null || !lastPlayerBlockPos.equals(currentPos)) {
            queueBehind(player, currentPos);
            lastPlayerBlockPos = currentPos;
        }

        // turn queued positions into placement requests and hand them to the global manager.
        int sentThisTick = 0;

        while (!queue.isEmpty() && sentThisTick < blocksPerTick.get()) {
            BlockPos pos = queue.pollFirst();
            queuedSet.remove(pos);

            // local sanity check so we don't send obviously bad requests
            if (!canStillClog(pos)) continue;

            // obsidian from hotbar, airplace, 1s timeout so no stupid shit, ~5 block reach
            PlacementRequest req = new PlacementRequest(
                pos,
                Blocks.OBSIDIAN,
                1000L,
                5.0 * 5.0,
                true,
                "HighwayClogger"
            );

            PlacementManager.INSTANCE.enqueue(req);
            sentThisTick++;
        }
    }

    private void queueBehind(ClientPlayerEntity player, BlockPos playerPos) {
        Direction facing = player.getHorizontalFacing();
        Direction behind = facing.getOpposite();

        int playerY = playerPos.getY();

        // if walking north/south -> width along X
        // if walking east/west  -> width along Z
        boolean widthAlongX = (behind == Direction.NORTH || behind == Direction.SOUTH);

        // 5 blocks wide => offsets -2..2
        int halfWidth = 2;
        // 3 blocks tall => 0,1,2 above base layer (or just 1 layer if sameYOnly)
        int height = 3;

        double p = density.get(); // swiss-cheese probability

        BlockPos base = playerPos.offset(behind, 1);

        for (int w = -halfWidth; w <= halfWidth; w++) {
            for (int h = 0; h < height; h++) {
                int y = playerY + h;

                BlockPos target;
                if (widthAlongX) {
                    target = new BlockPos(base.getX() + w, y, base.getZ());
                } else {
                    target = new BlockPos(base.getX(), y, base.getZ() + w);
                }
                // swiss-cheese: only keep some of the placements
                if (ThreadLocalRandom.current().nextDouble() > p) continue;
                if (!shouldClog(target)) continue;
                if (queuedSet.contains(target)) continue;
                // keep local queue bounded
                if (queue.size() >= MAX_LOCAL_QUEUE_SIZE) {
                    BlockPos old = queue.pollFirst();
                    if (old != null) queuedSet.remove(old);
                }

                queue.addLast(target);
                queuedSet.add(target);
            }
        }

    }

    private boolean shouldClog(BlockPos pos) {
        var world = mc.world;
        var state = world.getBlockState(pos);

        // onlu replace air / replaceable stuff so no stupid shit happens
        if (!state.isAir() && !state.isReplaceable()) return false;

        return true;
    }

    // quick re-check before sending a PlacementRequest
    private boolean canStillClog(BlockPos pos) {
        var state = mc.world.getBlockState(pos);
        return state.isAir() || state.isReplaceable();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (!render.get() || mc.world == null) return;

        for (BlockPos pos : queue) {
            event.renderer.box(
                pos,
                sideColor.get(),
                lineColor.get(),
                shapeMode.get(),
                0
            );
        }
    }
}

// leonetics 2025

package com.griefkit.modules;

import com.griefkit.GriefKit;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;

import java.util.ArrayList;
import java.util.List;

public class Wither extends Module {
    private static int successfulPlacements = 0;

    public static void incrementSuccessfulPlacements() {
        successfulPlacements++;
    }

    public static int getSuccessfulPlacements() {
        return successfulPlacements;
    }

    public static void resetSuccessfulPlacements() {
        successfulPlacements = 0;
    }

    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        // good for people w shit ping, but 2b is wonky
        // once you place a block you have 300ms to place 9 blocks, so it shouldn't really matter
        .description("How many blocks to place per tick")
        .defaultValue(4)
        .min(1)
        .max(9)
        .build()
    );

    private final Setting<Boolean> silentMode = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-notifications")
        .description("Remove notifications")
        .defaultValue(false)
        .build()
    );

    private final Setting<Keybind> witherPlace = sgGeneral.add(new KeybindSetting.Builder()
        .name("wither-place")
        .description("Places a wither")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Keybind> resetBind = sgGeneral.add(new KeybindSetting.Builder()
        .name("reset-counter")
        .description("Resets the successful wither placement counter")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> cursorPlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("cursor-placement")
        .description("Anchor withers around your crosshair instead of only in front of you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cursorMaxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("cursor-max-distance")
        .description("Maximum distance from you for cursor-based wither placement.")
        .defaultValue(6.0) // find a good value for this later
        .min(1.0)
        .max(15.0)
        .build()
    );

    private final Setting<Integer> cursorSearchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("cursor-search-radius")
        .description("Horizontal search radius around the cursor hit to find a valid wither position.")
        .defaultValue(2)
        .min(0)
        .max(10)
        .build()
    );

    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render the planned wither structure.")
        .defaultValue(true)
        .build()
    );

    private final Setting<ShapeMode> shapeMode = sgRender.add(new EnumSetting.Builder<ShapeMode>()
        .name("shape-mode")
        .description("How the boxes are rendered.")
        .defaultValue(ShapeMode.Both)
        .build()
    );

    private final Setting<SettingColor> sideColor = sgRender.add(new ColorSetting.Builder()
        .name("side-color")
        .description("Color of the box sides for blocks that are not yet placed.")
        .defaultValue(new SettingColor(255, 50, 50, 25))
        .build()
    );

    private final Setting<SettingColor> lineColor = sgRender.add(new ColorSetting.Builder()
        .name("line-color")
        .description("Outline color for blocks that are not yet placed.")
        .defaultValue(new SettingColor(255, 50, 50, 255))
        .build()
    );

    private final Setting<SettingColor> placedSideColor = sgRender.add(new ColorSetting.Builder()
        .name("placed-side-color")
        .description("Side color for blocks that are already placed.")
        .defaultValue(new SettingColor(50, 255, 50, 25))
        .build()
    );

    private final Setting<SettingColor> placedLineColor = sgRender.add(new ColorSetting.Builder()
        .name("placed-line-color")
        .description("Outline color for blocks that are already placed.")
        .defaultValue(new SettingColor(50, 255, 50, 255))
        .build()
    );

    // queue of blocks to place
    private final List<PlacementStep> steps = new ArrayList<>();
    private int currentIndex = 0;
    private boolean prepared = false;
    private boolean lastWitherKeyDown = false;
    private boolean lastResetKeyDown = false;

    private enum WitherOrientation {
        VERTICAL,
        HORIZONTAL
    }

    public Wither() {
        super(GriefKit.CATEGORY, "Wither", "Builds a wither in front of you");
    }

    @Override
    public void onActivate() {
        steps.clear();
        currentIndex = 0;
        prepared = false;

        if (mc.player == null || mc.world == null) {
            warning("Player/world not loaded");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        steps.clear();
        currentIndex = 0;
        prepared = false;
    }

    @EventHandler
    private void onTick(TickEvent.Pre event) {
        if (!prepared || mc.player == null || mc.world == null) return;

        if (currentIndex >= steps.size()) {
            if (!silentMode.get()) info("Wither done");
            Wither.incrementSuccessfulPlacements();
            prepared = false;
            return;
        }

        int placedThisTick = 0;

        while (currentIndex < steps.size() && placedThisTick < blocksPerTick.get()) {
            PlacementStep step = steps.get(currentIndex);

            Block currentBlock = mc.world.getBlockState(step.pos).getBlock();
            if (currentBlock == step.block) {
                currentIndex++;
                continue;
            }

            boolean success = placeStepAirplace(step);
            if (!success) {
                // just skip failed placements; user can see from render
            }

            currentIndex++;
            placedThisTick++;
        }
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        boolean witherKeyDown = witherPlace.get().isPressed();
        boolean resetKeyDown = resetBind.get().isPressed();

        // edge-press to actually start a real placement
        if (witherKeyDown && !lastWitherKeyDown && !prepared) {
            // first: inventory sanity check (hotbar only)
            if (!hasRequiredMaterialsInHotbar()) {
                if (!silentMode.get()) warning("Not enough soul sand / wither skulls in hotbar for a wither.");
                // don't even prepare a pattern; just bail.
                prepared = false;
            } else {
                steps.clear();
                currentIndex = 0;
                preparePatten(); // will respect cursorPlacement setting

                if (steps.isEmpty()) {
                    if (!silentMode.get()) warning("No valid build position found");
                    prepared = false;
                } else {
                    prepared = true;
                    if (!silentMode.get()) info("Withering...");
                }
            }
        }

        // reset HUD counter
        if (resetKeyDown && !lastResetKeyDown) {
            if (!silentMode.get()) info("Reset wither placement counter.");
            resetSuccessfulPlacements();
        }

        lastWitherKeyDown = witherKeyDown;
        lastResetKeyDown = resetKeyDown;

        if (!render.get()) return;

        // live preview: if not in a real placement sequence, recompute around cursor
        if (!prepared) {
            steps.clear();
            currentIndex = 0;
            preparePatten();
        }

        if (steps.isEmpty()) return;

        for (int i = 0; i < steps.size(); i++) {
            PlacementStep step = steps.get(i);

            boolean alreadyPlaced = prepared && (
                i < currentIndex
                    || mc.world.getBlockState(step.pos).getBlock() == step.block
            );

            SettingColor side = alreadyPlaced ? placedSideColor.get() : sideColor.get();
            SettingColor line = alreadyPlaced ? placedLineColor.get() : lineColor.get();

            event.renderer.box(step.pos, side, line, shapeMode.get(), 0);
        }
    }

    private boolean isObstructed(BlockPos pos) {
        return !mc.world.getBlockState(pos).isAir()
            && !mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isEntityIntersecting(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;

        Box box = new Box(pos);

        // check local player
        if (!mc.player.isSpectator() && mc.player.isAlive() && mc.player.getBoundingBox().intersects(box)) {
            return true;
        }

        // check all other entities
        return !mc.world.getOtherEntities(
            null,
            box,
            entity -> !entity.isSpectator() && entity.isAlive()
        ).isEmpty();
    }

    private boolean anyBlockedOrEntity(List<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (isObstructed(p) || isEntityIntersecting(p)) {
                return true;
            }
        }
        return false;
    }

    private boolean armsHaveAirBelow(BlockPos stem, Direction facing) {
        int bodyY = stem.getY() + 1;

        BlockPos centerBody = new BlockPos(stem.getX(), bodyY, stem.getZ());
        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        BlockPos leftArm = centerBody.offset(left);
        BlockPos rightArm = centerBody.offset(right);

        BlockPos leftBelow = leftArm.down();
        BlockPos rightBelow = rightArm.down();

        return mc.world.getBlockState(leftBelow).isAir()
            && mc.world.getBlockState(rightBelow).isAir();
    }

    // generic T-pattern builder in local coordinates.
    // origin = stem, upDir = "skull direction", rightDir = +X in pattern space.
    private List<BlockPos> getPatternPositions(BlockPos origin, Direction upDir, Direction rightDir) {
        BlockPos stem = origin;
        BlockPos centerBody = stem.offset(upDir);
        BlockPos leftArm = centerBody.offset(rightDir.getOpposite());
        BlockPos rightArm = centerBody.offset(rightDir);

        BlockPos headCenter = centerBody.offset(upDir);
        BlockPos headLeft = leftArm.offset(upDir);
        BlockPos headRight = rightArm.offset(upDir);

        return List.of(
            stem,
            centerBody,
            leftArm,
            rightArm,
            headLeft,
            headCenter,
            headRight
        );
    }

    private List<BlockPos> getVerticalPattern(BlockPos stem, Direction facing) {
        Direction upDir = Direction.UP;
        Direction rightDir = facing.rotateYClockwise();

        return getPatternPositions(stem, upDir, rightDir);
    }

    // horizontal wither: skulls "stick out" of the wall (facing), body is vertical.
    private List<BlockPos> getHorizontalPattern(BlockPos stem, Direction facing) {
        Direction upDir = facing; // skull direction
        Direction rightDir = facing.rotateYClockwise();

        return getPatternPositions(stem, upDir, rightDir);
    }

    private boolean validateVerticalPattern(BlockPos stem, Direction facing) {
        List<BlockPos> pattern = getVerticalPattern(stem, facing);
        if (anyBlockedOrEntity(pattern)) return false;

        // arms must have air under them in the vertical case
        return armsHaveAirBelow(stem, facing);
    }

    private boolean validateHorizontalPattern(BlockPos stem, Direction facing) {
        List<BlockPos> pattern = getHorizontalPattern(stem, facing);
        return !anyBlockedOrEntity(pattern);
    }

    private BlockPos findBestVerticalStemPos(BlockHitResult hit, Direction facing) {
        if (mc.world == null || mc.player == null) return null;

        BlockPos base;
        switch (hit.getSide()) {
            case UP -> base = hit.getBlockPos().up();
            case DOWN -> base = hit.getBlockPos().up();
            default -> base = hit.getBlockPos().offset(hit.getSide());
        }

        int radius = cursorSearchRadius.get();
        Vec3d hitVec = hit.getPos();

        // range limit from player
        double maxDist = cursorMaxDistance.get();
        double maxDistSq = maxDist * maxDist;
        Vec3d eyePos = mc.player.getEyePos(); // close enough for range

        BlockPos bestStem = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos candidateStem = base.add(dx, 0, dz);
                if (!validateVerticalPattern(candidateStem, facing)) continue;

                List<BlockPos> pattern = getVerticalPattern(candidateStem, facing);
                BlockPos centerBody = pattern.get(1);
                Vec3d centerVec = Vec3d.ofCenter(centerBody);

                // enforce max distance from player
                double distEyeSq = centerVec.squaredDistanceTo(eyePos);
                if (distEyeSq > maxDistSq) continue;

                // still pick the one closest to the cursor hit
                double distCursorSq = centerVec.squaredDistanceTo(hitVec);
                if (distCursorSq < bestDistSq) {
                    bestDistSq = distCursorSq;
                    bestStem = candidateStem;
                }
            }
        }

        return bestStem;
    }

    private BlockPos findBestHorizontalStemPos(BlockHitResult hit, Direction facing) {
        if (mc.world == null || mc.player == null) return null;

        BlockPos base;
        switch (hit.getSide()) {
            case UP -> base = hit.getBlockPos().up();
            case DOWN -> base = hit.getBlockPos().up();
            default -> base = hit.getBlockPos().offset(hit.getSide());
        }

        int radius = cursorSearchRadius.get();
        Vec3d hitVec = hit.getPos();

        double maxDist = cursorMaxDistance.get();
        double maxDistSq = maxDist * maxDist;
        Vec3d eyePos = mc.player.getEyePos();

        BlockPos bestStem = null;
        double bestDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos candidateStem = base.add(dx, 0, dz);
                if (!validateHorizontalPattern(candidateStem, facing)) continue;

                Vec3d centerVec = Vec3d.ofCenter(candidateStem);

                // enforce max distance from player
                double distEyeSq = centerVec.squaredDistanceTo(eyePos);
                if (distEyeSq > maxDistSq) continue;

                // still prefer the one closest to the cursor
                double distCursorSq = centerVec.squaredDistanceTo(hitVec);
                if (distCursorSq < bestDistSq) {
                    bestDistSq = distCursorSq;
                    bestStem = candidateStem;
                }
            }
        }

        return bestStem;
    }

    private void buildSteps(BlockPos stem, Direction facing, WitherOrientation orientation) {
        steps.clear();

        List<BlockPos> pattern = (orientation == WitherOrientation.VERTICAL)
            ? getVerticalPattern(stem, facing)
            : getHorizontalPattern(stem, facing);

        BlockPos stemPos = pattern.get(0);
        BlockPos centerBody = pattern.get(1);
        BlockPos leftArm = pattern.get(2);
        BlockPos rightArm = pattern.get(3);
        BlockPos headLeft = pattern.get(4);
        BlockPos headCenter = pattern.get(5);
        BlockPos headRight = pattern.get(6);

        // body (soul sand)
        steps.add(new PlacementStep(stemPos, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(centerBody, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(leftArm, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(rightArm, Blocks.SOUL_SAND));

        // skulls
        steps.add(new PlacementStep(headLeft, Blocks.WITHER_SKELETON_SKULL));
        steps.add(new PlacementStep(headCenter, Blocks.WITHER_SKELETON_SKULL));
        steps.add(new PlacementStep(headRight, Blocks.WITHER_SKELETON_SKULL));
    }

    private void preparePatten() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        Direction facing = player.getHorizontalFacing();

        BlockPos anchor = null;
        WitherOrientation orientation = WitherOrientation.VERTICAL;

        // cursor-based placement first
        if (cursorPlacement.get() && mc.crosshairTarget instanceof BlockHitResult bhr) {
            // try vertical around cursor
            anchor = findBestVerticalStemPos(bhr, facing);
            orientation = WitherOrientation.VERTICAL;

            // fallback: horizontal around cursor
            if (anchor == null) {
                anchor = findBestHorizontalStemPos(bhr, facing);
                orientation = WitherOrientation.HORIZONTAL;
            }
        }

        // fallback: fixed offset in front of player
        if (anchor == null) {
            BlockPos playerPos = player.getBlockPos();
            BlockPos base = playerPos.offset(facing, 2);

            if (validateVerticalPattern(base, facing)) {
                anchor = base;
                orientation = WitherOrientation.VERTICAL;
            } else if (validateHorizontalPattern(base, facing)) {
                anchor = base;
                orientation = WitherOrientation.HORIZONTAL;
            } else {
                steps.clear();
                return;
            }
        }

        buildSteps(anchor, facing, orientation);
    }

    private boolean hasRequiredMaterialsInHotbar() {
        if (mc.player == null) return false;

        PlayerInventory inv = mc.player.getInventory();

        int soulSandCount = 0;
        int skullCount = 0;

        // Only look in hotbar (0–8)
        for (int i = 0; i < 9; i++) {
            var stack = inv.getStack(i);
            if (stack.isEmpty()) continue;

            if (stack.getItem() == Blocks.SOUL_SAND.asItem()) {
                soulSandCount += stack.getCount();
            } else if (stack.getItem() == Blocks.WITHER_SKELETON_SKULL.asItem()) {
                skullCount += stack.getCount();
            }
        }

        // We always need 4 soul sand + 3 skulls for any of our patterns
        return soulSandCount >= 4 && skullCount >= 3;
    }

    private boolean placeStepAirplace(PlacementStep step) {
        if (mc.player == null || mc.world == null) return false;

        PlayerInventory inv = mc.player.getInventory();

        // choose slot based on block type
        int slot;
        if (step.block == Blocks.WITHER_SKELETON_SKULL) {
            slot = getSkullSlot(inv);
        } else {
            slot = findSlotWithBlock(inv, step.block);
        }

        if (slot == -1) {
            warning("Missing required block: " + step.block.getName().getString());
            return false;
        }

        // swap hotbar slot, send interaction packets
        if (inv.getSelectedSlot() != slot) {
            inv.setSelectedSlot(slot);
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            warning("Main hand does not hold a block item.");
            return false;
        }

        BlockPos target = step.pos;

        if (!mc.world.getBlockState(target).isReplaceable()) {
            return false;
        }

        // determine support block and face for skulls dynamically so vertical + horizontal both work
        BlockPos supportPos;
        Direction face;

        if (step.block == Blocks.WITHER_SKELETON_SKULL) {
            supportPos = null;
            face = Direction.UP;

            for (Direction dir : Direction.values()) {
                BlockPos candidateSupport = target.offset(dir.getOpposite());
                if (mc.world.getBlockState(candidateSupport).getBlock() == Blocks.SOUL_SAND) {
                    supportPos = candidateSupport;
                    face = dir;
                    break;
                }
            }

            // fallback: if no supporting soul sand yet (shouldn't happen given our order), just airplace above
            if (supportPos == null) {
                supportPos = target.down();
                face = Direction.UP;
            }
        } else {
            supportPos = target;
            face = Direction.UP;
        }

        Vec3d hitVec = Vec3d.ofCenter(supportPos);

        BlockHitResult bhr = new BlockHitResult(
            hitVec,
            face,
            supportPos,
            false
        );

        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));
        mc.player.networkHandler.sendPacket(new PlayerInteractBlockC2SPacket(
            Hand.OFF_HAND, bhr, mc.player.currentScreenHandler.getRevision() + 2));
        mc.player.networkHandler.sendPacket(new PlayerActionC2SPacket(
            PlayerActionC2SPacket.Action.SWAP_ITEM_WITH_OFFHAND, BlockPos.ORIGIN, Direction.DOWN));

        mc.player.swingHand(Hand.MAIN_HAND);
        return true;
    }

    // find slot with required block (must be in hotbar because im too lazy for good inventory management)
    private int findSlotWithBlock(PlayerInventory inv, Block block) {
        // search hotbar only (0–8)
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == block.asItem()) return i;
        }
        return -1;
    }

    private int getSkullSlot(PlayerInventory inv) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == Blocks.WITHER_SKELETON_SKULL.asItem()) return i;
        }
        return -1;
    }
}

// needed for queue, should probably put in a seperate file but im sending this class to people
class PlacementStep {
    public final BlockPos pos;
    public final Block block;

    public PlacementStep(BlockPos pos, Block block) {
        this.pos = pos;
        this.block = block;
    }
}

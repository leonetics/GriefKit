// leonetics 2025 evil inc

package com.griefkit.modules;

import com.griefkit.GriefKit;
import meteordevelopment.meteorclient.events.render.Render3DEvent;
import meteordevelopment.meteorclient.renderer.ShapeMode;
import meteordevelopment.meteorclient.settings.*;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.utils.misc.Keybind;
import meteordevelopment.meteorclient.utils.render.color.SettingColor;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.item.BlockItem;
import net.minecraft.network.packet.c2s.play.PlayerActionC2SPacket;
import net.minecraft.network.packet.c2s.play.PlayerInteractBlockC2SPacket;
import net.minecraft.network.packet.c2s.play.UpdateSelectedSlotC2SPacket;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Cross extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Block> block = sgGeneral.add(new BlockSetting.Builder()
        .name("block")
        .description("Block used to build the cross.")
        .defaultValue(Blocks.OBSIDIAN)
        .build()
    );

    private final Setting<Boolean> silentMode = sgGeneral.add(new BoolSetting.Builder()
        .name("silent-notifications")
        .description("Remove notifications.")
        .defaultValue(false)
        .build()
    );

    private final Setting<Keybind> crossPlace = sgGeneral.add(new KeybindSetting.Builder()
        .name("cross-place")
        .description("Places a cross instantly.")
        .defaultValue(Keybind.none())
        .build()
    );

    private final Setting<Boolean> cursorPlacement = sgGeneral.add(new BoolSetting.Builder()
        .name("cursor-placement")
        .description("Anchor crosses around your crosshair instead of only in front of you.")
        .defaultValue(true)
        .build()
    );

    private final Setting<Double> cursorMaxDistance = sgGeneral.add(new DoubleSetting.Builder()
        .name("cursor-max-distance")
        .description("Maximum distance from you for cursor-based cross placement.")
        .defaultValue(6.0)
        .min(1.0)
        .max(8.0)
        .build()
    );

    private final Setting<Integer> cursorSearchRadius = sgGeneral.add(new IntSetting.Builder()
        .name("cursor-search-radius")
        .description("Horizontal search radius around the cursor hit to find a valid cross anchor.")
        .defaultValue(2)
        .min(0)
        .max(8)
        .build()
    );

    private final SettingGroup sgRender = this.settings.createGroup("Render");

    private final Setting<Boolean> render = sgRender.add(new BoolSetting.Builder()
        .name("render")
        .description("Render the planned cross structure.")
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

    private final List<PlacementStep> steps = new ArrayList<>();
    private boolean lastPlaceKeyDown = false;

    private enum CrossOrientation {
        VERTICAL,
        HORIZONTAL
    }

    public Cross() {
        super(GriefKit.CATEGORY, "Cross", "Builds a 4-long 2-arm cross.");
    }

    @Override
    public void onActivate() {
        steps.clear();

        if (mc.player == null || mc.world == null) {
            warning("Player/world not loaded");
            toggle();
        }
    }

    @Override
    public void onDeactivate() {
        steps.clear();
    }

    @EventHandler
    private void onRender(Render3DEvent event) {
        if (mc.world == null) return;

        boolean placeKeyDown = crossPlace.get().isPressed();

        // edge-press to place instantly
        if (placeKeyDown && !lastPlaceKeyDown) {
            if (!hasRequiredMaterialsInHotbar()) {
                if (!silentMode.get()) warning("Not enough blocks in hotbar to build a cross (need 6)");
            } else {
                steps.clear();
                preparePattern();

                if (steps.isEmpty()) {
                    if (!silentMode.get()) warning("No valid build position found");
                } else {
                    // place everything right now
                    int placed = 0;
                    for (PlacementStep step : steps) {
                        Block current = mc.world.getBlockState(step.pos).getBlock();
                        if (current == step.block) continue;
                        if (placeStepAirplace(step)) placed++;
                    }
                    if (!silentMode.get()) info("Placed " + placed + " blocks");
                }
            }
        }

        lastPlaceKeyDown = placeKeyDown;

        if (!render.get()) return;

        // live preview recompute every frame
        steps.clear();
        preparePattern();

        if (steps.isEmpty()) return;

        for (PlacementStep step : steps) {
            boolean alreadyPlaced = mc.world.getBlockState(step.pos).getBlock() == step.block;

            SettingColor side = alreadyPlaced ? placedSideColor.get() : sideColor.get();
            SettingColor line = alreadyPlaced ? placedLineColor.get() : lineColor.get();

            event.renderer.box(step.pos, side, line, shapeMode.get(), 0);
        }
    }

    private boolean hasRequiredMaterialsInHotbar() {
        if (mc.player == null) return false;

        PlayerInventory inv = mc.player.getInventory();
        Block b = block.get();

        int count = 0;
        for (int i = 0; i < 9; i++) {
            var stack = inv.getStack(i);
            if (stack.isEmpty()) continue;
            if (stack.getItem() == b.asItem()) count += stack.getCount();
        }

        // 4 stem + 2 arms
        return count >= 6;
    }

    private boolean isObstructed(BlockPos pos) {
        return !mc.world.getBlockState(pos).isAir()
            && !mc.world.getBlockState(pos).isReplaceable();
    }

    private boolean isEntityIntersecting(BlockPos pos) {
        if (mc.world == null || mc.player == null) return false;

        Box box = new Box(pos);

        if (!mc.player.isSpectator() && mc.player.isAlive() && mc.player.getBoundingBox().intersects(box)) {
            return true;
        }

        return !mc.world.getOtherEntities(
            null,
            box,
            entity -> !entity.isSpectator() && entity.isAlive()
        ).isEmpty();
    }

    private boolean anyBlockedOrEntity(List<BlockPos> positions) {
        for (BlockPos p : positions) {
            if (isObstructed(p) || isEntityIntersecting(p)) return true;
        }
        return false;
    }

    private List<BlockPos> getVerticalCross(BlockPos stemBottom, Direction facing) {
        BlockPos s0 = stemBottom;
        BlockPos s1 = s0.up();
        BlockPos s2 = s1.up();
        BlockPos s3 = s2.up();

        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        BlockPos l = s1.offset(left);
        BlockPos r = s1.offset(right);

        return List.of(s0, s1, s2, s3, l, r);
    }

    private List<BlockPos> getHorizontalCross(BlockPos stemStart, Direction facing) {
        BlockPos s0 = stemStart;
        BlockPos s1 = s0.offset(facing);
        BlockPos s2 = s1.offset(facing);
        BlockPos s3 = s2.offset(facing);

        Direction left = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        BlockPos l = s1.offset(left);
        BlockPos r = s1.offset(right);

        return List.of(s0, s1, s2, s3, l, r);
    }

    private boolean validateVerticalCross(BlockPos stemBottom, Direction facing) {
        return !anyBlockedOrEntity(getVerticalCross(stemBottom, facing));
    }

    private boolean validateHorizontalCross(BlockPos stemStart, Direction facing) {
        return !anyBlockedOrEntity(getHorizontalCross(stemStart, facing));
    }

    private BlockPos computeBaseFromHit(BlockHitResult hit) {
        return switch (hit.getSide()) {
            case UP -> hit.getBlockPos().up();
            case DOWN -> hit.getBlockPos().up();
            default -> hit.getBlockPos().offset(hit.getSide());
        };
    }

    private BlockPos findBestVerticalAnchor(BlockHitResult hit, Direction facing) {
        if (mc.world == null || mc.player == null) return null;

        BlockPos base = computeBaseFromHit(hit);

        int radius = cursorSearchRadius.get();
        Vec3d hitVec = hit.getPos();

        double maxDist = cursorMaxDistance.get();
        double maxDistSq = maxDist * maxDist;
        Vec3d eyePos = mc.player.getEyePos();

        BlockPos best = null;
        double bestCursorDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos candidate = base.add(dx, 0, dz);
                if (!validateVerticalCross(candidate, facing)) continue;

                Vec3d center = Vec3d.ofCenter(candidate.up()); // arm level

                if (center.squaredDistanceTo(eyePos) > maxDistSq) continue;

                double cursorDistSq = center.squaredDistanceTo(hitVec);
                if (cursorDistSq < bestCursorDistSq) {
                    bestCursorDistSq = cursorDistSq;
                    best = candidate;
                }
            }
        }

        return best;
    }

    private BlockPos findBestHorizontalAnchor(BlockHitResult hit, Direction facing) {
        if (mc.world == null || mc.player == null) return null;

        BlockPos base = computeBaseFromHit(hit);

        int radius = cursorSearchRadius.get();
        Vec3d hitVec = hit.getPos();

        double maxDist = cursorMaxDistance.get();
        double maxDistSq = maxDist * maxDist;
        Vec3d eyePos = mc.player.getEyePos();

        BlockPos best = null;
        double bestCursorDistSq = Double.MAX_VALUE;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                BlockPos candidate = base.add(dx, 0, dz);
                if (!validateHorizontalCross(candidate, facing)) continue;

                Vec3d center = Vec3d.ofCenter(candidate.offset(facing)); // s1

                if (center.squaredDistanceTo(eyePos) > maxDistSq) continue;

                double cursorDistSq = center.squaredDistanceTo(hitVec);
                if (cursorDistSq < bestCursorDistSq) {
                    bestCursorDistSq = cursorDistSq;
                    best = candidate;
                }
            }
        }

        return best;
    }

    private void buildStepsVertical(BlockPos stemBottom, Direction facing) {
        steps.clear();
        Block b = block.get();
        for (BlockPos p : getVerticalCross(stemBottom, facing)) steps.add(new PlacementStep(p, b));
    }

    private void buildStepsHorizontal(BlockPos stemStart, Direction facing) {
        steps.clear();
        Block b = block.get();
        for (BlockPos p : getHorizontalCross(stemStart, facing)) steps.add(new PlacementStep(p, b));
    }

    private void preparePattern() {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.world == null) return;

        Direction facing = player.getHorizontalFacing();

        BlockPos anchor = null;
        CrossOrientation orientation = CrossOrientation.VERTICAL;

        if (cursorPlacement.get() && mc.crosshairTarget instanceof BlockHitResult bhr) {
            anchor = findBestVerticalAnchor(bhr, facing);
            orientation = CrossOrientation.VERTICAL;

            if (anchor == null) {
                anchor = findBestHorizontalAnchor(bhr, facing);
                orientation = CrossOrientation.HORIZONTAL;
            }
        }

        if (anchor == null) {
            BlockPos base = player.getBlockPos().offset(facing, 2);

            if (validateVerticalCross(base, facing)) {
                anchor = base;
                orientation = CrossOrientation.VERTICAL;
            } else if (validateHorizontalCross(base, facing)) {
                anchor = base;
                orientation = CrossOrientation.HORIZONTAL;
            } else {
                steps.clear();
                return;
            }
        }

        if (orientation == CrossOrientation.VERTICAL) buildStepsVertical(anchor, facing);
        else buildStepsHorizontal(anchor, facing);
    }

    private boolean placeStepAirplace(PlacementStep step) {
        if (mc.player == null || mc.world == null) return false;

        PlayerInventory inv = mc.player.getInventory();

        int slot = findSlotWithBlock(inv, step.block);
        if (slot == -1) {
            if (!silentMode.get()) warning("missing required block in hotbar: " + step.block.getName().getString());
            return false;
        }

        if (inv.selectedSlot != slot) {
            inv.selectedSlot = slot;;
            mc.player.networkHandler.sendPacket(new UpdateSelectedSlotC2SPacket(slot));
        }

        if (!(mc.player.getMainHandStack().getItem() instanceof BlockItem)) {
            if (!silentMode.get()) warning("main hand does not hold a block item");
            return false;
        }

        if (!mc.world.getBlockState(step.pos).isReplaceable()) return false;

        Vec3d hitVec = Vec3d.ofCenter(step.supportPos);
        BlockHitResult bhr = new BlockHitResult(hitVec, step.supportFace, step.supportPos, false);

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
        return true;
    }

    private int findSlotWithBlock(PlayerInventory inv, Block b) {
        for (int i = 0; i < 9; i++) {
            if (inv.getStack(i).getItem() == b.asItem()) return i;
        }
        return -1;
    }
}

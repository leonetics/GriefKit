package com.griefkit.modules;

import com.griefkit.GriefKit;
import meteordevelopment.meteorclient.settings.Setting;
import meteordevelopment.meteorclient.settings.SettingGroup;
import meteordevelopment.meteorclient.systems.modules.Module;
import meteordevelopment.meteorclient.events.world.TickEvent;
import meteordevelopment.meteorclient.settings.BoolSetting;
import meteordevelopment.meteorclient.settings.IntSetting;
import meteordevelopment.orbit.EventHandler;
import net.minecraft.block.Block;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.player.PlayerInventory;
import net.minecraft.util.Hand;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;
import net.minecraft.util.math.Vec3d;

import java.util.ArrayList;
import java.util.List;

public class Wither extends Module {
    private final SettingGroup sgGeneral = this.settings.getDefaultGroup();

    private final Setting<Integer> blocksPerTick = sgGeneral.add(new IntSetting.Builder()
        .name("blocks-per-tick")
        .description("How many blocks to place per tick")
        .defaultValue(4)
        .min(1)
        .max(9)
        .build()
    );

    private final Setting<Boolean> onlyOnGround = sgGeneral.add(new BoolSetting.Builder()
        .name("require-on-ground")
        .description("Only start building if you are standing on the ground")
        .defaultValue(true)
        .build()
    );

    private final List<PlacementStep> steps = new ArrayList<>();
    private int currentIndex = 0;
    private boolean prepared = false;

    /**
     * The {@code name} parameter should be in kebab-case.
     */
    public Wither() {
        super(GriefKit.CATEGORY, "Wither", "Builds a wither in front of you");
    }

    @Override
    public void onActivate() {
        steps.clear();
        currentIndex = 0;
        prepared = false;

        if (mc.player == null || mc.world == null) {
            info("what the fuck are we doing man");
            toggle();
            return;
        }
        if (onlyOnGround.get() && !mc.player.isOnGround()) {
            info("stand on something idiot");
            toggle();
            return;
        }

        preparePatten();

        if (steps.isEmpty()) {
            info("No valid build position found");
            toggle();
            return;
        }

        prepared = true;
        info("Withering...");
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
            info("Wither done");
            toggle();
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

            boolean success = placeBlock(step);

            currentIndex++;
            placedThisTick++;
        }
    }

    private void preparePatten() {
        ClientPlayerEntity player = mc.player;
        if (player == null) return;

        // ground block 2 blocks in front of player, make air place later because im a lazy fag and im a skid
        // to do: change this to block that cursor points at if within range

        Direction facing = player.getHorizontalFacing();
        BlockPos playerPos = player.getBlockPos();

        BlockPos inFront = playerPos.offset(facing, 2);


        BlockPos ground = null;
        for (int i = 0; i < 3; i++) {
            BlockPos check = inFront.down(i);
            if (!mc.world.getBlockState(check).isAir() && !mc.world.getBlockState(check).getCollisionShape(mc.world, check).isEmpty()) {
                ground = check;
                break;
            }
        }

        if (ground == null) {
            info("Block 2 blocks in front of you must be solid");
            return;
        }


//
        //   _
        //   _
        // _ _ _

        int stemY = ground.getY() + 1;
        int bodyY = stemY + 1;
        int headY = bodyY + 1;

        // center of the body row (the middle of the T bar)
        BlockPos centerBody = new BlockPos(ground.getX(), bodyY, ground.getZ());

        Direction left  = facing.rotateYCounterclockwise();
        Direction right = facing.rotateYClockwise();

        // soul sand
        BlockPos stem     = new BlockPos(ground.getX(), stemY, ground.getZ());
        BlockPos leftArm  = centerBody.offset(left);
        BlockPos rightArm = centerBody.offset(right);

        // skulls directly above the 3 top soul-sand blocks
        BlockPos headCenter = new BlockPos(centerBody.getX(), headY, centerBody.getZ());
        BlockPos headLeft   = new BlockPos(leftArm.getX(),   headY, leftArm.getZ());
        BlockPos headRight  = new BlockPos(rightArm.getX(),  headY, rightArm.getZ());
        // above right arm


        steps.clear();

        // Order: body first, then heads
        // order matters to not airplace

        // Body (soul sand)
        steps.add(new PlacementStep(stem, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(centerBody, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(leftArm, Blocks.SOUL_SAND));
        steps.add(new PlacementStep(rightArm, Blocks.SOUL_SAND));

        // Heads (skulls)
        steps.add(new PlacementStep(headLeft, Blocks.WITHER_SKELETON_SKULL));
        steps.add(new PlacementStep(headCenter, Blocks.WITHER_SKELETON_SKULL));
        steps.add(new PlacementStep(headRight, Blocks.WITHER_SKELETON_SKULL));
    }

    private boolean placeBlock(PlacementStep step) {
        ClientPlayerEntity player = mc.player;
        if (player == null || mc.interactionManager == null) return false;

        PlayerInventory inv = player.getInventory();

        int slot = findSlotWithBlock(inv, step.block);
        if (slot == -1) {
            warning("Missing block: " + step.block.getName().getString());
            return false;
        }

        // switch to hotbar slot (assume its in hotbar, too much of a skid for good inv management)
        if (slot >= 0 && slot < 9) {
            inv.setSelectedSlot(slot);
        } else {
            warning("Block " + step.block.getName().getString() + " is not in hotbar slot " + slot);
            return false;
        }
        BlockPos target = step.pos;

        // 1) pick a support block + face
        BlockPos support = null;
        Direction face = Direction.UP;

        // prefer below if solid
        BlockPos below = target.down();
        if (!mc.world.getBlockState(below).isAir()
            && !mc.world.getBlockState(below).getCollisionShape(mc.world, below).isEmpty()) {

            support = below;
            face = Direction.UP;
        } else {
            // otherwise look for any solid neighbor around the target
            for (Direction dir : Direction.values()) {
                BlockPos neighbor = target.offset(dir);
                if (!mc.world.getBlockState(neighbor).isAir()
                    && !mc.world.getBlockState(neighbor).getCollisionShape(mc.world, neighbor).isEmpty()) {

                    support = neighbor;
                    face = dir.getOpposite(); // click the face that faces the target
                    break;
                }
            }
        }

        if (support == null) {
            warning("No solid neighbor to place against at " + target.toShortString());
            return false;
        }

        // 2) build hit result
        Vec3d hitPos = Vec3d.ofCenter(target);
        BlockHitResult hitResult = new BlockHitResult(
            hitPos,
            face,
            support,
            false
        );

        // try to place

        mc.interactionManager.interactBlock(player, Hand.MAIN_HAND, hitResult);
        player.swingHand(Hand.MAIN_HAND);

        return true;
    }

    // find slot with required block (must be in hotbar)
    private int findSlotWithBlock(PlayerInventory inv, Block block) {
        for (int i = 0; i < 8; i++) {
            if (inv.getStack(i).getItem() == block.asItem()) {
                return i;
            }
        }
        for (int i = 9; i < inv.size(); i++) {
            if (inv.getStack(i).getItem() == block.asItem()) {
                return i; // not in hotbar but signal we found it
            }
        }

        return -1;
    }

    private void Info(String msg) {
        GriefKit.LOG.info("[Wither] " + msg);
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.of("[WitherBuilder] " + msg), false);
    }
    private void Warning(String msg) {
        GriefKit.LOG.warn("[Wither] " + msg);
        if (mc.player != null) mc.player.sendMessage(net.minecraft.text.Text.of("[WitherBuilder] " + msg), false);
    }

    // to do, add rendering
}

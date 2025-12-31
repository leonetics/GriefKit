package com.griefkit.modules;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Direction;

class PlacementStep {
    public final BlockPos pos;
    public final Block block;

    public final BlockPos supportPos;
    public final Direction supportFace;

    public PlacementStep(BlockPos pos, Block block, BlockPos supportPos, Direction supportFace) {
        this.pos = pos;
        this.block = block;
        this.supportPos = supportPos;
        this.supportFace = supportFace;
    }

    public PlacementStep(BlockPos pos, Block block) {
        this(pos, block, pos, Direction.UP);
    }
}

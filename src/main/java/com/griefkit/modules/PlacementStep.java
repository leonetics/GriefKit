package com.griefkit.modules;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;


public class PlacementStep {
    public final BlockPos pos;
    public final Block block;

    public PlacementStep(BlockPos pos, Block block) {
        this.pos = pos;
        this.block = block;
    }
}

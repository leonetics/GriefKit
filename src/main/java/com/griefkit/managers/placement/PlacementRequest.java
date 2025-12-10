package com.griefkit.managers.placement;

import net.minecraft.block.Block;
import net.minecraft.util.math.BlockPos;

public class PlacementRequest {
    public final BlockPos pos;
    /**
     * if non-null, placement will try to use this exact block type from hotbar.
     * if null, manager is allowed to use fuck-all in hotbar.
     */
    public final Block block;

    /** absolute creation time in ms. */
    public final long createdAt;

    /** optional timeout to avoid crazy shit. 0 = no timeout. */
    public final long timeoutMs;

    /** max distance squared from player. e.g. (6^2) for ~6 block reach. */
    public final double maxDistanceSq;

    /** to airplace or not to airplace, that is the question */
    public final boolean airplace;

    /** name of the source module for future logging/priority management. */
    public final String source;


    public PlacementRequest(BlockPos pos, Block block, long timeoutMs, double maxDistanceSq, boolean airplace, String source) {
        this.pos = pos;
        this.block = block;
        this.createdAt = System.currentTimeMillis();
        this.timeoutMs = timeoutMs;
        this.maxDistanceSq = maxDistanceSq;
        this.airplace = airplace;
        this.source = source;
    }

    public boolean isExpired() {
        return timeoutMs > 0 && System.currentTimeMillis() - createdAt > timeoutMs;
    }
}

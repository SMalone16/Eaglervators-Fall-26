package org.pawling.eaglervators;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.BubbleColumn;

/**
 * Creates complete source-water shafts and activates their bubble direction.
 * Each cell is deliberately set to water[level=0] before becoming a bubble
 * column; a running bubble column is its own vanilla block state, not flowing
 * water. No water cell is left to flow down from the one above it.
 */
final class BubbleColumnWater {
    private BubbleColumnWater() {
    }

    /**
     * @param floorY the soul-sand or magma-block Y coordinate
     * @param waterTopY the inclusive top of the water column
     * @return true if the column was rebuilt, false if it was already correct
     */
    static boolean ensure(World world, int x, int z, int floorY, int waterTopY, Material base) {
        if (base != Material.SOUL_SAND && base != Material.MAGMA_BLOCK) {
            throw new IllegalArgumentException("Bubble elevator requires soul sand or magma");
        }
        if (waterTopY <= floorY) {
            throw new IllegalArgumentException("Bubble elevator requires water above its base");
        }

        boolean dragDown = base == Material.MAGMA_BLOCK;
        boolean intact = world.getBlockAt(x, floorY, z).getType() == base;
        for (int y = floorY + 1; intact && y <= waterTopY; y++) {
            BlockData data = world.getBlockAt(x, y, z).getBlockData();
            intact = data instanceof BubbleColumn bubbles && bubbles.isDrag() == dragDown;
        }
        if (intact) {
            return false;
        }

        // Place an actual, still source-water block at EVERY height, not just
        // a top source which cascades as flowing water into the shaft.
        Levelled source = (Levelled) Bukkit.createBlockData(Material.WATER);
        source.setLevel(0);
        for (int y = floorY + 1; y <= waterTopY; y++) {
            world.getBlockAt(x, y, z).setBlockData(source, false);
        }

        // Activate the base with physics, then explicitly establish every
        // bubble cell so startup does not depend on a scheduled neighbor tick.
        world.getBlockAt(x, floorY, z).setType(base, true);
        BubbleColumn bubbles = (BubbleColumn) Bukkit.createBlockData(Material.BUBBLE_COLUMN);
        bubbles.setDrag(dragDown);
        for (int y = floorY + 1; y <= waterTopY; y++) {
            world.getBlockAt(x, y, z).setBlockData(bubbles, false);
        }
        return true;
    }
}

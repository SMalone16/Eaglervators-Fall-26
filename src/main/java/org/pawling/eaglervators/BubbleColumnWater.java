package org.pawling.eaglervators;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Levelled;
import org.bukkit.block.data.type.BubbleColumn;

/**
 * Fills every shaft cell with a source (water[level=0]) before activating a
 * native bubble column. The water column must have no flowing-water gaps.
 */
final class BubbleColumnWater {
    private BubbleColumnWater() {
    }

    record Status(int height, int bubbles, int sources, int flowing, int invalid, boolean baseCorrect) {
        boolean healthy() {
            return baseCorrect && bubbles == height;
        }

        String summary() {
            return bubbles + "/" + height + " bubbles, " + sources + " plain sources, "
                    + flowing + " flowing, " + invalid + " invalid, base="
                    + (baseCorrect ? "OK" : "WRONG");
        }
    }

    static Status inspect(World world, int x, int z, int floorY, int waterTopY, Material base) {
        boolean dragDown = base == Material.MAGMA_BLOCK;
        int bubbles = 0, sources = 0, flowing = 0, invalid = 0;
        for (int y = floorY + 1; y <= waterTopY; y++) {
            Block block = world.getBlockAt(x, y, z);
            BlockData data = block.getBlockData();
            if (data instanceof BubbleColumn column && column.isDrag() == dragDown) {
                bubbles++;
            } else if (block.getType() == Material.WATER && data instanceof Levelled water) {
                if (water.getLevel() == 0) sources++;
                else flowing++;
            } else {
                invalid++;
            }
        }
        return new Status(waterTopY - floorY, bubbles, sources, flowing, invalid,
                world.getBlockAt(x, floorY, z).getType() == base);
    }

    /**
     * @return true when the shaft required repair. A healthy shaft is read-only.
     */
    static boolean ensure(World world, int x, int z, int floorY, int waterTopY, Material base) {
        if (base != Material.SOUL_SAND && base != Material.MAGMA_BLOCK) {
            throw new IllegalArgumentException("Bubble elevator requires soul sand or magma");
        }
        if (waterTopY <= floorY) {
            throw new IllegalArgumentException("Bubble elevator requires water above its base");
        }
        if (inspect(world, x, z, floorY, waterTopY, base).healthy()) {
            return false;
        }

        // Explicitly create still SOURCE water at every single level, bottom
        // to top, even when restoring a previously flowing-water shaft.
        Levelled source = (Levelled) Bukkit.createBlockData(Material.WATER);
        source.setLevel(0);
        for (int y = floorY + 1; y <= waterTopY; y++) {
            world.getBlockAt(x, y, z).setBlockData(source, false);
        }

        // Replacing the base with a different block first guarantees a real
        // neighbor/physics change, including on previously built elevators.
        Block baseBlock = world.getBlockAt(x, floorY, z);
        baseBlock.setType(Material.STONE_BRICKS, false);
        baseBlock.setType(base, true);

        // Turn each source into a native bubble block with physics ENABLED.
        // The earlier false-physics placement could leave an inert column.
        BubbleColumn bubbles = (BubbleColumn) Bukkit.createBlockData(Material.BUBBLE_COLUMN);
        bubbles.setDrag(base == Material.MAGMA_BLOCK);
        for (int y = floorY + 1; y <= waterTopY; y++) {
            world.getBlockAt(x, y, z).setBlockData(bubbles, true);
        }
        return true;
    }
}

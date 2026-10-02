package org.pawling.eaglervators;

import org.bukkit.HeightMap;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

public final class TerrainScanner {
    private final EaglervatorsPlugin plugin;
    private final World world;
    private final List<Offset> offsets = new ArrayList<>();
    private final Map<Long, Integer> heightCache = new HashMap<>();

    private final int checksPerTick;
    private final int minDifference;
    private final int crossWidth;
    private final int requiredSupportingColumns;
    private final int supportingColumnHeight;
    private final int plateauDepth;
    private final int plateauWidth;
    private final int lowerTolerance;
    private final int upperTolerance;
    private final int maxHeight;

    private int cursor;
    private BukkitTask task;

    public TerrainScanner(EaglervatorsPlugin plugin, World world) {
        this.plugin = plugin;
        this.world = world;

        int radius = Math.max(16, plugin.getConfig().getInt("scan.radius-blocks", 128));
        checksPerTick = Math.max(1, plugin.getConfig().getInt("scan.checks-per-tick", 300));
        minDifference = Math.max(20, plugin.getConfig().getInt("scan.minimum-height-difference", 20));
        crossWidth = makeOdd(Math.max(3, plugin.getConfig().getInt("scan.cross-section-width", 5)));
        requiredSupportingColumns = Math.max(1, plugin.getConfig().getInt("scan.minimum-supporting-columns", 3));
        supportingColumnHeight = Math.max(1, plugin.getConfig().getInt("scan.supporting-column-height", 18));
        plateauDepth = Math.max(1, plugin.getConfig().getInt("scan.plateau-depth", 3));
        plateauWidth = makeOdd(Math.max(1, plugin.getConfig().getInt("scan.plateau-width", 3)));
        lowerTolerance = Math.max(1, plugin.getConfig().getInt("scan.lower-plateau-tolerance", 6));
        upperTolerance = Math.max(1, plugin.getConfig().getInt("scan.upper-plateau-tolerance", 8));
        maxHeight = Math.max(minDifference, plugin.getConfig().getInt("scan.maximum-elevator-height", 160));

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {
                offsets.add(new Offset(dx, dz));
            }
        }
        offsets.sort(Comparator.comparingInt(Offset::distanceSquared));
    }

    public void start(Consumer<ElevatorSite> onFound, Runnable onExhausted) {
        if (task != null) return;

        int spawnX = world.getSpawnLocation().getBlockX();
        int spawnZ = world.getSpawnLocation().getBlockZ();

        task = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            int processed = 0;

            while (processed < checksPerTick && cursor < offsets.size()) {
                Offset offset = offsets.get(cursor++);
                int x = spawnX + offset.dx();
                int z = spawnZ + offset.dz();

                ElevatorSite east = evaluatePair(x, z, x + 1, z);
                if (east != null) {
                    cancel();
                    onFound.accept(east);
                    return;
                }

                ElevatorSite south = evaluatePair(x, z, x, z + 1);
                if (south != null) {
                    cancel();
                    onFound.accept(south);
                    return;
                }
                processed++;
            }

            if (cursor >= offsets.size()) {
                cancel();
                onExhausted.run();
            }
        }, 1L, 1L);
    }

    public boolean isRunning() {
        return task != null;
    }

    public void cancel() {
        if (task != null) {
            task.cancel();
            task = null;
        }
    }

    private ElevatorSite evaluatePair(int ax, int az, int bx, int bz) {
        int ay = surfaceY(ax, az);
        int by = surfaceY(bx, bz);
        int difference = Math.abs(ay - by);

        if (difference < minDifference || difference > maxHeight + Math.max(lowerTolerance, upperTolerance)) {
            return null;
        }

        int lowX, lowZ, lowY, highX, highZ, highY;
        if (ay < by) {
            lowX = ax; lowZ = az; lowY = ay;
            highX = bx; highZ = bz; highY = by;
        } else {
            lowX = bx; lowZ = bz; lowY = by;
            highX = ax; highZ = az; highY = ay;
        }

        if (!isSolidSurface(lowX, lowY, lowZ) || !isSolidSurface(highX, highY, highZ)) {
            return null;
        }

        int ux = Integer.signum(highX - lowX);
        int uz = Integer.signum(highZ - lowZ);
        int vx = -uz;
        int vz = ux;

        int supporting = 0;
        int crossRadius = crossWidth / 2;
        for (int v = -crossRadius; v <= crossRadius; v++) {
            int lowCross = surfaceY(lowX + v * vx, lowZ + v * vz);
            int highCross = surfaceY(highX + v * vx, highZ + v * vz);
            if (highCross - lowCross >= supportingColumnHeight) {
                supporting++;
            }
        }
        if (supporting < Math.min(requiredSupportingColumns, crossWidth)) {
            return null;
        }

        List<Integer> lowerSamples = new ArrayList<>();
        List<Integer> upperSamples = new ArrayList<>();
        int plateauRadius = plateauWidth / 2;

        for (int depth = 0; depth < plateauDepth; depth++) {
            for (int v = -plateauRadius; v <= plateauRadius; v++) {
                lowerSamples.add(surfaceY(
                        lowX - depth * ux + v * vx,
                        lowZ - depth * uz + v * vz
                ));
                upperSamples.add(surfaceY(
                        highX + depth * ux + v * vx,
                        highZ + depth * uz + v * vz
                ));
            }
        }

        if (range(lowerSamples) > lowerTolerance || range(upperSamples) > upperTolerance) {
            return null;
        }

        int lowerMedian = median(lowerSamples);
        int upperMedian = median(upperSamples);
        if (upperMedian - lowerMedian < minDifference - 2) {
            return null;
        }

        int shaftX = lowX - (2 * ux);
        int shaftZ = lowZ - (2 * uz);
        int shaftGround = surfaceY(shaftX, shaftZ);

        if (Math.abs(shaftGround - lowerMedian) > lowerTolerance || !isSolidSurface(shaftX, shaftGround, shaftZ)) {
            return null;
        }

        int elevatorHeight = upperMedian - shaftGround;
        if (elevatorHeight < minDifference || elevatorHeight > maxHeight) {
            return null;
        }

        for (int u = -5; u <= -3; u++) {
            for (int v = -1; v <= 1; v++) {
                int px = shaftX + u * ux + v * vx;
                int pz = shaftZ + u * uz + v * vz;
                int py = surfaceY(px, pz);
                if (Math.abs(py - shaftGround) > lowerTolerance) {
                    return null;
                }
            }
        }

        return new ElevatorSite(
                world.getUID(),
                world.getName(),
                shaftX,
                shaftZ,
                shaftGround,
                upperMedian,
                faceFromVector(ux, uz)
        );
    }

    private int surfaceY(int x, int z) {
        long key = (((long) x) << 32) ^ (z & 0xffffffffL);
        Integer cached = heightCache.get(key);
        if (cached != null) return cached;

        int y = world.getHighestBlockYAt(x, z, HeightMap.MOTION_BLOCKING_NO_LEAVES);
        heightCache.put(key, y);
        return y;
    }

    private boolean isSolidSurface(int x, int y, int z) {
        Material material = world.getBlockAt(x, y, z).getType();
        return material.isSolid() && material != Material.BEDROCK;
    }

    private static int range(List<Integer> values) {
        int min = Integer.MAX_VALUE;
        int max = Integer.MIN_VALUE;
        for (int value : values) {
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return max - min;
    }

    private static int median(List<Integer> values) {
        List<Integer> sorted = new ArrayList<>(values);
        sorted.sort(Integer::compareTo);
        return sorted.get(sorted.size() / 2);
    }

    private static int makeOdd(int value) {
        return value % 2 == 0 ? value + 1 : value;
    }

    private static BlockFace faceFromVector(int x, int z) {
        if (x > 0) return BlockFace.EAST;
        if (x < 0) return BlockFace.WEST;
        if (z > 0) return BlockFace.SOUTH;
        return BlockFace.NORTH;
    }

    private record Offset(int dx, int dz) {
        int distanceSquared() {
            return (dx * dx) + (dz * dz);
        }
    }
}

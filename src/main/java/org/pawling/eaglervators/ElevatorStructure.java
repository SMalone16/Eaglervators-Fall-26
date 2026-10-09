package org.pawling.eaglervators;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Door;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

public final class ElevatorStructure implements Listener {
    private final EaglervatorsPlugin plugin;
    private final ElevatorSite site;
    private final World world;
    private final Set<BlockPos> protectedVolume = new HashSet<>();

    public ElevatorStructure(EaglervatorsPlugin plugin, ElevatorSite site) {
        this.plugin = plugin;
        this.site = site;
        this.world = Bukkit.getWorld(site.worldId());
        if (world == null) {
            throw new IllegalStateException("World is not loaded: " + site.worldName());
        }
        indexProtectionVolume();
    }

    public ElevatorSite site() {
        return site;
    }

    public void build() {
        clearLandingHeadroom();
        clearDropZone();
        buildShaft();
        buildTopLanding();
        buildPool();

        repairWaterColumn();
    }

    /** Repairs an existing saved elevator without regenerating its terrain or landing. */
    public void repairWaterColumn() {
        if (BubbleColumnWater.ensure(world, site.shaftX(), site.shaftZ(),
                site.bottomY(), site.topY() + 1, Material.SOUL_SAND)) {
            plugin.getLogger().info("Filled and activated every source-water cell in the cliff elevator.");
        }
    }

    public boolean anchorLooksIntact() {
        Block anchor = world.getBlockAt(site.shaftX(), site.bottomY(), site.shaftZ());
        Block wall = world.getBlockAt(site.x(1, 1), site.bottomY() + 1, site.z(1, 1));
        return anchor.getType() == Material.SOUL_SAND && wall.getType() == Material.GLASS;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("Eaglervator blocks are protected.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        if (isProtected(event.getBlockPlaced())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("Keep the Eaglervator shaft and landing clear.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("The Eaglervator water system is protected.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        if (isProtected(event.getBlock())) {
            event.setCancelled(true);
            event.getPlayer().sendActionBar("The Eaglervator water system is protected.");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent event) {
        event.blockList().removeIf(this::isProtected);
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        if (pistonTouchesProtected(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent event) {
        if (pistonTouchesProtected(event.getBlocks(), event.getDirection())) {
            event.setCancelled(true);
        }
    }

    private boolean pistonTouchesProtected(List<Block> blocks, BlockFace direction) {
        for (Block block : blocks) {
            if (isProtected(block) || isProtected(block.getRelative(direction))) {
                return true;
            }
        }
        return false;
    }

    private boolean isProtected(Block block) {
        if (!block.getWorld().getUID().equals(site.worldId())) {
            return false;
        }
        return protectedVolume.contains(new BlockPos(block.getX(), block.getY(), block.getZ()));
    }

    private void clearLandingHeadroom() {
        for (int u = -2; u <= 5; u++) {
            for (int v = -2; v <= 2; v++) {
                if (insideShaft(u, v)) {
                    continue;
                }
                for (int y = site.topY() + 1; y <= site.topY() + 3; y++) {
                    setLocal(u, v, y, Material.AIR, false);
                }
            }
        }
    }

    private void clearDropZone() {
        for (int u = -5; u <= -3; u++) {
            for (int v = -1; v <= 1; v++) {
                for (int y = site.bottomY() + 1; y <= site.topY() + 2; y++) {
                    setLocal(u, v, y, Material.AIR, false);
                }
            }
        }
    }

    private void buildShaft() {
        int bottom = site.bottomY();
        int top = site.topY();

        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                setLocal(u, v, bottom, Material.POLISHED_BLACKSTONE, false);
            }
        }

        for (int y = bottom + 1; y <= top + 2; y++) {
            for (int u = -1; u <= 1; u++) {
                for (int v = -1; v <= 1; v++) {
                    if (!isPerimeter(u, v)) {
                        continue;
                    }

                    boolean bottomDoor = u == -1 && v == 0
                            && (y == bottom + 1 || y == bottom + 2);
                    boolean topDoor = u == 1 && v == 0
                            && (y == top + 1 || y == top + 2);

                    if (!bottomDoor && !topDoor) {
                        setLocal(u, v, y, Material.GLASS, false);
                    }
                }
            }
        }

        for (int y = bottom + 1; y <= top + 1; y++) {
            setLocal(0, 0, y, Material.AIR, false);
        }
        setLocal(0, 0, top + 2, Material.AIR, false);

        for (int u = -1; u <= 1; u++) {
            for (int v = -1; v <= 1; v++) {
                setLocal(u, v, top + 3, Material.GLASS, false);
            }
        }

        placeDoor(-1, 0, bottom + 1, site.uphill().getOppositeFace());
        placeDoor(1, 0, top + 1, site.uphill());
    }

    private void buildTopLanding() {
        int y = site.topY();

        // A ring around the shaft connects the uphill exit to the downhill jump side.
        for (int u = -2; u <= 2; u++) {
            for (int v = -2; v <= 2; v++) {
                if (!insideShaft(u, v)) {
                    setLocal(u, v, y, Material.SMOOTH_STONE, false);
                }
            }
        }

        // The detected high terrain is about three local blocks uphill from the shaft center.
        // Extending to u=5 ensures the landing actually overlaps usable upper terrain.
        for (int u = 2; u <= 5; u++) {
            for (int v = -2; v <= 2; v++) {
                setLocal(u, v, y, Material.SMOOTH_STONE, false);
            }
        }

        for (int u = -2; u <= 4; u++) {
            setLocal(u, -2, y + 1, Material.GLASS, false);
            setLocal(u, 2, y + 1, Material.GLASS, false);
        }

        // Leave the center of the downhill edge open so players can jump into the pool.
        setLocal(-2, -2, y + 1, Material.GLASS, false);
        setLocal(-2, 2, y + 1, Material.GLASS, false);
    }

    private void buildPool() {
        int bottom = site.bottomY();

        // Exact 3 x 2 x 3 water volume.
        for (int u = -5; u <= -3; u++) {
            for (int v = -1; v <= 1; v++) {
                setLocal(u, v, bottom - 2, Material.GLASS, false);
                setLocal(u, v, bottom - 1, Material.WATER, false);
                setLocal(u, v, bottom, Material.WATER, false);
            }
        }

        // Rim outside the water footprint.
        for (int u = -6; u <= -2; u++) {
            setLocal(u, -2, bottom, Material.SMOOTH_STONE, false);
            setLocal(u, 2, bottom, Material.SMOOTH_STONE, false);
        }
        for (int v = -2; v <= 2; v++) {
            setLocal(-6, v, bottom, Material.SMOOTH_STONE, false);
            setLocal(-2, v, bottom, Material.SMOOTH_STONE, false);
        }
    }

    private void placeDoor(int u, int v, int lowerY, BlockFace facing) {
        Block lowerBlock = world.getBlockAt(site.x(u, v), lowerY, site.z(u, v));
        Block upperBlock = world.getBlockAt(site.x(u, v), lowerY + 1, site.z(u, v));

        Door lower = (Door) Bukkit.createBlockData(Material.WARPED_DOOR);
        lower.setHalf(Bisected.Half.BOTTOM);
        lower.setFacing(facing);
        lower.setHinge(Door.Hinge.LEFT);
        lower.setOpen(false);

        Door upper = (Door) lower.clone();
        upper.setHalf(Bisected.Half.TOP);

        lowerBlock.setBlockData(lower, false);
        upperBlock.setBlockData(upper, false);
    }

    private void setLocal(int u, int v, int y, Material material, boolean physics) {
        world.getBlockAt(site.x(u, v), y, site.z(u, v)).setType(material, physics);
    }

    private void indexProtectionVolume() {
        addBox(-1, 1, -1, 1, site.bottomY(), site.topY() + 3);
        addBox(-2, 5, -2, 2, site.topY(), site.topY() + 3);
        addBox(-6, -2, -2, 2, site.bottomY() - 2, site.bottomY());
        addBox(-5, -3, -1, 1, site.bottomY() + 1, site.topY() + 2);
    }

    private void addBox(int minU, int maxU, int minV, int maxV, int minY, int maxY) {
        for (int u = minU; u <= maxU; u++) {
            for (int v = minV; v <= maxV; v++) {
                for (int y = minY; y <= maxY; y++) {
                    protectedVolume.add(new BlockPos(site.x(u, v), y, site.z(u, v)));
                }
            }
        }
    }

    private static boolean isPerimeter(int u, int v) {
        return Math.abs(u) == 1 || Math.abs(v) == 1;
    }

    private static boolean insideShaft(int u, int v) {
        return Math.abs(u) <= 1 && Math.abs(v) <= 1;
    }

    private record BlockPos(int x, int y, int z) {
    }
}

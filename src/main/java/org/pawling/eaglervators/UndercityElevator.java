package org.pawling.eaglervators;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.persistence.PersistentDataType;

/**
 * Optional city entry: a two-column bubble lift below the surface, not a
 * replacement for the standalone cliff scanner. SOUTH is toward the pyramid.
 */
public final class UndercityElevator implements Listener {
    private static final NamespacedKey READY = new NamespacedKey("eaglercity", "undercity_ready");
    private static final NamespacedKey X = new NamespacedKey("eaglercity", "undercity_x");
    private static final NamespacedKey Y = new NamespacedKey("eaglercity", "undercity_y");
    private static final NamespacedKey Z = new NamespacedKey("eaglercity", "undercity_z");
    private static final NamespacedKey LIFT = new NamespacedKey("eaglervators", "undercity_lift");
    private final EaglervatorsPlugin plugin;
    private final World world;
    private final int x, y, z, top;

    private UndercityElevator(EaglervatorsPlugin plugin, World w, int x, int y, int z) {
        this.plugin = plugin;
        this.world = w;
        this.x = x;
        this.y = y;
        this.z = z;
        this.top = y + 29; // City floor is 29 blocks above the cavern floor.
    }

    public static UndercityElevator discover(EaglervatorsPlugin plugin, World world) {
        if (world.getPersistentDataContainer().getOrDefault(READY, PersistentDataType.INTEGER, 0) != 1)
            return null;
        int x = world.getPersistentDataContainer().getOrDefault(X, PersistentDataType.INTEGER, 0);
        int y = world.getPersistentDataContainer().getOrDefault(Y, PersistentDataType.INTEGER, 0);
        int z = world.getPersistentDataContainer().getOrDefault(Z, PersistentDataType.INTEGER, 0);
        if (y < world.getMinHeight() + 3 || y + 33 >= Math.min(256, world.getMaxHeight()))
            return null;
        return new UndercityElevator(plugin, world, x, y, z);
    }

    public boolean isBuilt() {
        return world.getPersistentDataContainer().getOrDefault(LIFT, PersistentDataType.INTEGER, 0) == 1
                && at(0, y, -13).getType() == Material.MAGMA_BLOCK
                && at(3, y, -13).getType() == Material.SOUL_SAND;
    }

    /**
     * Repairs both preexisting and freshly built shafts without rebuilding the city.
     * The top station floor is at 'top', so bubbles must end at top, leaving
     * two clear blocks (top+1 and top+2) to reach the doorway.
     */
    public void repairWaterColumns() {
        boolean exitFixed = repairTopExits();
        boolean down = BubbleColumnWater.ensure(world, x, z - 13, y, top, Material.MAGMA_BLOCK);
        boolean up = BubbleColumnWater.ensure(world, x + 3, z - 13, y, top, Material.SOUL_SAND);
        if (exitFixed || down || up) {
            plugin.getLogger().info("Undercity lift corrected; " + waterStatus());
        }
    }

    private boolean repairTopExits() {
        boolean repaired = false;
        for (int dx : new int[] {0, 3}) {
            // An old glass ceiling at top+2 blocked the player's head.
            // Remove old top+1 water too, so the exit is two blocks high.
            for (int h = top + 1; h <= top + 2; h++) {
                if (at(dx, h, -13).getType() != Material.AIR) {
                    set(dx, h, -13, Material.AIR);
                    repaired = true;
                }
            }
            // The top+3 glass roof already exists in newly generated lifts.
            if (at(dx, top + 3, -13).getType() != Material.GLASS) {
                set(dx, top + 3, -13, Material.GLASS);
                repaired = true;
            }
        }
        return repaired;
    }

    public String waterStatus() {
        return "DOWN: " + BubbleColumnWater.inspect(world, x, z - 13, y, top, Material.MAGMA_BLOCK).summary()
                + " | UP: " + BubbleColumnWater.inspect(world, x + 3, z - 13, y, top, Material.SOUL_SAND).summary()
                + " | top exit air: " + exitsClear();
    }

    private boolean exitsClear() {
        for (int dx : new int[] {0, 3}) {
            for (int h = top + 1; h <= top + 2; h++) {
                if (at(dx, h, -13).getType() != Material.AIR) return false;
            }
        }
        return true;
    }

    private Block at(int dx, int yy, int dz) { return world.getBlockAt(x + dx, yy, z + dz); }
    private void set(int dx, int yy, int dz, Material type) { at(dx, yy, dz).setType(type, false); }

    public void build() {
        // Install a dry protected access hall at the top and bottom, two
        // independent 1x1 glass water shafts (soul sand up, magma down).
        for (int h = y; h <= top + 2; h++) {
            for (int dx = -1; dx <= 4; dx++)
                for (int dz = -14; dz <= -12; dz++) {
                    boolean shaft = (dx == 0 || dx == 3) && dz == -13;
                    if (shaft) {
                        // Keep top+1 and top+2 clear; roof will be at top+3.
                        set(dx, h, dz, h == y ? Material.STONE_BRICKS : Material.AIR);
                    } else if (dx == -1 || dx == 4 || dz == -14 || dz == -12) {
                        set(dx, h, dz, Material.GLASS);
                    } else {
                        set(dx, h, dz, Material.AIR);
                    }
                }
        }
        // Floor-level dry airlock. Enter each shaft from its southern face,
        // then face south to see the front entrance of the temple.
        for (int h : new int[] {y, top}) {
            for (int dx = -1; dx <= 4; dx++)
                for (int dz = -12; dz <= -10; dz++) {
                    set(dx, h, dz, Material.SMOOTH_STONE);
                    for (int above = 1; above <= 3; above++)
                        set(dx, h + above, dz, Material.AIR);
                }
            set(0, h + 1, -12, Material.OAK_DOOR);
            set(0, h + 2, -12, Material.OAK_DOOR);
            set(3, h + 1, -12, Material.OAK_DOOR);
            set(3, h + 2, -12, Material.OAK_DOOR);
            for (int dx : new int[] {0, 3}) {
                org.bukkit.block.data.type.Door lower =
                        (org.bukkit.block.data.type.Door) Bukkit.createBlockData(Material.OAK_DOOR);
                lower.setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM);
                lower.setFacing(org.bukkit.block.BlockFace.SOUTH);
                org.bukkit.block.data.type.Door upper =
                        (org.bukkit.block.data.type.Door) lower.clone();
                upper.setHalf(org.bukkit.block.data.Bisected.Half.TOP);
                at(dx, h + 1, -12).setBlockData(lower, false);
                at(dx, h + 2, -12).setBlockData(upper, false);
            }
            for (int dx : new int[] {-1, 4}) {
                set(dx, h + 1, -10, Material.GLOWSTONE);
            }
        }
        // Top station rooftop and bottom exit marking.
        for (int dx = -1; dx <= 4; dx++)
            for (int dz = -14; dz <= -12; dz++)
                set(dx, top + 3, dz, Material.GLASS);
        set(1, y + 1, -11, Material.SEA_LANTERN);
        set(1, top + 1, -11, Material.SEA_LANTERN);
        // Water terminates at the upper floor (top), not above the door.
        // Magma drags down toward the temple; soul sand pushes players up.
        repairWaterColumns();
        world.getPersistentDataContainer().set(LIFT, PersistentDataType.INTEGER, 1);
        plugin.getLogger().info("Undercity double lift at " + x + "," + y + "," + (z - 13)
                + " facing SOUTH to the pyramid.");
    }

    public String location() { return x + ", " + y + ", " + (z - 13); }

    private boolean protectedAt(Block b) {
        return b.getWorld().getUID().equals(world.getUID())
                && b.getX() >= x - 1 && b.getX() <= x + 4
                && b.getZ() >= z - 14 && b.getZ() <= z - 10
                && b.getY() >= y && b.getY() <= top + 3;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (protectedAt(e.getBlock())) {
            e.setCancelled(true); e.getPlayer().sendActionBar("This lift is protected.");
        }
    }
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) { if (protectedAt(e.getBlockPlaced())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent e) { if (protectedAt(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) { if (protectedAt(e.getBlock())) e.setCancelled(true); }
    @EventHandler(ignoreCancelled = true)
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(this::protectedAt); }
    @EventHandler(ignoreCancelled = true)
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(this::protectedAt); }
    @EventHandler(ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> protectedAt(b) || protectedAt(b.getRelative(e.getDirection()))))
            e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true)
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> protectedAt(b) || protectedAt(b.getRelative(e.getDirection()))))
            e.setCancelled(true);
    }
}

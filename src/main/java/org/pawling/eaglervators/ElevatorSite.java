package org.pawling.eaglervators;

import org.bukkit.block.BlockFace;

import java.util.UUID;

public record ElevatorSite(
        UUID worldId,
        String worldName,
        int shaftX,
        int shaftZ,
        int bottomY,
        int topY,
        BlockFace uphill
) {
    public int ux() { return uphill.getModX(); }
    public int uz() { return uphill.getModZ(); }
    public int vx() { return -uz(); }
    public int vz() { return ux(); }

    public int x(int u, int v) {
        return shaftX + (u * ux()) + (v * vx());
    }

    public int z(int u, int v) {
        return shaftZ + (u * uz()) + (v * vz());
    }

    public int height() { return topY - bottomY; }
}

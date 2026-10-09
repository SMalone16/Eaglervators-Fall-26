# Eaglervators — Fall 2026

A Paper 1.21.11 classroom plugin that automatically finds the nearest **real 20+ block cliff** around world spawn and builds **one** protected glass-and-soul-sand elevator as a proof of concept.

## What it does

On first startup, Eaglervators searches outward from spawn in nearest-first order. A location is eligible only when:

- two adjacent terrain columns on the X or Z axis differ by at least 20 blocks in Y;
- a five-column cross-section confirms that the height change continues across the cliff face;
- the lower approach is reasonably level;
- the upper destination is reasonably level;
- the proposed elevator is not absurdly tall; and
- the downhill jump-pool area is safe enough to carve into the lower terrain.

This prevents one tree, tower, odd overhang, or isolated terrain spike from becoming an elevator site.

## Generated structure

The plugin creates exactly one structure:

- a 3x3 glass elevator shaft;
- a source-water column over soul sand for vanilla upward bubble lift;
- a warped-door entrance at the bottom;
- a warped-door exit at the top;
- an unbreakable top landing that bridges into the detected upper terrain;
- a protected downhill jump edge; and
- a **3 x 2 x 3 water pool** at the bottom for players who want to jump back down.

The shaft, landing, pool, and fall corridor are protected from block breaking/placing, buckets, explosions, and piston movement.

The generated site is saved to `plugins/Eaglervators/elevator-state.yml`, so restarting the server does **not** create another elevator.

## Build

This project targets the same classroom runtime as the Eaglercraft server:

- Paper 1.21.11
- Java 21
- Maven

Build locally:

```bash
mvn clean package
```

Output:

```text
target/Eaglervators-1.0.0.jar
```

GitHub Actions also builds every push to `main` and stores the latest classroom-ready copy at:

```text
dist/Eaglervators-1.0.0.jar
```

## Classroom server plugin-picker entry

When you are ready to expose this project in the classroom session picker, add this line to `classroom/plugins.conf` in the server repo:

```text
eaglervators|Eaglervators|SMalone16/Eaglervators-Fall-26|main|dist/Eaglervators-1.0.0.jar|0|Eaglervators-
```

## Admin commands

```text
/eaglervator status
/eaglervator scan
```

`scan` only starts when no Eaglervator currently exists. This is deliberately a one-elevator proof of concept.

## Detector tuning

See `src/main/resources/config.yml`.

The default search radius is 128 blocks. The detector starts from the required 20-block adjacent-column difference, then validates the surrounding cliff width plus upper/lower plateaus before building.

## Optional Undercity city lift
When EaglerCity and EaglerZombiesFall26 are both enabled, the plugin waits for the published world PDC Undercity location and builds a **two-column protected water/bubble elevator**. The soul-sand shaft rises into the city and the magma shaft descends into the cavern; the lower exit faces south toward the temple entrance. The structure is persisted with `eaglervators:undercity_lift` and is reconstructed only if its anchors disappear. Existing cliff lifts remain intact. With either dependency disabled, ordinary nearest-cliff scanning works unchanged. If the City does not publish a cavern within the startup grace window, cliff scanning resumes. The bubbles depend on the running server's water physics and should be verified with the legacy 1.12.2 browser client.

## Still-water bubble columns and saved-world repairs

Every water cell in both the stand-alone cliff shaft and the two Undercity shafts is explicitly placed as still **water level 0 (source water)**. Each cell is then converted to a real vanilla bubble-column block: soul sand pushes **up**, while magma pulls **down**. The running blocks therefore appear as `bubble_column[drag=false]` (up) or `bubble_column[drag=true]` (down), rather than ordinary `water[level=0]`; neither is falling/flowing water.

On server startup the plugin checks **every block from the base through the top water level**. If any height has flowing water, ordinary water, air, or the wrong bubble direction, it reconstructs just that shaft's water and bubbles. This repairs previously saved elevators without deleting the saved site, regenerating a cliff structure, or requiring a new Undercity.

Restart the Paper server after the repository's GitHub Actions build updates `dist/Eaglervators-1.0.0.jar`; the server's session picker downloads that file when Eaglervators is selected, including the Undercity `u` preset. A `/reload` is not recommended for plugin JAR updates.

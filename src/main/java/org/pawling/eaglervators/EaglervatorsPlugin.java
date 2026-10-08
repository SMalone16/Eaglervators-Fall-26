package org.pawling.eaglervators;

import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.block.BlockFace;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.PluginCommand;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class EaglervatorsPlugin extends JavaPlugin {
    private TerrainScanner scanner;
    private ElevatorStructure structure;
    private UndercityElevator cityLift;
    private File stateFile;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        stateFile = new File(getDataFolder(), "elevator-state.yml");

        PluginCommand command = getCommand("eaglervator");
        if (command != null) {
            command.setExecutor(this);
            command.setTabCompleter(this);
        }

        long delay = Math.max(1L, getConfig().getLong("generation.startup-delay-ticks", 60L));
        getServer().getScheduler().runTaskLater(this, this::initialize, delay);

        getLogger().info("Eaglervators enabled. Proof-of-concept mode allows exactly one generated elevator.");
    }

    @Override
    public void onDisable() {
        if (scanner != null) {
            scanner.cancel();
        }
    }

    private void initialize() {
        ElevatorSite saved = loadSite();
        if (saved != null) {
            World world = Bukkit.getWorld(saved.worldId());
            if (world != null) {
                ElevatorStructure loaded = new ElevatorStructure(this, saved);
                if (loaded.anchorLooksIntact()) {
                    structure = loaded;
                    getServer().getPluginManager().registerEvents(structure, this);
                    getLogger().info("Restored protection for the existing Eaglervator at "
                            + saved.shaftX() + ", " + saved.bottomY() + ", " + saved.shaftZ() + ".");
                    if (!wantsCityLift()) return;
                    startCityLiftPoll();
                    return;
                }
                getLogger().warning("Saved Eaglervator state exists, but the structure anchor is missing. Scanning again.");
            }
        }

        if (wantsCityLift()) startCityLiftPoll();
        else startScan(null);
    }

    private boolean wantsCityLift() {
        return getConfig().getBoolean("undercity.prefer-city", true)
                && getServer().getPluginManager().isPluginEnabled("EaglerCity")
                && getServer().getPluginManager().isPluginEnabled("EaglerZombiesFall26");
    }

    private void startCityLiftPoll() {
        new org.bukkit.scheduler.BukkitRunnable() {
            int checks;
            @Override public void run() {
                World world = selectWorld();
                UndercityElevator discovered = world == null ? null : UndercityElevator.discover(EaglervatorsPlugin.this, world);
                if (discovered != null) {
                    cityLift = discovered;
                    if (!cityLift.isBuilt()) cityLift.build();
                    getServer().getPluginManager().registerEvents(cityLift, EaglervatorsPlugin.this);
                    getLogger().info("City elevator online at " + cityLift.location());
                    cancel();
                } else if (++checks >= 30) {
                    getLogger().warning("City Undercity was not ready; retaining standalone Eaglervator behavior.");
                    if (structure == null) startScan(null);
                    cancel();
                }
            }
        }.runTaskTimer(this, 1L, 40L);
    }

    private void startScan(CommandSender requester) {
        if (structure != null) {
            if (requester != null) {
                requester.sendMessage("One Eaglervator already exists. Proof-of-concept mode will not create another.");
            }
            return;
        }

        if (scanner != null && scanner.isRunning()) {
            if (requester != null) {
                requester.sendMessage("The Eaglervator terrain scan is already running.");
            }
            return;
        }

        World world = selectWorld();
        if (world == null) {
            getLogger().severe("No normal Minecraft world is available for the Eaglervator scan.");
            if (requester != null) {
                requester.sendMessage("No normal world is available to scan.");
            }
            return;
        }

        scanner = new TerrainScanner(this, world);
        getLogger().info("Scanning nearest-first around spawn for a confirmed 20+ block cliff...");
        if (requester != null) {
            requester.sendMessage("Started nearest-first Eaglervator terrain scan around " + world.getName() + " spawn.");
        }

        scanner.start(site -> {
            structure = new ElevatorStructure(this, site);
            structure.build();
            getServer().getPluginManager().registerEvents(structure, this);
            saveSite(site);
            scanner = null;

            String message = "Built the single proof-of-concept Eaglervator at "
                    + site.shaftX() + ", " + site.bottomY() + ", " + site.shaftZ()
                    + " rising " + site.height() + " blocks toward "
                    + site.uphill().name().toLowerCase(Locale.ROOT) + ".";
            getLogger().info(message);
            if (requester != null) {
                requester.sendMessage(message);
            }
        }, () -> {
            scanner = null;
            String message = "No confirmed cliff met the Eaglervator criteria inside the configured scan radius.";
            getLogger().warning(message);
            if (requester != null) {
                requester.sendMessage(message);
            }
        });
    }

    private World selectWorld() {
        String configured = getConfig().getString("world-name", "").trim();
        if (!configured.isEmpty()) {
            World named = Bukkit.getWorld(configured);
            if (named != null) {
                return named;
            }
            getLogger().warning("Configured world-name '" + configured + "' is not loaded. Falling back to a normal world.");
        }

        for (World world : Bukkit.getWorlds()) {
            if (world.getEnvironment() == World.Environment.NORMAL) {
                return world;
            }
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private ElevatorSite loadSite() {
        if (!stateFile.isFile()) {
            return null;
        }

        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(stateFile);
        if (!yaml.getBoolean("generated", false)) {
            return null;
        }

        try {
            UUID worldId = UUID.fromString(yaml.getString("world-id", ""));
            String worldName = yaml.getString("world-name", "world");
            int shaftX = yaml.getInt("shaft.x");
            int shaftZ = yaml.getInt("shaft.z");
            int bottomY = yaml.getInt("shaft.bottom-y");
            int topY = yaml.getInt("shaft.top-y");
            BlockFace uphill = BlockFace.valueOf(yaml.getString("uphill", "NORTH"));

            return new ElevatorSite(worldId, worldName, shaftX, shaftZ, bottomY, topY, uphill);
        } catch (IllegalArgumentException ex) {
            getLogger().warning("Could not parse elevator-state.yml; a fresh scan will be used.");
            return null;
        }
    }

    private void saveSite(ElevatorSite site) {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("generated", true);
        yaml.set("world-id", site.worldId().toString());
        yaml.set("world-name", site.worldName());
        yaml.set("shaft.x", site.shaftX());
        yaml.set("shaft.z", site.shaftZ());
        yaml.set("shaft.bottom-y", site.bottomY());
        yaml.set("shaft.top-y", site.topY());
        yaml.set("uphill", site.uphill().name());

        try {
            yaml.save(stateFile);
        } catch (IOException ex) {
            getLogger().severe("Built the Eaglervator but could not save its state: " + ex.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("scan")) {
            startScan(sender);
            return true;
        }

        if (sub.equals("status")) {
            if (cityLift != null) sender.sendMessage("Undercity elevator: " + cityLift.location() + " (facing temple)");
            if (structure != null) {
                ElevatorSite site = structure.site();
                sender.sendMessage("Eaglervator: ACTIVE at "
                        + site.shaftX() + ", " + site.bottomY() + ", " + site.shaftZ()
                        + " | height " + site.height()
                        + " | uphill " + site.uphill().name().toLowerCase(Locale.ROOT));
            } else if (scanner != null && scanner.isRunning()) {
                sender.sendMessage("Eaglervator: scanning nearest-first around spawn.");
            } else {
                sender.sendMessage("Eaglervator: no elevator currently generated.");
            }
            return true;
        }

        sender.sendMessage("Usage: /eaglervator <status|scan>");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }

        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> matches = new ArrayList<>();
        for (String option : List.of("status", "scan")) {
            if (option.startsWith(prefix)) {
                matches.add(option);
            }
        }
        return matches;
    }
}

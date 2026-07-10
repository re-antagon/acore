package org.antagon.acore.listener;

import java.util.EnumMap;
import java.util.Map;
import java.util.logging.Logger;

import org.antagon.acore.core.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.Waterlogged;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class CopperOxidationListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final Logger logger = Logger.getLogger(CopperOxidationListener.class.getName());

    private final boolean enabled;
    private final double waterMultiplier;
    private final double rainMultiplier;
    private final long checkInterval;
    private final int scanRadius;

    private final Map<Material, Material> oxidationStages = new EnumMap<>(Material.class);

    public CopperOxidationListener(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        initializeOxidationStages();

        this.enabled = configManager.getBoolean("copperOxidation.enabled", true);
        this.waterMultiplier = configManager.getDouble("copperOxidation.water-speed-multiplier", 4.0);
        this.rainMultiplier = configManager.getDouble("copperOxidation.rain-speed-multiplier", 2.0);
        this.checkInterval = configManager.getInt("copperOxidation.check-interval", 40);
        this.scanRadius = configManager.getInt("copperOxidation.scan-radius", 48);

        if (enabled) {
            startOxidationTask();
            logger.info("Copper Oxidation acceleration task started (interval: " + checkInterval + " ticks)");
        }
    }

    private void initializeOxidationStages() {
        oxidationStages.put(Material.COPPER_BLOCK, Material.EXPOSED_COPPER);
        oxidationStages.put(Material.EXPOSED_COPPER, Material.WEATHERED_COPPER);
        oxidationStages.put(Material.WEATHERED_COPPER, Material.OXIDIZED_COPPER);

        oxidationStages.put(Material.CUT_COPPER, Material.EXPOSED_CUT_COPPER);
        oxidationStages.put(Material.EXPOSED_CUT_COPPER, Material.WEATHERED_CUT_COPPER);
        oxidationStages.put(Material.WEATHERED_CUT_COPPER, Material.OXIDIZED_CUT_COPPER);

        oxidationStages.put(Material.COPPER_BULB, Material.EXPOSED_COPPER_BULB);
        oxidationStages.put(Material.EXPOSED_COPPER_BULB, Material.WEATHERED_COPPER_BULB);
        oxidationStages.put(Material.WEATHERED_COPPER_BULB, Material.OXIDIZED_COPPER_BULB);

        oxidationStages.put(Material.COPPER_DOOR, Material.EXPOSED_COPPER_DOOR);
        oxidationStages.put(Material.EXPOSED_COPPER_DOOR, Material.WEATHERED_COPPER_DOOR);
        oxidationStages.put(Material.WEATHERED_COPPER_DOOR, Material.OXIDIZED_COPPER_DOOR);

        oxidationStages.put(Material.COPPER_TRAPDOOR, Material.EXPOSED_COPPER_TRAPDOOR);
        oxidationStages.put(Material.EXPOSED_COPPER_TRAPDOOR, Material.WEATHERED_COPPER_TRAPDOOR);
        oxidationStages.put(Material.WEATHERED_COPPER_TRAPDOOR, Material.OXIDIZED_COPPER_TRAPDOOR);

        tryAddMaterial("COPPER_STAIRS", "EXPOSED_COPPER_STAIRS");
        tryAddMaterial("EXPOSED_COPPER_STAIRS", "WEATHERED_COPPER_STAIRS");
        tryAddMaterial("WEATHERED_COPPER_STAIRS", "OXIDIZED_COPPER_STAIRS");

        tryAddMaterial("COPPER_SLAB", "EXPOSED_COPPER_SLAB");
        tryAddMaterial("EXPOSED_COPPER_SLAB", "WEATHERED_COPPER_SLAB");
        tryAddMaterial("WEATHERED_COPPER_SLAB", "OXIDIZED_COPPER_SLAB");
    }

    private void tryAddMaterial(String from, String to) {
        try {
            oxidationStages.put(Material.valueOf(from), Material.valueOf(to));
        } catch (IllegalArgumentException ignored) {}
    }

    private void startOxidationTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                accelerateCopperOxidation();
            }
        }.runTaskTimer(plugin, 20L, checkInterval);
    }

    private void accelerateCopperOxidation() {
        if (!enabled) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.isOnline()) continue;

            World world = player.getWorld();
            int centerX = player.getLocation().getBlockX();
            int centerY = player.getLocation().getBlockY();
            int centerZ = player.getLocation().getBlockZ();

            int minX = centerX - scanRadius;
            int maxX = centerX + scanRadius;
            int minZ = centerZ - scanRadius;
            int maxZ = centerZ + scanRadius;
            int minY = Math.max(centerY - scanRadius, world.getMinHeight());
            int maxY = Math.min(centerY + scanRadius, world.getMaxHeight() - 1);

            for (int x = minX; x <= maxX; x += 3) {
                for (int z = minZ; z <= maxZ; z += 3) {
                    for (int y = minY; y <= maxY; y += 2) {
                        try {
                            Block block = world.getBlockAt(x, y, z);
                            if (oxidationStages.containsKey(block.getType())) {
                                checkAndOxidize(block);
                            }
                        } catch (Exception ignored) {}
                    }
                }
            }
        }
    }

    private void checkAndOxidize(Block block) {
        Material currentType = block.getType();
        Material nextType = oxidationStages.get(currentType);
        if (nextType == null) return;

        boolean inWater = isSubmergedInWater(block);
        boolean underRain = isUnderRain(block);

        if (!inWater && !underRain) return;

        // Base chance per check (about 2%)
        double chance = 0.02;

        if (inWater) {
            chance *= waterMultiplier;
        }
        if (underRain) {
            chance *= rainMultiplier;
        }

        // Cap the chance at reasonable value
        chance = Math.min(chance, 0.85);

        if (Math.random() < chance) {
            // Oxidize the block
            block.setType(nextType);

            // Optional: play sound effect
            try {
                block.getWorld().playSound(
                    block.getLocation(),
                    Sound.BLOCK_COPPER_PLACE,
                    0.6f,
                    0.8f + (float)(Math.random() * 0.4)
                );
            } catch (Exception ignored) {}
        }
    }

    private boolean isSubmergedInWater(Block block) {
        // Check if waterlogged
        BlockData blockData = block.getBlockData();
        if (blockData instanceof Waterlogged && ((Waterlogged) blockData).isWaterlogged()) {
            return true;
        }

        // Check adjacent blocks for water
        BlockFace[] faces = {
            BlockFace.UP, BlockFace.DOWN,
            BlockFace.NORTH, BlockFace.EAST,
            BlockFace.SOUTH, BlockFace.WEST
        };

        for (BlockFace face : faces) {
            Block relative = block.getRelative(face);
            Material type = relative.getType();
            if (type == Material.WATER ||
                type == Material.WATER_CAULDRON ||
                type == Material.KELP ||
                type == Material.SEAGRASS) {
                return true;
            }
        }

        // Check if the block itself is in water (for non-waterlogged cases)
        Block above = block.getRelative(BlockFace.UP);
        return above.getType() == Material.WATER;
    }

    private boolean isUnderRain(Block block) {
        World world = block.getWorld();

        // Check if it's raining/storming
        if (!world.hasStorm()) {
            return false;
        }

        // Check if the block has sky access (no solid blocks directly above)
        int x = block.getX();
        int z = block.getZ();
        int startY = block.getY() + 1;
        int maxY = world.getMaxHeight();

        for (int y = startY; y < maxY; y++) {
            Block aboveBlock = world.getBlockAt(x, y, z);
            Material type = aboveBlock.getType();

            // Consider solid blocks as blocking rain (except transparent ones)
            if (type.isSolid() && !type.name().contains("LEAVES")) {
                return false;
            }
        }

        return true;
    }
}

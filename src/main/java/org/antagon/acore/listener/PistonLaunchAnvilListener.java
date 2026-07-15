package org.antagon.acore.listener;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.block.data.type.Piston;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockPhysicsEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Vector;

public class PistonLaunchAnvilListener implements Listener {

    private final Plugin plugin;
    private final NamespacedKey startYKey;

    // Tracks anvils that were removed but piston hasn't extended yet
    private final Map<Location, BlockData> pendingAnvils = new HashMap<>();

    private final EnumSet<Material> anvils = EnumSet.of(
        Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL
    );

    public PistonLaunchAnvilListener(Plugin plugin) {
        this.plugin = plugin;
        this.startYKey = new NamespacedKey(plugin, "anvil_start_y");
        plugin.getLogger().info("PistonLaunchAnvilListener registered successfully!");
    }

    /**
     * STAGE 1: Catch the redstone signal reaching the piston, before it tries to extend.
     * BlockPhysicsEvent fires when a block is updated due to a neighbor change.
     * When a button/lever is toggled, the redstone signal propagates and triggers
     * BlockPhysicsEvent on the piston BEFORE the piston attempts to extend.
     *
     * CRITICAL: We do NOT check isExtended() - by the time this fires, the piston
     * may have already updated its state. We only check if it's powered and has an
     * anvil above it.
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();

        // Only care about pistons facing up
        if (!(block.getBlockData() instanceof Piston pistonData)) return;
        if (pistonData.getFacing() != BlockFace.UP) return;

        // Check if the piston is powered
        if (!block.isBlockPowered() && !block.isBlockIndirectlyPowered()) return;

        // Check if there's an anvil 2 blocks above
        Block anvilBlock = block.getRelative(BlockFace.UP, 2);
        if (!anvils.contains(anvilBlock.getType())) return;

        // Already tracking this anvil? Skip
        Location anvilLoc = anvilBlock.getLocation();
        if (pendingAnvils.containsKey(anvilLoc)) return;

        // Remove the anvil BEFORE the piston tries to push
        BlockData anvilData = anvilBlock.getBlockData();
        anvilBlock.setType(Material.AIR, false);

        // Store it for the piston extend event
        pendingAnvils.put(anvilLoc, anvilData);
    }

    /**
     * STAGE 2: Launch the anvil AFTER the piston extends.
     * BlockPistonExtendEvent fires after the piston has successfully extended.
     * At this point, the anvil was already removed in Stage 1, so the piston
     * can extend freely. We spawn a FallingBlock in its place.
     */
    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        Block pistonBlock = event.getBlock();
        if (event.getDirection() != BlockFace.UP) return;

        // Check if we removed an anvil 2 blocks above this piston
        Location anvilLoc = pistonBlock.getRelative(BlockFace.UP, 2).getLocation();
        BlockData anvilData = pendingAnvils.remove(anvilLoc);

        if (anvilData == null) return;

        // Launch the anvil as a FallingBlock entity
        launchAnvil(pistonBlock.getRelative(BlockFace.UP, 2), anvilData);
    }

    private void launchAnvil(Block formerAnvilBlock, BlockData data) {
        var spawnLoc = formerAnvilBlock.getLocation().add(0.5, 0.2, 0.5);

        formerAnvilBlock.getWorld().spawn(spawnLoc, FallingBlock.class, spawned -> {
            spawned.setBlockData(data);
            spawned.setDropItem(true);
            spawned.setHurtEntities(true);
            spawned.setVelocity(new Vector(0, 1.2, 0));

            double predictedPeak = spawnLoc.getY() + (1.2 * 8.0);
            spawned.getPersistentDataContainer().set(
                startYKey, PersistentDataType.DOUBLE, predictedPeak
            );
        });
    }
}

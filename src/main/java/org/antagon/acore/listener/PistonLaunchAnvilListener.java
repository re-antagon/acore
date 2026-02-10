package org.antagon.acore.listener;

import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;
import org.bukkit.Bukkit;
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
    
    // Key: The Location of the ANVIL, Value: The Data of that Anvil
    private final Map<Location, BlockData> activeVoids = new HashMap<>();

    private final EnumSet<Material> anvils = EnumSet.of(Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL);
    private final EnumSet<Material> pistons = EnumSet.of(Material.PISTON, Material.STICKY_PISTON);

    public PistonLaunchAnvilListener(Plugin plugin) {
        this.plugin = plugin;
        this.startYKey = new NamespacedKey(plugin, "anvil_start_y");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block piston = event.getBlock();
        if (!pistons.contains(piston.getType())) return;
        if (!piston.isBlockPowered() && !piston.isBlockIndirectlyPowered()) return;
        
        if (!(piston.getBlockData() instanceof Piston pistonData) || pistonData.isExtended()) return;
        if (pistonData.getFacing() != BlockFace.UP) return;

        Block anvilBlock = piston.getRelative(BlockFace.UP, 2); // The Anvil is 2 blocks up
        if (!anvils.contains(anvilBlock.getType())) return;

        // Check if we are already tracking this specific anvil location
        Location anvilLoc = anvilBlock.getLocation();
        if (activeVoids.containsKey(anvilLoc)) return;

        // --- STAGE 1: THE SNAPSHOT ---
        BlockData data = anvilBlock.getBlockData();
        activeVoids.put(anvilLoc, data);
        
        // Remove the anvil so the piston can move
        anvilBlock.setType(Material.AIR, false);

        // --- STAGE 2: THE TICK-END SAFETY NET ---
        Bukkit.getScheduler().runTask(plugin, () -> {
            BlockData dataToRestore = activeVoids.remove(anvilLoc);
            if (dataToRestore != null) {
                // If the data is still here, no piston claimed it. 
                // We restore it even if the block isn't AIR (to prevent total loss)
                if (anvilLoc.getBlock().getType() == Material.AIR || anvils.contains(anvilLoc.getBlock().getType())) {
                     anvilLoc.getBlock().setBlockData(dataToRestore, false);
                } else {
                    // Space is occupied by a slime/piston head? drop as item so it doesn't vanish
                    anvilLoc.getWorld().dropItemNaturally(anvilLoc, dataToRestore.getMaterial().asItemType().createItemStack());
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPistonExtend(BlockPistonExtendEvent event) {
        // Find if any of the blocks this piston is pushing are "Registered Voids"
        // In your A 0 A setup, the piston is at Y, slime at Y+1, anvil was at Y+2.
        Location expectedAnvilLoc = event.getBlock().getRelative(BlockFace.UP, 2).getLocation();
        
        BlockData data = activeVoids.remove(expectedAnvilLoc);
        if (data == null) return;

        // --- STAGE 3: LAUNCH ---
        launchAnvilEntity(expectedAnvilLoc.getBlock(), data);
    }

    private void launchAnvilEntity(Block targetBlock, BlockData data) {
        // We spawn it slightly higher than the target block to ensure it clears the piston head
        var spawnLoc = targetBlock.getLocation().add(0.5, 0.2, 0.5);
        
        targetBlock.getWorld().spawn(spawnLoc, FallingBlock.class, spawned -> {
            spawned.setBlockData(data);
            spawned.setDropItem(true);
            spawned.setHurtEntities(true);
            spawned.setVelocity(new Vector(0, 1.2, 0));

            double predictedPeak = spawnLoc.getY() + (1.2 * 8.0);
            spawned.getPersistentDataContainer().set(startYKey, PersistentDataType.DOUBLE, predictedPeak);
        });
    }
}
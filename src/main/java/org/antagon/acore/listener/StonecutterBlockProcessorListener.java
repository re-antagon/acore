package org.antagon.acore.listener;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

public class StonecutterBlockProcessorListener implements Listener {

    private final JavaPlugin plugin;
    private final Set<Item> trackedItems = new HashSet<>();

    public StonecutterBlockProcessorListener(JavaPlugin plugin) {
        this.plugin = plugin;

        // Start scheduled task to check all dropped sandstone every 5 ticks
        startProcessingTask();
    }

    private void startProcessingTask() {
        new BukkitRunnable() {
            @Override
            public void run() {
                processingTask();
            }
        }.runTaskTimer(plugin, 0L, 5L); // Run every 5 ticks (0.25 seconds)
    }

    /**
     * Adds items to the tracking set.
     */
    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        Item item = event.getItemDrop();
        
        if (item.getItemStack().getType() == Material.SANDSTONE) {
            trackedItems.add(item);
        }
    }

    /**
     * Starts the single synchronous task that runs every 5 ticks.
     */
    private void processingTask() {
        if (trackedItems.isEmpty()) {
            return;
        }

        Iterator<Item> iterator = trackedItems.iterator();
        
        while (iterator.hasNext()) {
            Item item = iterator.next();

            // 1. Clean up invalid items (picked up, despawned, burned, etc.)
            if (!item.isValid()) {
                iterator.remove();
                continue;
            }


            // // 2. Logic Check: Is it on a Stonecutter?
            // // Stonecutters have a height of 0.5 blocks, so we need to check the block
            // // at the item's current height level, not just the block directly below
            // org.bukkit.Location itemLocation = item.getLocation();
            // org.bukkit.Location blockLocation = itemLocation.clone();
            
            // // Get the block at the item's current Y level (accounting for stonecutter height)
            // Block itemBlock = blockLocation.getBlock();
            
            // // Also check the block directly below in case the item is slightly above the stonecutter
            // Block blockBelow = itemLocation.getBlock().getRelative(BlockFace.DOWN);

            // if (itemBlock.getType() == Material.STONECUTTER || blockBelow.getType() == Material.STONECUTTER) {
            //     // Replace sandstone with sand
            //     convertItem(item);
            //     // Remove from tracking set as it is now processed/deleted
            //     iterator.remove(); 
            // }


            // 2. Logic Check: Is it on a Stonecutter?
            // We check the block directly beneath the item entity            
            Block blockBelow = item.getLocation().getBlock().getRelative(BlockFace.DOWN);
            Block block = item.getLocation().getBlock();

            if (blockBelow.getType() == Material.STONECUTTER || block.getType() == Material.STONECUTTER) {
                // Replace sandstone with sand
                convertItem(item);
                // Remove from tracking set as it is now processed/deleted
                iterator.remove(); 
            }
        }
    }

    /**
     * Handles the specific game logic for converting the item.
     */
    private void convertItem(Item item) {
        int droppedBlockAmount = item.getItemStack().getAmount();
        int amountPerBlock = 3; 
        int totalSandToDrop = droppedBlockAmount * amountPerBlock;
        
        // Remove the sandstone entity
        item.remove();

        // Drop the sand
        // We drop 'amountToDrop' individual items or a stack depending on preference.
        // Here we drop a single stack of 3 sand.
        if (totalSandToDrop > 0) {            
            ItemStack sandStack = new ItemStack(Material.SAND, totalSandToDrop);
            item.getWorld().dropItem(item.getLocation(), sandStack);            
        }
    }
}
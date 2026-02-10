package org.antagon.acore.listener;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.FallingBlock;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntitySpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Vector;

import java.util.EnumSet;

public class AnvilFallListener implements Listener {

    private final JavaPlugin plugin;
    private final NamespacedKey startYKey;
    private final EnumSet<Material> anvils = EnumSet.of(Material.ANVIL, Material.CHIPPED_ANVIL, Material.DAMAGED_ANVIL);

    // Settings for the bounce feel
    private static final double VELOCITY_PER_BLOCK = 0.09; // Adjusted for better physics
    private static final double MIN_BOUNCE = 0.05;
    private static final double MAX_BOUNCE = 2.5;
    private static final double STOP_THRESHOLD = 0.15; // Increased to prevent annoying micro-bounces

    public AnvilFallListener(JavaPlugin plugin) {
        this.plugin = plugin;
        this.startYKey = new NamespacedKey(plugin, "anvil_start_y");
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAnvilSpawn(EntitySpawnEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) return;
        if (!anvils.contains(fallingBlock.getBlockData().getMaterial())) return;

        // If it doesn't have a start Y yet (first spawn), set it to current Y
        if (!fallingBlock.getPersistentDataContainer().has(startYKey, PersistentDataType.DOUBLE)) {
            fallingBlock.getPersistentDataContainer().set(startYKey, PersistentDataType.DOUBLE, fallingBlock.getLocation().getY());
        }
    }

    @EventHandler(priority = EventPriority.NORMAL, ignoreCancelled = true)
    public void onAnvilLand(EntityChangeBlockEvent event) {
        if (!(event.getEntity() instanceof FallingBlock fallingBlock)) return;
        if (!anvils.contains(fallingBlock.getBlockData().getMaterial())) return;

        if (event.getBlock().getRelative(BlockFace.DOWN).getType() != Material.SLIME_BLOCK) return;

        Double startY = fallingBlock.getPersistentDataContainer().get(startYKey, PersistentDataType.DOUBLE);
        if (startY == null) return;

        double currentY = fallingBlock.getLocation().getY();
        double blocksFallen = Math.max(0.0, startY - currentY);
        
        // Calculate the power of the bounce based on how far it fell
        double bounceVel = Math.max(MIN_BOUNCE, Math.min(MAX_BOUNCE, VELOCITY_PER_BLOCK * blocksFallen));

        // If the bounce is too weak, let it land
        if (bounceVel < STOP_THRESHOLD) return;

        event.setCancelled(true);
        handleBounce(fallingBlock, bounceVel);
    }

    private void handleBounce(FallingBlock original, double velocity) {
        Location loc = original.getLocation();
        
        // We calculate how high this velocity will actually carry the anvil.
        // In Minecraft physics, height is roughly (velocity^2 / (2 * gravity)).
        // Using a simpler multiplier (velocity * 10) works well for "game feel".
        double predictedPeakY = loc.getY() + (velocity * 8.0);

        original.remove(); 

        original.getWorld().spawn(loc, FallingBlock.class, spawned -> {
            spawned.setBlockData(original.getBlockData());
            spawned.setDropItem(original.getDropItem());
            spawned.setHurtEntities(original.canHurtEntities());
            spawned.setVelocity(new Vector(0, velocity, 0));
            
            // CRITICAL: We set the new StartY to the peak of the jump.
            // When it falls from the peak back to the slime, it will calculate a new (smaller) bounce.
            spawned.getPersistentDataContainer().set(startYKey, PersistentDataType.DOUBLE, predictedPeakY);
        });

        original.getWorld().playSound(loc, Sound.BLOCK_SLIME_BLOCK_FALL, 1.0f, 1.0f);
    }
}
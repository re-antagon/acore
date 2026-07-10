package org.antagon.acore.listener;

import org.antagon.acore.util.EntityKillTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;

public class EntityKillListener implements Listener {
    private final EntityKillTracker tracker;

    public EntityKillListener() {
        this.tracker = EntityKillTracker.getInstance();
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        // Check if the killer is a player
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        
        // Get the location where the entity died
        Location location = event.getEntity().getLocation();
        
        // Record the kill
        tracker.recordKill(killer, location);
    }
}

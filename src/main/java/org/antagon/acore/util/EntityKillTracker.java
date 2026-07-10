package org.antagon.acore.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class EntityKillTracker {
    private static EntityKillTracker instance;

    // Map of location -> list of player kills (timestamp + player name)
    private final Map<Location, List<KillRecord>> kills = new ConcurrentHashMap<>();

    private EntityKillTracker() {}

    public static EntityKillTracker getInstance() {
        if (instance == null) {
            instance = new EntityKillTracker();
        }
        return instance;
    }

    public void recordKill(Player player, Location location) {
        Location key = roundLocation(location);
        long timestamp = System.currentTimeMillis();

        List<KillRecord> locationKills = kills.computeIfAbsent(key, k -> new ArrayList<>());

        // Add new kill record
        locationKills.add(new KillRecord(player.getName(), timestamp));

        // Keep only last 10 kills per location to prevent memory leaks
        if (locationKills.size() > 10) {
            locationKills.remove(0);
        }
    }

    public List<String> getPlayersWhoKilledEntities(Location center, int radius, int timeHours) {
        Location centerKey = roundLocation(center);
        Set<String> players = new HashSet<>();

        long cutoffTime = System.currentTimeMillis() - (timeHours * 60L * 60L * 1000L);

        // Check all locations within radius
        for (Map.Entry<Location, List<KillRecord>> entry : kills.entrySet()) {
            Location killLocation = entry.getKey();
            List<KillRecord> locationKills = entry.getValue();

            if (isWithinRadius(centerKey, killLocation, radius)) {
                for (KillRecord kill : locationKills) {
                    // Only include kills within time limit
                    if (kill.timestamp >= cutoffTime) {
                        players.add(kill.playerName);
                    }
                }
            }
        }

        return new ArrayList<>(players);
    }

    private Location roundLocation(Location location) {
        return new Location(
            location.getWorld(),
            location.getBlockX(),
            location.getBlockY(),
            location.getBlockZ()
        );
    }

    private boolean isWithinRadius(Location center, Location location, int radius) {
        if (center.getWorld() == null || location.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
            return false;
        }

        double distance = center.distance(location);
        return distance <= radius;
    }

    private static class KillRecord {
        final String playerName;
        final long timestamp;

        KillRecord(String playerName, long timestamp) {
            this.playerName = playerName;
            this.timestamp = timestamp;
        }
    }

    public void cleanupOldKills() {
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours ago

        kills.entrySet().removeIf(entry -> {
            entry.getValue().removeIf(kill -> kill.timestamp < cutoffTime);
            return entry.getValue().isEmpty();
        });
    }
}

package org.antagon.acore.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Location;
import org.bukkit.entity.Player;

public class BlockInteractionTracker {

    private static BlockInteractionTracker instance;

    // Map of location -> list of player interactions (timestamp + player name) - ALL interactions
    private final Map<Location, List<PlayerInteraction>> interactions = new ConcurrentHashMap<>();
    
    // Separate maps for different interaction types
    private final Map<Location, List<PlayerInteraction>> blockPlaces = new ConcurrentHashMap<>();
    private final Map<Location, List<PlayerInteraction>> blockBreaks = new ConcurrentHashMap<>();
    private final Map<Location, List<PlayerInteraction>> blockInteracts = new ConcurrentHashMap<>();

    // Map of location -> last use timestamp for cooldowns
    private final Map<Location, Long> cooldowns = new ConcurrentHashMap<>();

    // Map of player UUID -> last use timestamp for player cooldowns
    private final Map<String, Long> playerCooldowns = new ConcurrentHashMap<>();

    private BlockInteractionTracker() {}

    public static BlockInteractionTracker getInstance() {
        if (instance == null) {
            instance = new BlockInteractionTracker();
        }
        return instance;
    }

    public enum InteractionType {
        PLACE,    // Block placed
        BREAK,    // Block broken
        INTERACT  // Player interacted with block (right-click)
    }

    public void recordInteraction(Player player, Location location, InteractionType type) {
        Location key = roundLocation(location);
        long timestamp = System.currentTimeMillis();

        // Record in general interactions map (for backward compatibility)
        List<PlayerInteraction> locationInteractions = interactions.computeIfAbsent(key, k -> new ArrayList<>());
        locationInteractions.add(new PlayerInteraction(player.getName(), timestamp));
        if (locationInteractions.size() > 10) {
            locationInteractions.remove(0);
        }

        // Record in specific type map
        Map<Location, List<PlayerInteraction>> targetMap;
        switch (type) {
            case PLACE:
                targetMap = blockPlaces;
                break;
            case BREAK:
                targetMap = blockBreaks;
                break;
            case INTERACT:
            default:
                targetMap = blockInteracts;
                break;
        }

        List<PlayerInteraction> typeList = targetMap.computeIfAbsent(key, k -> new ArrayList<>());
        typeList.add(new PlayerInteraction(player.getName(), timestamp));
        if (typeList.size() > 10) {
            typeList.remove(0);
        }
    }

    public void recordInteraction(Player player, Location location) {
        recordInteraction(player, location, InteractionType.INTERACT);
    }

    public List<String> getPlayersWhoPlacedBlocks(Location center, int radius, int timeHours) {
        return getPlayersInRadius(blockPlaces, center, radius, timeHours);
    }

    public List<String> getPlayersWhoBrokeBlocks(Location center, int radius, int timeHours) {
        return getPlayersInRadius(blockBreaks, center, radius, timeHours);
    }

    public List<String> getPlayersWhoInteracted(Location center, int radius, int timeHours) {
        return getPlayersInRadius(blockInteracts, center, radius, timeHours);
    }

    public List<String> getLastPlayersInRadius(Location center, int radius) {
        return getPlayersInRadius(interactions, center, radius, 24);
    }

    private List<String> getPlayersInRadius(Map<Location, List<PlayerInteraction>> interactionMap, Location center, int radius, int timeHours) {
        Location centerKey = roundLocation(center);
        Set<String> players = new HashSet<>();

        long cutoffTime = System.currentTimeMillis() - (timeHours * 60L * 60L * 1000L);

        // Check all locations within radius
        for (Map.Entry<Location, List<PlayerInteraction>> entry : interactionMap.entrySet()) {
            Location interactionLocation = entry.getKey();

            if (isWithinRadius(centerKey, interactionLocation, radius)) {
                List<PlayerInteraction> locationInteractions = entry.getValue();

                for (PlayerInteraction interaction : locationInteractions) {
                    // Only include interactions within time limit
                    if (interaction.timestamp >= cutoffTime) {
                        players.add(interaction.playerName);
                    }
                }
            }
        }

        return new ArrayList<>(players);
    }

    public boolean isOnCooldown(Location location, int cooldownSeconds) {
        Location key = roundLocation(location);
        Long lastUse = cooldowns.get(key);

        if (lastUse == null) {
            return false;
        }

        long timeSinceLastUse = (System.currentTimeMillis() - lastUse) / 1000;
        return timeSinceLastUse < cooldownSeconds;
    }

    public int getRemainingCooldown(Location location, int cooldownSeconds) {
        Location key = roundLocation(location);
        Long lastUse = cooldowns.get(key);

        if (lastUse == null) {
            return 0;
        }

        long timeSinceLastUse = (System.currentTimeMillis() - lastUse) / 1000;
        long remaining = cooldownSeconds - timeSinceLastUse;

        return Math.max(0, (int) remaining);
    }

    public void setCooldown(Location location) {
        Location key = roundLocation(location);
        cooldowns.put(key, System.currentTimeMillis());
    }

    public void removeCooldown(Location location) {
        Location key = roundLocation(location);
        cooldowns.remove(key);
    }

    public Map<Location, Long> getAllCooldowns() {
        return new ConcurrentHashMap<>(cooldowns);
    }

    public boolean isPlayerOnCooldown(Player player, int cooldownSeconds) {
        String playerUUID = player.getUniqueId().toString();
        Long lastUse = playerCooldowns.get(playerUUID);

        if (lastUse == null) {
            return false;
        }

        long timeSinceLastUse = (System.currentTimeMillis() - lastUse) / 1000;
        return timeSinceLastUse < cooldownSeconds;
    }

    public int getPlayerRemainingCooldown(Player player, int cooldownSeconds) {
        String playerUUID = player.getUniqueId().toString();
        Long lastUse = playerCooldowns.get(playerUUID);

        if (lastUse == null) {
            return 0;
        }

        long timeSinceLastUse = (System.currentTimeMillis() - lastUse) / 1000;
        long remaining = cooldownSeconds - timeSinceLastUse;

        return Math.max(0, (int) remaining);
    }

    public void setPlayerCooldown(Player player) {
        String playerUUID = player.getUniqueId().toString();
        playerCooldowns.put(playerUUID, System.currentTimeMillis());
    }

    public Map<String, Long> getAllPlayerCooldowns() {
        return new ConcurrentHashMap<>(playerCooldowns);
    }

    private Location roundLocation(Location location) {
        return new Location(location.getWorld(),
                          location.getBlockX(),
                          location.getBlockY(),
                          location.getBlockZ());
    }

    public Location getLocationKey(Location location) {
        return roundLocation(location);
    }

    private boolean isWithinRadius(Location center, Location location, int radius) {
        if (center.getWorld() == null || location.getWorld() == null || !center.getWorld().equals(location.getWorld())) {
            return false;
        }

        double distance = center.distance(location);
        return distance <= radius;
    }

    private boolean isSameLocation(Location loc1, Location loc2) {
        if (!loc1.getWorld().equals(loc2.getWorld())) {
            return false;
        }

        return loc1.getBlockX() == loc2.getBlockX() &&
               loc1.getBlockY() == loc2.getBlockY() &&
               loc1.getBlockZ() == loc2.getBlockZ();
    }

    private static class PlayerInteraction {
        final String playerName;
        final long timestamp;

        PlayerInteraction(String playerName, long timestamp) {
            this.playerName = playerName;
            this.timestamp = timestamp;
        }
    }

    public void cleanupOldInteractions() {
        long cutoffTime = System.currentTimeMillis() - (24 * 60 * 60 * 1000); // 24 hours ago

        List<Map<Location, List<PlayerInteraction>>> maps = List.of(interactions, blockPlaces, blockBreaks, blockInteracts);
        
        for (Map<Location, List<PlayerInteraction>> map : maps) {
            map.entrySet().removeIf(entry -> {
                entry.getValue().removeIf(interaction -> interaction.timestamp < cutoffTime);
                return entry.getValue().isEmpty();
            });
        }

        // Clean up old cooldowns (older than 1 hour)
        long cooldownCutoff = System.currentTimeMillis() - (60 * 60 * 1000);
        cooldowns.entrySet().removeIf(entry -> entry.getValue() < cooldownCutoff);

        // Clean up old player cooldowns (older than 1 hour)
        playerCooldowns.entrySet().removeIf(entry -> entry.getValue() < cooldownCutoff);
    }
}

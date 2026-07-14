package org.antagon.acore.listener;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.antagon.acore.core.ConfigManager;
import org.antagon.acore.util.BlockInteractionTracker;
import org.antagon.acore.util.EntityKillTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.components.CustomModelDataComponent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;

public class IndicatorPotionListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BlockInteractionTracker blockTracker = BlockInteractionTracker.getInstance();
    private final EntityKillTracker entityTracker = EntityKillTracker.getInstance();

    // Registered indicator potions awaiting impact: UUID → (PotionType, Player name)
    private final Map<UUID, PendingPotion> pendingPotions = new ConcurrentHashMap<>();

    public IndicatorPotionListener(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;

        // Clean up stale entries (potions that never landed, e.g. fell into void)
        new BukkitRunnable() {
            @Override
            public void run() {
                pendingPotions.entrySet().removeIf(entry -> {
                    org.bukkit.entity.Entity entity = plugin.getServer().getEntity(entry.getKey());
                    return entity == null || !entity.isValid();
                });
            }
        }.runTaskTimer(plugin, 600L, 600L); // Every 30 seconds
    }

    public enum PotionType {
        BLUE_INDICATOR("blue_indicator", 1185, "§9Игроки, ставившие блоки", "§9"),
        GREEN_INDICATOR("green_indicator", 1187, "§aИгроки, ломавшие блоки", "§a"),
        PINK_INDICATOR("pink_indicator", 1189, "§dИгроки, взаимодействовавшие с блоками", "§d"),
        RED_INDICATOR("red_indicator", 1191, "§cИгроки, убивавшие сущности", "§c");

        private final String configKey;
        private final int defaultCmd;
        private final String messagePrefix;
        private final String colorCode;

        PotionType(String configKey, int defaultCmd, String messagePrefix, String colorCode) {
            this.configKey = configKey;
            this.defaultCmd = defaultCmd;
            this.messagePrefix = messagePrefix;
            this.colorCode = colorCode;
        }

        public String getConfigKey() { return configKey; }
        public int getDefaultCmd() { return defaultCmd; }
        public String getMessagePrefix() { return messagePrefix; }
        public String getColorCode() { return colorCode; }
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion thrownPotion)) {
            return;
        }

        // Try to get the player who threw the potion
        if (!(thrownPotion.getShooter() instanceof Player player)) {
            return;
        }

        // Check if this is our indicator potion and get its type
        PotionType potionType = getPotionType(thrownPotion);
        if (potionType == null) return;

        // Check global enabled setting
        if (!configManager.getBoolean("indicatorPotions.enabled", true)) {
            return;
        }

        // Check if this potion type is enabled in config
        String enabledPath = "indicatorPotions.potions." + potionType.getConfigKey() + ".enabled";
        if (!configManager.getBoolean(enabledPath, true)) {
            return;
        }

        // Check player-specific cooldown (global cooldown per player)
        int cooldown = configManager.getInt("indicatorPotions.cooldown", 30);

        if (blockTracker.isPlayerOnCooldown(player, cooldown)) {
            int remainingCooldown = blockTracker.getPlayerRemainingCooldown(player, cooldown);
            String msg = "§cВы недавно использовали зелье! Подождите еще §e" + remainingCooldown + " §cсекунд.";
            player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(msg));
            event.setCancelled(true); // Cancel the potion throw
            return;
        }

        // Set player cooldown immediately when potion is thrown
        blockTracker.setPlayerCooldown(player);

        // Register the potion so we can process it when it lands
        pendingPotions.put(thrownPotion.getUniqueId(), new PendingPotion(potionType, player.getUniqueId()));
    }

    @EventHandler
    public void onProjectileHit(ProjectileHitEvent event) {
        if (!(event.getEntity() instanceof ThrownPotion thrownPotion)) {
            return;
        }

        UUID potionUUID = thrownPotion.getUniqueId();
        PendingPotion pending = pendingPotions.remove(potionUUID);
        if (pending == null) return;

        Player player = plugin.getServer().getPlayer(pending.playerId);
        if (player == null || !player.isOnline()) return;

        // Get the impact location — the entity is still valid at this point
        Location effectLocation = thrownPotion.getLocation();

        // Process the indicator effect at the impact location
        handleIndicatorPotionEffect(effectLocation, player, pending.potionType);
    }

    private void handleIndicatorPotionEffect(Location effectLocation, Player player, PotionType potionType) {
        // Get configuration values
        int radius = configManager.getInt("indicatorPotions.radius", 10);
        int timeHours = configManager.getInt("indicatorPotions.time-lookup-hours", 12);

        // Get players based on potion type
        List<String> players;
        switch (potionType) {
            case BLUE_INDICATOR:
                players = blockTracker.getPlayersWhoPlacedBlocks(effectLocation, radius, timeHours);
                break;
            case GREEN_INDICATOR:
                players = blockTracker.getPlayersWhoBrokeBlocks(effectLocation, radius, timeHours);
                break;
            case PINK_INDICATOR:
                players = blockTracker.getPlayersWhoInteracted(effectLocation, radius, timeHours);
                break;
            case RED_INDICATOR:
            default:
                players = entityTracker.getPlayersWhoKilledEntities(effectLocation, radius, timeHours);
                break;
        }

        if (players.isEmpty()) {
            showChatMessage(player, "§7В этом радиусе никого не обнаружено за последние §e" + timeHours + " §7часов.");
            return;
        }

        // Format message with players highlighted in the potion's color
        String playerList = String.join(potionType.getColorCode() + ", " + potionType.getColorCode(), players);
        String message = potionType.getMessagePrefix() + ": " + potionType.getColorCode() + playerList;

        // Show in chat
        showChatMessage(player, message);
    }

    private PotionType getPotionType(ThrownPotion potion) {
        ItemStack item = potion.getItem();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelDataComponent()) {
                CustomModelDataComponent cmdComponent = meta.getCustomModelDataComponent();
                List<Float> floats = cmdComponent.getFloats();

                // Check each potion type
                for (PotionType type : PotionType.values()) {
                    float configCmd = configManager.getInt(
                        "indicatorPotions.potions." + type.getConfigKey() + ".custom-model-data",
                        type.getDefaultCmd()
                    );
                    if (floats.contains(configCmd)) {
                        return type;
                    }
                }
            }
        }

        return null;
    }

    private void showChatMessage(Player player, String message) {
        // Build a beautiful multi-line chat message with borders
        String line1 = "§7§m                                             ";
        String line2 = " " + message + " ";
        String line3 = "§7§m                                             ";

        String fullMessage = "\n" + line1 + "\n" + line2 + "\n" + line3;

        Component component = LegacyComponentSerializer.legacySection().deserialize(fullMessage);
        player.sendMessage(component);
    }

    private record PendingPotion(PotionType potionType, UUID playerId) {}
}

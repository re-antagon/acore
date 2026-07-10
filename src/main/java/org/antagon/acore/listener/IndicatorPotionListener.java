package org.antagon.acore.listener;

import java.util.List;

import org.antagon.acore.core.ConfigManager;
import org.antagon.acore.util.BlockInteractionTracker;
import org.antagon.acore.util.EntityKillTracker;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.entity.ThrownPotion;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public class IndicatorPotionListener implements Listener {

    private final JavaPlugin plugin;
    private final ConfigManager configManager;
    private final BlockInteractionTracker blockTracker = BlockInteractionTracker.getInstance();
    private final EntityKillTracker entityTracker = EntityKillTracker.getInstance();

    public IndicatorPotionListener(JavaPlugin plugin, ConfigManager configManager) {
        this.plugin = plugin;
        this.configManager = configManager;
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
        if (!(event.getEntity() instanceof ThrownPotion)) {
            return;
        }

        ThrownPotion thrownPotion = (ThrownPotion) event.getEntity();

        // Try to get the player who threw the potion
        if (!(thrownPotion.getShooter() instanceof Player)) {
            return;
        }

        Player player = (Player) thrownPotion.getShooter();

        // Check if this is our indicator potion and get its type
        PotionType potionType = getPotionType(thrownPotion);
        if (potionType == null) return;
        
        // Check if this potion type is enabled in config
        String enabledPath = "indicatorPotions.potions." + potionType.getConfigKey() + ".enabled";
        if (!configManager.getBoolean(enabledPath, true)) {
            return;
        }

        // Check global enabled setting
        if (!configManager.getBoolean("indicatorPotions.enabled", true)) {
            return;
        }

        // Check player-specific cooldown (global cooldown per player)
        int cooldown = configManager.getInt("indicatorPotions.cooldown", 30);

        if (blockTracker.isPlayerOnCooldown(player, cooldown)) {
            int remainingCooldown = blockTracker.getPlayerRemainingCooldown(player, cooldown);
            player.sendActionBar("§cВы недавно использовали зелье! Подождите еще §e" + remainingCooldown + " §cсекунд.");
            event.setCancelled(true); // Cancel the potion throw
            return;
        }

        // Set player cooldown immediately when potion is thrown
        blockTracker.setPlayerCooldown(player);

        // Schedule the effect after a short delay to let the potion land
        new BukkitRunnable() {
            @Override
            public void run() {
                handleIndicatorPotionEffect(thrownPotion, player, potionType);
            }
        }.runTaskLater(plugin, 20L); // 1 second delay
    }

    private void handleIndicatorPotionEffect(ThrownPotion thrownPotion, Player player, PotionType potionType) {
        Location effectLocation = thrownPotion.getLocation();

        // Get configuration values
        int radius = configManager.getInt("indicatorPotions.radius", 10);
        int displayDuration = configManager.getInt("indicatorPotions.display-duration", 10);
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
            showActionBarForDuration(player, "§7В этом радиусе никого не обнаружено за последние §e" + timeHours + " §7часов.", displayDuration);
            return;
        }

        // Format message with players highlighted in the potion's color
        String playerList = String.join(potionType.getColorCode() + ", " + potionType.getColorCode(), players);
        String message = potionType.getMessagePrefix() + ": " + potionType.getColorCode() + playerList;

        // Show in action bar for specified duration
        showActionBarForDuration(player, message, displayDuration);
    }

    private PotionType getPotionType(ThrownPotion potion) {
        ItemStack item = potion.getItem();
        if (item != null && item.hasItemMeta()) {
            ItemMeta meta = item.getItemMeta();
            if (meta != null && meta.hasCustomModelData()) {
                int cmd = meta.getCustomModelData();
                
                // Check each potion type
                for (PotionType type : PotionType.values()) {
                    int configCmd = configManager.getInt(
                        "indicatorPotions.potions." + type.getConfigKey() + ".custom-model-data",
                        type.getDefaultCmd()
                    );
                    if (cmd == configCmd) {
                        return type;
                    }
                }
            }
        }

        return null;
    }

    private void showActionBarForDuration(Player player, String message, int seconds) {
        // Show initial message
        player.sendActionBar(message);

        if (seconds > 0) {
            // Schedule repeated messages
            new BukkitRunnable() {
                private int remaining = seconds;

                @Override
                public void run() {
                    if (remaining <= 0 || !player.isOnline()) {
                        this.cancel();
                        return;
                    }

                    player.sendActionBar(message);
                    remaining--;
                }
            }.runTaskTimer(plugin, 20L, 20L); // Every second
        }
    }
}

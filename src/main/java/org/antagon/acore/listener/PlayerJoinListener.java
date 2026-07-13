package org.antagon.acore.listener;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

import org.antagon.acore.core.ConfigManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.CustomModelData;

public class PlayerJoinListener implements Listener {

    private final Logger logger = Logger.getLogger(PlayerJoinListener.class.getName());
    private final boolean firstJoinItemEnabled;
    private final String mythicItemName;
    private final int customModelData;
    private final File dataFolder;
    private final Path receivedPlayersFile;

    public PlayerJoinListener() {
        ConfigManager config = ConfigManager.getInstance();

        this.firstJoinItemEnabled = config.getBoolean("firstJoinItem.enabled", true);
        this.mythicItemName = config.getString("firstJoinItem.mythicItemName", "menu_book");
        this.customModelData = config.getInt("firstJoinItem.customModelData", 1039);

        // Get data folder from the plugin
        this.dataFolder = Bukkit.getPluginManager().getPlugin("acore").getDataFolder();
        this.receivedPlayersFile = Paths.get(dataFolder.getAbsolutePath(), "first_join_players.yml");

        // Create the file if it doesn't exist
        try {
            if (!Files.exists(receivedPlayersFile)) {
                Files.createDirectories(receivedPlayersFile.getParent());
                Files.createFile(receivedPlayersFile);
            }
        } catch (IOException e) {
            logger.severe("Failed to create first join players file: " + e.getMessage());
        }
    }

    @EventHandler
    public void onPlayerJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();

        if (!firstJoinItemEnabled) return;

        UUID playerUUID = player.getUniqueId();

        // Check if player already received the item
        if (hasReceivedItem(playerUUID)) {
            return; // Player already received the item
        }

        // Check if MythicMobs is loaded
        if (Bukkit.getPluginManager().getPlugin("MythicMobs") == null) {
            logger.info("MythicMobs plugin not found, skipping first join item for " + player.getName());
            return;
        }

        try {
            // Use reflection to access MythicMobs classes
            // First try to get the class from the MythicMobs plugin
            var mythicMobsPlugin = Bukkit.getPluginManager().getPlugin("MythicMobs");
            Class<?> mythicBukkitClass = null;

            if (mythicMobsPlugin != null) {
                try {
                    mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit", true, mythicMobsPlugin.getClass().getClassLoader());
                } catch (ClassNotFoundException e) {
                    // Fallback to system classloader
                    mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
                }
            } else {
                mythicBukkitClass = Class.forName("io.lumine.mythic.bukkit.MythicBukkit");
            }

            Method instMethod = mythicBukkitClass.getMethod("inst");
            Object mythicBukkit = instMethod.invoke(null);

            if (mythicBukkit == null) {
                logger.warning("MythicBukkit instance is null, cannot give first join item to " + player.getName());
                return;
            }

            // Get item manager
            Method getItemManagerMethod = mythicBukkitClass.getMethod("getItemManager");
            Object itemManager = getItemManagerMethod.invoke(mythicBukkit);

            if (itemManager == null) {
                logger.warning("ItemManager is null, cannot give first join item to " + player.getName());
                return;
            }

            // Get item stack
            Method getItemStackMethod = itemManager.getClass().getMethod("getItemStack", String.class);
            ItemStack item = (ItemStack) getItemStackMethod.invoke(itemManager, mythicItemName);

            if (item == null) {
                logger.warning("MythicMobs item '" + mythicItemName + "' not found, cannot give first join item to " + player.getName());
                return;
            }

            // Set custom model data using the DataComponent API (1.21.5+)
            item.setData(DataComponentTypes.CUSTOM_MODEL_DATA, 
                CustomModelData.customModelData().addFloat(customModelData).build());

            // Give item to player
            player.getInventory().addItem(item);

            // Mark as received
            markAsReceived(playerUUID);

            logger.info("Gave first join item '" + mythicItemName + "' with model data " + customModelData + " to " + player.getName());

        } catch (ClassNotFoundException e) {
            logger.warning("MythicMobs classes not found, cannot give first join item to " + player.getName());
        } catch (NoSuchMethodException e) {
            logger.severe("MythicMobs API method not found: " + e.getMessage());
        } catch (Exception e) {
            logger.severe("Error giving first join item to " + player.getName() + ": " + e.getMessage());
            e.printStackTrace();
        }
    }

    private boolean hasReceivedItem(UUID playerUUID) {
        try {
            List<String> lines = Files.readAllLines(receivedPlayersFile);
            return lines.contains(playerUUID.toString());
        } catch (IOException e) {
            logger.warning("Failed to read first join players file: " + e.getMessage());
            return false;
        }
    }

    private void markAsReceived(UUID playerUUID) {
        try {
            Files.writeString(receivedPlayersFile, playerUUID.toString() + System.lineSeparator(),
                            StandardOpenOption.APPEND);
        } catch (IOException e) {
            logger.severe("Failed to write to first join players file: " + e.getMessage());
        }
    }
}

package org.antagon.acore.listener;

import org.antagon.acore.core.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.BannerMeta;

public class BannerHeadListener implements Listener {

    private final ConfigManager config;

    public BannerHeadListener(ConfigManager config) {
        this.config = config;
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!config.getBoolean("bannerHead.enabled", true)) {
            return;
        }

        if (!(event.getInventory() instanceof PlayerInventory)) {
            return;
        }

        Player player = (Player) event.getWhoClicked();
        ItemStack clickedItem = event.getCurrentItem();
        ItemStack cursorItem = event.getCursor();

        if (isBanner(clickedItem) || isBanner(cursorItem)) {
            ItemStack banner = isBanner(clickedItem) ? clickedItem : cursorItem;

            if (event.getSlot() == 39) {
                player.getInventory().setHelmet(banner.clone());

                if (isBanner(clickedItem)) {
                    event.setCurrentItem(new ItemStack(Material.AIR));
                } else {
                    player.setItemOnCursor(new ItemStack(Material.AIR));
                }

                event.setCancelled(true);
                player.updateInventory();
            }
        }
    }

    private boolean isBanner(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        Material type = item.getType();
        return type == Material.WHITE_BANNER ||
               type == Material.ORANGE_BANNER ||
               type == Material.MAGENTA_BANNER ||
               type == Material.LIGHT_BLUE_BANNER ||
               type == Material.YELLOW_BANNER ||
               type == Material.LIME_BANNER ||
               type == Material.PINK_BANNER ||
               type == Material.GRAY_BANNER ||
               type == Material.LIGHT_GRAY_BANNER ||
               type == Material.CYAN_BANNER ||
               type == Material.PURPLE_BANNER ||
               type == Material.BLUE_BANNER ||
               type == Material.BROWN_BANNER ||
               type == Material.GREEN_BANNER ||
               type == Material.RED_BANNER ||
               type == Material.BLACK_BANNER ||
               (item.hasItemMeta() && item.getItemMeta() instanceof BannerMeta);
    }
}

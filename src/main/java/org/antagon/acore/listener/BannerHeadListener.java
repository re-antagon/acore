package org.antagon.acore.listener;

import org.antagon.acore.core.ConfigManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

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

        if (!(event.getWhoClicked() instanceof Player player)) {
            return;
        }

        ItemStack cursorItem = event.getCursor();
        ItemStack clickedItem = event.getCurrentItem();

        // Determine which item is the banner (cursor or clicked slot)
        ItemStack banner = null;
        boolean bannerOnCursor = false;

        if (isBanner(cursorItem)) {
            banner = cursorItem;
            bannerOnCursor = true;
        } else if (isBanner(clickedItem)) {
            banner = clickedItem;
        }

        if (banner == null) {
            return;
        }

        // Helmet slot rawSlot in player inventory view:
        // 5 = helmet, 6 = chestplate, 7 = leggings, 8 = boots
        int rawSlot = event.getRawSlot();

        // Only handle clicks on the helmet slot (rawSlot 5)
        if (rawSlot != 5) {
            return;
        }

        // If banner is on cursor → put it on head
        if (bannerOnCursor) {
            player.getInventory().setHelmet(banner.clone());
            player.setItemOnCursor(new ItemStack(Material.AIR));
            event.setCancelled(true);
            player.updateInventory();
        }
        // If banner is in helmet slot → pick it up to cursor
        else {
            player.setItemOnCursor(banner.clone());
            player.getInventory().setHelmet(new ItemStack(Material.AIR));
            event.setCancelled(true);
            player.updateInventory();
        }
    }

    private boolean isBanner(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) {
            return false;
        }

        String name = item.getType().name();
        return name.endsWith("_BANNER");
    }
}

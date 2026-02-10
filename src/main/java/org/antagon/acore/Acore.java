package org.antagon.acore;

import java.lang.reflect.Constructor;

import org.antagon.acore.commands.AntiSchvapchichiCommand;
import org.antagon.acore.commands.LinkCommand;
import org.antagon.acore.commands.SchvapchichiCommand;
import org.antagon.acore.commands.ShowInfoCommand;
import org.antagon.acore.core.ConfigManager;
import org.antagon.acore.listener.AnvilFallListener;
import org.antagon.acore.listener.BannerHeadListener;
import org.antagon.acore.listener.BlockInteractionListener;
import org.antagon.acore.listener.FogPotionListener;
import org.antagon.acore.listener.ItemFrameListener;
import org.antagon.acore.listener.MinecartSpeedListener;
import org.antagon.acore.listener.PistonLaunchAnvilListener;
import org.antagon.acore.listener.PlayerJoinListener;
import org.antagon.acore.listener.PlayerMoveListener;
import org.antagon.acore.listener.ReferralListener;
import org.antagon.acore.listener.SchvapchichiListener;
import org.antagon.acore.listener.StonecutterBlockProcessorListener;
import org.antagon.acore.listener.VillagerTransportListener;
import org.antagon.acore.util.CurseManager;
import org.antagon.acore.util.ReferralManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Acore extends JavaPlugin {

    private ConfigManager configManager;
    private CurseManager curseManager;
    private ReferralManager referralManager;

    @Override
    public void onEnable() {
        // Initialize config
        this.configManager = ConfigManager.initialize(getDataFolder(), getLogger());

        // Initialize curse manager
        this.curseManager = new CurseManager(this);

        // Initialize referral manager
        this.referralManager = new ReferralManager(this);

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        getLogger().info("Acore plugin has been enabled successfully!");

        // !!! С этим полем плагин не заводится, хз почему, поэтому пока так !!!
        // потом почекаю
        //ConditionalEventsAPI.registerApiActions(this,new SpawnMythicMob(), new DropMythicItem());
    }

    private void registerListeners() {
        // Register VillagerTransportListener if enabled in config
        if (configManager.getBoolean("villagerTransport.enabled", true)) {
            getServer().getPluginManager().registerEvents(
                new VillagerTransportListener(this, configManager), this);
            getLogger().info("Villager Transportation feature enabled");
        }

        // Register MinecartSpeedListener if enabled in config
        if (configManager.getBoolean("minecartSpeed.enabled", true)) {
            getServer().getPluginManager().registerEvents(new MinecartSpeedListener(), this);
            getLogger().info("Minecart Speed feature enabled");
        }

        getServer().getPluginManager().registerEvents(new ItemFrameListener(configManager), this);

        // Register BlockInteractionListener for tracking player block interactions
        getServer().getPluginManager().registerEvents(new BlockInteractionListener(), this);
        getLogger().info("Block Interaction Tracker enabled");

        // Register FogPotionListener if enabled in config
        if (configManager.getBoolean("fogPotion.enabled", true)) {
            getServer().getPluginManager().registerEvents(new FogPotionListener(this, configManager), this);
            getLogger().info("Fog Potion feature enabled");
        }

        // Register BannerHeadListener if enabled in config
        if (configManager.getBoolean("bannerHead.enabled", true)) {
            getServer().getPluginManager().registerEvents(new BannerHeadListener(configManager), this);
            getLogger().info("Banner Head feature enabled");
        }

        // Register SchvapchichiListener if enabled in config
        if (configManager.getBoolean("schvapchichi.enabled", true)) {
            getServer().getPluginManager().registerEvents(new SchvapchichiListener(this, curseManager), this);
            getLogger().info("Schvapchichi feature enabled");
            getLogger().info("Cursed players loaded: " + curseManager.getCursedPlayers().size());
        }

        // Register PlayerMoveListener
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(), this);

        // Register PlayerJoinListener if enabled in config
        if (configManager.getBoolean("firstJoinItem.enabled", true)) {
            getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
            getLogger().info("First Join Item feature enabled");
        }

        // Register ReferralListener if enabled in config
        if (configManager.getBoolean("referrals.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ReferralListener(this, referralManager), this);
            getLogger().info("Referral feature enabled");
        }

        // Register StonecutterBlockProcessorListener if enabled in config
        // TODO: Missing sound when processing
        // TODO: Process stone to cobblestone, deepslate to cracked deepslate and etc
        if (configManager.getBoolean("stonecutterBlockProcessor.enabled", true)) {
            getServer().getPluginManager().registerEvents(new StonecutterBlockProcessorListener(this), this);
            getLogger().info("Stonecutter Block Processor feature enabled");
        }

        // Register AnvilFallListener if enabled in config
        if (configManager.getBoolean("anvilFall.enabled", true)) {
            getServer().getPluginManager().registerEvents(new AnvilFallListener(this), this);
            getLogger().info("Anvil Fall Listener feature enabled");
        }

        // Register pistonLaunchAnvil if enabled in config
        if (configManager.getBoolean("pistonLaunchAnvil.enabled", true)) {
            getServer().getPluginManager().registerEvents(new PistonLaunchAnvilListener(this), this);
            getLogger().info("Piston Launch Anvil Listener feature enabled");
        }
    }

    private void registerCommands() {
        // Register showinfo command using Paper API
        try {
            var commandMap = getServer().getCommandMap();
            var command = commandMap.getCommand("showinfo");
            if (command == null) {
                // Create command if it doesn't exist using reflection
                Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                constructor.setAccessible(true);
                command = constructor.newInstance("showinfo", this);
                command.setDescription("Переключить отображение боссбара");
                command.setUsage("/showinfo");
                ((PluginCommand) command).setExecutor(new ShowInfoCommand());
                commandMap.register("acore", command);
            } else {
                ((PluginCommand) command).setExecutor(new ShowInfoCommand());
            }
            getLogger().info("ShowInfo command registered");
        } catch (Exception e) {
            getLogger().warning("Failed to register showinfo command: " + e.getMessage());
        }

        // Register schvapchichi command
        try {
            var commandMap = getServer().getCommandMap();
            var command = commandMap.getCommand("schvapchichi");
            if (command == null) {
                // Create command if it doesn't exist using reflection
                Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                constructor.setAccessible(true);
                command = constructor.newInstance("schvapchichi", this);
                command.setDescription("Проклясть игрока Швапчичи");
                command.setUsage("/schvapchichi");
                ((PluginCommand) command).setExecutor(new SchvapchichiCommand(this, curseManager));
                commandMap.register("acore", command);
            } else {
                ((PluginCommand) command).setExecutor(new SchvapchichiCommand(this, curseManager));
            }
            getLogger().info("Schvapchichi command registered");
        } catch (Exception e) {
            getLogger().warning("Failed to register schvapchichi command: " + e.getMessage());
        }

        // Register anti_schvapchichi command
        try {
            var commandMap = getServer().getCommandMap();
            var command = commandMap.getCommand("anti_schvapchichi");
            if (command == null) {
                // Create command if it doesn't exist using reflection
                Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                constructor.setAccessible(true);
                command = constructor.newInstance("anti_schvapchichi", this);
                command.setDescription("Избавиться от проклятья Швапчичи");
                command.setUsage("/anti_schvapchichi");
                ((PluginCommand) command).setExecutor(new AntiSchvapchichiCommand(this, curseManager));
                commandMap.register("acore", command);
            } else {
                ((PluginCommand) command).setExecutor(new AntiSchvapchichiCommand(this, curseManager));
            }
            getLogger().info("AntiSchvapchichi command registered");
        } catch (Exception e) {
            getLogger().warning("Failed to register anti_schvapchichi command: " + e.getMessage());
        }

        // Register link command
        try {
            var commandMap = getServer().getCommandMap();
            var command = commandMap.getCommand("link");
            if (command == null) {
                // Create command if it doesn't exist using reflection
                Constructor<PluginCommand> constructor =
                    PluginCommand.class.getDeclaredConstructor(String.class, Plugin.class);
                constructor.setAccessible(true);
                command = constructor.newInstance("link", this);
                command.setDescription("Invite a player to become your referral");
                command.setUsage("/link <player>");
                ((PluginCommand) command).setExecutor(new LinkCommand(this, referralManager));
                commandMap.register("acore", command);
            } else {
                ((PluginCommand) command).setExecutor(new LinkCommand(this, referralManager));
            }
            getLogger().info("Link command registered");
        } catch (Exception e) {
            getLogger().warning("Failed to register link command: " + e.getMessage());
        }
    }

    @Override
    public void onDisable() {
        // Plugin shutdown logic
        getLogger().info("Acore plugin has been disabled");

        // !!! Жиза !!!
        //ConditionalEventsAPI.unregisterApiActions(this);
    }
}

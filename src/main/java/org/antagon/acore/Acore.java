package org.antagon.acore;

import java.lang.reflect.Constructor;

import org.antagon.acore.commands.LinkCommand;
import org.antagon.acore.commands.ShowInfoCommand;
import org.antagon.acore.core.ConfigManager;
import org.antagon.acore.listener.AnvilFallListener;
import org.antagon.acore.listener.BannerHeadListener;
import org.antagon.acore.listener.BlockBurnListener;
import org.antagon.acore.listener.BlockInteractionListener;
import org.antagon.acore.listener.CopperOxidationListener;
import org.antagon.acore.listener.EntityKillListener;
import org.antagon.acore.listener.IndicatorPotionListener;
import org.antagon.acore.listener.ItemFrameListener;
import org.antagon.acore.listener.LightningConversionListener;
import org.antagon.acore.listener.MinecartSpeedListener;
import org.antagon.acore.listener.PistonLaunchAnvilListener;
import org.antagon.acore.listener.PlayerJoinListener;
import org.antagon.acore.listener.PlayerMoveListener;
import org.antagon.acore.listener.ReferralListener;
import org.antagon.acore.listener.StonecutterBlockProcessorListener;
import org.antagon.acore.listener.VillagerTransportListener;
import org.antagon.acore.util.ReferralManager;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.java.JavaPlugin;

public final class Acore extends JavaPlugin {

    private ConfigManager configManager;
    private ReferralManager referralManager;

    @Override
    public void onEnable() {
        // Initialize config
        this.configManager = ConfigManager.initialize(getDataFolder(), getLogger());

        // Initialize referral manager
        this.referralManager = new ReferralManager(this);

        // Register commands
        registerCommands();

        // Register listeners
        registerListeners();

        getLogger().info("Acore plugin has been enabled successfully!");

        // ConditionalEvents API actions - disabled for now
        // ConditionalEventsAPI.registerApiActions(this,new SpawnMythicMob(), new DropMythicItem());
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

        // ItemFrameListener - invisible item frames
        if (configManager.getBoolean("invisibleItemFrames.enabled", true)) {
            getServer().getPluginManager().registerEvents(new ItemFrameListener(configManager), this);
            getLogger().info("Invisible ItemFrames feature enabled");
        }

        // Register BlockInteractionListener for tracking player block interactions
        // Always enabled if indicatorPotions is enabled, but we keep it always on for tracker
        getServer().getPluginManager().registerEvents(new BlockInteractionListener(), this);
        getLogger().info("Block Interaction Tracker enabled");

        // Register EntityKillListener for indicator potions
        getServer().getPluginManager().registerEvents(new EntityKillListener(), this);
        getLogger().info("Entity Kill Tracker enabled");

        // Register BannerHeadListener if enabled in config
        if (configManager.getBoolean("bannerHead.enabled", true)) {
            getServer().getPluginManager().registerEvents(new BannerHeadListener(configManager), this);
            getLogger().info("Banner Head feature enabled");
        } else {
            getLogger().info("Banner Head feature is DISABLED in config");
        }

        // Register PlayerMoveListener - betterRun and beehive features
        getServer().getPluginManager().registerEvents(new PlayerMoveListener(), this);
        getLogger().info("PlayerMove (betterRun) feature enabled");

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
            getLogger().info("Registering PistonLaunchAnvilListener...");
            getServer().getPluginManager().registerEvents(new PistonLaunchAnvilListener(this), this);
            getLogger().info("Piston Launch Anvil Listener feature enabled");
        } else {
            getLogger().info("Piston Launch Anvil feature is DISABLED in config");
        }

        // Register BlockBurnListener / fireAdjustment
        if (configManager.getBoolean("fireAdjustment.enabled", true)) {
            getServer().getPluginManager().registerEvents(new BlockBurnListener(), this);
            getLogger().info("Fire Adjustment (BlockBurn) feature enabled");
        }

        // Register LightningConversionListener
        if (configManager.getBoolean("lightningConversion.enabled", true)) {
            getServer().getPluginManager().registerEvents(new LightningConversionListener(), this);
            getLogger().info("Lightning Conversion feature enabled");
        }

        // Register IndicatorPotionListener
        if (configManager.getBoolean("indicatorPotions.enabled", true)) {
            getServer().getPluginManager().registerEvents(new IndicatorPotionListener(this, configManager), this);
            getLogger().info("Indicator Potions feature enabled");
        }

        // Register CopperOxidationListener
        if (configManager.getBoolean("copperOxidation.enabled", true)) {
            getServer().getPluginManager().registerEvents(new CopperOxidationListener(this, configManager), this);
            getLogger().info("Copper Oxidation feature enabled");
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

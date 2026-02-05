package org.antagon.acore.config;

import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ConfigUpdater {
    private final Logger logger;
    private static final int REQUIRED_VERSION = 2;

    public ConfigUpdater(Logger logger) {
        this.logger = logger;
    }

    public FileConfiguration updateConfiguration(FileConfiguration config, FileConfiguration defaultConfig, File configFile) {
        int currentVersion = config.getInt("config-version", 0);
        if (currentVersion < REQUIRED_VERSION) {
            logger.info("Updating configuration to version " + REQUIRED_VERSION);
            config.set("config-version", REQUIRED_VERSION);
        }

        DefaultsMerger.mergeDefaults(defaultConfig, config, logger);

        try {
            config.save(configFile);
        } catch (Exception e) {
            logger.log(Level.SEVERE, "Failed to save updated configuration: " + e.getMessage(), e);
        }

        return config;
    }
}

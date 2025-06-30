package com.trynocs.tryLibs.utils.config;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

/**
 * Manages configuration files for the plugin.
 * This includes the default `config.yml` and any custom configuration files.
 */
public class Configmanager {
    private final JavaPlugin plugin;
    private FileConfiguration config = null;
    private File configFile = null;
    private final Map<String, FileConfiguration> customConfigs = new HashMap<>();
    private final Map<String, File> customConfigFiles = new HashMap<>();

    /**
     * Constructs a new Configmanager.
     *
     * @param plugin The JavaPlugin instance.
     */
    public Configmanager(JavaPlugin plugin) {
        this.plugin = plugin;
        saveDefaultConfig();
    }

    /**
     * Reloads the default configuration file (config.yml).
     */
    public void reloadConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        config = YamlConfiguration.loadConfiguration(configFile);
    }

    /**
     * Gets the default configuration (config.yml).
     * If the configuration is not loaded, it will be reloaded.
     *
     * @return The default FileConfiguration.
     */
    public FileConfiguration getConfig() {
        if (config == null) {
            reloadConfig();
        }
        return config;
    }

    /**
     * Saves the default configuration file (config.yml) if it doesn't exist.
     * It copies the default config.yml from the plugin's resources.
     */
    public void saveDefaultConfig() {
        if (configFile == null) {
            configFile = new File(plugin.getDataFolder(), "config.yml");
        }
        if (!configFile.exists()) {
            plugin.saveResource("config.yml", false);
        }
    }

    /**
     * Creates a custom configuration file if it doesn't exist.
     * It copies the default custom config from the plugin's resources.
     *
     * @param name The name of the custom configuration file (without .yml extension).
     */
    public void createCustomConfig(String name) {
        if (!customConfigFiles.containsKey(name)) {
            File file = new File(plugin.getDataFolder(), name + ".yml");
            customConfigFiles.put(name, file);
            if (!file.exists()) {
                plugin.saveResource(name + ".yml", false);
            }
        }
    }

    /**
     * Reloads a custom configuration file.
     * If the file doesn't exist, it will be created first.
     *
     * @param name The name of the custom configuration file (without .yml extension).
     */
    public void reloadCustomConfig(String name) {
        if (!customConfigFiles.containsKey(name)) {
            createCustomConfig(name);
        }
        File file = customConfigFiles.get(name);
        FileConfiguration customConfig = YamlConfiguration.loadConfiguration(file);
        customConfigs.put(name, customConfig);
    }

    /**
     * Gets a custom configuration file.
     * If the configuration is not loaded, it will be reloaded.
     *
     * @param name The name of the custom configuration file (without .yml extension).
     * @return The FileConfiguration for the custom config.
     */
    public FileConfiguration getCustomConfig(String name) {
        if (!customConfigs.containsKey(name)) {
            reloadCustomConfig(name);
        }
        return customConfigs.get(name);
    }

    /**
     * Saves a custom configuration file.
     *
     * @param name The name of the custom configuration file (without .yml extension).
     */
    public void saveCustomConfig(String name) {
        if (customConfigs.containsKey(name)) {
            File file = customConfigFiles.get(name);
            FileConfiguration customConfig = customConfigs.get(name);
            try {
                customConfig.save(file);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }
}
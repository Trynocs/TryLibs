package com.trynocs.tryLibs;

import com.google.gson.JsonObject;
import com.trynocs.tryLibs.utils.config.Configmanager;
import com.trynocs.tryLibs.utils.database.DatabaseHandler;
import com.trynocs.tryLibs.utils.economy.VaultSetup;
import com.trynocs.tryLibs.utils.gui.ItemBuilder;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.Event;
import org.bukkit.event.HandlerList;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Hauptklasse von TryLibs.
 *
 * Diese Klasse stellt eine statische Instanz sowie Getter für wichtige Manager bereit,
 * sodass andere Plugins einfach darauf zugreifen können:
 *
 * Beispiel:
 *   main.getPlugin().getDatabaseHandler().createTable("users");
 *   main.getPlugin().getConfigManager().getConfig();
 *
 * Der ItemBuilder kann direkt importiert und genutzt werden:
 *   import com.trynocs.tryLibs.utils.gui.ItemBuilder;
 *   ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD).setName("§bSchwert").build();
 */
public final class TryLibs extends JavaPlugin {
    private static TryLibs instance;
    private static boolean fullyInitialized = false;
    private static boolean initializing = false;
    private static String initializationState = "Not started";
    private static final boolean DEBUG_MODE = true;
    private static ClassLoader originalClassLoader = null;
    private static boolean classLoaderWarningShown = false;
    private static boolean isEmbeddedMode = false;
    private Configmanager configManager;
    private DatabaseHandler databaseHandler;
    private String economyDatabaseName;

    static {
        // Track the original classloader that first loaded this class
        if (originalClassLoader == null) {
            originalClassLoader = TryLibs.class.getClassLoader();
            debugLog("TryLibs class first loaded by classloader: " + originalClassLoader);
        } else {
            // If we're here, the class is being loaded by a different classloader
            ClassLoader currentClassLoader = TryLibs.class.getClassLoader();
            if (currentClassLoader != originalClassLoader && !classLoaderWarningShown) {
                debugLog("WARNING: TryLibs class loaded by multiple classloaders!");
                debugLog("Original classloader: " + originalClassLoader);
                debugLog("Current classloader: " + currentClassLoader);
                debugLog("This typically happens when a plugin includes (shades) TryLibs in its JAR.");
                classLoaderWarningShown = true;
                isEmbeddedMode = true;
            }
        }
    }

    @Override
    public void onLoad() {
        // Check classloader to detect if we're running as the real plugin or a shaded copy
        ClassLoader currentClassLoader = getClass().getClassLoader();
        debugLog("TryLibs plugin onLoad called with classloader: " + currentClassLoader);
        
        // Only set instance if this is the actual plugin instance or no instance exists yet
        if (instance == null || currentClassLoader == originalClassLoader) {
            instance = this;
            initializationState = "Instance set in onLoad";
            debugLog("TryLibs instance initialized in onLoad phase");
        } else {
            debugLog("Ignoring onLoad call from non-original classloader");
        }
    }

    @Override
    public void onEnable() {
        // Check classloader to detect if we're running as the real plugin or a shaded copy
        ClassLoader currentClassLoader = getClass().getClassLoader();
        debugLog("TryLibs plugin onEnable called with classloader: " + currentClassLoader);
        
        // Only proceed with initialization if this is the actual plugin instance or no instance exists yet
        if (instance == null) {
            instance = this;
            initializationState = "Instance set in onEnable";
            debugLog("TryLibs instance initialized in onEnable phase");
        } else if (currentClassLoader != originalClassLoader) {
            getLogger().warning("TryLibs detected multiple classloaders - this instance is running in embedded mode");
            getLogger().warning("Original classloader: " + originalClassLoader);
            getLogger().warning("Current classloader: " + currentClassLoader);
            getLogger().warning("This typically happens when a plugin includes (shades) TryLibs in its JAR.");
            getLogger().warning("Only one instance of TryLibs should be loaded - prefer using it as a plugin dependency.");
            return; // Skip initialization for shaded copies
        }
        
        initializing = true;
        try {
            // Initialize configuration manager first
            initializationState = "Creating ConfigManager";
            configManager = new Configmanager(this);
            
            // Create database handler (but actual config loading is deferred)
            initializationState = "Creating DatabaseHandler";
            databaseHandler = new DatabaseHandler();
            
            // Load configuration values
            initializationState = "Loading config values";
            economyDatabaseName = getConfig().getString("database.economytable", "economy");
            
            // Mark as fully initialized
            initializationState = "Hooking into Vault";
            VaultSetup.setupVault(this);

            initializationState = "Successfully initialized";
            fullyInitialized = true;
            initializing = false;
            
            // Log the loaded plugins to check load order
            debugLog("Current plugin load order:");
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                debugLog("  - " + plugin.getName() + " (enabled: " + plugin.isEnabled() + ")");
            }
            
            getLogger().info("TryLibs wurde erfolgreich aktiviert!");
        } catch (Exception e) {
            initializationState = "Failed with error: " + e.getMessage();
            getLogger().severe("Failed to initialize TryLibs: " + e.getMessage());
            e.printStackTrace();
            fullyInitialized = false;
            initializing = false;
        }
    }

    @Override
    public void onDisable() {
        if (databaseHandler != null) {
            databaseHandler.closeConnection();
        }
        fullyInitialized = false;
        initializationState = "Plugin disabled";
        getLogger().info("TryLibs wurde deaktiviert!");
    }

    /**
     * Gibt die Instanz des Plugins zurück.
     * @return main Instanz
     */
    public static TryLibs getPlugin() {
        if (isEmbeddedMode) {
            String errorMsg = "TryLibs detected that it's being loaded from multiple classloaders. " +
                "Original: " + originalClassLoader + ", Current: " + TryLibs.class.getClassLoader() + ". " +
                "This typically happens when a plugin includes (shades) TryLibs classes in its JAR. " +
                "Please modify your plugin to use TryLibs as a proper dependency instead of embedding it.";
            Bukkit.getLogger().severe(errorMsg);
        }
        
        if (instance == null) {
            String errorMsg = "TryLibs instance is not yet initialized! " +
                "Make sure your plugin declares 'depend: [TryLibs]' in its plugin.yml " +
                "and that the server is loading plugins in the correct order.";
            Bukkit.getLogger().severe(errorMsg);
            
            // Print the current plugin load state for debugging
            printPluginLoadState();
            
            throw new IllegalStateException(errorMsg);
        }
        
        if (!fullyInitialized) {
            String errorMsg = "TryLibs is initializing but not fully ready! " +
                "Current state: " + initializationState + ". " +
                "This might be a plugin loading order issue. Make sure TryLibs is fully enabled " +
                "before your plugin tries to use it, or listen to TryLibsInitializedEvent.";
            Bukkit.getLogger().severe(errorMsg);
            
            // Print the current plugin load state for debugging
            printPluginLoadState();
            
            // If we're in the process of initializing, just return the instance
            // This helps plugins that just need the basic instance, not full functionality
            if (initializing) {
                Bukkit.getLogger().warning("Returning partially initialized TryLibs instance - some features may not work!");
                return instance;
            }
            
            throw new IllegalStateException(errorMsg);
        }
        
        return instance;
    }

    /**
     * A safer way to get the plugin instance that doesn't throw exceptions.
     * Returns null if TryLibs is not yet initialized.
     * 
     * @return TryLibs instance or null if not fully initialized
     */
    public static TryLibs getPluginSafe() {
        if (isEmbeddedMode) {
            debugLog("TryLibs.getPluginSafe() called from embedded mode. Classloader: " + 
                  TryLibs.class.getClassLoader());
        }
        
        if (instance == null || !fullyInitialized) {
            return null;
        }
        return instance;
    }

    /**
     * Checks if TryLibs is fully initialized and ready to use.
     * @return true if TryLibs is ready to use
     */
    public static boolean isInitialized() {
        if (isEmbeddedMode) {
            debugLog("TryLibs.isInitialized() called from embedded mode. Classloader: " + 
                  TryLibs.class.getClassLoader());
            // If we're in embedded mode, we should warn about it but still allow initialization checks
        }
        return instance != null && fullyInitialized;
    }

    /**
     * Checks if TryLibs is being loaded from multiple classloaders.
     * @return true if TryLibs is being loaded by multiple classloaders
     */
    public static boolean isEmbeddedMode() {
        return isEmbeddedMode;
    }

    /**
     * Gets the current initialization state of TryLibs.
     * Useful for debugging loading issues.
     * 
     * @return String describing the current initialization state
     */
    public static String getInitializationState() {
        if (isEmbeddedMode) {
            return initializationState + " (WARNING: Embedded mode detected)";
        }
        return initializationState;
    }
    
    /**
     * Prints the current load state of all plugins for debugging purposes
     */
    private static void printPluginLoadState() {
        if (Bukkit.getPluginManager() != null) {
            Bukkit.getLogger().severe("Current plugin states:");
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                Bukkit.getLogger().severe("  - " + plugin.getName() + " (enabled: " + plugin.isEnabled() + ")");
            }
        } else {
            Bukkit.getLogger().severe("Plugin manager not available yet - extremely early initialization!");
        }
    }
    
    /**
     * Log debug information if debug mode is enabled
     */
    private static void debugLog(String message) {
        if (DEBUG_MODE) {
            if (instance != null && instance.getLogger() != null) {
                instance.getLogger().log(Level.INFO, "[DEBUG] " + message);
            } else {
                System.out.println("[TryLibs DEBUG] " + message);
            }
        }
    }

    /**
     * Gibt den Configmanager zurück.
     * @return Configmanager Instanz
     */
    public Configmanager getConfigManager() {
        return configManager;
    }

    /**
     * Gibt den DatabaseHandler zurück.
     * @return DatabaseHandler Instanz
     */
    public DatabaseHandler getDatabaseHandler() {
        return databaseHandler;
    }

    public static String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> translateColors(List<String> texts) {
        return texts.stream().map(TryLibs::translateColors).collect(Collectors.toList());
    }

    /**
     * Gibt den Namen der Economy
     * Datenbanktabelle zurück.
        * @return Name der Economy-Datenbanktabelle
        */
    public String getEconomyDatabaseName() {
        return economyDatabaseName;
    }

    public ItemStack getPlacerholderItem() {
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("§7").build();
    }

    private void sendToDiscordWebhook(URL webhookUrl, String content) {
        try {
            JsonObject jsonObject = new JsonObject();
            JsonObject embed = new JsonObject();

            embed.addProperty("title", "BlockEngine Notification");
            embed.addProperty("description", content);
            embed.addProperty("color", 65280);

            embed.addProperty("timestamp", java.time.Instant.now().toString());

            jsonObject.add("embeds", new com.google.gson.JsonArray());
            jsonObject.getAsJsonArray("embeds").add(embed);

            HttpsURLConnection connection = (HttpsURLConnection) webhookUrl.openConnection();
            connection.addRequestProperty("Content-Type", "application/json");
            connection.addRequestProperty("User-Agent", "BE-DiscordWebhook");
            connection.setDoOutput(true);
            connection.setRequestMethod("POST");

            OutputStream stream = connection.getOutputStream();
            stream.write(jsonObject.toString().getBytes());
            stream.flush();
            stream.close();

            connection.getInputStream().close();
            connection.disconnect();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    /**
     * Event that gets fired when TryLibs is fully initialized.
     * Plugins can listen to this event to safely start using TryLibs.
     */
    public static class TryLibsInitializedEvent extends Event {
        private static final HandlerList handlers = new HandlerList();

        public HandlerList getHandlers() {
            return handlers;
        }

        public static HandlerList getHandlerList() {
            return handlers;
        }
    }
}

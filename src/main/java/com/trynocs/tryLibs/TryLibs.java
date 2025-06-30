package com.trynocs.tryLibs;

import com.google.gson.JsonObject;
import com.trynocs.tryLibs.api.TryLibsAPI;
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
import org.bukkit.plugin.ServicePriority;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
import java.util.logging.Level;
import java.util.stream.Collectors;

/**
 * Main class for TryLibs.
 *
 * This plugin provides a library of utilities for other Bukkit plugins.
 * Access its functionalities through the {@link TryLibsAPI} obtained from Bukkit's Services Manager.
 *
 * The ItemBuilder can be directly imported and used:
 *   import com.trynocs.tryLibs.utils.gui.ItemBuilder;
 *   ItemStack item = new ItemBuilder(Material.DIAMOND_SWORD).setName("§bSchwert").build();
 */
public final class TryLibs extends JavaPlugin implements TryLibsAPI {
    /** The static instance of the main TryLibs plugin. */
    private static TryLibs instance; // Retained for internal use and managing the "primary" instance
    private static boolean fullyInitialized = false;
    private static boolean initializing = false;
    private static String initializationState = "Not started";
    private static final boolean DEBUG_MODE = false; // Set to false for production
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

        // Only allow the original classloader to set the static instance.
        if (currentClassLoader == originalClassLoader) {
            instance = this;
            initializationState = "Instance set in onLoad by original classloader";
            debugLog("TryLibs static instance assigned in onLoad by original classloader: " + currentClassLoader);
        } else {
            // This is a shaded copy. Do not let it set the static 'instance'.
            // The 'isEmbeddedMode' static variable is already set by the static initializer if this is a different classloader.
            debugLog("Ignoring onLoad for shaded instance. Static 'instance' will not be set by classloader: " + currentClassLoader);
            if (instance == null) {
                // If no instance (neither real nor another shaded one) has loaded yet.
                initializationState = "onLoad called for shaded instance; static instance remains null for now.";
            }
        }
    }

    @Override
    public void onEnable() {
        ClassLoader currentClassLoader = getClass().getClassLoader();
        debugLog("TryLibs plugin onEnable called with classloader: " + currentClassLoader);

        if (currentClassLoader != originalClassLoader) {
            // This is a shaded (embedded) copy of TryLibs.
            // It should not perform primary initialization or register services.
            // It relies on the "real" TryLibs plugin being present and providing the service.
            getLogger().warning("This is an embedded (shaded) instance of TryLibs from " + getName() +
                    ". It will not perform primary initialization. Ensure the standalone TryLibs plugin is installed.");
            isEmbeddedMode = true; // Explicitly set here for clarity
            return; // Skip initialization for shaded copies
        }

        // At this point, currentClassLoader == originalClassLoader, so this is the "real" plugin.
        if (instance != this && instance != null) {
            getLogger().warning("TryLibs instance was unexpectedly set by a different classloader (" +
                    (instance.getClass().getClassLoader() != null ? instance.getClass().getClassLoader().toString() : "unknown") +
                    ") before the main plugin instance (" +
                    currentClassLoader + ") could initialize. Correcting to this instance.");
        }
        instance = this; // Ensure 'instance' points to this, the "real" plugin instance.
        initializing = true;
        initializationState = "Starting initialization";

        try {
            initializationState = "Creating ConfigManager";
            configManager = new Configmanager(this);

            initializationState = "Creating DatabaseHandler";
            // Corrected instantiation: Pass 'this' (as TryLibsAPI) and the plugin's logger
            databaseHandler = new DatabaseHandler(this, getLogger());

            initializationState = "Loading economy table name from config";
            economyDatabaseName = configManager.getConfig().getString("database.economytable", "economy");

            initializationState = "Registering TryLibsAPI service";
            getServer().getServicesManager().register(TryLibsAPI.class, this, this, ServicePriority.Normal);
            debugLog("TryLibsAPI service registered.");

            initializationState = "Hooking into Vault";
            VaultSetup.setupVault(this);

            // Fire the initialization event AFTER service registration and basic setup
            initializationState = "Firing TryLibsInitializedEvent";
            Bukkit.getPluginManager().callEvent(new TryLibsInitializedEvent());
            debugLog("TryLibsInitializedEvent fired.");

            initializationState = "Successfully initialized";
            fullyInitialized = true; // Mark as fully initialized
            initializing = false;

            debugLog("Current plugin load order:");
            for (org.bukkit.plugin.Plugin plugin : Bukkit.getPluginManager().getPlugins()) {
                debugLog("  - " + plugin.getName() + " (enabled: " + plugin.isEnabled() + ")");
            }

            getLogger().info("TryLibs has been successfully activated!");

        } catch (Exception e) {
            initializationState = "Failed with error: " + e.getMessage();
            getLogger().log(Level.SEVERE, "Failed to initialize TryLibs: " + e.getMessage(), e);
            fullyInitialized = false;
            initializing = false;
            // Optionally disable the plugin if initialization fails critically
            // getServer().getPluginManager().disablePlugin(this);
        }
    }

    @Override
    public void onDisable() {
        // Only unregister and close resources if this is the original, main instance
        if (getClass().getClassLoader() == originalClassLoader && instance == this) {
            debugLog("TryLibs onDisable called for the primary instance.");
            // Unregister the service
            try {
                getServer().getServicesManager().unregister(TryLibsAPI.class, this);
                debugLog("TryLibsAPI service unregistered.");
            } catch (Exception e) {
                getLogger().log(Level.WARNING, "Error unregistering TryLibsAPI service: " + e.getMessage(), e);
            }

            if (databaseHandler != null) {
                databaseHandler.closeConnection();
                debugLog("Database connection closed.");
            }
            getLogger().info("TryLibs has been deactivated!");
        } else {
            debugLog("TryLibs onDisable called for an embedded/shaded instance or a non-primary instance. No action taken. Classloader: " + getClass().getClassLoader());
        }
        // Reset state fields regardless, useful for server reloads or /plugman type reloads
        fullyInitialized = false;
        initializing = false;
        initializationState = "Plugin disabled";
        // 'instance' should ideally be cleared if this was the primary one,
        // or if any instance is disabled to allow a clean load next time.
        // However, static 'instance' management with classloaders is tricky.
        // If 'instance' refers to *this* instance, clear it.
        if (instance == this) {
            instance = null;
        }
    }

    /**
     * Returns the static instance of the main TryLibs plugin.
     * <p>
     *     <b>Note:</b> This method is intended for internal use or specific scenarios where
     *     the plugin instance itself is needed.
     * </p>
     * <p>
     *     For accessing TryLibs functionalities, it is highly recommended to obtain the {@link TryLibsAPI}
     *     from Bukkit's Services Manager. See {@link TryLibsAPI} for details.
     * </p>
     *
     * @return The primary TryLibs plugin instance, or null if not initialized or if called from a problematic state.
     * @deprecated Prefer using Bukkit's Services Manager to get {@link TryLibsAPI}.
     */
    @Deprecated
    public static TryLibs getPlugin() {
        if (isEmbeddedMode && (instance == null || instance.getClass().getClassLoader() != originalClassLoader)) {
            String errorMsg = "TryLibs.getPlugin() called in an ambiguous context (likely embedded mode). " +
                    "Original CL: " + originalClassLoader + ", Current static instance CL: " + (instance != null ? (instance.getClass().getClassLoader() != null ? instance.getClass().getClassLoader().toString() : "unknown") : "null") +
                    ". Please use Bukkit's Services Manager to get TryLibsAPI.";
            Bukkit.getLogger().warning(errorMsg);
            org.bukkit.plugin.RegisteredServiceProvider<TryLibsAPI> provider = Bukkit.getServer().getServicesManager().getRegistration(TryLibsAPI.class);
            if (provider != null && provider.getProvider() instanceof TryLibs) {
                return (TryLibs) provider.getProvider();
            }
            return null;
        }

        if (instance == null) {
            Bukkit.getLogger().severe("TryLibs.getPlugin() called before instance is initialized! " +
                    "Ensure TryLibs is loaded and enabled. Current state: " + initializationState);
            if (Bukkit.getPluginManager() != null) printPluginLoadState(); // Check if plugin manager is available
            return null;
        }

        if (!fullyInitialized && initializing) {
            Bukkit.getLogger().warning("TryLibs.getPlugin() called while TryLibs is still initializing (State: " + initializationState + "). " +
                    "The API might not be fully available. Consider listening to TryLibsInitializedEvent or using the Services Manager later.");
        } else if (!fullyInitialized) {
            Bukkit.getLogger().warning("TryLibs.getPlugin() called but TryLibs is not fully initialized (State: " + initializationState + ").");
        }

        return instance;
    }

    /**
     * A safer way to get the plugin instance that doesn't throw exceptions and encourages service usage.
     * Returns null if TryLibs is not yet initialized or if it's safer to use the service.
     *
     * @return TryLibs instance or null if not fully initialized or if in embedded mode.
     * @deprecated Prefer using Bukkit's Services Manager to get {@link TryLibsAPI}.
     */
    @Deprecated
    public static TryLibs getPluginSafe() {
        if (isEmbeddedMode) {
            debugLog("TryLibs.getPluginSafe() called from embedded mode. It's recommended to use the Services Manager for TryLibsAPI.");
            org.bukkit.plugin.RegisteredServiceProvider<TryLibsAPI> provider = Bukkit.getServer().getServicesManager().getRegistration(TryLibsAPI.class);
            if (provider != null && provider.getProvider() instanceof TryLibs) {
                return (TryLibs) provider.getProvider();
            }
            return null;
        }
        // Check if the current instance is the primary one and fully initialized
        if (instance == null || !fullyInitialized || instance.getClass().getClassLoader() != originalClassLoader) {
            debugLog("TryLibs.getPluginSafe() returning null. Instance: " + (instance == null ? "null" : "exists") +
                    ", Initialized: " + fullyInitialized +
                    ", Instance CL: " + (instance != null ? (instance.getClass().getClassLoader() != null ? instance.getClass().getClassLoader().toString() : "unknown") : "N/A") +
                    ", Original CL: " + originalClassLoader +
                    ", State: " + initializationState);
            return null;
        }
        return instance;
    }

    /**
     * Checks if the primary TryLibs plugin instance is fully initialized and its API service should be available.
     * @return true if TryLibs is ready to use.
     */
    public static boolean isInitialized() {
        // In embedded mode, this method should reflect the state of the *service*, not the shaded copy.
        if (isEmbeddedMode()) { // Call the method to ensure latest state of isEmbeddedMode
            debugLog("TryLibs.isInitialized() called from embedded mode. Checking service availability.");
            org.bukkit.plugin.RegisteredServiceProvider<TryLibsAPI> provider = Bukkit.getServer().getServicesManager().getRegistration(TryLibsAPI.class);
            if (provider != null && provider.getProvider() != null) {
                // Additionally, ensure the provider is an instance of this class from the original classloader
                // This helps prevent a shaded copy's service from being seen as "the" service if something went wrong.
                Object providedService = provider.getProvider();
                return (providedService instanceof TryLibs && providedService.getClass().getClassLoader() == originalClassLoader);
            }
            return false;
        }
        // For non-embedded mode, check if the current 'instance' is the one from originalClassLoader and is fullyInitialized.
        return instance != null && fullyInitialized && instance.getClass().getClassLoader() == originalClassLoader;
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
    @Override
    public Configmanager getConfigManager() {
        // Ensure that only the primary instance provides managers, or that embedded instances correctly get them from the service.
        // For now, this assumes configManager is initialized if 'this' is the primary instance.
        // If called on a shaded instance that didn't initialize, this would be null.
        // A more robust approach might involve shaded instances forwarding calls to the service if they detect they are shaded.
        if (this.getClass().getClassLoader() != originalClassLoader && instance != null && instance != this) {
            TryLibsAPI service = Bukkit.getServer().getServicesManager().load(TryLibsAPI.class);
            if (service != null) return service.getConfigManager();
            getLogger().warning("Tried to access ConfigManager from a shaded instance without a primary service. This is problematic.");
            return null; // Or throw
        }
        return configManager;
    }

    /**
     * Gibt den DatabaseHandler zurück.
     * @return DatabaseHandler Instanz
     */
    @Override
    public DatabaseHandler getDatabaseHandler() {
        if (this.getClass().getClassLoader() != originalClassLoader && instance != null && instance != this) {
            TryLibsAPI service = Bukkit.getServer().getServicesManager().load(TryLibsAPI.class);
            if (service != null) return service.getDatabaseHandler();
            getLogger().warning("Tried to access DatabaseHandler from a shaded instance without a primary service. This is problematic.");
            return null; // Or throw
        }
        return databaseHandler;
    }

    /**
     * Translates alternate color codes using '&amp;' character in the given text.
     * This method is static and can be used directly, but also provided via API for consistency.
     * @param text The text to translate.
     * @return The translated text with color codes.
     */
    public static String staticTranslateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    @Override
    public String translateColors(String text) {
        return staticTranslateColors(text);
    }

    /**
     * Translates alternate color codes using '&amp;' character in each string of the given list.
     * This method is static and can be used directly, but also provided via API for consistency.
     * @param texts The list of strings to translate.
     * @return A new list containing the translated strings.
     */
    public static List<String> staticTranslateColors(List<String> texts) {
        return texts.stream().map(TryLibs::staticTranslateColors).collect(Collectors.toList());
    }

    @Override
    public List<String> translateColors(List<String> texts) {
        return staticTranslateColors(texts);
    }

    /**
     * Gibt den Namen der Economy
     * Datenbanktabelle zurück.
     * @return Name der Economy-Datenbanktabelle
     */
    @Override
    public String getEconomyDatabaseName() {
        if (this.getClass().getClassLoader() != originalClassLoader && instance != null && instance != this) {
            TryLibsAPI service = Bukkit.getServer().getServicesManager().load(TryLibsAPI.class);
            if (service != null) return service.getEconomyDatabaseName();
            getLogger().warning("Tried to access EconomyDatabaseName from a shaded instance without a primary service. This is problematic.");
            return null; // Or throw
        }
        return economyDatabaseName;
    }

    @Override
    public ItemStack getPlaceholderItem() {
        // This method creates a new item, so it's generally safe to call from any instance.
        // However, ensure consistency if the material/name could ever be configurable by the primary plugin.
        return new ItemBuilder(Material.BLACK_STAINED_GLASS_PANE).setName("§7").build();
    }

    // This method seems specific to "BlockEngine" and uses GSON from TryLibs.
    // If other plugins need this, it should be part of the API.
    // For now, keeping it private as its usage context is unclear.
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

        /**
         * Default constructor for TryLibsInitializedEvent.
         */
        public TryLibsInitializedEvent() {
            super(); // Call the Event constructor
        }

        @Override
        public HandlerList getHandlers() {
            return handlers;
        }

        /**
         * Gets the handler list for this event.
         * @return The handler list.
         */
        public static HandlerList getHandlerList() {
            return handlers;
        }
    }
}

package com.trynocs.tryLibs.api;

import com.trynocs.tryLibs.utils.config.Configmanager;
import com.trynocs.tryLibs.utils.database.DatabaseHandler;
import org.bukkit.inventory.ItemStack;

import java.util.List;

/**
 * The main API interface for TryLibs.
 * <p>
 * This interface provides access to the core functionalities of TryLibs.
 * To obtain an instance of this API, retrieve it from Bukkit's Services Manager:
 * <pre>{@code
 * RegisteredServiceProvider<TryLibsAPI> provider = Bukkit.getServer().getServicesManager().getRegistration(TryLibsAPI.class);
 * if (provider != null) {
 *     TryLibsAPI api = provider.getProvider();
 *     // You can now use the api instance
 * } else {
 *     // TryLibs is not available or not loaded properly
 *     Bukkit.getLogger().severe("TryLibs API not found! Make sure TryLibs is installed and enabled.");
 * }
 * }</pre>
 */
public interface TryLibsAPI {

    /**
     * Gets the Configmanager instance used by TryLibs.
     *
     * @return The Configmanager instance.
     */
    Configmanager getConfigManager();

    /**
     * Gets the DatabaseHandler instance used by TryLibs.
     *
     * @return The DatabaseHandler instance.
     */
    DatabaseHandler getDatabaseHandler();

    /**
     * Translates alternate color codes using '&amp;' character in the given text.
     *
     * @param text The text to translate.
     * @return The translated text with color codes.
     */
    String translateColors(String text);

    /**
     * Translates alternate color codes using '&amp;' character in each string of the given list.
     *
     * @param texts The list of strings to translate.
     * @return A new list containing the translated strings.
     */
    List<String> translateColors(List<String> texts);

    /**
     * Gets the configured name of the economy database table.
     *
     * @return The name of the economy database table.
     */
    String getEconomyDatabaseName();

    /**
     * Gets a pre-defined placeholder item.
     * This is typically a black stained glass pane with an empty name ("§7").
     *
     * @return The placeholder ItemStack.
     */
    ItemStack getPlaceholderItem();

    // Potentially add other methods from TryLibs.java that should be part of the public API
    // For example, if ItemBuilder or other utilities are intended to be accessed via the API instance.
    // However, ItemBuilder is often used as a utility class directly.
}

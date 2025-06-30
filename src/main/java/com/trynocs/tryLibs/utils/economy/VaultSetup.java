package com.trynocs.tryLibs.utils.economy;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Utility class for setting up and accessing the Vault economy provider.
 */
public class VaultSetup {
    private static Economy economy;

    /**
     * Sets up the Vault economy provider.
     * This method should be called during plugin initialization.
     *
     * @param plugin The JavaPlugin instance.
     * @return true if Vault and an economy provider were successfully hooked, false otherwise.
     */
    public static boolean setupVault(JavaPlugin plugin) {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) {
            return false;
        }
        RegisteredServiceProvider<Economy> rsp = plugin.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            return false;
        }
        economy = rsp.getProvider();
        return economy != null;
    }

    /**
     * Gets the currently hooked Vault Economy provider.
     *
     * @return The Economy provider instance, or null if not set up.
     */
    public static Economy getEconomy() {
        return economy;
    }
}

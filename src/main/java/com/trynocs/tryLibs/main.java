package com.trynocs.tryLibs;

import com.trynocs.tryLibs.utils.config.Configmanager;
import com.trynocs.tryLibs.utils.database.DatabaseHandler;
import org.bukkit.plugin.java.JavaPlugin;

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
public final class main extends JavaPlugin {
    private static main instance;
    private Configmanager configManager;
    private DatabaseHandler databaseHandler;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new Configmanager(this);
        databaseHandler = new DatabaseHandler();
        
        getLogger().info("TryLibs wurde erfolgreich aktiviert!");
    }

    @Override
    public void onDisable() {
        if (databaseHandler != null) {
            databaseHandler.closeConnection();
        }
        getLogger().info("TryLibs wurde deaktiviert!");
    }

    /**
     * Gibt die Instanz des Plugins zurück.
     * @return main Instanz
     */
    public static main getPlugin() {
        return instance;
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
}

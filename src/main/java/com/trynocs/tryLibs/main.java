package com.trynocs.tryLibs;

import com.google.gson.JsonObject;
import com.trynocs.tryLibs.utils.config.Configmanager;
import com.trynocs.tryLibs.utils.database.DatabaseHandler;
import com.trynocs.tryLibs.utils.gui.ItemBuilder;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import javax.net.ssl.HttpsURLConnection;
import java.io.IOException;
import java.io.OutputStream;
import java.net.URL;
import java.util.List;
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
public final class main extends JavaPlugin {
    private static main instance;
    private Configmanager configManager;
    private DatabaseHandler databaseHandler;
    private String economyDatabaseName;

    @Override
    public void onEnable() {
        instance = this;
        configManager = new Configmanager(this);
        databaseHandler = new DatabaseHandler();
        economyDatabaseName = getConfig().getString("database.economytable", "economy");
        
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

    public static String translateColors(String text) {
        return ChatColor.translateAlternateColorCodes('&', text);
    }

    public static List<String> translateColors(List<String> texts) {
        return texts.stream().map(main::translateColors).collect(Collectors.toList());
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
}
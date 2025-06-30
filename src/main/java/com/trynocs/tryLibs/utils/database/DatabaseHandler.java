package com.trynocs.tryLibs.utils.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trynocs.tryLibs.api.TryLibsAPI; // Changed
import org.bukkit.configuration.file.FileConfiguration; // Keep for now, but ideally config access is through API

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Handles all database interactions for TryLibs, supporting SQLite and MySQL.
 * This class manages connections, table creation, and data persistence.
 */
public class DatabaseHandler {
    private Connection connection;
    private final Logger logger;
    private final TryLibsAPI api; // Changed: To access config and other API features if needed

    private String dbType;
    private String sqlitePath; // Default path should be TryLibs specific
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private final Gson gson = new Gson();
    private UUID dummyUUID = UUID.fromString("00000000-0000-0000-0000-000000000000");
    private boolean configLoaded = false;
    private int initAttempts = 0;
    private final int MAX_INIT_ATTEMPTS = 3;
    // private boolean classloaderWarningShown = false; // No longer needed here with direct API/Logger passing

    /**
     * Creates a new DatabaseHandler.
     * Configuration is loaded immediately using the provided TryLibsAPI.
     * Tables are NOT automatically created.
     *
     * @param api The TryLibsAPI instance, used to access configuration.
     * @param pluginLogger The Logger instance from the main plugin.
     */
    public DatabaseHandler(TryLibsAPI api, Logger pluginLogger) {
        this.api = api;
        this.logger = pluginLogger; // Use the logger from the main TryLibs plugin instance
        loadConfig(); // Load configuration immediately on construction
    }

    /**
     * Loads the database configuration from the plugin's config file.
     * This method is called during the construction of the DatabaseHandler.
     */
    private synchronized void loadConfig() {
        if (configLoaded) return;

        if (this.api == null) {
            // This should ideally not happen if DatabaseHandler is constructed correctly by TryLibs
            logger.log(Level.SEVERE, "TryLibsAPI instance is null in DatabaseHandler. Cannot load database configuration.");
            throw new IllegalStateException("TryLibsAPI not provided to DatabaseHandler.");
        }

        // No need to check TryLibs.isInitialized() or TryLibs.getPlugin() anymore.
        // If DatabaseHandler is being created, TryLibs's onEnable should be far enough along
        // for ConfigManager to be ready.

        try {
            FileConfiguration config = api.getConfigManager().getConfig(); // Use API to get config
            this.dbType = config.getString("database.type", "sqlite").toLowerCase();
            logger.info("Using database type: " + dbType);

            // Default SQLite path changed to be TryLibs specific
            this.sqlitePath = config.getString("database.sqlite.path", "plugins/TryLibs/database.db");
            File dbFile = new File(this.sqlitePath);
            File dataFolder = dbFile.getParentFile();
            if (!dataFolder.exists()) {
                if (!dataFolder.mkdirs()) {
                    logger.warning("Could not create data folder for SQLite database: " + dataFolder.getAbsolutePath());
                }
            }

            this.mysqlHost = config.getString("database.mysql.host", "localhost");
            this.mysqlPort = config.getInt("database.mysql.port", 3306);
            this.mysqlDatabase = config.getString("database.mysql.database", "trylibs"); // Default DB name changed
            this.mysqlUsername = config.getString("database.mysql.username", "root");
            this.mysqlPassword = config.getString("database.mysql.password", "password");

            configLoaded = true;
            logger.info("Database configuration successfully loaded for TryLibs.");
        } catch (Exception e) { // Catch broader exceptions during config access
            logger.log(Level.SEVERE, "Failed to load database configuration: " + e.getMessage(), e);
            // Do not throw, allow server to continue loading, but database functionality will be impaired.
            // Or, re-throw if database is critical: throw new IllegalStateException("Failed to load database configuration", e);
        }
    }

    private synchronized void ensureConnection() {
        // Config should be loaded by constructor. If not, something is wrong.
        if (!configLoaded) {
            logger.severe("Database configuration not loaded. Cannot establish connection.");
            // Attempt to load config again, though this indicates a problem during construction.
            loadConfig();
            if (!configLoaded) { // If still not loaded, bail.
                throw new IllegalStateException("Database configuration failed to load. Cannot ensure connection.");
            }
        }

        try {
            if (connection == null || connection.isClosed()) {
                if ("mysql".equals(dbType)) {
                    connectToMysql();
                } else {
                    connectToSqlite();
                }
                connection.setAutoCommit(true);
                logger.info("Datenbankverbindung hergestellt! (" + dbType + ")");
            }
        } catch (Exception e) {
            logger.severe("Fehler beim Verbindungsaufbau: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void connectToSqlite() throws ClassNotFoundException, SQLException {
        Class.forName("org.sqlite.JDBC");
        connection = DriverManager.getConnection("jdbc:sqlite:" + sqlitePath);
    }

    private void connectToMysql() throws ClassNotFoundException, SQLException {
        Class.forName("com.mysql.cj.jdbc.Driver");
        String url = "jdbc:mysql://" + mysqlHost + ":" + mysqlPort + "/" + mysqlDatabase +
                "?useSSL=false&allowPublicKeyRetrieval=true&useUnicode=true&characterEncoding=utf8";
        connection = DriverManager.getConnection(url, mysqlUsername, mysqlPassword);
    }

    /**
     * Führt ein beliebiges CREATE TABLE-Statement aus.
     * Der Entwickler ist selbst für das Statement verantwortlich!
     * Beispiel:
     *   handler.executeTableStatement("CREATE TABLE IF NOT EXISTS users (UUID TEXT PRIMARY KEY, Name TEXT);");
     * @param sql The SQL statement to execute.
     */
    public void executeTableStatement(String sql) {
        try {
            ensureConnection();
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(sql);
                logger.info("Tabellen-Statement erfolgreich ausgeführt.");
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Ausführen des Tabellen-Statements: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Führt ein beliebiges CREATE TABLE-Statement aus.
     * Beispiel:
     *   databaseHandler.createTable("CREATE TABLE IF NOT EXISTS users (UUID TEXT PRIMARY KEY, Name TEXT);");
     * @param name The name of the table to create.
     */
    public void createTable(String name) {
        try {
            ensureConnection();
            String sql;
            if ("mysql".equals(dbType)) {
                sql = "CREATE TABLE IF NOT EXISTS " + name + " (" +
                        "UUID VARCHAR(36)," +
                        "KeyName VARCHAR(255)," +
                        "Value TEXT," +
                        "Type VARCHAR(20)," +
                        "PRIMARY KEY (UUID, KeyName)" +
                        ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
            } else {
                sql = "CREATE TABLE IF NOT EXISTS " + name + " (" +
                        "UUID TEXT," +
                        "Key TEXT," +
                        "Value TEXT," +
                        "Type TEXT," +
                        "PRIMARY KEY (UUID, Key)" +
                        ");";
            }
            try (Statement stmt = connection.createStatement()) {
                stmt.executeUpdate(sql);
                logger.info("Tabelle '" + name + "' erfolgreich erstellt oder bereits vorhanden.");
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Erstellen der Tabelle '" + name + "': " + e.getMessage());
            e.printStackTrace();
        }
    }

    private synchronized void saveGeneric(String tableName, UUID uuid, String key, Object value, String type) {
        try {
            ensureConnection();
            tableName = tableName.toLowerCase();

            String serializedValue;
            if (value instanceof String) {
                serializedValue = (String) value;
            } else {
                serializedValue = gson.toJson(value);
            }

            String sql;
            if ("mysql".equals(dbType)) {
                sql = "INSERT INTO " + tableName + " (UUID, KeyName, Value, Type) VALUES (?, ?, ?, ?) " +
                        "ON DUPLICATE KEY UPDATE Value = ?, Type = ?";
            } else {
                sql = "INSERT OR REPLACE INTO " + tableName + " (UUID, Key, Value, Type) VALUES (?, ?, ?, ?)";
            }

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, key);
                pstmt.setString(3, serializedValue);
                pstmt.setString(4, type);

                if ("mysql".equals(dbType)) {
                    pstmt.setString(5, serializedValue);
                    pstmt.setString(6, type);
                }

                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Speichern von Daten: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Saves a string value to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The string value to save.
     */
    public void saveData(String tableName, UUID uuid, String key, String value) {
        saveGeneric(tableName, uuid, key, value, "string");
    }

    /**
     * Saves a string value to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The string value to save.
     */
    public void saveData(UUID uuid, String key, String value) {
        saveData("users", uuid, key, value);
    }

    /**
     * Saves an integer value to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The integer value to save.
     */
    public void saveInt(String tableName, UUID uuid, String key, int value) {
        saveGeneric(tableName, uuid, key, value, "int");
    }

    /**
     * Saves an integer value to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The integer value to save.
     */
    public void saveInt(UUID uuid, String key, int value) {
        saveInt("users", uuid, key, value);
    }

    /**
     * Saves a double value to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The double value to save.
     */
    public void saveDouble(String tableName, UUID uuid, String key, double value) {
        saveGeneric(tableName, uuid, key, value, "double");
    }

    /**
     * Saves a double value to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The double value to save.
     */
    public void saveDouble(UUID uuid, String key, double value) {
        saveDouble("users", uuid, key, value);
    }

    /**
     * Saves a boolean value to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The boolean value to save.
     */
    public void saveBoolean(String tableName, UUID uuid, String key, boolean value) {
        saveGeneric(tableName, uuid, key, value, "boolean");
    }

    /**
     * Saves a boolean value to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The boolean value to save.
     */
    public void saveBoolean(UUID uuid, String key, boolean value) {
        saveBoolean("users", uuid, key, value);
    }

    /**
     * Saves a long value to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The long value to save.
     */
    public void saveLong(String tableName, UUID uuid, String key, long value) {
        saveGeneric(tableName, uuid, key, value, "long");
    }

    /**
     * Saves a long value to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The long value to save.
     */
    public void saveLong(UUID uuid, String key, long value) {
        saveLong("users", uuid, key, value);
    }

    /**
     * Saves a float value to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The float value to save.
     */
    public void saveFloat(String tableName, UUID uuid, String key, float value) {
        saveGeneric(tableName, uuid, key, value, "float");
    }

    /**
     * Saves a float value to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The float value to save.
     */
    public void saveFloat(UUID uuid, String key, float value) {
        saveFloat("users", uuid, key, value);
    }

    /**
     * Saves a string array to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The string array to save.
     */
    public void saveStringArray(String tableName, UUID uuid, String key, String[] value) {
        saveGeneric(tableName, uuid, key, value, "string_array");
    }

    /**
     * Saves a string array to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The string array to save.
     */
    public void saveStringArray(UUID uuid, String key, String[] value) {
        saveStringArray("users", uuid, key, value);
    }

    /**
     * Saves a list of strings to the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The list of strings to save.
     */
    public void saveStringList(String tableName, UUID uuid, String key, List<String> value) {
        saveGeneric(tableName, uuid, key, value, "string_list");
    }

    /**
     * Saves a list of strings to the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param value The list of strings to save.
     */
    public void saveStringList(UUID uuid, String key, List<String> value) {
        saveStringList("users", uuid, key, value);
    }

    /**
     * Loads raw data from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return ResultSet containing the raw data, or null if an error occurs.
     */
    private synchronized ResultSet loadRawData(String tableName, UUID uuid, String key) {
        try {
            ensureConnection();
            tableName = tableName.toLowerCase();

            String sql;
            if ("mysql".equals(dbType)) {
                sql = "SELECT Value, Type FROM " + tableName + " WHERE UUID = ? AND KeyName = ?";
            } else {
                sql = "SELECT Value, Type FROM " + tableName + " WHERE UUID = ? AND Key = ?";
            }

            PreparedStatement pstmt = connection.prepareStatement(sql);
            pstmt.setString(1, uuid.toString());
            pstmt.setString(2, key);

            return pstmt.executeQuery();
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der Daten: " + e.getMessage());
            e.printStackTrace();
            return null;
        }
    }

    /**
     * Loads a string value from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return The string value, or null if not found or an error occurs.
     */
    public String loadData(String tableName, UUID uuid, String key) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String value = rs.getString("Value");
                rs.close();
                return value;
            }
            if (rs != null) rs.close();
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden von String-Daten: " + e.getMessage());
        }
        return null;
    }

    /**
     * Loads a string value from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return The string value, or null if not found or an error occurs.
     */
    public String loadData(UUID uuid, String key) {
        return loadData("users", uuid, key);
    }

    /**
     * Loads a string value from the "users" table, returning a default value if not found.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found.
     * @return The string value, or the default value.
     */
    public String loadData(UUID uuid, String key, String defaultValue) {
        String value = loadData(uuid, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Loads a string value from the database, returning a default value if not found.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found.
     * @return The string value, or the default value.
     */
    public String loadData(String tableName, UUID uuid, String key, String defaultValue) {
        String value = loadData(tableName, uuid, key);
        return value != null ? value : defaultValue;
    }

    /**
     * Loads an integer value from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The integer value, or the default value.
     */
    public int loadInt(String tableName, UUID uuid, String key, int defaultValue) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("int".equals(type)) {
                    return Integer.parseInt(value);
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException | NumberFormatException e) {
            logger.warning("Fehler beim Laden von Integer-Daten: " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Loads an integer value from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The integer value, or the default value.
     */
    public int loadInt(UUID uuid, String key, int defaultValue) {
        return loadInt("users", uuid, key, defaultValue);
    }

    /**
     * Loads a double value from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The double value, or the default value.
     */
    public double loadDouble(String tableName, UUID uuid, String key, double defaultValue) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("double".equals(type)) {
                    return Double.parseDouble(value);
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException | NumberFormatException e) {
            logger.warning("Fehler beim Laden von Double-Daten: " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Loads a double value from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The double value, or the default value.
     */
    public double loadDouble(UUID uuid, String key, double defaultValue) {
        return loadDouble("users", uuid, key, defaultValue);
    }

    /**
     * Loads a boolean value from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The boolean value, or the default value.
     */
    public boolean loadBoolean(String tableName, UUID uuid, String key, boolean defaultValue) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("boolean".equals(type)) {
                    return Boolean.parseBoolean(value);
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException e) {
            logger.warning("Fehler beim Laden von Boolean-Daten: " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Loads a boolean value from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The boolean value, or the default value.
     */
    public boolean loadBoolean(UUID uuid, String key, boolean defaultValue) {
        return loadBoolean("users", uuid, key, defaultValue);
    }

    /**
     * Loads a long value from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The long value, or the default value.
     */
    public long loadLong(String tableName, UUID uuid, String key, long defaultValue) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("long".equals(type)) {
                    return Long.parseLong(value);
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException | NumberFormatException e) {
            logger.warning("Fehler beim Laden von Long-Daten: " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Loads a long value from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The long value, or the default value.
     */
    public long loadLong(UUID uuid, String key, long defaultValue) {
        return loadLong("users", uuid, key, defaultValue);
    }

    /**
     * Loads a float value from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The float value, or the default value.
     */
    public float loadFloat(String tableName, UUID uuid, String key, float defaultValue) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("float".equals(type)) {
                    return Float.parseFloat(value);
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException | NumberFormatException e) {
            logger.warning("Fehler beim Laden von Float-Daten: " + e.getMessage());
        }
        return defaultValue;
    }

    /**
     * Loads a float value from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @param defaultValue The default value to return if data is not found or type mismatch.
     * @return The float value, or the default value.
     */
    public float loadFloat(UUID uuid, String key, float defaultValue) {
        return loadFloat("users", uuid, key, defaultValue);
    }

    /**
     * Loads a string array from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return The string array, or an empty array if not found or type mismatch.
     */
    public String[] loadStringArray(String tableName, UUID uuid, String key) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("string_array".equals(type)) {
                    return gson.fromJson(value, String[].class);
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException e) {
            logger.warning("Fehler beim Laden von String-Array: " + e.getMessage());
        }
        return new String[0];
    }

    /**
     * Loads a string array from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return The string array, or an empty array if not found or type mismatch.
     */
    public String[] loadStringArray(UUID uuid, String key) {
        return loadStringArray("users", uuid, key);
    }

    /**
     * Loads a list of strings from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return The list of strings, or an empty list if not found or type mismatch.
     */
    public List<String> loadStringList(String tableName, UUID uuid, String key) {
        try {
            ResultSet rs = loadRawData(tableName, uuid, key);
            if (rs != null && rs.next()) {
                String type = rs.getString("Type");
                String value = rs.getString("Value");
                rs.close();

                if ("string_list".equals(type)) {
                    return gson.fromJson(value, new TypeToken<List<String>>(){}.getType());
                }
            }
            if (rs != null) rs.close();
        } catch (SQLException e) {
            logger.warning("Fehler beim Laden von String-Liste: " + e.getMessage());
        }
        return new ArrayList<>();
    }

    /**
     * Loads a list of strings from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key for the data.
     * @return The list of strings, or an empty list if not found or type mismatch.
     */
    public List<String> loadStringList(UUID uuid, String key) {
        return loadStringList("users", uuid, key);
    }

    /**
     * Deletes a specific data entry from the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key of the data to delete.
     * @return true if data was deleted, false otherwise.
     */
    public boolean deleteData(String tableName, UUID uuid, String key) {
        try {
            ensureConnection();

            String sql;
            if ("mysql".equals(dbType)) {
                sql = "DELETE FROM " + tableName.toLowerCase() + " WHERE UUID = ? AND KeyName = ?";
            } else {
                sql = "DELETE FROM " + tableName.toLowerCase() + " WHERE UUID = ? AND Key = ?";
            }

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, key);
                return pstmt.executeUpdate() > 0;
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Löschen von Daten: " + e.getMessage());
            return false;
        }
    }

    /**
     * Deletes a specific data entry from the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key of the data to delete.
     * @return true if data was deleted, false otherwise.
     */
    public boolean deleteData(UUID uuid, String key) {
        return deleteData("users", uuid, key);
    }

    /**
     * Wipes all data associated with a specific UUID from predefined tables ("users", "currency", "info").
     * @param uuid The UUID of the player whose data to wipe.
     * @return true if any data was wiped, false otherwise.
     */
    public boolean wipeAllData(UUID uuid) {
        boolean isWiped = false;
        try {
            ensureConnection();
            isWiped = wipeDataFromTable("users", uuid) |
                    wipeDataFromTable("currency", uuid) |
                    wipeDataFromTable("info", uuid);
        } catch (SQLException e) {
            logger.severe("Fehler beim Löschen aller Daten: " + e.getMessage());
        }
        return isWiped;
    }

    /**
     * Wipes data for a specific UUID from a given table.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @return true if data was wiped, false otherwise.
     * @throws SQLException if a database access error occurs.
     */
    private boolean wipeDataFromTable(String tableName, UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + tableName.toLowerCase() + " WHERE UUID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            return pstmt.executeUpdate() > 0;
        }
    }

    /**
     * Wipes all data associated with a specific UUID. This is an alias for {@link #wipeAllData(UUID)}.
     * @param uuid The UUID of the player whose data to wipe.
     * @return true if any data was wiped, false otherwise.
     */
    public boolean wipeData(UUID uuid) {
        return wipeAllData(uuid);
    }

    /**
     * Checks if a specific data entry exists in the database.
     * @param tableName The name of the table.
     * @param uuid The UUID of the player.
     * @param key The key of the data.
     * @return true if data exists, false otherwise.
     */
    public boolean hasData(String tableName, UUID uuid, String key) {
        try {
            ensureConnection();

            String sql;
            if ("mysql".equals(dbType)) {
                sql = "SELECT 1 FROM " + tableName.toLowerCase() + " WHERE UUID = ? AND KeyName = ? LIMIT 1";
            } else {
                sql = "SELECT 1 FROM " + tableName.toLowerCase() + " WHERE UUID = ? AND Key = ? LIMIT 1";
            }

            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                pstmt.setString(2, key);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Überprüfen der Datenexistenz: " + e.getMessage());
            return false;
        }
    }

    /**
     * Checks if a specific data entry exists in the "users" table.
     * @param uuid The UUID of the player.
     * @param key The key of the data.
     * @return true if data exists, false otherwise.
     */
    public boolean hasData(UUID uuid, String key) {
        return hasData("users", uuid, key);
    }

    /**
     * Closes the database connection if it is open.
     */
    public void closeConnection() {
        try {
            if (connection != null && !connection.isClosed()) {
                connection.close();
                logger.info("Datenbankverbindung geschlossen");
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Schließen der Datenbankverbindung: " + e.getMessage());
        }
    }

    /**
     * Prüft, ob eine Tabelle bereits existiert.
     * @param tableName Name der Tabelle
     * @return true, wenn die Tabelle existiert, sonst false
     */
    public boolean tableExists(String tableName) {
        ensureConnection();
        try {
            if ("mysql".equals(dbType)) {
                DatabaseMetaData meta = connection.getMetaData();
                try (ResultSet rs = meta.getTables(null, null, tableName, null)) {
                    return rs.next();
                }
            } else {
                String sql = "SELECT name FROM sqlite_master WHERE type='table' AND name=?;";
                try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                    pstmt.setString(1, tableName);
                    try (ResultSet rs = pstmt.executeQuery()) {
                        return rs.next();
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Überprüfen der Tabellenvorhandensein: " + e.getMessage());
            return false;
        }
    }

    @Override
    protected void finalize() throws Throwable {
        closeConnection();
        super.finalize();
    }
}
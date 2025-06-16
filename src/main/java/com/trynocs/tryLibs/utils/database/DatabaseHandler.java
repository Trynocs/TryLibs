package com.trynocs.tryLibs.utils.database;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.trynocs.tryLibs.main;
import com.trynocs.blockengine.utils.parents.Stadt;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.logging.Logger;

public class DatabaseHandler {
    private Connection connection;
    private final Logger logger = Logger.getLogger("DatabaseHandler");
    private String dbType;
    private String sqlitePath;
    private String mysqlHost;
    private int mysqlPort;
    private String mysqlDatabase;
    private String mysqlUsername;
    private String mysqlPassword;
    private final Gson gson = new Gson();

    public DatabaseHandler() {
        loadConfig();
        setupDatabase();
    }

    private void loadConfig() {
        FileConfiguration config = main.getPlugin().getConfigManager().getConfig();
        this.dbType = config.getString("database.type", "sqlite").toLowerCase();
        logger.info("Verwende Datenbanktyp: " + dbType);
        this.sqlitePath = config.getString("database.sqlite.path", "plugins/BlockEngine/blockengine.db");
        File dataFolder = new File(sqlitePath.substring(0, sqlitePath.lastIndexOf("/")));
        if (!dataFolder.exists()) {
            dataFolder.mkdirs();
        }
        this.mysqlHost = config.getString("database.mysql.host", "localhost");
        this.mysqlPort = config.getInt("database.mysql.port", 3306);
        this.mysqlDatabase = config.getString("database.mysql.database", "blockengine");
        this.mysqlUsername = config.getString("database.mysql.username", "root");
        this.mysqlPassword = config.getString("database.mysql.password", "password");
    }

    private synchronized void ensureConnection() {
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

    public void setupDatabase() {
        try {
            ensureConnection();
            try (Statement stmt = connection.createStatement()) {
                String createTableSql;

                if ("mysql".equals(dbType)) {
                    createTableSql = "CREATE TABLE IF NOT EXISTS %s ("
                            + "UUID VARCHAR(36),"
                            + "KeyName VARCHAR(255)," // 'Key' ist in MySQL ein reserviertes Wort
                            + "Value TEXT,"
                            + "Type VARCHAR(20),"
                            + "PRIMARY KEY (UUID, KeyName)"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
                } else {
                    createTableSql = "CREATE TABLE IF NOT EXISTS %s ("
                            + "UUID TEXT,"
                            + "Key TEXT,"
                            + "Value TEXT,"
                            + "Type TEXT,"
                            + "PRIMARY KEY (UUID, Key)"
                            + ");";
                }

                stmt.executeUpdate(String.format(createTableSql, "users"));
                stmt.executeUpdate(String.format(createTableSql, "currency"));
                stmt.executeUpdate(String.format(createTableSql, "info"));
                stmt.executeUpdate(String.format(createTableSql, "clan"));

                // towns-Tabelle
                String createTownsTable;
                if ("mysql".equals(dbType)) {
                    createTownsTable = "CREATE TABLE IF NOT EXISTS towns ("
                            + "UUID VARCHAR(36) PRIMARY KEY,"
                            + "Name VARCHAR(255),"
                            + "Owner VARCHAR(255),"
                            + "Members TEXT,"
                            + "Population INT,"
                            + "Budget DOUBLE,"
                            + "ZentrumPlotId VARCHAR(255),"
                            + "Level INT,"
                            + "XP INT,"
                            + "XPToNextLevel INT,"
                            + "Tax INT DEFAULT 0,"
                            + "MaxClaimedPlots INT DEFAULT 5,"
                            + "IsPublic BOOLEAN DEFAULT TRUE"
                            + ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";
                } else {
                    createTownsTable = "CREATE TABLE IF NOT EXISTS towns ("
                            + "UUID TEXT PRIMARY KEY,"
                            + "Name TEXT,"
                            + "Owner TEXT,"
                            + "Members TEXT,"
                            + "Population INTEGER,"
                            + "Budget REAL,"
                            + "ZentrumPlotId TEXT,"
                            + "Level INTEGER,"
                            + "XP INTEGER,"
                            + "XPToNextLevel INTEGER,"
                            + "Tax INTEGER DEFAULT 0,"
                            + "MaxClaimedPlots INTEGER DEFAULT 5,"
                            + "IsPublic BOOLEAN DEFAULT 1"
                            + ");";
                }
                stmt.executeUpdate(createTownsTable);

                // town_plots
                String createTownPlotsTable = "CREATE TABLE IF NOT EXISTS town_plots ("
                        + "PlotId VARCHAR(255) PRIMARY KEY,"
                        + "TownUUID VARCHAR(36)"
                        + ");";
                stmt.executeUpdate(createTownPlotsTable);

                // town_citizens
                String createTownCitizensTable = "CREATE TABLE IF NOT EXISTS town_citizens ("
                        + "PlayerUUID VARCHAR(36),"
                        + "TownUUID VARCHAR(36),"
                        + "Role VARCHAR(64),"
                        + "PRIMARY KEY (PlayerUUID, TownUUID)"
                        + ");";
                stmt.executeUpdate(createTownCitizensTable);

                // town_ranks
                String createTownRanksTable = "CREATE TABLE IF NOT EXISTS town_ranks ("
                        + "TownUUID VARCHAR(36),"
                        + "RankName VARCHAR(64),"
                        + "Permissions TEXT,"
                        + "PRIMARY KEY (TownUUID, RankName)"
                        + ");";
                stmt.executeUpdate(createTownRanksTable);

                // town_invitations
                String createTownInvitationsTable = "CREATE TABLE IF NOT EXISTS town_invitations ("
                        + "PlayerUUID VARCHAR(36),"
                        + "TownUUID VARCHAR(36),"
                        + "PRIMARY KEY (PlayerUUID, TownUUID)"
                        + ");";
                stmt.executeUpdate(createTownInvitationsTable);

                logger.info("Datenbanktabellen erfolgreich erstellt/überprüft!");
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Einrichten der Datenbank: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // --- Städte-Methoden ---

    public void saveTown(Stadt stadt) {
        try {
            ensureConnection();
            String sql = "INSERT INTO towns (UUID, Name, Owner, ZentrumPlotId, Level, Budget, XP, XPToNextLevel, Tax, MaxClaimedPlots, IsPublic) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?) " +
                    "ON DUPLICATE KEY UPDATE Name=?, Owner=?, ZentrumPlotId=?, Level=?, Budget=?, XP=?, XPToNextLevel=?, Tax=?, MaxClaimedPlots=?, IsPublic=?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, stadt.getStadtId().toString());
                pstmt.setString(2, stadt.getName());
                pstmt.setString(3, stadt.getGruenderUUID().toString());
                pstmt.setString(4, stadt.getZentrumPlotId());
                pstmt.setInt(5, stadt.getLevel());
                pstmt.setInt(6, stadt.getBudget());
                pstmt.setInt(7, stadt.getXp());
                pstmt.setInt(8, stadt.getXpToNextLevel());
                pstmt.setInt(9, stadt.getTax());
                pstmt.setInt(10, stadt.getMaxClaimedPlots());
                pstmt.setBoolean(11, stadt.isPublic());
                pstmt.setString(12, stadt.getName());
                pstmt.setString(13, stadt.getGruenderUUID().toString());
                pstmt.setString(14, stadt.getZentrumPlotId());
                pstmt.setInt(15, stadt.getLevel());
                pstmt.setInt(16, stadt.getBudget());
                pstmt.setInt(17, stadt.getXp());
                pstmt.setInt(18, stadt.getXpToNextLevel());
                pstmt.setInt(19, stadt.getTax());
                pstmt.setInt(20, stadt.getMaxClaimedPlots());
                pstmt.setBoolean(21, stadt.isPublic());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Speichern der Stadt: " + e.getMessage());
        }
    }

    public Stadt loadTown(UUID uuid) {
        try {
            ensureConnection();
            String sql = "SELECT * FROM towns WHERE UUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, uuid.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToStadt(rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der Stadt: " + e.getMessage());
        }
        return null;
    }

    public Stadt getTownByCitizen(UUID playerUUID) {
        try {
            ensureConnection();
            String sql = "SELECT t.* FROM towns t " +
                    "JOIN town_citizens c ON t.UUID = c.TownUUID " +
                    "WHERE c.PlayerUUID = ? LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToStadt(rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der Stadt eines Bürgers: " + e.getMessage());
        }
        return null;
    }

    public Stadt getTownByName(String name) {
        try {
            ensureConnection();
            String sql = "SELECT * FROM towns WHERE Name = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, name);
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return mapResultSetToStadt(rs);
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der Stadt nach Name: " + e.getMessage());
        }
        return null;
    }

    private Stadt mapResultSetToStadt(ResultSet rs) throws SQLException {
        UUID stadtId = UUID.fromString(rs.getString("UUID"));
        String name = rs.getString("Name");
        UUID owner = UUID.fromString(rs.getString("Owner"));
        String zentrumPlotId = rs.getString("ZentrumPlotId");
        int level = rs.getInt("Level");
        int budget = rs.getInt("Budget");
        int xp = rs.getInt("XP");
        int xpToNextLevel = rs.getInt("XPToNextLevel");
        int tax = rs.getInt("Tax");
        int maxClaimedPlots = rs.getInt("MaxClaimedPlots");
        boolean isPublic = rs.getBoolean("IsPublic");
        Stadt stadt = new Stadt(stadtId, name, owner, zentrumPlotId, level, budget, xp, xpToNextLevel);
        stadt.setTax(tax);
        stadt.setMaxClaimedPlots(maxClaimedPlots);
        stadt.setPublic(isPublic);
        return stadt;
    }

    public boolean isPlotAssignedToTown(String plotId) {
        try {
            ensureConnection();
            String sql = "SELECT 1 FROM town_plots WHERE PlotId = ? LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, plotId);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler bei Plot-Stadt-Prüfung: " + e.getMessage());
        }
        return false;
    }

    public boolean isTownNameExists(String name) {
        try {
            ensureConnection();
            String sql = "SELECT 1 FROM towns WHERE Name = ? LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, name);
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler bei Stadtname-Prüfung: " + e.getMessage());
        }
        return false;
    }

    public void assignPlotToTown(String plotId, UUID stadtId) {
        try {
            ensureConnection();
            String sql = "INSERT INTO town_plots (PlotId, TownUUID) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, plotId);
                pstmt.setString(2, stadtId.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Plot-Zuweisen: " + e.getMessage());
        }
    }

    public void addCitizenToTown(UUID playerUUID, UUID stadtId, String role) {
        try {
            ensureConnection();
            String sql = "INSERT INTO town_citizens (PlayerUUID, TownUUID, Role) VALUES (?, ?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, stadtId.toString());
                pstmt.setString(3, role);
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Hinzufügen eines Bürgers: " + e.getMessage());
        }
    }

    public List<String> getTownPlots(UUID townUUID) {
        List<String> plots = new ArrayList<>();
        try {
            ensureConnection();
            String sql = "SELECT PlotId FROM town_plots WHERE TownUUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, townUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        plots.add(rs.getString("PlotId"));
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der Stadtplots: " + e.getMessage());
        }
        return plots;
    }

    public void removeCitizen(UUID playerUUID, UUID townUUID) {
        try {
            ensureConnection();
            String sql = "DELETE FROM town_citizens WHERE PlayerUUID = ? AND TownUUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, townUUID.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Entfernen eines Bürgers: " + e.getMessage());
        }
    }

    public void inviteCitizen(UUID playerUUID, UUID townUUID) {
        try {
            ensureConnection();
            String sql = "INSERT INTO town_invitations (PlayerUUID, TownUUID) VALUES (?, ?)";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, townUUID.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Einladen eines Bürgers: " + e.getMessage());
        }
    }

    public boolean hasInvitation(UUID playerUUID, UUID townUUID) {
        try {
            ensureConnection();
            String sql = "SELECT 1 FROM town_invitations WHERE PlayerUUID = ? AND TownUUID = ? LIMIT 1";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, townUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    return rs.next();
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler bei Einladungssuche: " + e.getMessage());
        }
        return false;
    }

    public void removeInvitation(UUID playerUUID, UUID townUUID) {
        try {
            ensureConnection();
            String sql = "DELETE FROM town_invitations WHERE PlayerUUID = ? AND TownUUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                pstmt.setString(2, townUUID.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Entfernen einer Einladung: " + e.getMessage());
        }
    }

    public List<Stadt> getInvitedTowns(UUID playerUUID) {
        List<Stadt> invitedTowns = new ArrayList<>();
        try {
            ensureConnection();
            String sql = "SELECT t.* FROM towns t " +
                    "JOIN town_invitations i ON t.UUID = i.TownUUID " +
                    "WHERE i.PlayerUUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, playerUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    while (rs.next()) {
                        invitedTowns.add(mapResultSetToStadt(rs));
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der eingeladenen Städte: " + e.getMessage());
        }
        return invitedTowns;
    }

    // --- Level/XP/Upgrade ---

    public void addXpToTown(Stadt stadt, int amount) {
        int xp = stadt.getXp() + amount;
        int level = stadt.getLevel();
        int xpToNext = stadt.getXpToNextLevel();
        boolean leveledUp = false;
        while (xp >= xpToNext) {
            xp -= xpToNext;
            level++;
            xpToNext = calculateXpToNextLevel(level);
            leveledUp = true;
        }
        stadt.setXp(xp);
        stadt.setLevel(level);
        stadt.setXpToNextLevel(xpToNext);
        saveTown(stadt);
        if (leveledUp) {
            handleTownLevelUp(stadt);
        }
    }

    public int calculateXpToNextLevel(int level) {
        int baseXp = 1000;
        double multiplier = 1.5;
        return (int) Math.round(baseXp * level * multiplier);
    }

    public void upgradeStadtLevel(Stadt stadt) {
        int newLevel = stadt.getLevel() + 1;
        int newMaxPlots = newLevel * 5;
        stadt.setLevel(newLevel);
        stadt.setMaxClaimedPlots(newMaxPlots);
        stadt.setXp(0);
        stadt.setXpToNextLevel(calculateXpToNextLevel(newLevel));
        saveTown(stadt);
        logger.info("Stadt " + stadt.getName() + " wurde auf Level " + newLevel + " upgegradet. Neue MaxClaimedPlots: " + newMaxPlots);
    }

    public void setTownTax(UUID townUUID, int tax) {
        try {
            ensureConnection();
            String sql = "UPDATE towns SET Tax = ? WHERE UUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setInt(1, tax);
                pstmt.setString(2, townUUID.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Setzen der Stadtsteuer: " + e.getMessage());
        }
    }

    public int getTownTax(UUID townUUID) {
        try {
            ensureConnection();
            String sql = "SELECT Tax FROM towns WHERE UUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, townUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("Tax");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden der Stadtsteuer: " + e.getMessage());
        }
        return 0;
    }

    public void setTownPublic(UUID townUUID, boolean isPublic) {
        try {
            ensureConnection();
            String sql = "UPDATE towns SET IsPublic = ? WHERE UUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setBoolean(1, isPublic);
                pstmt.setString(2, townUUID.toString());
                pstmt.executeUpdate();
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Setzen von IsPublic: " + e.getMessage());
        }
    }

    public boolean isTownPublic(UUID townUUID) {
        try {
            ensureConnection();
            String sql = "SELECT IsPublic FROM towns WHERE UUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, townUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getBoolean("IsPublic");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden von IsPublic: " + e.getMessage());
        }
        return true;
    }

    public int getMaxClaimedPlots(UUID townUUID) {
        try {
            ensureConnection();
            String sql = "SELECT MaxClaimedPlots FROM towns WHERE UUID = ?";
            try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
                pstmt.setString(1, townUUID.toString());
                try (ResultSet rs = pstmt.executeQuery()) {
                    if (rs.next()) {
                        return rs.getInt("MaxClaimedPlots");
                    }
                }
            }
        } catch (SQLException e) {
            logger.severe("Fehler beim Laden von MaxClaimedPlots: " + e.getMessage());
        }
        return 5;
    }

    // Dummy-Implementierung für angrenzende Plots (immer true)
    public boolean isPlotAdjacentToTown(String plotId, UUID townUUID) {
        // TODO: Implementiere echte Prüfung mit PlotSquared-API und town_plots
        return true;
    }

    // --- Ressourcen-Management ---

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

    @Override
    protected void finalize() throws Throwable {
        closeConnection();
        super.finalize();
    }

    /**
     * Wird aufgerufen, wenn eine Stadt ein Level-Up erreicht.
     * Hier können Belohnungen, Nachrichten oder andere Aktionen implementiert werden.
     */
    private void handleTownLevelUp(Stadt stadt) {
        logger.info("Stadt " + stadt.getName() + " hat Level " + stadt.getLevel() + " erreicht!");
        // Hier können weitere Aktionen/Broadcasts/Belohnungen ergänzt werden.
    }

    private synchronized void saveGeneric(String tableName, UUID uuid, String key, Object value, String type) {
        try {
            ensureConnection();
            tableName = tableName.toLowerCase();

            // Wert serialisieren
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

    public void saveData(String tableName, UUID uuid, String key, String value) {
        saveGeneric(tableName, uuid, key, value, "string");
    }

    public void saveData(UUID uuid, String key, String value) {
        saveData("users", uuid, key, value);
    }

    public void saveInt(String tableName, UUID uuid, String key, int value) {
        saveGeneric(tableName, uuid, key, value, "int");
    }

    public void saveInt(UUID uuid, String key, int value) {
        saveInt("users", uuid, key, value);
    }

    public void saveDouble(String tableName, UUID uuid, String key, double value) {
        saveGeneric(tableName, uuid, key, value, "double");
    }

    public void saveDouble(UUID uuid, String key, double value) {
        saveDouble("users", uuid, key, value);
    }

    public void saveBoolean(String tableName, UUID uuid, String key, boolean value) {
        saveGeneric(tableName, uuid, key, value, "boolean");
    }

    public void saveBoolean(UUID uuid, String key, boolean value) {
        saveBoolean("users", uuid, key, value);
    }

    public void saveLong(String tableName, UUID uuid, String key, long value) {
        saveGeneric(tableName, uuid, key, value, "long");
    }

    public void saveLong(UUID uuid, String key, long value) {
        saveLong("users", uuid, key, value);
    }

    public void saveFloat(String tableName, UUID uuid, String key, float value) {
        saveGeneric(tableName, uuid, key, value, "float");
    }

    public void saveFloat(UUID uuid, String key, float value) {
        saveFloat("users", uuid, key, value);
    }

    public void saveStringArray(String tableName, UUID uuid, String key, String[] value) {
        saveGeneric(tableName, uuid, key, value, "string_array");
    }

    public void saveStringArray(UUID uuid, String key, String[] value) {
        saveStringArray("users", uuid, key, value);
    }

    public void saveStringList(String tableName, UUID uuid, String key, List<String> value) {
        saveGeneric(tableName, uuid, key, value, "string_list");
    }

    public void saveStringList(UUID uuid, String key, List<String> value) {
        saveStringList("users", uuid, key, value);
    }

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

    public String loadData(UUID uuid, String key) {
        return loadData("users", uuid, key);
    }

    public String loadData(UUID uuid, String key, String defaultValue) {
        String value = loadData(uuid, key);
        return value != null ? value : defaultValue;
    }

    public String loadData(String tableName, UUID uuid, String key, String defaultValue) {
        String value = loadData(tableName, uuid, key);
        return value != null ? value : defaultValue;
    }

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

    public int loadInt(UUID uuid, String key, int defaultValue) {
        return loadInt("users", uuid, key, defaultValue);
    }

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

    public double loadDouble(UUID uuid, String key, double defaultValue) {
        return loadDouble("users", uuid, key, defaultValue);
    }

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

    public boolean loadBoolean(UUID uuid, String key, boolean defaultValue) {
        return loadBoolean("users", uuid, key, defaultValue);
    }

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

    public long loadLong(UUID uuid, String key, long defaultValue) {
        return loadLong("users", uuid, key, defaultValue);
    }

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

    public float loadFloat(UUID uuid, String key, float defaultValue) {
        return loadFloat("users", uuid, key, defaultValue);
    }

    // String-Array laden
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

    public String[] loadStringArray(UUID uuid, String key) {
        return loadStringArray("users", uuid, key);
    }

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

    public List<String> loadStringList(UUID uuid, String key) {
        return loadStringList("users", uuid, key);
    }

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

    public boolean deleteData(UUID uuid, String key) {
        return deleteData("users", uuid, key);
    }

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

    private boolean wipeDataFromTable(String tableName, UUID uuid) throws SQLException {
        String sql = "DELETE FROM " + tableName.toLowerCase() + " WHERE UUID = ?";
        try (PreparedStatement pstmt = connection.prepareStatement(sql)) {
            pstmt.setString(1, uuid.toString());
            return pstmt.executeUpdate() > 0;
        }
    }

    public boolean wipeData(UUID uuid) {
        return wipeAllData(uuid);
    }

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

    public boolean hasData(UUID uuid, String key) {
        return hasData("users", uuid, key);
    }
}

package com.trynocs.tryLibs.utils.economy;

import com.trynocs.tryLibs.utils.database.DatabaseHandler;
import com.trynocs.tryLibs.TryLibs;
import java.util.UUID;

public class EconomyManager {
    private final DatabaseHandler databaseHandler;
    private final String economyTable;

    public EconomyManager(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
        this.economyTable = TryLibs.getPlugin().getConfig().getString("database.economytable", "economy");
        databaseHandler.createTable(this.economyTable);
    }

    public double getBalance(UUID uuid) {
        return databaseHandler.loadDouble(economyTable, uuid, "balance", 0.0);
    }

    public void setBalance(UUID uuid, double amount) {
        databaseHandler.saveDouble(economyTable, uuid, "balance", amount);
    }

    public void deposit(UUID uuid, double amount) {
        double current = getBalance(uuid);
        setBalance(uuid, current + amount);
    }

    public boolean withdraw(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }
}

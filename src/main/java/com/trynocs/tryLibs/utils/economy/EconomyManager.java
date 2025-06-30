package com.trynocs.tryLibs.utils.economy;

import com.trynocs.tryLibs.utils.database.DatabaseHandler;
import com.trynocs.tryLibs.TryLibs;
import java.util.UUID;

/**
 * Manages player economy using the DatabaseHandler.
 * Handles operations like getting balance, setting balance, depositing, and withdrawing.
 */
public class EconomyManager {
    private final DatabaseHandler databaseHandler;
    private final String economyTable;

    /**
     * Constructs a new EconomyManager.
     *
     * @param databaseHandler The DatabaseHandler instance to use for data storage.
     */
    public EconomyManager(DatabaseHandler databaseHandler) {
        this.databaseHandler = databaseHandler;
        this.economyTable = TryLibs.getPlugin().getConfig().getString("database.economytable", "economy");
        databaseHandler.createTable(this.economyTable);
    }

    /**
     * Gets the balance of a player.
     *
     * @param uuid The UUID of the player.
     * @return The player's balance, or 0.0 if not found.
     */
    public double getBalance(UUID uuid) {
        return databaseHandler.loadDouble(economyTable, uuid, "balance", 0.0);
    }

    /**
     * Sets the balance of a player.
     *
     * @param uuid The UUID of the player.
     * @param amount The new balance amount.
     */
    public void setBalance(UUID uuid, double amount) {
        databaseHandler.saveDouble(economyTable, uuid, "balance", amount);
    }

    /**
     * Deposits an amount into a player's balance.
     *
     * @param uuid The UUID of the player.
     * @param amount The amount to deposit.
     */
    public void deposit(UUID uuid, double amount) {
        double current = getBalance(uuid);
        setBalance(uuid, current + amount);
    }

    /**
     * Withdraws an amount from a player's balance.
     *
     * @param uuid The UUID of the player.
     * @param amount The amount to withdraw.
     * @return true if the withdrawal was successful, false if the player has insufficient funds.
     */
    public boolean withdraw(UUID uuid, double amount) {
        double current = getBalance(uuid);
        if (current < amount) return false;
        setBalance(uuid, current - amount);
        return true;
    }
}

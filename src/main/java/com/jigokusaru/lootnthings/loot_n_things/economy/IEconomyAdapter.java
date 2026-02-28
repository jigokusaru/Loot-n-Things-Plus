package com.jigokusaru.lootnthings.loot_n_things.economy;

import net.minecraft.server.level.ServerPlayer;

/**
 * An interface that defines a standard contract for interacting with different economy systems.
 * This allows for easy integration with various economy mods or command-based systems.
 * All amounts are handled as 'long' representing the smallest currency unit (e.g., cents)
 * to avoid floating-point inaccuracies.
 */
public interface IEconomyAdapter {
    /**
     * Gets the formatted name of the currency for display purposes.
     * @param amount The amount in the smallest unit (e.g., 525 for $5.25).
     * @return A formatted string (e.g., "$5.25" or "500 Coins").
     */
    String getCurrencyName(long amount);

    /**
     * Deposits money into a player's account.
     * @param player The player to give money to.
     * @param amount The amount to deposit, in the smallest currency unit.
     */
    void deposit(ServerPlayer player, long amount);

    /**
     * Withdraws money from a player's account.
     * @param player The player to take money from.
     * @param amount The amount to withdraw, in the smallest currency unit.
     * @return true if the withdrawal was successful, false otherwise.
     */
    boolean withdraw(ServerPlayer player, long amount);

    /**
     * Checks if a player has enough money.
     * @param player The player to check.
     * @param amount The amount to check for, in the smallest currency unit.
     * @return true if the player has enough money, false otherwise.
     */
    boolean hasEnough(ServerPlayer player, long amount);
}

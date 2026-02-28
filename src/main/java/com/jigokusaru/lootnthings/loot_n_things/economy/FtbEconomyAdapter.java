package com.jigokusaru.lootnthings.loot_n_things.economy;

/**
 * This file is a template for implementing native support for an economy mod.
 * To enable native support:
 * 1. Uncomment the class block below.
 * 2. Add the required economy mod API as a dependency in your build.gradle.
 * 3. Update the import statements to match the new mod's API.
 * 4. Implement the methods using the mod's API calls.
 * 5. In ModEvents.java, update setupEconomyAdapter() to check for the mod and instantiate this adapter.
 *
 * Example for a hypothetical FTB Essentials:
 *
 * import dev.ftb.mods.ftbessentials.api.FTBEssentialsAPI;
 * import net.minecraft.server.level.ServerPlayer;
 *
 * public class FtbEconomyAdapter implements IEconomyAdapter {
 *
 *     @Override
 *     public String getCurrencyName(long amount) {
 *         // Example: return FTBMoney.getMoneySymbol().getString() + amount;
 *         return "$" + String.format("%.2f", amount / 100.0);
 *     }
 *
 *     @Override
 *     public void deposit(ServerPlayer player, long amount) {
 *         // Example: FTBEssentialsAPI.getMoneyManager().deposit(player, amount, true);
 *     }
 *
 *     @Override
 *     public boolean withdraw(ServerPlayer player, long amount) {
 *         // Example: return FTBEssentialsAPI.getMoneyManager().withdraw(player, amount, true);
 *         return false;
 *     }
 *
 *     @Override
 *     public boolean hasEnough(ServerPlayer player, long amount) {
 *         // Example: return FTBEssentialsAPI.getMoneyManager().getBalance(player) >= amount;
 *         return false;
 *     }
 * }
 */
public class FtbEconomyAdapter {
    // This file is intentionally left blank to prevent build errors.
    // See the commented-out example above for implementation guidance.
}

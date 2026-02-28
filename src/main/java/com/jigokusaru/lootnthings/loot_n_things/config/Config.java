package com.jigokusaru.lootnthings.loot_n_things.config;

import net.neoforged.neoforge.common.ModConfigSpec;
import org.apache.commons.lang3.tuple.Pair;

/**
 * Handles the mod's configuration file, loot_n_things-common.toml.
 * This class defines all the available options that server admins can change.
 */
public class Config {
    public static final ModConfigSpec COMMON_SPEC;
    public static final Common COMMON;

    static {
        final Pair<Common, ModConfigSpec> specPair = new ModConfigSpec.Builder().configure(Common::new);
        COMMON_SPEC = specPair.getRight();
        COMMON = specPair.getLeft();
    }

    public static class Common {
        public final ModConfigSpec.BooleanValue commandBasedEconomyEnabled;
        public final ModConfigSpec.ConfigValue<String> currencySymbol;
        public final ModConfigSpec.BooleanValue hasDecimals;
        public final ModConfigSpec.ConfigValue<String> giveCommand;
        public final ModConfigSpec.ConfigValue<String> takeCommand;
        public final ModConfigSpec.ConfigValue<String> balanceCommand;

        public Common(ModConfigSpec.Builder builder) {
            builder.comment("Settings for integrating with a server economy via commands.").push("economy");

            commandBasedEconomyEnabled = builder
                    .comment("Set to true to enable the command-based economy system. This allows the mod to use commands for rewards and costs if no natively supported economy mod is found.")
                    .define("command_based_enabled", false);

            currencySymbol = builder
                    .comment("The symbol for the currency (e.g., $, €, etc.). Used for display purposes.")
                    .define("currency_symbol", "$");
            
            hasDecimals = builder
                    .comment("Set to true if your economy uses decimals (e.g., 5.25). If false, all amounts will be treated as whole numbers.")
                    .define("has_decimals", true);

            giveCommand = builder
                    .comment("The command used to give money to a player. [player] is replaced with the player's name, and <amount> is replaced with the currency amount.")
                    .define("give_command", "eco give [player] <amount>");

            takeCommand = builder
                    .comment("The command used to take money from a player. Placeholders are the same as give_command.")
                    .define("take_command", "eco take [player] <amount>");
            
            balanceCommand = builder
                    .comment("The command to check a player's balance. [player] is the placeholder. This command's output MUST contain a number (e.g., 'Balance: 500.25').")
                    .define("balance_command", "bal [player]");

            builder.pop();
        }
    }
}

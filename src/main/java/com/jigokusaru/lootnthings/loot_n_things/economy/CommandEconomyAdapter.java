package com.jigokusaru.lootnthings.loot_n_things.economy;

import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import net.minecraft.commands.CommandSource;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;

import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * An economy adapter that works by executing server commands defined in the config.
 * This provides a universal fallback for any economy plugin that has command support.
 */
public class CommandEconomyAdapter implements IEconomyAdapter {

    // This pattern finds the first number (integer or decimal) in a string.
    private static final Pattern NUMBER_PATTERN = Pattern.compile("([0-9]+(?:\\.[0-9]+)?)");

    @Override
    public String getCurrencyName(long amount) {
        if (Config.COMMON.hasDecimals.get()) {
            // If decimals are enabled, divide by 100.0 to format correctly (e.g., 525 -> "$5.25")
            return Config.COMMON.currencySymbol.get() + String.format("%.2f", amount / 100.0);
        }
        return Config.COMMON.currencySymbol.get() + amount;
    }

    @Override
    public void deposit(ServerPlayer player, long amount) {
        String amountStr = Config.COMMON.hasDecimals.get() ? String.format("%.2f", amount / 100.0) : String.valueOf(amount);
        String command = Config.COMMON.giveCommand.get()
                .replace("[player]", player.getScoreboardName())
                .replace("<amount>", amountStr);
        
        executeCommand(player, command);
    }

    @Override
    public boolean withdraw(ServerPlayer player, long amount) {
        if (hasEnough(player, amount)) {
            String amountStr = Config.COMMON.hasDecimals.get() ? String.format("%.2f", amount / 100.0) : String.valueOf(amount);
            String command = Config.COMMON.takeCommand.get()
                    .replace("[player]", player.getScoreboardName())
                    .replace("<amount>", amountStr);
            
            executeCommand(player, command);
            return true;
        }
        return false;
    }

    @Override
    public boolean hasEnough(ServerPlayer player, long amount) {
        String command = Config.COMMON.balanceCommand.get()
                .replace("[player]", player.getScoreboardName());
        
        // If no balance command is configured, we can't check, so we optimistically assume they have enough.
        if (command.isBlank()) return true;

        final AtomicLong balance = new AtomicLong(-1);

        // Create a custom command source to capture the output of the balance command.
        CommandSourceStack source = player.getServer().createCommandSourceStack()
                .withSuppressedOutput()
                .withSource(new CommandSource() {
                    @Override
                    public void sendSystemMessage(Component component) {
                        Matcher matcher = NUMBER_PATTERN.matcher(component.getString());
                        if (matcher.find()) {
                            try {
                                if (Config.COMMON.hasDecimals.get()) {
                                    // Parse as a double and multiply by 100 to get cents.
                                    double value = Double.parseDouble(matcher.group(1));
                                    balance.set((long) (value * 100));
                                } else {
                                    // Parse as a whole number, ignoring any decimal part.
                                    long value = Long.parseLong(matcher.group(1).split("\\.")[0]);
                                    balance.set(value);
                                }
                            } catch (NumberFormatException e) {
                                // Ignore if parsing fails. Balance will remain -1.
                            }
                        }
                    }
                    @Override public boolean acceptsSuccess() { return true; }
                    @Override public boolean acceptsFailure() { return true; }
                    @Override public boolean shouldInformAdmins() { return false; }
                });

        executeCommand(player, command, source);

        return balance.get() >= amount;
    }

    private void executeCommand(ServerPlayer player, String command) {
        executeCommand(player, command, player.getServer().createCommandSourceStack().withSuppressedOutput());
    }

    private void executeCommand(ServerPlayer player, String command, CommandSourceStack source) {
        player.getServer().getCommands().performPrefixedCommand(source, command);
    }
}

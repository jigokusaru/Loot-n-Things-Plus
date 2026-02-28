package com.jigokusaru.lootnthings.loot_n_things.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;

import javax.annotation.Nullable;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A utility class for resolving placeholders and variables in strings from loot table JSONs.
 */
public class LootResolver {
    private static final Random RANDOM = new Random();
    private static final Map<String, String> COLOR_MAP = new LinkedHashMap<>();

    static {
        // Pre-populate a map of simple color/formatting codes for easy use in JSONs.
        COLOR_MAP.put("[black]", "§0"); COLOR_MAP.put("[dark_blue]", "§1");
        COLOR_MAP.put("[dark_green]", "§2"); COLOR_MAP.put("[dark_aqua]", "§3");
        COLOR_MAP.put("[dark_red]", "§4"); COLOR_MAP.put("[dark_purple]", "§5");
        COLOR_MAP.put("[gold]", "§6"); COLOR_MAP.put("[gray]", "§7");
        COLOR_MAP.put("[dark_gray]", "§8"); COLOR_MAP.put("[blue]", "§9");
        COLOR_MAP.put("[green]", "§a"); COLOR_MAP.put("[aqua]", "§b");
        COLOR_MAP.put("[red]", "§c"); COLOR_MAP.put("[light_purple]", "§d");
        COLOR_MAP.put("[yellow]", "§e"); COLOR_MAP.put("[white]", "§f");
        COLOR_MAP.put("[obfuscated]", "§k"); COLOR_MAP.put("[bold]", "§l");
        COLOR_MAP.put("[strikethrough]", "§m"); COLOR_MAP.put("[underline]", "§n");
        COLOR_MAP.put("[italic]", "§o"); COLOR_MAP.put("[reset]", "§r");
    }

    public static String applyPlaceholders(String text, @Nullable Player player, @Nullable JsonObject rootJson) {
        return applyPlaceholders(text, player, rootJson, null, null, null);
    }

    /**
     * The main method for applying all placeholders to a given string.
     * @param text The input string with placeholders.
     * @param player The player context, for player-specific placeholders like [player] or [xp_level].
     * @param rootJson The root JSON object of the loot table, for accessing global 'vars'.
     * @param entryJson The specific loot entry being processed, for accessing local 'vars'.
     * @param resolvedVars A pre-resolved map of variables. If provided, this is used instead of re-rolling variables.
     * @param tierName The clean name of the loot tier, for the [tier] placeholder.
     * @return The string with all placeholders replaced.
     */
    public static String applyPlaceholders(String text, @Nullable Player player, @Nullable JsonObject rootJson, @Nullable JsonObject entryJson, @Nullable Map<String, String> resolvedVars, @Nullable String tierName) {
        String p = text;

        // Apply variables first, using pre-resolved ones if available.
        if (resolvedVars != null) {
            for (Map.Entry<String, String> entry : resolvedVars.entrySet()) {
                p = p.replace("<" + entry.getKey() + ">", entry.getValue());
            }
        } else {
            // If no pre-resolved vars, roll them now. Local vars take precedence over global.
            if (entryJson != null && entryJson.has("vars")) {
                p = applyVars(p, entryJson.getAsJsonObject("vars"));
            }
            if (rootJson != null && rootJson.has("vars")) {
                p = applyVars(p, rootJson.getAsJsonObject("vars"));
            }
        }

        // Apply player-specific placeholders
        if (player != null) {
            p = p.replace("[player]", player.getScoreboardName())
                    .replace("[x]", String.valueOf(player.getBlockX()))
                    .replace("[y]", String.valueOf(player.getBlockY()))
                    .replace("[z]", String.valueOf(player.getBlockZ()))
                    .replace("[xp_level]", String.valueOf(player.experienceLevel));
            
            // Handle [score.objective_name] placeholders
            Matcher scoreMatcher = Pattern.compile("\\[score\\.(\\w+)\\]").matcher(p);
            if (scoreMatcher.find()) {
                StringBuilder scoreSb = new StringBuilder();
                do {
                    String objectiveName = scoreMatcher.group(1);
                    Scoreboard scoreboard = player.level().getScoreboard();
                    Objective objective = scoreboard.getObjective(objectiveName);
                    if (objective != null) {
                        ScoreAccess scoreAccess = scoreboard.getOrCreatePlayerScore(player, objective);
                        scoreMatcher.appendReplacement(scoreSb, String.valueOf(scoreAccess.get()));
                    } else {
                        scoreMatcher.appendReplacement(scoreSb, "0");
                    }
                } while (scoreMatcher.find());
                scoreMatcher.appendTail(scoreSb);
                p = scoreSb.toString();
            }
        }
        
        // Apply the [tier] placeholder
        if (tierName != null) {
            p = p.replace("[tier]", tierName.replace("chests/", "").replace("bags/", ""));
        }

        // Apply simple math placeholders like [10+5]
        Matcher mathMatcher = Pattern.compile("\\[([0-9]+(?:\\.[0-9]+)?)\\s*([+\\-*/])\\s*([0-9]+(?:\\.[0-9]+)?)\\]").matcher(p);
        if (mathMatcher.find()) {
            StringBuilder mathSb = new StringBuilder();
            do {
                try {
                    double n1 = Double.parseDouble(mathMatcher.group(1));
                    String op = mathMatcher.group(2);
                    double n2 = Double.parseDouble(mathMatcher.group(3));
                    double result = switch (op) {
                        case "+" -> n1 + n2;
                        case "-" -> n1 - n2;
                        case "*" -> n1 * n2;
                        case "/" -> n1 / n2;
                        default -> 0;
                    };
                    mathMatcher.appendReplacement(mathSb, String.valueOf(result));
                } catch (Exception e) {
                    // Ignore math parse errors, leaving the original text.
                }
            } while (mathMatcher.find());
            mathMatcher.appendTail(mathSb);
p = mathSb.toString();
        }

        // Apply simple color codes like [gold]
        for (Map.Entry<String, String> en : COLOR_MAP.entrySet()) {
            p = p.replace(en.getKey(), en.getValue());
        }

        // Apply hex color codes like [#FFA500]
        Matcher m = Pattern.compile("\\[#([A-Fa-f0-9]{6})]").matcher(p);
        if (m.find()) {
            StringBuilder sb = new StringBuilder();
            do {
                String h = m.group(1);
                StringBuilder mc = new StringBuilder("§x");
                for (char c : h.toCharArray()) {
                    mc.append("§").append(c);
                }
                m.appendReplacement(sb, mc.toString());
            } while (m.find());
            m.appendTail(sb);
            p = sb.toString();
        }
        return p;
    }

    /**
     * Resolves all variables for a given loot entry, creating a map of variable names to their rolled values.
     */
    public static Map<String, String> resolveVariables(@Nullable JsonObject entry, @Nullable JsonObject rootJson) {
        Map<String, String> resolved = new HashMap<>();
        if (entry != null && entry.has("vars")) {
            JsonObject vars = entry.getAsJsonObject("vars");
            for (Map.Entry<String, JsonElement> v : vars.entrySet()) {
                resolved.put(v.getKey(), rollVariableValue(v.getValue()));
            }
        }
        if (rootJson != null && rootJson.has("vars")) {
            JsonObject vars = rootJson.getAsJsonObject("vars");
            for (Map.Entry<String, JsonElement> v : vars.entrySet()) {
                resolved.putIfAbsent(v.getKey(), rollVariableValue(v.getValue()));
            }
        }
        return resolved;
    }

    /**
     * Applies variables to a string on-the-fly. Used when pre-resolved variables are not available.
     */
    private static String applyVars(String text, JsonObject vars) {
        String p = text;
        for (Map.Entry<String, JsonElement> entry : vars.entrySet()) {
            String varName = "<" + entry.getKey() + ">";
            if (p.contains(varName)) {
                String selectedValue = rollVariableValue(entry.getValue());
                p = p.replace(varName, selectedValue);
            }
        }
        return p;
    }

    /**
     * Rolls a single variable, which can be a simple value, a min/max range, or a weighted list of options.
     */
    private static String rollVariableValue(JsonElement varElement) {
        if (varElement.isJsonPrimitive()) return varElement.getAsString();
        if (varElement.isJsonObject()) {
            JsonObject varData = varElement.getAsJsonObject();
            if (varData.has("min") && varData.has("max")) {
                int min = varData.get("min").getAsInt();
                int max = varData.get("max").getAsInt();
                return String.valueOf(RANDOM.nextInt(min, max + 1));
            }
            if (varData.has("value") && !varData.has("weight")) return varData.get("value").getAsString();
        }
        JsonArray options;
        if (varElement.isJsonArray()) options = varElement.getAsJsonArray();
        else if (varElement.isJsonObject() && varElement.getAsJsonObject().has("options")) options = varElement.getAsJsonObject().getAsJsonArray("options");
        else {
            if (varElement.isJsonObject() && varElement.getAsJsonObject().has("value")) return varElement.getAsJsonObject().get("value").getAsString();
            return "";
        }
        int totalWeight = 0;
        for (JsonElement e : options) {
            if (e.isJsonObject() && e.getAsJsonObject().has("weight")) totalWeight += e.getAsJsonObject().get("weight").getAsInt();
            else totalWeight += 1;
        }
        if (totalWeight <= 0) return "";
        int roll = RANDOM.nextInt(totalWeight);
        int current = 0;
        for (JsonElement e : options) {
            JsonObject opt = e.getAsJsonObject();
            int w = opt.has("weight") ? opt.get("weight").getAsInt() : 1;
            current += w;
            if (roll < current) return opt.get("value").getAsString();
        }
        return "";
    }

    /**
     * Rolls a value from a weighted range object (e.g., {"1": 50, "2": 30, "3": 20}).
     */
    public static int rollWeightedRange(JsonObject range) {
        int tw = 0;
        for (Map.Entry<String, JsonElement> en : range.entrySet()) tw += en.getValue().getAsInt();
        if (tw <= 0) return 1;
        int roll = RANDOM.nextInt(tw);
        int cur = 0;
        for (Map.Entry<String, JsonElement> en : range.entrySet()) {
            cur += en.getValue().getAsInt();
            if (roll < cur) return Integer.parseInt(en.getKey());
        }
        return 1;
    }

    /**
     * Gets the display name for a reward entry, used in chat messages and previews.
     */
    public static String getRewardSummaryName(JsonObject entry, long count, @Nullable JsonObject rootJson, @Nullable Map<String, String> vars) {
        String type = entry.get("type").getAsString();
        String name;
        if (type.equals("item")) {
            String id = entry.get("id").getAsString();
            id = applyPlaceholders(id, null, rootJson, entry, vars, null);
            name = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).getDescription().getString();
        } else if (type.equals("nothing")) {
            name = entry.has("message") ? entry.get("message").getAsString() : "Nothing";
        } else if (type.equals("economy")) {
            if (Loot_n_things.economy != null) {
                long amount = count;
                if (Config.COMMON.hasDecimals.get()) {
                    amount = count * 100;
                }
                return Loot_n_things.economy.getCurrencyName(amount);
            } else {
                return count + " (Economy Mod Not Found)";
            }
        } else {
            name = entry.has("display_name") ? entry.get("display_name").getAsString() : "Reward";
        }
        return (int)count + " " + applyPlaceholders(name, null, rootJson, entry, vars, null);
    }
}

package com.jigokusaru.lootnthings.loot_n_things.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ScoreAccess;
import net.minecraft.world.scores.Scoreboard;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LootResolver {
    private static final Random RANDOM = new Random();
    private static final Pattern FORMATTING_PATTERN = Pattern.compile("(?i)\\[(#[A-Fa-f0-9]{6}|[A-Z_]+)\\]");

    public static MutableComponent resolveComponent(String text, @Nullable Player player, @Nullable JsonObject rootJson, @Nullable JsonObject entryJson, @Nullable Map<String, String> resolvedVars, @Nullable String tierName) {
        String processedText = applyPlaceholders(text, player, rootJson, entryJson, resolvedVars, tierName);
        
        MutableComponent baseComponent = Component.empty();
        Matcher matcher = FORMATTING_PATTERN.matcher(processedText);
        int lastEnd = 0;
        Style currentStyle = Style.EMPTY;

        while (matcher.find()) {
            if (matcher.start() > lastEnd) {
                baseComponent.append(Component.literal(processedText.substring(lastEnd, matcher.start())).withStyle(currentStyle));
            }

            String tag = matcher.group(1).toUpperCase();
            if (tag.startsWith("#")) {
                Optional<TextColor> textColor = TextColor.parseColor(tag).result();
                currentStyle = currentStyle.withColor(textColor.orElse(null));
            } else {
                currentStyle = switch (tag) {
                    case "BLACK" -> currentStyle.withColor(ChatFormatting.BLACK);
                    case "DARK_BLUE" -> currentStyle.withColor(ChatFormatting.DARK_BLUE);
                    case "DARK_GREEN" -> currentStyle.withColor(ChatFormatting.DARK_GREEN);
                    case "DARK_AQUA" -> currentStyle.withColor(ChatFormatting.DARK_AQUA);
                    case "DARK_RED" -> currentStyle.withColor(ChatFormatting.DARK_RED);
                    case "DARK_PURPLE" -> currentStyle.withColor(ChatFormatting.DARK_PURPLE);
                    case "GOLD" -> currentStyle.withColor(ChatFormatting.GOLD);
                    case "GRAY" -> currentStyle.withColor(ChatFormatting.GRAY);
                    case "DARK_GRAY" -> currentStyle.withColor(ChatFormatting.DARK_GRAY);
                    case "BLUE" -> currentStyle.withColor(ChatFormatting.BLUE);
                    case "GREEN" -> currentStyle.withColor(ChatFormatting.GREEN);
                    case "AQUA" -> currentStyle.withColor(ChatFormatting.AQUA);
                    case "RED" -> currentStyle.withColor(ChatFormatting.RED);
                    case "LIGHT_PURPLE" -> currentStyle.withColor(ChatFormatting.LIGHT_PURPLE);
                    case "YELLOW" -> currentStyle.withColor(ChatFormatting.YELLOW);
                    case "WHITE" -> currentStyle.withColor(ChatFormatting.WHITE);
                    case "BOLD" -> currentStyle.withBold(true);
                    case "ITALIC" -> currentStyle.withItalic(true);
                    case "UNDERLINE" -> currentStyle.withUnderlined(true);
                    case "STRIKETHROUGH" -> currentStyle.withStrikethrough(true);
                    case "OBFUSCATED" -> currentStyle.withObfuscated(true);
                    case "RESET" -> Style.EMPTY;
                    default -> currentStyle;
                };
            }
            lastEnd = matcher.end();
        }

        if (lastEnd < processedText.length()) {
            baseComponent.append(Component.literal(processedText.substring(lastEnd)).withStyle(currentStyle));
        }

        return baseComponent;
    }

    public static String applyPlaceholders(String text, @Nullable Player player, @Nullable JsonObject rootJson, @Nullable JsonObject entryJson, @Nullable Map<String, String> resolvedVars, @Nullable String tierName) {
        String p = text;

        if (resolvedVars != null) {
            for (Map.Entry<String, String> entry : resolvedVars.entrySet()) {
                p = p.replace("<" + entry.getKey() + ">", entry.getValue());
            }
        } else {
            if (entryJson != null && entryJson.has("vars")) {
                p = applyVars(p, entryJson.getAsJsonObject("vars"));
            }
            if (rootJson != null && rootJson.has("vars")) {
                p = applyVars(p, rootJson.getAsJsonObject("vars"));
            }
        }

        if (player != null) {
            p = p.replace("[player]", player.getScoreboardName())
                    .replace("[x]", String.valueOf(player.getBlockX()))
                    .replace("[y]", String.valueOf(player.getBlockY()))
                    .replace("[z]", String.valueOf(player.getBlockZ()))
                    .replace("[xp_level]", String.valueOf(player.experienceLevel));
            
            Matcher scoreMatcher = Pattern.compile("\\[score\\.(\\w+)\\]").matcher(p);
            if (scoreMatcher.find()) {
                StringBuffer scoreSb = new StringBuffer();
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
        
        if (tierName != null) {
            p = p.replace("[tier]", tierName.replace("chests/", "").replace("bags/", ""));
        }

        Matcher mathMatcher = Pattern.compile("\\[([0-9]+(?:\\.[0-9]+)?)\\s*([+\\-*/])\\s*([0-9]+(?:\\.[0-9]+)?)\\]").matcher(p);
        if (mathMatcher.find()) {
            StringBuffer mathSb = new StringBuffer();
            do {
                try {
                    double n1 = Double.parseDouble(mathMatcher.group(1));
                    String op = mathMatcher.group(2);
                    double n2 = Double.parseDouble(mathMatcher.group(3));
                    double result = switch (op) {
                        case "+" -> n1 + n2;
                        case "-" -> n1 - n2;
                        case "*" -> n1 * n2;
                        case "/" -> n2 != 0 ? n1 / n2 : 0;
                        default -> 0;
                    };
                    mathMatcher.appendReplacement(mathSb, String.valueOf(result));
                } catch (Exception e) {
                    // Ignore
                }
            } while (mathMatcher.find());
            mathMatcher.appendTail(mathSb);
            p = mathSb.toString();
        }
        
        return p;
    }

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

    public static String getRewardSummaryName(JsonObject entry, long count, @Nullable JsonObject rootJson, @Nullable Map<String, String> vars) {
        String type = entry.get("type").getAsString();
        String name;
        switch (type) {
            case "item" -> {
                String id = entry.get("id").getAsString();
                id = applyPlaceholders(id, null, rootJson, entry, vars, null);
                name = BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)).getDescription().getString();
            }
            case "nothing" -> name = entry.has("message") ? entry.get("message").getAsString() : "Nothing";
            case "economy" -> {
                if (Loot_n_things.economy != null) {
                    long amount = count;
                    if (Config.COMMON.hasDecimals.get()) {
                        amount = count * 100;
                    }
                    return Loot_n_things.economy.getCurrencyName(amount);
                } else {
                    return count + " (Economy Mod Not Found)";
                }
            }
            default -> name = entry.has("display_name") ? entry.get("display_name").getAsString() : "Reward";
        }
        return (int)count + " " + applyPlaceholders(name, null, rootJson, entry, vars, null);
    }
}

package com.jigokusaru.lootnthings.loot_n_things.core;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import com.jigokusaru.lootnthings.loot_n_things.config.LootConfigManager;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModComponents;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.SimpleMenuProvider;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.ChestMenu;
import net.minecraft.world.inventory.ClickType;
import net.minecraft.world.inventory.MenuType;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.IntStream;

public class LootLibrary {
    private static final Map<UUID, LootSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<String, List<Integer>> GLOBAL_DECKS = new ConcurrentHashMap<>();
    private static final String PITY_TAG = "lnt_pity";

    public static void onMenuClose(PlayerContainerEvent.Close event) {
        LootSession session = SESSIONS.remove(event.getEntity().getUUID());
        if (session != null && !session.isFinished()) {
            session.forceGrantRewards();
        }
    }

    public static void onServerTick(ServerTickEvent.Post event) {
        SESSIONS.values().removeIf(LootSession::tick);
    }

    public static void initConfig() {
        LootConfigManager.initConfig();
    }

    public static boolean reload() {
        GLOBAL_DECKS.clear();
        return LootConfigManager.reload();
    }

    public static JsonObject getLootFile(String name) {
        return LootConfigManager.getLootFile(name);
    }

    public static ItemStack createBagFromTier(String tier) {
        String fullPath = tier.startsWith("bags/") ? tier : "bags/" + tier;
        JsonObject json = LootConfigManager.getLootFile(fullPath);
        if (json == null) return ItemStack.EMPTY;

        String iconId = json.has("icon") ? json.get("icon").getAsString() : "minecraft:bundle";
        Item iconItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(iconId));
        ItemStack bag = new ItemStack(iconItem);

        if (json.has("model_id")) {
            bag.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(json.get("model_id").getAsInt()));
        }

        bag.set(ModComponents.LNT_BAG_TIER.get(), fullPath);

        String displayName = json.has("display_name") ? json.get("display_name").getAsString() : tier + " Loot Bag";
        bag.set(DataComponents.CUSTOM_NAME, LootResolver.resolveComponent(displayName, null, json, null, null, tier));

        return bag;
    }

    public static void openLootPreview(String tier, Player player) {
        JsonObject json = LootConfigManager.getLootFile(tier);
        if (json == null || !json.has("loot")) return;
        JsonArray pool = json.getAsJsonArray("loot");
        int totalWeight = 0;
        for (JsonElement e : pool) totalWeight += e.getAsJsonObject().get("weight").getAsInt();

        SimpleContainer container = new SimpleContainer(54);
        for (int i = 0; i < Math.min(pool.size(), 54); i++) {
            container.setItem(i, createPreviewIcon(pool.get(i).getAsJsonObject(), totalWeight, json));
        }

        String title = json.has("display_name") ? json.get("display_name").getAsString() : "[gold]" + tier.toUpperCase() + " Preview";

        if (player.level() instanceof ServerLevel serverLevel) {
            serverLevel.getServer().execute(() -> player.openMenu(new SimpleMenuProvider((id, inv, p) ->
                    new ChestMenu(MenuType.GENERIC_9x6, id, inv, container, 6) {
                        @Override
                        public void clicked(int slotId, int button, ClickType clickType, Player playerEntity) {
                            if (slotId < 54 && slotId >= 0) return;
                            super.clicked(slotId, button, clickType, playerEntity);
                        }
                        @Override
                        public boolean stillValid(Player p_40220_) { return true; }
                    },
                    LootResolver.resolveComponent(title, player, json, null, null, tier))));
        }
    }
    
    public static boolean canOpen(String tier, Player player, JsonObject json) {
        if (json.has("cooldown")) {
            long cooldown = json.get("cooldown").getAsLong() * 1000;
            long lastUsed = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).getOrDefault(tier, 0L);
            if (System.currentTimeMillis() - lastUsed < cooldown) {
                long remaining = (cooldown - (System.currentTimeMillis() - lastUsed)) / 1000;
                player.displayClientMessage(LootResolver.resolveComponent("§cYou must wait " + remaining + " seconds to use this again.", player, json, null, null, tier), true);
                return false;
            }
        }

        if (json.has("cost")) {
            JsonObject cost = json.getAsJsonObject("cost");
            String type = cost.get("type").getAsString();
            
            switch (type) {
                case "xp" -> {
                    int amount = cost.get("amount").getAsInt();
                    if (player.experienceLevel < amount) {
                        player.displayClientMessage(LootResolver.resolveComponent("§cYou need " + amount + " XP levels to open this.", player, json, null, null, tier), true);
                        return false;
                    }
                }
                case "item" -> {
                    int amount = cost.get("amount").getAsInt();
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.get("id").getAsString()));
                    if (player.getInventory().countItem(item) < amount) {
                        player.displayClientMessage(LootResolver.resolveComponent("§cYou need " + amount + " " + item.getDescription().getString() + " to open this.", player, json, null, null, tier), true);
                        return false;
                    }
                }
                case "economy" -> {
                    if (Loot_n_things.economy == null) {
                        Loot_n_things.LOGGER.warn("Economy cost defined for tier '{}', but no economy system is enabled!", tier);
                        return false; // Fail closed if economy isn't running
                    }
                    double amount = cost.get("amount").getAsDouble();
                    long longAmount = (long) (Config.COMMON.hasDecimals.get() ? amount * 100 : amount);
                    if (!Loot_n_things.economy.hasEnough((ServerPlayer) player, longAmount)) {
                        player.displayClientMessage(LootResolver.resolveComponent("§cYou do not have enough money! You need " + Loot_n_things.economy.getCurrencyName(longAmount), player, json, null, null, tier), true);
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public static void openLootSpinner(String tier, Player player, ServerLevel level) {
        JsonObject json = LootConfigManager.getLootFile(tier);
        if (json == null || !json.has("loot")) return;
        
        if (json.has("cost")) {
            JsonObject cost = json.getAsJsonObject("cost");
            String type = cost.get("type").getAsString();
            
            switch (type) {
                case "xp" -> player.giveExperienceLevels(-cost.get("amount").getAsInt());
                case "item" -> {
                    Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.get("id").getAsString()));
                    player.getInventory().clearOrCountMatchingItems(p -> p.getItem() == item, cost.get("amount").getAsInt(), player.inventoryMenu.getCraftSlots());
                }
                case "economy" -> {
                    if (Loot_n_things.economy != null) {
                        double amount = cost.get("amount").getAsDouble();
                        long longAmount = (long) (Config.COMMON.hasDecimals.get() ? amount * 100 : amount);
                        Loot_n_things.economy.withdraw((ServerPlayer) player, longAmount);
                    }
                }
            }
        }
        if (json.has("cooldown")) {
            COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).put(tier, System.currentTimeMillis());
        }
        
        SESSIONS.remove(player.getUUID());

        int spins = Math.min(json.has("spins") ? json.get("spins").getAsInt() : 1, 5);
        
        JsonArray lootArray = json.getAsJsonArray("loot");
        List<JsonObject> pool = new ArrayList<>();
        for (JsonElement e : lootArray) pool.add(e.getAsJsonObject());

        List<JsonObject> winners = new ArrayList<>();
        List<JsonObject> rollPool;
        String weightField = "weight";
        int actualSpins = spins;
        
        boolean isPitySpin = false;
        CompoundTag pityData = player.getPersistentData().getCompound(PITY_TAG);
        int currentPity = pityData.getInt(tier);

        if (json.has("pity_after") && currentPity >= json.get("pity_after").getAsInt()) {
            isPitySpin = true;
            pityData.putInt(tier, 0);
            player.sendSystemMessage(LootResolver.resolveComponent("§d§lPity roll activated!", player, json, null, null, tier));
        }

        if (isPitySpin) {
            weightField = "pity_weight";
            final String finalWeightField = weightField;
            rollPool = pool.stream().filter(e -> e.has(finalWeightField)).toList();
            if (rollPool.isEmpty()) {
                rollPool = pool; // Fallback
                weightField = "weight";
            }
            
            actualSpins = json.has("pity_spins") ? json.get("pity_spins").getAsInt() : spins;
            boolean pityUnique = json.has("pity_unique") && json.get("pity_unique").getAsBoolean();
            
            if (pityUnique) {
                List<JsonObject> tempPityPool = new ArrayList<>(rollPool);
                for (int i = 0; i < actualSpins; i++) {
                    if (tempPityPool.isEmpty()) {
                        tempPityPool.addAll(rollPool); // Reshuffle if we run out
                    }
                    JsonObject winner = rollWinner(tempPityPool, weightField);
                    winners.add(winner);
                    tempPityPool.remove(winner);
                }
            } else {
                for (int i = 0; i < actualSpins; i++) {
                    winners.add(rollWinner(rollPool, weightField)); // Draw with replacement
                }
            }
        } else {
            rollPool = pool;
            boolean useDeck = json.has("deck") && json.get("deck").getAsBoolean();
            if (useDeck) {
                List<Integer> deck = GLOBAL_DECKS.computeIfAbsent(tier, k -> 
                    new ArrayList<>(IntStream.range(0, pool.size()).boxed().toList())
                );
                
                if (deck.size() < spins) {
                    deck.clear();
                    deck.addAll(IntStream.range(0, pool.size()).boxed().toList());
                    if (json.has("broadcast") && json.getAsJsonObject("broadcast").has("shuffle")) {
                        String shuffleMsg = json.getAsJsonObject("broadcast").get("shuffle").getAsString();
                        level.getServer().getPlayerList().broadcastSystemMessage(LootResolver.resolveComponent(shuffleMsg, null, json, null, null, tier), false);
                    }
                }
                
                Collections.shuffle(deck);
                
                for (int i = 0; i < spins; i++) {
                    if (deck.isEmpty()) break;
                    int poolIndex = deck.removeFirst();
                    JsonObject winner = pool.get(poolIndex);
                    winners.add(winner);
                    if (winner.has("always") && winner.get("always").getAsBoolean()) {
                        deck.add(poolIndex); // Add it back to be drawn again
                    }
                }
            } else {
                List<JsonObject> currentPool = new ArrayList<>(rollPool);
                boolean uniqueRolls = json.has("unique_rolls") && json.get("unique_rolls").getAsBoolean();
                for (int i = 0; i < spins; i++) {
                    if (currentPool.isEmpty()) break;
                    JsonObject win = rollWinner(currentPool, weightField);
                    winners.add(win);
                    if (uniqueRolls && win.has("group")) {
                        String group = win.get("group").getAsString();
                        currentPool.removeIf(e -> e.has("group") && e.get("group").getAsString().equals(group));
                    } else {
                        currentPool.remove(win);
                    }
                }
            }
            
            boolean wonPityItem = false;
            for (JsonObject winner : winners) {
                if (winner.has("pity_weight")) {
                    wonPityItem = true;
                    break;
                }
            }
            if (wonPityItem) {
                pityData.putInt(tier, 0); // Reset on lucky win
            } else {
                pityData.putInt(tier, currentPity + 1); // Increment on normal win
            }
        }
        player.getPersistentData().put(PITY_TAG, pityData);

        int totalRows = actualSpins + 1;
        MenuType<ChestMenu> menuType = getMenuType(totalRows);
        SimpleContainer container = new SimpleContainer(54);

        List<Integer> winningAmounts = new ArrayList<>();
        List<Map<String, String>> winnerVars = new ArrayList<>();
        boolean hasRealWin = false;
        for (JsonObject winner : winners) {
            Map<String, String> resolvedVars = LootResolver.resolveVariables(winner, json);
            winnerVars.add(resolvedVars);
            
            if (!winner.get("type").getAsString().equals("nothing")) hasRealWin = true;
            
            int amount = 1;
            if (winner.has("count")) {
                JsonElement ce = winner.get("count");
                if (ce.isJsonObject()) {
                    amount = LootResolver.rollWeightedRange(ce.getAsJsonObject());
                } else {
                    String countStr = LootResolver.applyPlaceholders(ce.getAsString(), player, json, winner, resolvedVars, tier);
                    try {
                        amount = Integer.parseInt(countStr);
                    } catch (NumberFormatException e) {
                        Loot_n_things.LOGGER.error("Failed to parse count '{}' for tier '{}'", countStr, tier, e);
                        amount = 1;
                    }
                }
            }
            winningAmounts.add(amount);
        }

        ItemStack casing = new ItemStack(net.minecraft.world.item.Items.BLACK_STAINED_GLASS_PANE);
        casing.set(DataComponents.CUSTOM_NAME, Component.literal(" "));
        for (int i = 0; i < 9; i++) container.setItem(i, casing);

        ItemStack hopper = new ItemStack(net.minecraft.world.item.Items.HOPPER);
        hopper.set(DataComponents.CUSTOM_NAME, Component.literal("§eWinner Below"));
        container.setItem(4, hopper);

        final List<JsonObject> finalRollPool = rollPool;
        final String finalWeightField = weightField;
        for (int s = 0; s < actualSpins; s++) {
            int rowStart = (s + 1) * 9;
            for (int col = 0; col < 9; col++) {
                JsonObject randomEntry = rollWinner(finalRollPool, finalWeightField);
                Map<String, String> randomVars = LootResolver.resolveVariables(randomEntry, json);
                container.setItem(rowStart + col, LootSession.createIcon(randomEntry, json, randomVars));
            }
        }

        String title = json.has("display_name") ? json.get("display_name").getAsString() : "[gold]" + tier.toUpperCase() + " Spinner";

        final int finalTotalRows = totalRows;
        SimpleMenuProvider menuProvider = new SimpleMenuProvider((id, inv, p) ->
                new ChestMenu(menuType, id, inv, container, finalTotalRows) {
                    @Override
                    public void clicked(int slotId, int button, ClickType clickType, Player playerEntity) {
                        if (slotId < (finalTotalRows * 9) && slotId >= 0) return;
                        super.clicked(slotId, button, clickType, playerEntity);
                    }
                    @Override
                    public boolean stillValid(Player p_40220_) { return true; }
                },
                LootResolver.resolveComponent(title, player, json, null, null, tier));

        final boolean finalHasRealWin = hasRealWin;
        final int finalActualSpins = actualSpins;

        level.getServer().execute(() -> {
            player.openMenu(menuProvider);
            container.startOpen(player);
            
            SESSIONS.put(player.getUUID(), new LootSession(
                player, level, container, json, finalRollPool, winners, winningAmounts, winnerVars, finalHasRealWin, finalActualSpins, finalTotalRows, casing, hopper, tier
            ));
        });
    }

    private static ItemStack createPreviewIcon(JsonObject entry, int totalWeight, @Nullable JsonObject rootJson) {
        ItemStack stack = LootSession.createIcon(entry, rootJson, null);
        
        int weight = entry.get("weight").getAsInt();
        double percent = (double) weight / totalWeight * 100.0;
        List<Component> lore = new ArrayList<>();
        lore.add(LootResolver.resolveComponent(String.format("§7Chance: §6%.2f%%", percent), null, rootJson, entry, null, null));
        
        if (entry.has("count")) {
            JsonElement ce = entry.get("count");
            if (ce.isJsonObject()) {
                JsonObject range = ce.getAsJsonObject();
                List<Integer> keys = range.keySet().stream().map(Integer::parseInt).sorted().toList();
                if (!keys.isEmpty()) lore.add(LootResolver.resolveComponent("§7Amount: §e" + keys.getFirst() + " - " + keys.getLast(), null, rootJson, entry, null, null));
            } else if (ce.isJsonPrimitive() && ce.getAsJsonPrimitive().isString()) {
                String varName = ce.getAsString().replace("<", "").replace(">", "");
                if (rootJson != null && rootJson.has("vars") && rootJson.getAsJsonObject("vars").has(varName)) {
                    JsonElement varData = rootJson.getAsJsonObject("vars").get(varName);
                    if (varData.isJsonObject() && varData.getAsJsonObject().has("min") && varData.getAsJsonObject().has("max")) {
                        lore.add(LootResolver.resolveComponent("§7Amount: §e" + varData.getAsJsonObject().get("min").getAsInt() + " - " + varData.getAsJsonObject().get("max").getAsInt(), null, rootJson, entry, null, null));
                    } else if (varData.isJsonArray()) {
                        List<String> amounts = new ArrayList<>();
                        for (JsonElement e : varData.getAsJsonArray()) {
                            if (e.isJsonObject() && e.getAsJsonObject().has("value")) {
                                amounts.add(e.getAsJsonObject().get("value").getAsString());
                            }
                        }
                        lore.add(LootResolver.resolveComponent("§7Amount: §e" + String.join(", ", amounts), null, rootJson, entry, null, null));
                    }
                } else {
                    lore.add(LootResolver.resolveComponent("§7Amount: §e" + ce.getAsString(), null, rootJson, entry, null, null));
                }
            } else {
                lore.add(LootResolver.resolveComponent("§7Amount: §e" + ce.getAsInt(), null, rootJson, entry, null, null));
            }
        }
        
        String type = entry.get("type").getAsString();
        String textToCheck = "";
        if (type.equals("item")) textToCheck = entry.get("id").getAsString();
        else if (type.equals("command")) textToCheck = entry.get("command").getAsString();
        
        if (type.equals("multi") && entry.has("rewards")) {
            lore.add(LootResolver.resolveComponent("§bContains:", null, rootJson, entry, null, null));
            for (JsonElement subElement : entry.getAsJsonArray("rewards")) {
                JsonObject subEntry = subElement.getAsJsonObject();
                long subCount = subEntry.has("count") ? subEntry.get("count").getAsLong() : 1;
                String summary = LootResolver.getRewardSummaryName(subEntry, subCount, rootJson, null);
                lore.add(LootResolver.resolveComponent("  §7- " + summary, null, rootJson, subEntry, null, null));
            }
        }
        
        if (rootJson != null && rootJson.has("vars")) {
            Matcher m = Pattern.compile("<(\\w+)>").matcher(textToCheck);
            Set<String> foundVars = new HashSet<>();
            while (m.find()) {
                foundVars.add(m.group(1));
            }
            
            JsonObject vars = rootJson.getAsJsonObject("vars");
            for (String varName : foundVars) {
                if (vars.has(varName)) {
                    JsonElement varData = vars.get(varName);
                    List<String> options = new ArrayList<>();
                    
                    if (varData.isJsonArray()) {
                        for (JsonElement e : varData.getAsJsonArray()) {
                            if (e.isJsonObject() && e.getAsJsonObject().has("value")) {
                                options.add(e.getAsJsonObject().get("value").getAsString());
                            }
                        }
                    } else if (varData.isJsonObject() && varData.getAsJsonObject().has("value")) {
                        options.add(varData.getAsJsonObject().get("value").getAsString());
                    }
                    
                    if (!options.isEmpty()) {
                        String optionsStr = String.join(", ", options);
                        if (optionsStr.length() > 40) {
                            optionsStr = optionsStr.substring(0, 37) + "...";
                        }
                        lore.add(LootResolver.resolveComponent("§bVar <" + varName + ">: §f" + optionsStr, null, rootJson, entry, null, null));
                    }
                }
            }
        }
        
        stack.set(DataComponents.LORE, new ItemLore(lore));
        return stack;
    }

    private static MenuType<ChestMenu> getMenuType(int rows) {
        return switch (rows) {
            case 1 -> MenuType.GENERIC_9x1; case 2 -> MenuType.GENERIC_9x2;
            case 3 -> MenuType.GENERIC_9x3; case 4 -> MenuType.GENERIC_9x4;
            case 5 -> MenuType.GENERIC_9x5; default -> MenuType.GENERIC_9x6;
        };
    }

    public static JsonObject rollWinner(List<JsonObject> pool, String weightField) {
        int totalWeight = 0;
        for (JsonObject e : pool) {
            if (e.has(weightField)) {
                totalWeight += e.get(weightField).getAsInt();
            }
        }
        if (totalWeight <= 0) {
            if (pool.isEmpty()) return new JsonObject(); // Should not happen
            return pool.getFirst();
        }
        int roll = new Random().nextInt(totalWeight);
        int current = 0;
        for (JsonObject entry : pool) {
            if (entry.has(weightField)) {
                current += entry.get(weightField).getAsInt();
                if (roll < current) return entry;
            }
        }
        return pool.getFirst();
    }
}

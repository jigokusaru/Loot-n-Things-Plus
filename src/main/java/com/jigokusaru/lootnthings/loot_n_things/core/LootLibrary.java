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
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import javax.annotation.Nullable;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class LootLibrary {
    private static final Map<UUID, LootSession> SESSIONS = new ConcurrentHashMap<>();
    private static final Map<UUID, Map<String, Long>> COOLDOWNS = new ConcurrentHashMap<>();
    private static final Map<String, List<Integer>> GLOBAL_DECKS = new ConcurrentHashMap<>();
    private static final String PITY_TAG = "lootnthings_pity";

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
        bag.set(DataComponents.CUSTOM_NAME, Component.literal(LootResolver.applyPlaceholders(displayName, null, json, null, null, tier)));

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
                        public boolean stillValid(Player p) { return true; }
                    },
                    Component.literal(LootResolver.applyPlaceholders(title, player, json, null, null, tier)))));
        }
    }
    
    public static boolean canOpen(String tier, Player player, JsonObject json) {
        if (json.has("cooldown")) {
            long cooldown = json.get("cooldown").getAsLong() * 1000;
            long lastUsed = COOLDOWNS.computeIfAbsent(player.getUUID(), k -> new HashMap<>()).getOrDefault(tier, 0L);
            if (System.currentTimeMillis() - lastUsed < cooldown) {
                long remaining = (cooldown - (System.currentTimeMillis() - lastUsed)) / 1000;
                player.displayClientMessage(Component.literal("§cYou must wait " + remaining + " seconds to use this again."), true);
                return false;
            }
        }

        if (json.has("cost")) {
            JsonObject cost = json.getAsJsonObject("cost");
            String type = cost.get("type").getAsString();
            
            if (type.equals("xp")) {
                int amount = cost.get("amount").getAsInt();
                if (player.experienceLevel < amount) {
                    player.displayClientMessage(Component.literal("§cYou need " + amount + " XP levels to open this."), true);
                    return false;
                }
            } else if (type.equals("item")) {
                int amount = cost.get("amount").getAsInt();
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.get("id").getAsString()));
                if (player.getInventory().countItem(item) < amount) {
                    player.displayClientMessage(Component.literal("§cYou need " + amount + " " + item.getDescription().getString() + " to open this."), true);
                    return false;
                }
            } else if (type.equals("economy")) {
                if (Loot_n_things.economy == null) {
                    Loot_n_things.LOGGER.warn("Economy cost defined for tier '{}', but no economy system is enabled!", tier);
                    return false; // Fail closed if economy isn't running
                }
                double amount = cost.get("amount").getAsDouble();
                long longAmount = (long) (Config.COMMON.hasDecimals.get() ? amount * 100 : amount);
                
                if (!Loot_n_things.economy.hasEnough((ServerPlayer) player, longAmount)) {
                    player.displayClientMessage(Component.literal("§cYou do not have enough money! You need " + Loot_n_things.economy.getCurrencyName(longAmount)), true);
                    return false;
                }
            }
        }
        return true;
    }

    public static void openLootSpinner(String tier, Player player, ServerLevel level) {
        JsonObject json = LootConfigManager.getLootFile(tier);
        if (json == null || !json.has("loot")) return;
        
        // Consume cost and set cooldown AFTER checks have passed
        if (json.has("cost")) {
            JsonObject cost = json.getAsJsonObject("cost");
            String type = cost.get("type").getAsString();
            
            if (type.equals("xp")) {
                player.giveExperienceLevels(-cost.get("amount").getAsInt());
            } else if (type.equals("item")) {
                Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(cost.get("id").getAsString()));
                player.getInventory().clearOrCountMatchingItems(p -> p.getItem() == item, cost.get("amount").getAsInt(), player.inventoryMenu.getCraftSlots());
            } else if (type.equals("economy")) {
                if (Loot_n_things.economy != null) {
                    double amount = cost.get("amount").getAsDouble();
                    long longAmount = (long) (Config.COMMON.hasDecimals.get() ? amount * 100 : amount);
                    Loot_n_things.economy.withdraw((ServerPlayer) player, longAmount);
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
            player.sendSystemMessage(Component.literal("§d§lPity roll activated!"));
        }

        if (isPitySpin) {
            weightField = "pity_weight";
            final String finalWeightField = weightField;
            rollPool = pool.stream().filter(e -> e.has(finalWeightField)).collect(Collectors.toList());
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
                    new ArrayList<>(IntStream.range(0, pool.size()).boxed().collect(Collectors.toList()))
                );
                
                if (deck.size() < spins) {
                    deck.clear();
                    deck.addAll(IntStream.range(0, pool.size()).boxed().collect(Collectors.toList()));
                    if (json.has("broadcast") && json.getAsJsonObject("broadcast").has("shuffle")) {
                        String shuffleMsg = json.getAsJsonObject("broadcast").get("shuffle").getAsString();
                        level.getServer().getPlayerList().broadcastSystemMessage(Component.literal(LootResolver.applyPlaceholders(shuffleMsg, null, json, null, null, tier)), false);
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
            winnerVars.add(LootResolver.resolveVariables(winner, json));
            if (!winner.get("type").getAsString().equals("nothing")) hasRealWin = true;
            int amount = 1;
            if (winner.has("count")) {
                JsonElement ce = winner.get("count");
                amount = ce.isJsonObject() ? LootResolver.rollWeightedRange(ce.getAsJsonObject()) : ce.getAsInt();
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
                    public boolean stillValid(Player p) { return true; }
                },
                Component.literal(LootResolver.applyPlaceholders(title, player, json, null, null, tier)));

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
        // ... (rest of the method is unchanged)
        return null;
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
        if (totalWeight <= 0) return pool.getFirst();
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

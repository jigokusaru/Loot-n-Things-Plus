package com.jigokusaru.lootnthings.loot_n_things.core;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.SimpleContainer;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.FireworkRocketEntity;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomModelData;
import net.minecraft.world.item.component.ItemLore;

import javax.annotation.Nullable;
import java.util.List;
import java.util.Map;
import java.util.Random;

public class LootSession {
    private static final Random RANDOM = new Random();
    private final Player player;
    private final ServerLevel level;
    private final SimpleContainer container;
    private final JsonObject config;
    private final List<JsonObject> pool;
    private final List<JsonObject> winners;
    private final List<Integer> winningAmounts;
    private final List<Map<String, String>> winnerVars;
    private final boolean hasRealWin;
    private final int spins;
    private final int totalRows;
    private final ItemStack casing;
    private final ItemStack hopper;
    private final String tier;

    private int tickCounter = 0;
    private int animationStep = 0;
    private int delay = 2;
    private final int maxSteps = 30;
    private boolean finished = false;
    private int finishDelay = 0;

    public LootSession(Player player, ServerLevel level, SimpleContainer container, JsonObject config,
                       List<JsonObject> pool, List<JsonObject> winners, List<Integer> winningAmounts,
                       List<Map<String, String>> winnerVars, boolean hasRealWin, int spins, int totalRows,
                       ItemStack casing, ItemStack hopper, String tier) {
        this.player = player;
        this.level = level;
        this.container = container;
        this.config = config;
        this.pool = pool;
        this.winners = winners;
        this.winningAmounts = winningAmounts;
        this.winnerVars = winnerVars;
        this.hasRealWin = hasRealWin;
        this.spins = spins;
        this.totalRows = totalRows;
        this.casing = casing;
        this.hopper = hopper;
        this.tier = tier;
    }

    public boolean isFinished() {
        return finished;
    }

    public void forceGrantRewards() {
        if (finished) return;
        grantAndAnnounceRewards();
    }

    public boolean tick() {
        if (player.containerMenu == player.inventoryMenu) {
            forceGrantRewards();
            return true;
        }

        if (finished) {
            finishDelay++;
            return finishDelay > 40;
        }

        tickCounter++;
        if (tickCounter < delay) return false;
        tickCounter = 0;

        animationStep++;
        delay = 2 + (int)((animationStep / (float)maxSteps) * 8);

        container.setItem(4, hopper);

        for (int s = 0; s < spins; s++) {
            int rowStart = (s + 1) * 9;
            for (int col = 0; col < 8; col++) {
                container.setItem(rowStart + col, container.getItem(rowStart + col + 1));
            }
            
            if (maxSteps - animationStep == 4) {
                container.setItem(rowStart + 8, createIcon(winners.get(s), config, winnerVars.get(s)));
            } else {
                JsonObject randomEntry = LootLibrary.rollWinner(pool, "weight");
                Map<String, String> randomVars = LootResolver.resolveVariables(randomEntry, config);
                container.setItem(rowStart + 8, createIcon(randomEntry, config, randomVars));
            }
        }

        SoundEvent clickSound = SoundEvents.UI_BUTTON_CLICK.value();
        if (config.has("sounds") && config.getAsJsonObject("sounds").has("click")) {
            clickSound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(config.getAsJsonObject("sounds").get("click").getAsString()));
        }
        float pitch = 0.7F + ((float)animationStep / maxSteps * 0.3F);
        level.playSound(null, player.getX(), player.getY(), player.getZ(), clickSound, SoundSource.PLAYERS, 0.4F, pitch);

        if (animationStep >= maxSteps) {
            finish();
        }
        return false;
    }

    private void finish() {
        if (finished) return;
        
        grantAndAnnounceRewards();
        
        for (int s = 0; s < spins; s++) {
            int winIndex = ((s + 1) * 9) + 4;
            ItemStack winStack = container.getItem(winIndex);
            if (!winStack.isEmpty()) {
                winStack.set(DataComponents.ENCHANTMENT_GLINT_OVERRIDE, true);
                winStack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal("§a§lREWARD GRANTED"))));
            }
        }
        
        for (int i = 0; i <= 4; i++) {
            if (i == 4) continue;
            int colIndex = i;
            for (int row = 1; row < totalRows; row++) {
                container.setItem(row * 9 + colIndex, casing);
                container.setItem(row * 9 + (8 - colIndex), casing);
            }
        }
    }

    private void grantAndAnnounceRewards() {
        if (finished) return;
        this.finished = true;

        SoundEvent winSound = SoundEvents.IRON_TRAPDOOR_CLOSE;
        if (config.has("sounds") && config.getAsJsonObject("sounds").has("win")) {
            winSound = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.parse(config.getAsJsonObject("sounds").get("win").getAsString()));
        }
        level.playSound(null, player.getX(), player.getY(), player.getZ(), winSound, SoundSource.PLAYERS, 1.2F, 1.1F);

        player.sendSystemMessage(LootResolver.resolveComponent("[gold]--- [white]ROULETTE RESULTS [gold]---", player, config, null, null, tier));

        for (int s = 0; s < spins; s++) {
            int finalAmt = winningAmounts.get(s);
            JsonObject winner = winners.get(s);
            Map<String, String> vars = winnerVars.get(s);
            
            String summary = LootResolver.getRewardSummaryName(winner, finalAmt, config, vars);
            player.sendSystemMessage(LootResolver.resolveComponent("  [green]> [white]" + summary, player, config, winner, vars, tier));

            if (config.has("broadcast") && config.getAsJsonObject("broadcast").has("win")) {
                String winMsg = config.getAsJsonObject("broadcast").get("win").getAsString();
                String resolvedWinMsg = winMsg.replace("<display_name>", winner.has("display_name") ? winner.get("display_name").getAsString() : "a reward");
                level.getServer().getPlayerList().broadcastSystemMessage(LootResolver.resolveComponent(resolvedWinMsg, player, config, winner, vars, tier), false);
            }

            executeRewardSpecific(winner, player, level, finalAmt, config, vars);
        }

        if (hasRealWin) {
            SoundEvent toastSound = SoundEvents.UI_TOAST_CHALLENGE_COMPLETE;
            level.playSound(null, player.getX(), player.getY(), player.getZ(), toastSound, SoundSource.PLAYERS, 0.8F, 1.0F);
            spawnFirework(level, player);
        }
    }

    private static void spawnFirework(ServerLevel level, Player player) {
        ItemStack firework = new ItemStack(Items.FIREWORK_ROCKET);
        FireworkRocketEntity rocket = new FireworkRocketEntity(level, player.getX(), player.getY(), player.getZ(), firework);
        level.addFreshEntity(rocket);
    }

    public static ItemStack createIcon(JsonObject entry, @Nullable JsonObject rootJson, @Nullable Map<String, String> resolvedVars) {
        String type = entry.get("type").getAsString();
        ItemStack stack;
        String name;

        if (entry.has("icon")) {
            Item iconItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(entry.get("icon").getAsString()));
            stack = new ItemStack(iconItem);
        } else if (type.equals("item")) {
            String id = entry.get("id").getAsString();
            String resolvedId = LootResolver.applyPlaceholders(id, null, rootJson, entry, resolvedVars, null);
            Item item = BuiltInRegistries.ITEM.get(ResourceLocation.parse(resolvedId));
            if (item == Items.AIR && id.contains("<")) {
                 stack = new ItemStack(Items.BARRIER);
            } else {
                stack = new ItemStack(item);
            }
        } else if (type.equals("nothing")) {
            stack = new ItemStack(Items.BARRIER);
        } else {
            stack = new ItemStack(Items.PAPER);
        }
        
        if (entry.has("display_name")) {
            name = entry.get("display_name").getAsString();
        } else {
            name = LootResolver.getRewardSummaryName(entry, 1, rootJson, resolvedVars);
        }

        if (entry.has("model_id")) {
            stack.set(DataComponents.CUSTOM_MODEL_DATA, new CustomModelData(entry.get("model_id").getAsInt()));
        }

        stack.set(DataComponents.CUSTOM_NAME, LootResolver.resolveComponent(name, null, rootJson, entry, resolvedVars, null));

        return stack;
    }

    private static void executeRewardSpecific(JsonObject entry, Player player, ServerLevel level, long count, @Nullable JsonObject rootJson, @Nullable Map<String, String> resolvedVars) {
        String type = entry.get("type").getAsString();
        switch (type) {
            case "item" -> {
                String id = entry.get("id").getAsString();
                id = LootResolver.applyPlaceholders(id, player, rootJson, entry, resolvedVars, null);
                ItemStack stack = new ItemStack(BuiltInRegistries.ITEM.get(ResourceLocation.parse(id)));
                stack.setCount((int)count);
                if (!player.getInventory().add(stack)) {
                    level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), stack));
                }
            }
            case "command" -> {
                String cmd = LootResolver.applyPlaceholders(entry.get("command").getAsString(), player, rootJson, entry, resolvedVars, null);
                for (int i = 0; i < count; i++) level.getServer().getCommands().performPrefixedCommand(player.createCommandSourceStack().withSuppressedOutput(), cmd);
            }
            case "economy" -> {
                if (Loot_n_things.economy != null && player instanceof ServerPlayer sp) {
                    long amount = count;
                    if (Config.COMMON.hasDecimals.get()) {
                        amount = count * 100;
                    }
                    Loot_n_things.economy.deposit(sp, amount);
                }
            }
            case "multi" -> {
                if (entry.has("rewards")) {
                    for (JsonElement subElement : entry.getAsJsonArray("rewards")) {
                        JsonObject subEntry = subElement.getAsJsonObject();
                        long subCount = subEntry.has("count") ? subEntry.get("count").getAsLong() : 1;
                        
                        String summary = LootResolver.getRewardSummaryName(subEntry, subCount, rootJson, resolvedVars);
                        player.sendSystemMessage(LootResolver.resolveComponent("    - " + summary, player, rootJson, subEntry, resolvedVars, null));
                        executeRewardSpecific(subEntry, player, level, subCount, rootJson, resolvedVars);
                    }
                }
            }
            case "loot_table" -> {
                String tierPath = entry.get("id").getAsString();
                ItemStack bag = LootLibrary.createBagFromTier(tierPath);
                if (!bag.isEmpty()) {
                    if (!player.getInventory().add(bag)) {
                        level.addFreshEntity(new ItemEntity(level, player.getX(), player.getY(), player.getZ(), bag));
                    }
                }
            }
            case "nothing" -> {
                String msg = entry.has("message") ? entry.get("message").getAsString() : "[red]Empty...";
                player.displayClientMessage(LootResolver.resolveComponent(msg, player, rootJson, entry, resolvedVars, null), true);
            }
        }
    }
}

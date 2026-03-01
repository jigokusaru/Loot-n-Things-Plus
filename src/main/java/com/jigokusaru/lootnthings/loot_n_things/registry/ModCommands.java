package com.jigokusaru.lootnthings.loot_n_things.registry;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.config.LootConfigManager;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import com.jigokusaru.lootnthings.loot_n_things.util.NameplateManager;
import com.jigokusaru.lootnthings.loot_n_things.util.PermissionManager;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.Collection;

public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> CHEST_TIER_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    LootConfigManager.getAvailableTiers().stream()
                            .filter(s -> s.startsWith("chests/"))
                            .map(s -> s.replace("chests/", "")),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> BAG_TIER_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    LootConfigManager.getAvailableTiers().stream()
                            .filter(s -> s.startsWith("bags/"))
                            .map(s -> s.replace("bags/", "")),
                    builder);

    private static final SuggestionProvider<CommandSourceStack> PITY_TIER_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    LootConfigManager.getAvailableTiers().stream()
                            .filter(tier -> {
                                JsonObject json = LootLibrary.getLootFile(tier);
                                return json != null && json.has("pity_after");
                            })
                            .map(s -> s.replace("chests/", "").replace("bags/", "")),
                    builder);

    public static void register(CommandDispatcher<CommandSourceStack> dispatcher) {
        dispatcher.register(Commands.literal("lnt")
                .then(Commands.literal("set")
                        .requires(source -> PermissionManager.hasPermission(source, "lootnthings.command.set"))
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("§cUsage: /lnt set <tier>"));
                            return 0;
                        })
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(CHEST_TIER_SUGGESTIONS)
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();
                                    String tier = StringArgumentType.getString(context, "tier");
                                    
                                    BlockHitResult hit = getLookingAt(player);
                                    if (hit.getType() == HitResult.Type.MISS) {
                                        context.getSource().sendFailure(Component.literal("§cYou are not looking at a block!"));
                                        return 0;
                                    }
                                    
                                    BlockPos pos = hit.getBlockPos();
                                    BlockEntity be = player.level().getBlockEntity(pos);

                                    if (be != null && player.level() instanceof ServerLevel level) {
                                        String path = "chests/" + tier;
                                        be.getPersistentData().putString("lnt_tier", path);
                                        NameplateManager.add(be);
                                        NameplateManager.createOrUpdate(level, pos, be);
                                        
                                        final String finalPath = path;
                                        context.getSource().sendSuccess(() -> Component.literal("§aLoot block set to: §6" + finalPath), true);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal("§cYou must be looking at a Block Entity!"));
                                    return 0;
                                })))

                .then(Commands.literal("key")
                        .requires(source -> PermissionManager.hasPermission(source, "lootnthings.command.key"))
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("§cUsage: /lnt key <tier> [targets]"));
                            return 0;
                        })
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(CHEST_TIER_SUGGESTIONS)
                                .executes(context -> {
                                    try {
                                        giveKey(context.getSource(), StringArgumentType.getString(context, "tier"), context.getSource().getPlayerOrException());
                                    } catch (CommandSyntaxException e) {
                                        // Should not happen
                                    }
                                    return 1;
                                })
                                .then(Commands.argument("targets", EntityArgument.players())
                                        .executes(context -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                                            String tier = StringArgumentType.getString(context, "tier");
                                            int successCount = 0;
                                            for (ServerPlayer target : targets) {
                                                if (giveKey(context.getSource(), tier, target)) {
                                                    successCount++;
                                                }
                                            }
                                            final int finalSuccessCount = successCount;
                                            context.getSource().sendSuccess(() -> Component.literal("§aGave key for " + tier + " to " + finalSuccessCount + " player(s)."), true);
                                            return finalSuccessCount;
                                        }))))

                .then(Commands.literal("remove")
                        .requires(source -> PermissionManager.hasPermission(source, "lootnthings.command.remove"))
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            
                            BlockHitResult hit = getLookingAt(player);
                            if (hit.getType() == HitResult.Type.MISS) {
                                context.getSource().sendFailure(Component.literal("§cYou are not looking at a block!"));
                                return 0;
                            }
                            
                            BlockPos pos = hit.getBlockPos();
                            BlockEntity be = player.level().getBlockEntity(pos);

                            if (be != null && player.level() instanceof ServerLevel level) {
                                CompoundTag data = be.getPersistentData();
                                if (data.contains("lnt_tier")) {
                                    data.remove("lnt_tier");
                                    
                                    NameplateManager.remove(be);
                                    NameplateManager.removeDisplay(level, pos);
                                    
                                    be.setChanged();
                                    level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                                    context.getSource().sendSuccess(() -> Component.literal("§aLoot table removed."), true);
                                    return 1;
                                } else {
                                    context.getSource().sendFailure(Component.literal("§cNo Loot n' Things data found on this block."));
                                    return 0;
                                }
                            }
                            context.getSource().sendFailure(Component.literal("§cYou must be looking at a Block Entity!"));
                            return 0;
                        }))

                .then(Commands.literal("reload")
                        .requires(source -> PermissionManager.hasPermission(source, "lootnthings.command.reload"))
                        .executes(context -> {
                            boolean success = LootLibrary.reload();
                            if (success) {
                                NameplateManager.refreshAll();
                                context.getSource().sendSuccess(() -> Component.literal("§aConfigs reloaded and all loot chest displays refreshed!"), true);
                            } else {
                                context.getSource().sendFailure(Component.literal("§cError in configs! Check server logs."));
                            }
                            return success ? 1 : 0;
                        }))

                .then(Commands.literal("givebag")
                        .requires(source -> PermissionManager.hasPermission(source, "lootnthings.command.givebag"))
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("§cUsage: /lnt givebag <targets> <tier>"));
                            return 0;
                        })
                        .then(Commands.argument("targets", EntityArgument.players())
                                .then(Commands.argument("tier", StringArgumentType.string()).suggests(BAG_TIER_SUGGESTIONS)
                                        .executes(context -> {
                                            Collection<ServerPlayer> targets = EntityArgument.getPlayers(context, "targets");
                                            String tier = StringArgumentType.getString(context, "tier");
                                            int successCount = 0;
                                            for (ServerPlayer target : targets) {
                                                ItemStack bag = LootLibrary.createBagFromTier(tier);
                                                if (!bag.isEmpty()) {
                                                    target.getInventory().add(bag);
                                                    successCount++;
                                                }
                                            }
                                            final int finalSuccessCount = successCount;
                                            context.getSource().sendSuccess(() -> Component.literal("§aGave " + tier + " bag to " + finalSuccessCount + " player(s)."), true);
                                            return finalSuccessCount;
                                        }))))
                
                .then(Commands.literal("pity")
                        .requires(source -> PermissionManager.hasPermission(source, "lootnthings.command.pity"))
                        .executes(context -> {
                            context.getSource().sendFailure(Component.literal("§cUsage: /lnt pity <tier>"));
                            return 0;
                        })
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(PITY_TIER_SUGGESTIONS)
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();
                                    String tierName = StringArgumentType.getString(context, "tier");
                                    String tierPath = LootConfigManager.getAvailableTiers().stream()
                                            .filter(t -> t.endsWith(tierName))
                                            .findFirst()
                                            .orElse(tierName);
                                    
                                    JsonObject json = LootLibrary.getLootFile(tierPath);
                                    if (json == null || !json.has("pity_after")) {
                                        context.getSource().sendFailure(Component.literal("§cThat tier does not have a pity system enabled."));
                                        return 0;
                                    }
                                    
                                    int pityAfter = json.get("pity_after").getAsInt();
                                    CompoundTag pityData = player.getPersistentData().getCompound("lnt_pity");
                                    int currentPity = pityData.getInt(tierPath);
                                    
                                    player.sendSystemMessage(Component.literal("§ePity for §6" + tierName + "§e: §a" + currentPity + " §7/ §c" + pityAfter));
                                    return 1;
                                })))
        );
    }

    private static boolean giveKey(CommandSourceStack source, String tier, Player target) {
        String path = "chests/" + tier;
        
        JsonObject json = LootLibrary.getLootFile(path);
        if (json == null) {
            source.sendFailure(Component.literal("§cLoot table '" + path + "' not found!"));
            return false;
        }

        Item keyItem = Items.TRIPWIRE_HOOK;
        if (json.has("key_item")) {
            String itemId = json.get("key_item").getAsString();
            keyItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        }

        ItemStack stack = new ItemStack(keyItem);
        
        CustomData.update(DataComponents.CUSTOM_DATA, stack, tag -> {
            CompoundTag lntTag = new CompoundTag();
            lntTag.putString("type", "key");
            lntTag.putString("tier", path);
            tag.put("loot_n_things", lntTag);
        });
        
        String keyName = "§6Key: §e" + path.replace("chests/", "").replace("bags/", "");
        if (json.has("display_name")) {
            String chestName = json.get("display_name").getAsString();
            keyName = "§6Key: " + LootResolver.applyPlaceholders(chestName, target, json, null, null, path);
        }
        
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, LootResolver.resolveComponent(keyName, target, json, null, null, path));
        
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        
        return true;
    }

    private static BlockHitResult getLookingAt(Player player) {
        return player.level().clip(new ClipContext(
                player.getEyePosition(),
                player.getEyePosition().add(player.getLookAngle().scale(5.0)),
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                player
        ));
    }
}

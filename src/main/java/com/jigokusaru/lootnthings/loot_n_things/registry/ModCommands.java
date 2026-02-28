package com.jigokusaru.lootnthings.loot_n_things.registry;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.config.LootConfigManager;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.commands.arguments.EntityArgument;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

/**
 * Registers all server commands for the mod.
 */
public class ModCommands {

    // Suggestion provider for all available loot tiers (chests and bags).
    private static final SuggestionProvider<CommandSourceStack> TIER_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    LootConfigManager.getAvailableTiers().stream()
                            .map(s -> s.replace("chests/", "").replace("bags/", "")),
                    builder);

    // Suggestion provider for only those tiers that have a pity system enabled.
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
                // Command: /lnt set <tier>
                // Sets the loot table of the block the player is looking at.
                .then(Commands.literal("set")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(TIER_SUGGESTIONS)
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

                                    if (be != null) {
                                        String path = tier;
                                        if (!path.startsWith("chests/") && !path.startsWith("bags/")) {
                                            path = "chests/" + tier;
                                        }
                                        
                                        CompoundTag data = be.getPersistentData();
                                        data.putString("lnt_tier", path);
                                        
                                        JsonObject json = LootLibrary.getLootFile(path);
                                        if (json != null && json.has("display_name")) {
                                            String displayName = json.get("display_name").getAsString();
                                            Component newName = Component.literal(LootResolver.applyPlaceholders(displayName, player, json, null, null, path));
                                            
                                            CompoundTag tag = be.saveWithFullMetadata(context.getSource().registryAccess());
                                            tag.putString("CustomName", Component.Serializer.toJson(newName, context.getSource().registryAccess()));
                                            be.loadWithComponents(tag, context.getSource().registryAccess());
                                            player.level().sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                                        }
                                        
                                        be.setChanged();
                                        
                                        final String finalPath = path;
                                        context.getSource().sendSuccess(() -> Component.literal("§aLoot block set to: §6" + finalPath), true);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal("§cYou must be looking at a Block Entity!"));
                                    return 0;
                                })))

                // Command: /lnt key <tier> [target]
                // Gives a key for the specified loot tier.
                .then(Commands.literal("key")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(TIER_SUGGESTIONS)
                                .executes(context -> giveKey(context.getSource(), StringArgumentType.getString(context, "tier"), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> giveKey(context.getSource(), StringArgumentType.getString(context, "tier"), EntityArgument.getPlayer(context, "target"))))))

                // Command: /lnt remove
                // Removes the loot table from the block the player is looking at.
                .then(Commands.literal("remove")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            Player player = context.getSource().getPlayerOrException();
                            
                            BlockHitResult hit = getLookingAt(player);
                            if (hit.getType() == HitResult.Type.MISS) {
                                context.getSource().sendFailure(Component.literal("§cYou are not looking at a block!"));
                                return 0;
                            }
                            
                            BlockPos pos = hit.getBlockPos();
                            BlockEntity be = player.level().getBlockEntity(pos);

                            if (be != null) {
                                boolean changed = false;
                                
                                CompoundTag data = be.getPersistentData();
                                if (data.contains("lnt_tier")) {
                                    data.remove("lnt_tier");
                                    changed = true;
                                }
                                
                                CompoundTag tag = be.saveWithFullMetadata(context.getSource().registryAccess());
                                if (tag.contains("CustomName")) {
                                    tag.remove("CustomName");
                                    be.loadWithComponents(tag, context.getSource().registryAccess());
                                    player.level().sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
                                    changed = true;
                                }

                                if (changed) {
                                    be.setChanged();
                                    context.getSource().sendSuccess(() -> Component.literal("§aLoot table removed and name reset."), true);
                                    return 1;
                                } else {
                                    context.getSource().sendFailure(Component.literal("§cNo Loot n' Things data found on this block."));
                                    return 0;
                                }
                            }
                            context.getSource().sendFailure(Component.literal("§cYou must be looking at a Block Entity!"));
                            return 0;
                        }))

                // Command: /lnt reload
                // Reloads all loot table JSON files from the config folder.
                .then(Commands.literal("reload")
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            boolean success = LootLibrary.reload();
                            context.getSource().sendSuccess(() -> Component.literal(success ? "§aConfigs reloaded!" : "§cError in configs!"), true);
                            return success ? 1 : 0;
                        }))

                // Command: /lnt givebag <target> <tier>
                // Gives a loot bag to the specified player.
                .then(Commands.literal("givebag")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("target", EntityArgument.player())
                                .then(Commands.argument("tier", StringArgumentType.string()).suggests(TIER_SUGGESTIONS)
                                        .executes(context -> {
                                            Player target = EntityArgument.getPlayer(context, "target");
                                            String tier = StringArgumentType.getString(context, "tier");
                                            ItemStack bag = LootLibrary.createBagFromTier(tier);

                                            if (bag.isEmpty()) {
                                                context.getSource().sendFailure(Component.literal("§cTier '" + tier + "' not found in bags subfolder!"));
                                                return 0;
                                            }

                                            target.getInventory().add(bag);
                                            context.getSource().sendSuccess(() -> Component.literal("§aGave " + tier + " bag to " + target.getScoreboardName()), true);
                                            return 1;
                                        }))))
                
                // Command: /lnt pity <tier>
                // Allows players to check their pity counter for a specific tier.
                .then(Commands.literal("pity")
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(PITY_TIER_SUGGESTIONS)
                                .executes(context -> {
                                    Player player = context.getSource().getPlayerOrException();
                                    String tierName = StringArgumentType.getString(context, "tier");
                                    // Reconstruct the full path for lookup
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

    private static int giveKey(CommandSourceStack source, String tier, Player target) {
        String path = tier;
        if (!path.startsWith("chests/") && !path.startsWith("bags/")) {
            path = "chests/" + tier;
        }
        
        final String finalPath = path;
        JsonObject json = LootLibrary.getLootFile(finalPath);
        if (json == null) {
            source.sendFailure(Component.literal("§cLoot table '" + finalPath + "' not found!"));
            return 0;
        }

        Item keyItem = Items.TRIPWIRE_HOOK;
        if (json.has("key_item")) {
            String itemId = json.get("key_item").getAsString();
            keyItem = BuiltInRegistries.ITEM.get(ResourceLocation.parse(itemId));
        }

        ItemStack stack = new ItemStack(keyItem);
        
        stack.set(ModComponents.LOOT_KEY.get(), finalPath);
        
        String keyName = "§6Key: §e" + finalPath.replace("chests/", "").replace("bags/", "");
        if (json.has("display_name")) {
            String chestName = json.get("display_name").getAsString();
            keyName = "§6Key: " + LootResolver.applyPlaceholders(chestName, target, json, null, null, finalPath);
        }
        
        stack.set(net.minecraft.core.component.DataComponents.CUSTOM_NAME, Component.literal(keyName));
        
        if (!target.getInventory().add(stack)) {
            target.drop(stack, false);
        }
        
        source.sendSuccess(() -> Component.literal("§aGave key for " + finalPath + " to " + target.getScoreboardName()), true);
        return 1;
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

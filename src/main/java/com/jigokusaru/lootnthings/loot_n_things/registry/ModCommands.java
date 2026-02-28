package com.jigokusaru.lootnthings.loot_n_things.registry;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
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
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

import java.util.UUID;

public class ModCommands {

    private static final SuggestionProvider<CommandSourceStack> TIER_SUGGESTIONS = (context, builder) ->
            SharedSuggestionProvider.suggest(
                    LootConfigManager.getAvailableTiers().stream()
                            .map(s -> s.replace("chests/", "").replace("bags/", "")),
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

                                    if (be != null && player.level() instanceof ServerLevel level) {
                                        String path = tier;
                                        if (!path.startsWith("chests/") && !path.startsWith("bags/")) {
                                            path = "chests/" + tier;
                                        }
                                        
                                        createOrUpdateDisplay(level, pos, be, path);
                                        
                                        final String finalPath = path;
                                        context.getSource().sendSuccess(() -> Component.literal("§aLoot block set to: §6" + finalPath), true);
                                        return 1;
                                    }
                                    context.getSource().sendFailure(Component.literal("§cYou must be looking at a Block Entity!"));
                                    return 0;
                                })))

                .then(Commands.literal("key")
                        .requires(source -> source.hasPermission(2))
                        .then(Commands.argument("tier", StringArgumentType.string()).suggests(TIER_SUGGESTIONS)
                                .executes(context -> giveKey(context.getSource(), StringArgumentType.getString(context, "tier"), context.getSource().getPlayerOrException()))
                                .then(Commands.argument("target", EntityArgument.player())
                                        .executes(context -> giveKey(context.getSource(), StringArgumentType.getString(context, "tier"), EntityArgument.getPlayer(context, "target"))))))

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

                            if (be != null && player.level() instanceof ServerLevel level) {
                                CompoundTag data = be.getPersistentData();
                                if (data.contains("lnt_tier")) {
                                    data.remove("lnt_tier");
                                    
                                    if (data.hasUUID("lnt_display_uuid")) {
                                        UUID oldUuid = data.getUUID("lnt_display_uuid");
                                        Entity oldDisplay = level.getEntity(oldUuid);
                                        if (oldDisplay != null) {
                                            oldDisplay.kill();
                                        }
                                        data.remove("lnt_display_uuid");
                                    }
                                    
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
                        .requires(source -> source.hasPermission(2))
                        .executes(context -> {
                            boolean success = LootLibrary.reload();
                            context.getSource().sendSuccess(() -> Component.literal(success ? "§aConfigs reloaded!" : "§cError in configs!"), true);
                            return success ? 1 : 0;
                        }))

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
                
                .then(Commands.literal("pity")
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

    public static void createOrUpdateDisplay(ServerLevel level, BlockPos pos, BlockEntity be, String tierPath) {
        Loot_n_things.LOGGER.info("createOrUpdateDisplay called for tier '{}' at {}", tierPath, pos);
        CompoundTag data = be.getPersistentData();
        
        if (data.hasUUID("lnt_display_uuid")) {
            Entity oldDisplay = level.getEntity(data.getUUID("lnt_display_uuid"));
            if (oldDisplay != null) {
                oldDisplay.kill();
            }
        }

        data.putString("lnt_tier", tierPath);
        
        JsonObject json = LootLibrary.getLootFile(tierPath);
        if (json != null && json.has("display_name")) {
            String displayName = json.get("display_name").getAsString();
            Component newName = Component.literal(LootResolver.applyPlaceholders(displayName, null, json, null, null, tierPath));
            
            TextDisplay textDisplay = EntityType.TEXT_DISPLAY.create(level);
            if (textDisplay != null) {
                CompoundTag displayData = new CompoundTag();
                ListTag posList = new ListTag();
                posList.add(DoubleTag.valueOf(pos.getX() + 0.5));
                posList.add(DoubleTag.valueOf(pos.getY() + 1.1));
                posList.add(DoubleTag.valueOf(pos.getZ() + 0.5));
                
                displayData.put("Pos", posList);
                displayData.putString("text", Component.Serializer.toJson(newName, level.registryAccess()));
                displayData.putString("billboard", "center");
                displayData.putInt("background", 0xFF000000); // Fully opaque black
                displayData.putBoolean("see_through", false);
                textDisplay.load(displayData);
                
                level.addFreshEntity(textDisplay);
                data.putUUID("lnt_display_uuid", textDisplay.getUUID());
                Loot_n_things.LOGGER.info("Successfully created new display entity with UUID {}", textDisplay.getUUID());
            }
        }
        
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
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

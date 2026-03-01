package com.jigokusaru.lootnthings.loot_n_things.event;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModComponents;
import com.jigokusaru.lootnthings.loot_n_things.util.PermissionManager;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class LootBagListener {

    /**
     * Fired when a player right-clicks on a block with a loot bag.
     * We consume the event immediately to prevent the block from being placed or interacted with.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        String tierPath = stack.get(ModComponents.LNT_BAG_TIER.get());

        if (tierPath != null) {
            // This is the robust way to handle this.
            // It immediately consumes the event, preventing any other right-click actions.
            event.setCancellationResult(InteractionResult.CONSUME);
            
            // Manually trigger the item interaction logic since we consumed the block one.
            if (event.getEntity().level() instanceof ServerLevel serverLevel) {
                handleBagInteraction(event.getEntity(), stack, tierPath, serverLevel);
            }
        }
    }

    /**
     * Fired when a player right-clicks with an item in the air.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        String tierPath = stack.get(ModComponents.LNT_BAG_TIER.get());

        if (tierPath != null) {
            event.setCancellationResult(InteractionResult.SUCCESS);

            if (player.level() instanceof ServerLevel serverLevel) {
                handleBagInteraction(player, stack, tierPath, serverLevel);
            }
        }
    }

    /**
     * Centralized logic for handling a bag interaction.
     */
    private static void handleBagInteraction(Player player, ItemStack stack, String tierPath, ServerLevel serverLevel) {
        JsonObject json = LootLibrary.getLootFile(tierPath);
        if (json == null) return;

        if (player.isShiftKeyDown()) {
            String permission = json.has("permission") ? json.get("permission").getAsString() + ".preview" : "lootnthings.preview." + tierPath.replace("/", ".");
            if (PermissionManager.hasPermission(player, permission)) {
                LootLibrary.openLootPreview(tierPath, player);
            } else {
                player.displayClientMessage(LootResolver.resolveComponent("§cYou do not have permission to preview this loot.", player, null, null, null, tierPath), true);
            }
        } else {
            String permission = json.has("permission") ? json.get("permission").getAsString() + ".open" : "lootnthings.open." + tierPath.replace("/", ".");
            if (!PermissionManager.hasPermission(player, permission)) {
                player.displayClientMessage(LootResolver.resolveComponent("§cYou do not have permission to open this.", player, null, null, null, tierPath), true);
                return;
            }
            
            if (LootLibrary.canOpen(tierPath, player, json)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                LootLibrary.openLootSpinner(tierPath, player, serverLevel);
            }
        }
    }
}

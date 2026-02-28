package com.jigokusaru.lootnthings.loot_n_things.event;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModComponents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

/**
 * Handles all events related to loot bags (item-based loot).
 */
public class LootBagListener {

    /**
     * Fired when a player right-clicks on a block.
     * If the player is holding a loot bag, we deny the block interaction but allow the item interaction to proceed.
     * This is the standard way to prevent placeable items from being placed.
     */
    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        String tierPath = stack.get(ModComponents.LNT_BAG_TIER.get());

        if (tierPath != null) {
            event.setCancellationResult(InteractionResult.PASS);
        }
    }

    /**
     * Fired when a player right-clicks with an item (either in the air or after a block interaction was passed).
     * This is the primary handler for the bag's logic.
     */
    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        String tierPath = stack.get(ModComponents.LNT_BAG_TIER.get());

        if (tierPath != null) {
            // We are handling this interaction, so consume the event to prevent other actions.
            event.setCancellationResult(InteractionResult.SUCCESS);

            if (player.level() instanceof ServerLevel serverLevel) {
                handleBagInteraction(player, stack, tierPath, serverLevel);
            }
        }
    }

    /**
     * Centralized logic for handling a bag interaction.
     * Checks for shift-clicking to determine whether to open the preview or the spinner.
     */
    private static void handleBagInteraction(Player player, ItemStack stack, String tierPath, ServerLevel serverLevel) {
        if (player.isShiftKeyDown()) {
            LootLibrary.openLootPreview(tierPath, player);
        } else {
            JsonObject json = LootLibrary.getLootFile(tierPath);
            if (json != null && LootLibrary.canOpen(tierPath, player, json)) {
                if (!player.getAbilities().instabuild) {
                    stack.shrink(1);
                }
                LootLibrary.openLootSpinner(tierPath, player, serverLevel);
            }
        }
    }
}

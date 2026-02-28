package com.jigokusaru.lootnthings.loot_n_things.event;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
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

    @SubscribeEvent
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        ItemStack stack = event.getItemStack();
        String tierPath = stack.get(ModComponents.LNT_BAG_TIER.get());

        if (tierPath != null) {
            event.setCancellationResult(InteractionResult.PASS);
        }
    }

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

    private static void handleBagInteraction(Player player, ItemStack stack, String tierPath, ServerLevel serverLevel) {
        JsonObject json = LootLibrary.getLootFile(tierPath);
        if (json == null) return;

        if (player.isShiftKeyDown()) {
            String permission = json.has("permission") ? json.get("permission").getAsString() + ".preview" : "lootnthings.preview." + tierPath.replace("/", ".");
            if (PermissionManager.hasPermission(player, permission)) {
                LootLibrary.openLootPreview(tierPath, player);
            } else {
                player.displayClientMessage(Component.literal("§cYou do not have permission to preview this loot."), true);
            }
        } else {
            String permission = json.has("permission") ? json.get("permission").getAsString() + ".open" : "lootnthings.open." + tierPath.replace("/", ".");
            if (!PermissionManager.hasPermission(player, permission)) {
                player.displayClientMessage(Component.literal("§cYou do not have permission to open this."), true);
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

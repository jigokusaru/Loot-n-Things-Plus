package com.jigokusaru.lootnthings.loot_n_things.event;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import com.jigokusaru.lootnthings.loot_n_things.util.PermissionManager;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.InteractionResult;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.neoforged.bus.api.EventPriority;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.util.TriState;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;

public class LootBagListener {

    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onRightClickBlock(PlayerInteractEvent.RightClickBlock event) {
        // If the event has already been used by another mod or vanilla, do nothing.
        if (event.getUseBlock() != TriState.DEFAULT || event.getUseItem() != TriState.DEFAULT || event.isCanceled()) {
            return;
        }

        ItemStack stack = event.getItemStack();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag lntTag = customData.copyTag().getCompound("loot_n_things");
            if (lntTag.getString("type").equals("bag")) {
                // Since we are at the lowest priority, we know the block had no action.
                // We can now safely use our item.
                if (event.getEntity().level() instanceof ServerLevel serverLevel) {
                    handleBagInteraction(event.getEntity(), stack, lntTag.getString("tier"), serverLevel);
                }
                // Consume the event to prevent the default "place block" action.
                event.setCancellationResult(InteractionResult.CONSUME);
            }
        }
    }

    @SubscribeEvent
    public static void onRightClickItem(PlayerInteractEvent.RightClickItem event) {
        Player player = event.getEntity();
        ItemStack stack = event.getItemStack();
        CustomData customData = stack.get(DataComponents.CUSTOM_DATA);
        if (customData != null) {
            CompoundTag lntTag = customData.copyTag().getCompound("loot_n_things");
            if (lntTag.getString("type").equals("bag")) {
                event.setCancellationResult(InteractionResult.SUCCESS);
                if (player.level() instanceof ServerLevel serverLevel) {
                    handleBagInteraction(player, stack, lntTag.getString("tier"), serverLevel);
                }
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

package com.jigokusaru.lootnthings.loot_n_things.event;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import com.jigokusaru.lootnthings.loot_n_things.util.PermissionManager;
import net.minecraft.core.BlockPos;
import net.minecraft.core.component.DataComponents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.component.CustomData;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

public class ChestEventHandler {

    private String getLootTier(BlockEntity be) {
        if (be != null && be.getPersistentData().contains("lnt_tier")) {
            return be.getPersistentData().getString("lnt_tier");
        }
        return null;
    }

    @SubscribeEvent
    public void onChestPunch(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        String tier = getLootTier(be);
        
        if (tier != null && !tier.equals("none")) {
            event.setCanceled(true);
            if (!level.isClientSide) {
                JsonObject json = LootLibrary.getLootFile(tier);
                String permission = (json != null && json.has("permission")) ? json.get("permission").getAsString() + ".preview" : "lootnthings.preview." + tier.replace("/", ".");
                if (PermissionManager.hasPermission(event.getEntity(), permission)) {
                    LootLibrary.openLootPreview(tier, event.getEntity());
                } else {
                    event.getEntity().displayClientMessage(Component.literal("§cYou do not have permission to preview this loot."), true);
                }
            }
        }
    }

    @SubscribeEvent
    public void onChestInteract(PlayerInteractEvent.RightClickBlock event) {
        Level level = event.getLevel();
        if (level.isClientSide) return;

        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);
        Player player = event.getEntity();
        String chestTier = getLootTier(be);

        if (chestTier != null && !chestTier.equals("none")) {
            event.setCanceled(true);
            
            JsonObject json = LootLibrary.getLootFile(chestTier);
            String permission = (json != null && json.has("permission")) ? json.get("permission").getAsString() + ".open" : "lootnthings.open." + chestTier.replace("/", ".");
            
            if (!PermissionManager.hasPermission(player, permission)) {
                player.displayClientMessage(Component.literal("§cYou do not have permission to open this."), true);
                return;
            }

            ItemStack heldItem = player.getMainHandItem();
            CustomData customData = heldItem.get(DataComponents.CUSTOM_DATA);
            if (customData != null && customData.copyTag().contains("lnt_key_tier")) {
                String keyTier = customData.copyTag().getString("lnt_key_tier");
                if (keyTier.equals(chestTier)) {
                    if (json != null && LootLibrary.canOpen(chestTier, player, json)) {
                        if (!player.getAbilities().instabuild) {
                            heldItem.shrink(1);
                        }
                        LootLibrary.openLootSpinner(chestTier, player, (ServerLevel) level);
                    }
                }
            } else {
                String msg = "[red][bold]Locked! [reset][gray]Requires a [gold]" + chestTier.replace("chests/", "") + " Key[gray].";
                player.displayClientMessage(LootResolver.resolveComponent(msg, player, null, null, null, chestTier), true);
            }
        }
    }

    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        String tier = getLootTier(be);
        if (tier != null && !tier.equals("none")) {
            event.setCanceled(true);
            event.getPlayer().displayClientMessage(Component.literal("§cThis is a loot chest and cannot be broken. Use /lnt remove."), true);
        }
    }

    @SubscribeEvent
    public void onKeyPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            ItemStack heldItem = player.getMainHandItem();
            CustomData customData = heldItem.get(DataComponents.CUSTOM_DATA);
            if (customData != null && (customData.copyTag().contains("lnt_bag_tier") || customData.copyTag().contains("lnt_key_tier"))) {
                if (heldItem.getItem() instanceof net.minecraft.world.item.BlockItem) {
                    event.setCanceled(true);
                }
            }
        }
    }
}

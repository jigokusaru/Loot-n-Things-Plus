package com.jigokusaru.lootnthings.loot_n_things.event;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModComponents;
import net.minecraft.core.BlockPos;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.entity.player.PlayerInteractEvent;
import net.neoforged.neoforge.event.level.BlockEvent;

/**
 * Handles all events related to loot chests placed in the world.
 */
public class ChestEventHandler {

    private String getLootTier(BlockEntity be) {
        if (be != null && be.getPersistentData().contains("lnt_tier")) {
            return be.getPersistentData().getString("lnt_tier");
        }
        return null;
    }

    /**
     * Handles players left-clicking a loot chest.
     * This opens the loot preview GUI.
     */
    @SubscribeEvent
    public void onChestPunch(PlayerInteractEvent.LeftClickBlock event) {
        Level level = event.getLevel();
        BlockPos pos = event.getPos();
        BlockEntity be = level.getBlockEntity(pos);

        String tier = getLootTier(be);
        
        if (tier != null && !tier.equals("none")) {
            event.setCanceled(true);
            if (!level.isClientSide) {
                LootLibrary.openLootPreview(tier, event.getEntity());
            }
        }
    }

    /**
     * Handles players right-clicking a loot chest.
     * This checks for keys, costs, and cooldowns before opening the spinner.
     */
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

            ItemStack heldItem = player.getMainHandItem();
            String keyTier = heldItem.get(ModComponents.LOOT_KEY.get());

            if (keyTier != null && keyTier.equals(chestTier)) {
                JsonObject json = LootLibrary.getLootFile(chestTier);
                if (json != null && LootLibrary.canOpen(chestTier, player, json)) {
                    if (!player.getAbilities().instabuild) {
                        heldItem.shrink(1);
                    }
                    LootLibrary.openLootSpinner(chestTier, player, (ServerLevel) level);
                }
            } else {
                String msg = "[red][bold]Locked! [reset][gray]Requires a [gold]" + chestTier.replace("chests/", "") + " Key[gray].";
                player.displayClientMessage(Component.literal(LootResolver.applyPlaceholders(msg, player, null, null, null, chestTier)), true);
            }
        }
    }

    /**
     * This code ensures that a chest cannot be broken while it has a loot table connected to it.
     * Prevents players from breaking loot chests in survival mode.
     */
    @SubscribeEvent
    public void onBlockBreak(BlockEvent.BreakEvent event) {
        BlockEntity be = event.getLevel().getBlockEntity(event.getPos());
        String tier = getLootTier(be);
        if (tier != null && !tier.equals("none")) {
            if (!event.getPlayer().isCreative()) {
                event.setCanceled(true);
                event.getPlayer().displayClientMessage(Component.literal("§cYou cannot break Loot n' Things chests!"), true);
            }
        }
    }

    /**
     * Prevents players from placing loot bags or keys if they are block items.
     */
    @SubscribeEvent
    public void onKeyPlace(BlockEvent.EntityPlaceEvent event) {
        if (event.getEntity() instanceof Player player) {
            if (player.getMainHandItem().has(ModComponents.LNT_BAG_TIER.get()) || player.getMainHandItem().has(ModComponents.LOOT_KEY.get())) {
                if (player.getMainHandItem().getItem() instanceof net.minecraft.world.item.BlockItem) {
                    event.setCanceled(true);
                }
            }
        }
    }
}

package com.jigokusaru.lootnthings.loot_n_things.event;

import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModCommands;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

import java.util.UUID;

public class ForgeEvents {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LootLibrary.onServerTick(event);
    }

    @SubscribeEvent
    public static void onMenuClose(PlayerContainerEvent.Close event) {
        LootLibrary.onMenuClose(event);
    }

    /**
     * Scans for loot chests when a chunk loads to ensure their text displays are present.
     */
    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            Loot_n_things.LOGGER.debug("Checking chunk for loot chests at: {}", event.getChunk().getPos());
            for (BlockPos pos : event.getChunk().getBlockEntitiesPos()) {
                BlockEntity be = event.getChunk().getBlockEntity(pos);
                if (be == null) continue;

                CompoundTag data = be.getPersistentData();
                if (data.contains("lnt_tier")) {
                    Loot_n_things.LOGGER.info("Found loot chest at {}. Checking for display entity...", pos);
                    if (data.hasUUID("lnt_display_uuid")) {
                        UUID uuid = data.getUUID("lnt_display_uuid");
                        if (level.getEntity(uuid) == null) {
                            Loot_n_things.LOGGER.warn("Loot chest at {} has a dead display UUID. It will be replaced.", pos);
                            data.remove("lnt_display_uuid");
                        }
                    }
                    
                    if (!data.hasUUID("lnt_display_uuid")) {
                        Loot_n_things.LOGGER.info("Loot chest at {} is missing a display entity. Creating one now.", pos);
                        ModCommands.createOrUpdateDisplay(level, be.getBlockPos(), be, data.getString("lnt_tier"));
                    }
                }
            }
        }
    }
}

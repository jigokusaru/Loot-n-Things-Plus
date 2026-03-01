package com.jigokusaru.lootnthings.loot_n_things.event;

import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModCommands;
import com.jigokusaru.lootnthings.loot_n_things.util.NameplateManager;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.level.ChunkEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

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

    @SubscribeEvent
    public static void onChunkLoad(ChunkEvent.Load event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (BlockPos pos : event.getChunk().getBlockEntitiesPos()) {
                BlockEntity be = event.getChunk().getBlockEntity(pos);
                if (be != null && be.getPersistentData().contains("lnt_tier")) {
                    NameplateManager.add(be);
                    NameplateManager.createOrUpdate(level, pos, be);
                }
            }
        }
    }

    @SubscribeEvent
    public static void onChunkUnload(ChunkEvent.Unload event) {
        if (event.getLevel() instanceof ServerLevel level) {
            for (BlockPos pos : event.getChunk().getBlockEntitiesPos()) {
                BlockEntity be = event.getChunk().getBlockEntity(pos);
                if (be != null && be.getPersistentData().contains("lnt_tier")) {
                    NameplateManager.remove(be);
                    NameplateManager.removeDisplay(level, pos);
                }
            }
        }
    }
}

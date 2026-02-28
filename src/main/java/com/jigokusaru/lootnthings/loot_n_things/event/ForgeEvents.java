package com.jigokusaru.lootnthings.loot_n_things.event;

import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModCommands;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.player.PlayerContainerEvent;
import net.neoforged.neoforge.event.tick.ServerTickEvent;

/**
 * Handles events on the FORGE event bus.
 * These events are fired during normal gameplay.
 */
public class ForgeEvents {

    /**
     * Registers the mod's commands with the server.
     */
    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        ModCommands.register(event.getDispatcher());
    }

    /**
     * Ticks all active loot spinner sessions on the server.
     */
    @SubscribeEvent
    public static void onServerTick(ServerTickEvent.Post event) {
        LootLibrary.onServerTick(event);
    }

    /**
     * Provides a safety net for players who close the spinner GUI early.
     */
    @SubscribeEvent
    public static void onMenuClose(PlayerContainerEvent.Close event) {
        LootLibrary.onMenuClose(event);
    }
}

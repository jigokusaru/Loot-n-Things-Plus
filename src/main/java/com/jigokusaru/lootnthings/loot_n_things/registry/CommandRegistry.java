package com.jigokusaru.lootnthings.loot_n_things.registry;

import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.fml.common.EventBusSubscriber;

public class CommandRegistry {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        // This is where we will call our specific command builders
        ModCommands.register(event.getDispatcher());
    }
}
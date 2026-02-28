package com.jigokusaru.lootnthings.loot_n_things;

import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import com.jigokusaru.lootnthings.loot_n_things.economy.IEconomyAdapter;
import com.jigokusaru.lootnthings.loot_n_things.event.ChestEventHandler;
import com.jigokusaru.lootnthings.loot_n_things.event.ForgeEvents;
import com.jigokusaru.lootnthings.loot_n_things.event.LootBagListener;
import com.jigokusaru.lootnthings.loot_n_things.event.ModEvents;
import com.jigokusaru.lootnthings.loot_n_things.registry.ModComponents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

/**
 * The main class for the Loot and Things Plus mod.
 * This class is the entry point for the mod and is responsible for
 * registering all necessary components and event handlers to their respective event buses.
 */
@Mod(Loot_n_things.MODID)
public class Loot_n_things {
    public static final String MODID = "loot_n_things";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    /**
     * A static reference to the active economy adapter.
     * This will be null if no compatible economy system is found or configured.
     * It is populated during the FMLCommonSetupEvent in ModEvents.
     */
    public static IEconomyAdapter economy;

    public Loot_n_things(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Loot and Things Plus: Logic Engaged.");

        // --- CONFIG REGISTRATION ---
        // Registers the common config file for the mod. NeoForge handles loading and generating this file.
        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC, "loot_n_things-common.toml");

        // --- MOD EVENT BUS LISTENERS ---
        // These listeners handle events specific to the mod's loading lifecycle (e.g., setup, registry creation).
        // We register our static event handler class for mod-specific events.
        modEventBus.register(ModEvents.class);
        // We register our custom data components.
        ModComponents.register(modEventBus);
        
        // --- FORGE EVENT BUS LISTENERS ---
        // These listeners handle general game events that happen during gameplay (e.g., player interactions, server ticks).
        // We register our static event handler classes for game-wide events.
        NeoForge.EVENT_BUS.register(ForgeEvents.class);
        NeoForge.EVENT_BUS.register(LootBagListener.class);
        // We register an instance of ChestEventHandler because it is not a static class.
        NeoForge.EVENT_BUS.register(new ChestEventHandler());
    }
}

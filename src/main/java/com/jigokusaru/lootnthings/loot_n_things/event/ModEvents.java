package com.jigokusaru.lootnthings.loot_n_things.event;

import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.economy.CommandEconomyAdapter;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

/**
 * Handles events on the MOD event bus.
 * These events are fired during the mod loading process.
 */
public class ModEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    /**
     * Fired during the common setup phase.
     * This is the ideal time to perform initialization that depends on configs being loaded.
     */
    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        // Use enqueueWork to ensure thread safety for operations that might not be thread-safe.
        event.enqueueWork(() -> {
            // Initialize default JSON files in the config folder.
            LootLibrary.initConfig();
            // Set up the economy adapter based on the loaded config.
            setupEconomyAdapter();
        });
    }

    /**
     * Checks the config to see if a command-based economy should be used.
     */
    private static void setupEconomyAdapter() {
        if (Config.COMMON.commandBasedEconomyEnabled.get()) {
            LOGGER.info("Command-based economy is enabled. Using config settings.");
            Loot_n_things.economy = new CommandEconomyAdapter();
        } else {
            LOGGER.info("No supported economy mod found or enabled. Economy loot will be disabled.");
        }
    }
}

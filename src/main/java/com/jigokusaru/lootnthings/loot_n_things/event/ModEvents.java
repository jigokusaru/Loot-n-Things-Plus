package com.jigokusaru.lootnthings.loot_n_things.event;

import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.economy.CommandEconomyAdapter;
import com.jigokusaru.lootnthings.loot_n_things.util.PermissionManager;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.event.lifecycle.FMLCommonSetupEvent;
import org.slf4j.Logger;

public class ModEvents {
    private static final Logger LOGGER = LogUtils.getLogger();

    @SubscribeEvent
    public static void onCommonSetup(FMLCommonSetupEvent event) {
        event.enqueueWork(() -> {
            LootLibrary.initConfig();
            setupEconomyAdapter();
            PermissionManager.init();
        });
    }

    private static void setupEconomyAdapter() {
        if (Config.COMMON.commandBasedEconomyEnabled.get()) {
            LOGGER.info("Command-based economy is enabled. Using config settings.");
            Loot_n_things.economy = new CommandEconomyAdapter();
        } else {
            LOGGER.info("No supported economy mod found or enabled. Economy loot will be disabled.");
        }
    }
}

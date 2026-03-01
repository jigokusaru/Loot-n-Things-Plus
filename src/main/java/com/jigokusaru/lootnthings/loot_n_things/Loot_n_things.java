package com.jigokusaru.lootnthings.loot_n_things;

import com.jigokusaru.lootnthings.loot_n_things.config.Config;
import com.jigokusaru.lootnthings.loot_n_things.economy.IEconomyAdapter;
import com.jigokusaru.lootnthings.loot_n_things.event.ChestEventHandler;
import com.jigokusaru.lootnthings.loot_n_things.event.ForgeEvents;
import com.jigokusaru.lootnthings.loot_n_things.event.LootBagListener;
import com.jigokusaru.lootnthings.loot_n_things.event.ModEvents;
import com.mojang.logging.LogUtils;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(Loot_n_things.MODID)
public class Loot_n_things {
    public static final String MODID = "loot_n_things";
    public static final Logger LOGGER = LogUtils.getLogger();
    
    public static IEconomyAdapter economy;

    public Loot_n_things(IEventBus modEventBus, ModContainer modContainer) {
        LOGGER.info("Loot and Things Plus: Logic Engaged.");

        modContainer.registerConfig(ModConfig.Type.COMMON, Config.COMMON_SPEC, "loot_n_things-common.toml");

        modEventBus.register(ModEvents.class);
        
        NeoForge.EVENT_BUS.register(ForgeEvents.class);
        NeoForge.EVENT_BUS.register(LootBagListener.class);
        NeoForge.EVENT_BUS.register(new ChestEventHandler());
    }
}

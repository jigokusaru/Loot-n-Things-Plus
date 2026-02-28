package com.jigokusaru.lootnthings.loot_n_things.registry;

import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import net.minecraft.core.registries.Registries;
import net.minecraft.core.component.DataComponentType;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.registries.DeferredRegister;
import com.mojang.serialization.Codec;
import net.minecraft.network.codec.ByteBufCodecs;
import java.util.function.Supplier;

/**
 * Registers all custom Data Components for the mod.
 * Data Components are the modern way to attach custom data to ItemStacks.
 */
public class ModComponents {
    public static final DeferredRegister<DataComponentType<?>> COMPONENTS =
            DeferredRegister.create(Registries.DATA_COMPONENT_TYPE, Loot_n_things.MODID);

    /**
     * This component is attached to key items. It stores a string representing
     * the loot tier the key is for (e.g., "chests/epic").
     */
    public static final Supplier<DataComponentType<String>> LOOT_KEY =
            COMPONENTS.register("loot_key", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    /**
     * This component is attached to loot bag items. It stores a string representing
     * the loot tier of the bag (e.g., "bags/common").
     */
    public static final Supplier<DataComponentType<String>> LNT_BAG_TIER =
            COMPONENTS.register("lnt_bag_tier", () -> DataComponentType.<String>builder()
                    .persistent(Codec.STRING)
                    .networkSynchronized(ByteBufCodecs.STRING_UTF8)
                    .build());

    public static void register(IEventBus eventBus) {
        COMPONENTS.register(eventBus);
    }
}

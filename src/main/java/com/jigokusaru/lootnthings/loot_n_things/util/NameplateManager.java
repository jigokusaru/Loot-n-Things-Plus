package com.jigokusaru.lootnthings.loot_n_things.util;

import com.google.gson.JsonObject;
import com.jigokusaru.lootnthings.loot_n_things.Loot_n_things;
import com.jigokusaru.lootnthings.loot_n_things.core.LootLibrary;
import com.jigokusaru.lootnthings.loot_n_things.core.LootResolver;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.DoubleTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Display.TextDisplay;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.phys.AABB;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class NameplateManager {

    private static final String NAMEPLATE_TAG = "lnt_nameplate";
    private static final Set<BlockEntity> LOADED_LOOT_CHESTS = Collections.synchronizedSet(new HashSet<>());

    public static void add(BlockEntity be) {
        LOADED_LOOT_CHESTS.add(be);
    }

    public static void remove(BlockEntity be) {
        LOADED_LOOT_CHESTS.remove(be);
    }

    public static void createOrUpdate(ServerLevel level, BlockPos pos, BlockEntity be) {
        String tierPath = be.getPersistentData().getString("lnt_tier");
        if (tierPath.isEmpty()) return;

        removeDisplay(level, pos); 

        JsonObject json = LootLibrary.getLootFile(tierPath);
        if (json != null && json.has("display_name")) {
            String displayName = json.get("display_name").getAsString();
            Component newName = LootResolver.resolveComponent(displayName, null, json, null, null, tierPath);
            
            TextDisplay textDisplay = EntityType.TEXT_DISPLAY.create(level);
            if (textDisplay != null) {
                CompoundTag displayData = new CompoundTag();
                ListTag posList = new ListTag();
                posList.add(DoubleTag.valueOf(pos.getX() + 0.5));
                posList.add(DoubleTag.valueOf(pos.getY() + 1.1));
                posList.add(DoubleTag.valueOf(pos.getZ() + 0.5));
                
                displayData.put("Pos", posList);
                displayData.putString("text", Component.Serializer.toJson(newName, level.registryAccess()));
                displayData.putString("billboard", "center");
                displayData.putInt("background", 0x80000000);
                textDisplay.load(displayData);
                
                // This is the correct way to add a persistent tag for identification.
                textDisplay.getPersistentData().putBoolean(NAMEPLATE_TAG, true);
                
                level.addFreshEntity(textDisplay);
            }
        }
        
        be.setChanged();
        level.sendBlockUpdated(pos, be.getBlockState(), be.getBlockState(), 3);
    }

    public static void removeDisplay(ServerLevel level, BlockPos pos) {
        AABB searchBox = new AABB(pos).inflate(1.5);
        List<TextDisplay> displays = level.getEntitiesOfClass(TextDisplay.class, searchBox);
        for (TextDisplay display : displays) {
            // Now this check will correctly find the entity.
            if (display.getPersistentData().getBoolean(NAMEPLATE_TAG)) {
                display.kill();
            }
        }
    }

    public static void refreshAll() {
        Loot_n_things.LOGGER.info("Refreshing all loaded loot chest displays...");
        for (BlockEntity be : List.copyOf(LOADED_LOOT_CHESTS)) {
            if (be.getLevel() instanceof ServerLevel level && !be.isRemoved()) {
                createOrUpdate(level, be.getBlockPos(), be);
            }
        }
    }
}

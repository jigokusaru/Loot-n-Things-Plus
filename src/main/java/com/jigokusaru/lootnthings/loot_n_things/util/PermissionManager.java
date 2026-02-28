package com.jigokusaru.lootnthings.loot_n_things.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.neoforged.fml.ModList;

public class PermissionManager {

    private static final boolean LUCKPERMS_INSTALLED = ModList.get().isLoaded("luckperms");
    private static LuckPerms luckPermsApi;

    public static void init() {
        if (LUCKPERMS_INSTALLED) {
            try {
                luckPermsApi = LuckPermsProvider.get();
            } catch (IllegalStateException e) {
                // API not ready yet, will try again on first use.
            }
        }
    }

    public static boolean hasPermission(CommandSourceStack source, String permission) {
        if (source.getEntity() instanceof ServerPlayer player) {
            return hasPermission(player, permission);
        }
        // For console or non-player command sources, default to operator level 2.
        return source.hasPermission(2);
    }

    public static boolean hasPermission(Player player, String permission) {
        if (LUCKPERMS_INSTALLED) {
            if (luckPermsApi == null) {
                try {
                    luckPermsApi = LuckPermsProvider.get();
                } catch (IllegalStateException e) {
                    return player.hasPermission(2); // Fallback if API is still not ready
                }
            }
            User user = luckPermsApi.getUserManager().getUser(player.getUUID());
            if (user != null) {
                return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            }
            return false;
        } else {
            // Fallback to default OP check if LuckPerms is not installed
            return player.hasPermission(2);
        }
    }
}

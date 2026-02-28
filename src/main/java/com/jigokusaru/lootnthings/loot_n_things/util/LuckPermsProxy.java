package com.jigokusaru.lootnthings.loot_n_things.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.minecraft.server.level.ServerPlayer;

/**
 * A proxy class to safely interact with the LuckPerms API.
 * This class should ONLY be loaded or called if LuckPerms is present.
 */
public class LuckPermsProxy {

    private static LuckPerms luckPermsApi;

    private static LuckPerms getApi() {
        if (luckPermsApi == null) {
            luckPermsApi = LuckPermsProvider.get();
        }
        return luckPermsApi;
    }

    public static boolean checkPermission(ServerPlayer player, String permission) {
        try {
            User user = getApi().getUserManager().getUser(player.getUUID());
            if (user != null) {
                return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
            }
        } catch (IllegalStateException e) {
            // This can happen if LuckPerms is not yet loaded. Fallback to OP check.
            return player.createCommandSourceStack().hasPermission(2);
        }
        return false;
    }
}

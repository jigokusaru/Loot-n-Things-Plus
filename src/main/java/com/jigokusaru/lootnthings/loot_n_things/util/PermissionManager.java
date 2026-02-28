package com.jigokusaru.lootnthings.loot_n_things.util;

import net.luckperms.api.LuckPerms;
import net.luckperms.api.LuckPermsProvider;
import net.luckperms.api.model.user.User;
import net.luckperms.api.node.Node;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.fml.ModList;

public class PermissionManager {

    private static final boolean LUCKPERMS_INSTALLED = ModList.get().isLoaded("luckperms");
    private static LuckPerms luckPermsApi;

    public static void init() {
        if (LUCKPERMS_INSTALLED) {
            try {
                luckPermsApi = LuckPermsProvider.get();
            } catch (IllegalStateException e) {
                // This can happen if LuckPerms is present but not yet loaded.
                // It should be available by the time commands are used.
            }
        }
    }

    public static boolean hasPermission(CommandSourceStack source, String permission) {
        if (source.getEntity() instanceof ServerPlayer player) {
            if (LUCKPERMS_INSTALLED) {
                if (luckPermsApi == null) {
                    try {
                        luckPermsApi = LuckPermsProvider.get();
                    } catch (IllegalStateException e) {
                        return source.hasPermission(2); // Fallback if API is still not ready
                    }
                }
                User user = luckPermsApi.getUserManager().getUser(player.getUUID());
                if (user != null) {
                    return user.getCachedData().getPermissionData().checkPermission(permission).asBoolean();
                }
                return false; // Should not happen
            } else {
                // Fallback to default OP check if LuckPerms is not installed
                return source.hasPermission(2);
            }
        }
        return source.hasPermission(2); // True for console
    }
}

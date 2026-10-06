package com.raresb.professions.common;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import org.bukkit.permissions.Permissible;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * Per-feature permission nodes such as {@code paths.path.lancer}, all granted by default, plus a
 * {@code paths.path.*} parent. A permissions plugin (LuckPerms…) can then deny single features.
 * Undeclared nodes would default to op-only, which is why they are registered here.
 */
public final class Perms {
    private Perms() {
    }

    public static void register(JavaPlugin plugin, String prefix, Collection<String> ids, String what) {
        PluginManager manager = plugin.getServer().getPluginManager();
        Map<String, Boolean> children = new LinkedHashMap<>();
        for (String id : ids) {
            String node = prefix + "." + id;
            children.put(node, true);
            if (manager.getPermission(node) == null) {
                manager.addPermission(new Permission(node, "Use the " + id + " " + what + ".", PermissionDefault.TRUE));
            }
        }
        String wildcard = prefix + ".*";
        if (manager.getPermission(wildcard) == null) {
            manager.addPermission(new Permission(wildcard, "Use every " + what + ".", PermissionDefault.TRUE, children));
        }
    }

    public static boolean has(Permissible who, String prefix, String id) {
        return who.hasPermission(prefix + "." + id);
    }
}

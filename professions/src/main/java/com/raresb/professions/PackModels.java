package com.raresb.professions;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;
import org.bukkit.plugin.Plugin;

/**
 * Which custom icons the server resource pack really has. The Hud plugin sends
 * {@code plugins/Hud/pack.zip} to players; an icon that isn't in it would show up as a missing
 * texture, so menus only use icons listed here (the vanilla item is shown otherwise).
 */
final class PackModels {
    private static final String PREFIX = "assets/raresb/items/";
    private static Set<String> models = Set.of();
    private static long loadedStamp = -1;

    private PackModels() {
    }

    static boolean has(Plugin plugin, String model) {
        File pack = new File(plugin.getDataFolder().getParentFile(), "Hud/pack.zip");
        long stamp = pack.isFile() ? pack.lastModified() : 0;
        if (stamp != loadedStamp) {
            loadedStamp = stamp;
            Set<String> found = new HashSet<>();
            if (stamp != 0) {
                try (ZipFile zip = new ZipFile(pack)) {
                    zip.stream().map(ZipEntry::getName)
                            .filter(name -> name.startsWith(PREFIX) && name.endsWith(".json"))
                            .forEach(name -> found.add(name.substring(PREFIX.length(), name.length() - 5)));
                } catch (IOException e) {
                    plugin.getLogger().warning("Could not read the resource pack: " + e.getMessage());
                }
            }
            models = found;
        }
        return models.contains(model);
    }
}

package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Bukkit;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Scanner;

public class UpdateChecker {
    private final MagicBlockPlugin plugin;
    private final int resourceId;

    public UpdateChecker(MagicBlockPlugin plugin, int resourceId) {
        this.plugin = plugin;
        this.resourceId = resourceId;
    }

    public void checkForUpdates() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    String latestVersion = scanner.next();
                    String currentVersion = plugin.getDescription().getVersion();

                    // 比较版本号
                    if (!currentVersion.equals(latestVersion)) {
                        plugin.getLogger().info(plugin.getMessage("general.update-found", latestVersion));
                        plugin.getLogger().info(plugin.getMessage("general.current-version", currentVersion));
                        plugin.getLogger().info(plugin.getMessage("general.download-link", resourceId));
                    } else {
                        plugin.getLogger().info(plugin.getMessage("general.up-to-date"));
                    }
                }
            } catch (IOException e) {
                plugin.getLogger().warning(plugin.getMessage("general.update-check-failed", e.getMessage()));
                if (plugin.getConfig().getBoolean("debug-mode")) {
                    e.printStackTrace();
                }
            }
        });
    }
} 
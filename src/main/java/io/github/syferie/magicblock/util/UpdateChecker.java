package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;

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
        plugin.getFoliaLib().getImpl().runAsync(task -> {
            try (InputStream inputStream = new URL("https://api.spigotmc.org/legacy/update.php?resource=" + resourceId).openStream();
                 Scanner scanner = new Scanner(inputStream)) {
                if (scanner.hasNext()) {
                    String latestVersion = scanner.next();
                    String currentVersion = plugin.getDescription().getVersion();

                    // 智能版本比较
                    int comparison = compareVersions(currentVersion, latestVersion);
                    if (comparison < 0) {
                        // 当前版本较旧，有新版本可用
                        plugin.getLogger().info(plugin.getMessage("general.update-found", latestVersion));
                        plugin.getLogger().info(plugin.getMessage("general.current-version", currentVersion));
                        plugin.getLogger().info(plugin.getMessage("general.download-link", resourceId));
                    } else if (comparison > 0) {
                        // 当前版本较新（开发版本）
                        plugin.getLogger().info(plugin.getMessage("general.dev-version", currentVersion, latestVersion));
                    } else {
                        // 版本相同
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

    /**
     * 智能版本比较方法
     * 支持语义化版本号格式：major.minor.patch.build
     *
     * @param version1 当前版本
     * @param version2 远程版本
     * @return 负数：version1 < version2，0：相等，正数：version1 > version2
     */
    protected int compareVersions(String version1, String version2) {
        if (version1 == null || version2 == null) {
            return version1 == null ? (version2 == null ? 0 : -1) : 1;
        }

        // 移除可能的前缀（如 "v"）
        version1 = version1.replaceFirst("^v", "");
        version2 = version2.replaceFirst("^v", "");

        // 分割版本号
        String[] parts1 = version1.split("\\.");
        String[] parts2 = version2.split("\\.");

        // 获取最大长度，用于比较
        int maxLength = Math.max(parts1.length, parts2.length);

        for (int i = 0; i < maxLength; i++) {
            // 获取版本号的各个部分，如果不存在则默认为0
            int part1 = i < parts1.length ? parseVersionPart(parts1[i]) : 0;
            int part2 = i < parts2.length ? parseVersionPart(parts2[i]) : 0;

            if (part1 < part2) {
                return -1;
            } else if (part1 > part2) {
                return 1;
            }
        }

        return 0; // 版本相同
    }

    /**
     * 解析版本号的单个部分
     * 处理纯数字和包含字母的版本号（如 "1.0.0-SNAPSHOT"）
     */
    protected int parseVersionPart(String part) {
        if (part == null || part.isEmpty()) {
            return 0;
        }

        try {
            // 尝试解析为纯数字
            return Integer.parseInt(part);
        } catch (NumberFormatException e) {
            // 如果包含非数字字符，提取数字部分
            StringBuilder numPart = new StringBuilder();
            for (char c : part.toCharArray()) {
                if (Character.isDigit(c)) {
                    numPart.append(c);
                } else {
                    break; // 遇到非数字字符就停止
                }
            }

            if (numPart.length() > 0) {
                return Integer.parseInt(numPart.toString());
            } else {
                return 0;
            }
        }
    }
}
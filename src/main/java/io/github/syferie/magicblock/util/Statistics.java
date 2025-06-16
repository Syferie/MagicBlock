package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.io.IOException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.UUID;

public class Statistics {
    private final MagicBlockPlugin plugin;
    private final File statsFile;
    private FileConfiguration stats;
    private final ConcurrentHashMap<UUID, Integer> blockUses = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<UUID, Integer> foodUses = new ConcurrentHashMap<>();

    // 性能优化：批量保存和缓存
    private volatile boolean needsSave = false;
    private long lastSaveTime = 0;

    public Statistics(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.statsFile = new File(plugin.getDataFolder(), "stats.yml");
        loadStats();
    }

    private void loadStats() {
        if (!statsFile.exists()) {
            try {
                statsFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建统计文件: " + e.getMessage());
                return;
            }
        }
        stats = YamlConfiguration.loadConfiguration(statsFile);
    }

    public void saveStats() {
        if (stats == null) return;
        
        try {
            stats.save(statsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存统计数据: " + e.getMessage());
        }
    }

    public void logBlockUse(Player player, ItemStack block) {
        UUID playerUUID = player.getUniqueId();
        String path = "blocks." + playerUUID;
        int uses = stats.getInt(path, 0) + 1;
        stats.set(path, uses);
        blockUses.put(playerUUID, uses);

        // 性能优化：智能保存策略
        scheduleSmartSave(uses);
    }

    public void logFoodUse(Player player, ItemStack food) {
        UUID playerUUID = player.getUniqueId();
        String path = "foods." + playerUUID;
        int uses = stats.getInt(path, 0) + 1;
        stats.set(path, uses);
        foodUses.put(playerUUID, uses);

        // 性能优化：智能保存策略
        scheduleSmartSave(uses);
    }

    // 性能优化：智能保存策略
    private void scheduleSmartSave(int totalUses) {
        needsSave = true;
        long currentTime = System.currentTimeMillis();

        // 从配置读取性能设置
        int batchThreshold = plugin.getConfig().getInt("performance.statistics.batch-threshold", 50);
        long saveInterval = plugin.getConfig().getLong("performance.statistics.save-interval", 30000);

        // 条件1：达到批量保存阈值
        // 条件2：距离上次保存超过指定时间间隔
        if (totalUses % batchThreshold == 0 ||
            (currentTime - lastSaveTime) > saveInterval) {

            plugin.getFoliaLib().getScheduler().runAsync(wrappedTask -> {
                if (needsSave) {
                    saveStats();
                    needsSave = false;
                    lastSaveTime = System.currentTimeMillis();
                }
            });
        }
    }

    public int getBlockUses(UUID playerUUID) {
        return blockUses.getOrDefault(playerUUID, stats.getInt("blocks." + playerUUID, 0));
    }

    public int getFoodUses(UUID playerUUID) {
        return foodUses.getOrDefault(playerUUID, stats.getInt("foods." + playerUUID, 0));
    }
} 
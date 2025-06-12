package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.concurrent.atomic.AtomicLong;

/**
 * 魔法方块防刷检测器
 * 在玩家切换到魔法方块时检测并移除重复方块：
 * 1. 检测使用者自己背包中的重复方块（除了当前手持的）
 * 2. 检测其他玩家背包中的重复方块
 * 注意：只检测背包，不检测末影箱（末影箱中的方块无法直接使用）
 */
public class DuplicateBlockDetector implements Listener {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey blockIdKey;
    
    // 性能统计
    private final AtomicLong duplicateChecks = new AtomicLong(0);
    private final AtomicLong duplicatesFound = new AtomicLong(0);
    private final AtomicLong duplicatesRemoved = new AtomicLong(0);
    
    public DuplicateBlockDetector(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.blockIdKey = new NamespacedKey(plugin, "block_id");
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        ItemStack newItem = player.getInventory().getItem(event.getNewSlot());
        
        if (newItem != null && plugin.getBlockManager().isMagicBlock(newItem)) {
            // 确保方块有ID（兼容旧版本）
            String blockId = plugin.getOrCreateBlockId(newItem);
            if (blockId != null) {
                // 异步检测重复方块
                plugin.getFoliaLib().getScheduler().runAsync(task -> {
                    detectAndRemoveDuplicates(player, blockId);
                });
            }
        }
    }
    
    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerSwapHandItems(PlayerSwapHandItemsEvent event) {
        if (event.isCancelled()) return;
        
        Player player = event.getPlayer();
        ItemStack offHandItem = event.getOffHandItem();
        
        if (offHandItem != null && plugin.getBlockManager().isMagicBlock(offHandItem)) {
            // 确保方块有ID（兼容旧版本）
            String blockId = plugin.getOrCreateBlockId(offHandItem);
            if (blockId != null) {
                // 异步检测重复方块
                plugin.getFoliaLib().getScheduler().runAsync(task -> {
                    detectAndRemoveDuplicates(player, blockId);
                });
            }
        }
    }
    
    /**
     * 检测并移除重复的魔法方块
     */
    private void detectAndRemoveDuplicates(Player currentPlayer, String blockId) {
        long startTime = System.nanoTime();
        duplicateChecks.incrementAndGet();

        int duplicatesRemovedInThisCheck = 0;

        // 🆕 首先检查使用者自己背包中的重复方块（除了当前手持的）
        duplicatesRemovedInThisCheck += removeDuplicatesFromCurrentPlayerInventory(
            currentPlayer, blockId
        );

        // 然后检查其他在线玩家的背包
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            if (onlinePlayer.equals(currentPlayer)) continue;

            // 只检查玩家背包（末影箱中的方块无法直接使用，无需检测）
            duplicatesRemovedInThisCheck += removeDuplicatesFromInventory(
                onlinePlayer, blockId, "背包"
            );
        }
        
        if (duplicatesRemovedInThisCheck > 0) {
            duplicatesFound.addAndGet(duplicatesRemovedInThisCheck);
            duplicatesRemoved.addAndGet(duplicatesRemovedInThisCheck);
            
            // 记录到日志
            plugin.getLogger().info(String.format(
                "检测到并移除了 %d 个重复的魔法方块 (ID: %s, 触发玩家: %s)",
                duplicatesRemovedInThisCheck, blockId, currentPlayer.getName()
            ));
            
            // 发送消息给管理员（如果配置启用）
            if (plugin.getConfig().getBoolean("anti-duplication.notify-admins", true)) {
                String message = plugin.getLanguageManager().getMessage("anti-duplication.duplicates-removed")
                    .replace("%amount%", String.valueOf(duplicatesRemovedInThisCheck))
                    .replace("%player%", currentPlayer.getName());

                for (Player admin : Bukkit.getOnlinePlayers()) {
                    if (admin.hasPermission("magicblock.admin")) {
                        admin.sendMessage(plugin.getLanguageManager().getMessage("general.prefix") + message);
                    }
                }
            }
        }
        
        // 性能监控
        long duration = (System.nanoTime() - startTime) / 1_000_000; // 转换为毫秒
        plugin.getPerformanceMonitor().recordDuplicateCheck(duration);
        
        plugin.debug(String.format(
            "重复检测完成 - 耗时: %dms, 检查玩家数: %d (包括自己), 移除重复: %d (仅检测背包)",
            duration, Bukkit.getOnlinePlayers().size(), duplicatesRemovedInThisCheck
        ));
    }
    
    /**
     * 从玩家背包中移除重复方块
     */
    private int removeDuplicatesFromInventory(Player player, String targetBlockId, String inventoryType) {
        int removedCount = 0;
        ItemStack[] contents = player.getInventory().getContents();
        
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && plugin.getBlockManager().isMagicBlock(item)) {
                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String itemBlockId = meta.getPersistentDataContainer().get(
                        blockIdKey, PersistentDataType.STRING
                    );
                    
                    if (targetBlockId.equals(itemBlockId)) {
                        // 找到重复方块，移除它
                        player.getInventory().setItem(i, null);
                        removedCount++;
                        
                        plugin.debug(String.format(
                            "从玩家 %s 的%s中移除重复方块 (ID: %s)",
                            player.getName(), inventoryType, targetBlockId
                        ));
                    }
                }
            }
        }
        
        return removedCount;
    }

    /**
     * 从当前玩家背包中移除重复方块（除了当前手持的方块）
     */
    private int removeDuplicatesFromCurrentPlayerInventory(Player player, String targetBlockId) {
        int removedCount = 0;
        ItemStack[] contents = player.getInventory().getContents();
        ItemStack mainHandItem = player.getInventory().getItemInMainHand();
        ItemStack offHandItem = player.getInventory().getItemInOffHand();

        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && plugin.getBlockManager().isMagicBlock(item)) {
                // 跳过当前手持的方块（主手和副手）
                if (item.equals(mainHandItem) || item.equals(offHandItem)) {
                    continue;
                }

                ItemMeta meta = item.getItemMeta();
                if (meta != null) {
                    String itemBlockId = meta.getPersistentDataContainer().get(
                        blockIdKey, PersistentDataType.STRING
                    );

                    if (targetBlockId.equals(itemBlockId)) {
                        // 找到重复方块，移除它
                        player.getInventory().setItem(i, null);
                        removedCount++;

                        plugin.debug(String.format(
                            "从玩家 %s 的背包中移除重复方块 (ID: %s, 自己的重复方块)",
                            player.getName(), targetBlockId
                        ));
                    }
                }
            }
        }

        return removedCount;
    }



    /**
     * 获取性能统计信息
     */
    public String getPerformanceStats() {
        return String.format(
            "重复检测统计 - 总检测次数: %d, 发现重复: %d, 移除重复: %d",
            duplicateChecks.get(), duplicatesFound.get(), duplicatesRemoved.get()
        );
    }
    
    /**
     * 重置统计数据
     */
    public void resetStats() {
        duplicateChecks.set(0);
        duplicatesFound.set(0);
        duplicatesRemoved.set(0);
    }
}

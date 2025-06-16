package io.github.syferie.magicblock.manager;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 魔法方块索引管理器
 * 实现高性能的魔法方块位置索引和查找
 * 
 * 性能优化策略：
 * 1. 内存索引：O(1) 快速查找
 * 2. 区块缓存：减少内存使用
 * 3. 持久化存储：数据安全保障
 * 4. 智能过滤：早期事件过滤
 */
public class MagicBlockIndexManager {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey magicBlockKey;
    
    // 第一层：全局内存索引 - 最快的查找
    private final Set<String> globalMagicBlockIndex = ConcurrentHashMap.newKeySet();
    
    // 第二层：区块级别索引 - 减少内存使用和提供区块过滤
    private final Map<String, Set<String>> chunkMagicBlocks = new ConcurrentHashMap<>();
    
    // 第三层：世界级别索引 - 用于快速判断世界是否有魔法方块
    private final Set<String> worldsWithMagicBlocks = ConcurrentHashMap.newKeySet();
    
    // 性能统计
    private long totalLookups = 0;
    private long cacheHits = 0;
    private long cacheMisses = 0;
    
    public MagicBlockIndexManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.magicBlockKey = new NamespacedKey(plugin, "magicblock_location");
        
        // 启动时加载现有的魔法方块索引
        loadExistingMagicBlocks();
        
        // 启动定期清理任务
        startCleanupTask();
    }
    
    /**
     * 注册魔法方块到索引系统
     * 当魔法方块被放置时调用
     */
    public void registerMagicBlock(Location location, ItemStack magicBlock) {
        String locationKey = serializeLocation(location);
        String chunkKey = getChunkKey(location);
        String worldName = location.getWorld().getName();
        
        // 1. 添加到全局索引
        globalMagicBlockIndex.add(locationKey);
        
        // 2. 添加到区块索引
        chunkMagicBlocks.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet())
                       .add(locationKey);
        
        // 3. 标记世界包含魔法方块
        worldsWithMagicBlocks.add(worldName);
        
        // 4. 持久化存储（异步）
        plugin.getFoliaLib().getScheduler().runAtLocation(location, task -> {
            saveToPersistentStorage(location, magicBlock);
        });
        
        plugin.debug("注册魔法方块: " + locationKey);
    }
    
    /**
     * 从索引系统移除魔法方块
     * 当魔法方块被破坏时调用
     */
    public void unregisterMagicBlock(Location location) {
        String locationKey = serializeLocation(location);
        String chunkKey = getChunkKey(location);
        
        // 1. 从全局索引移除
        boolean removed = globalMagicBlockIndex.remove(locationKey);
        
        if (removed) {
            // 2. 从区块索引移除
            Set<String> chunkBlocks = chunkMagicBlocks.get(chunkKey);
            if (chunkBlocks != null) {
                chunkBlocks.remove(locationKey);
                
                // 如果区块没有魔法方块了，清理区块索引
                if (chunkBlocks.isEmpty()) {
                    chunkMagicBlocks.remove(chunkKey);
                }
            }
            
            // 3. 检查世界是否还有魔法方块
            checkAndCleanupWorld(location.getWorld().getName());
            
            // 4. 从持久化存储移除（异步）
            plugin.getFoliaLib().getScheduler().runAtLocation(location, task -> {
                removeFromPersistentStorage(location);
            });
            
            plugin.debug("移除魔法方块: " + locationKey);
        }
    }
    
    /**
     * 超高性能的魔法方块检查
     * O(1) 时间复杂度
     */
    public boolean isMagicBlock(Location location) {
        totalLookups++;
        
        String locationKey = serializeLocation(location);
        boolean result = globalMagicBlockIndex.contains(locationKey);
        
        if (result) {
            cacheHits++;
        } else {
            cacheMisses++;
        }
        
        return result;
    }
    
    /**
     * 检查区块是否包含魔法方块
     * 用于早期事件过滤
     */
    public boolean chunkHasMagicBlocks(Location location) {
        String chunkKey = getChunkKey(location);
        return chunkMagicBlocks.containsKey(chunkKey);
    }
    
    /**
     * 检查世界是否包含魔法方块
     * 用于最早期的事件过滤
     */
    public boolean worldHasMagicBlocks(String worldName) {
        return worldsWithMagicBlocks.contains(worldName);
    }
    
    /**
     * 获取区块中的所有魔法方块位置
     */
    public Set<String> getMagicBlocksInChunk(Location location) {
        String chunkKey = getChunkKey(location);
        Set<String> chunkBlocks = chunkMagicBlocks.get(chunkKey);
        return chunkBlocks != null ? new HashSet<>(chunkBlocks) : new HashSet<>();
    }
    
    /**
     * 获取性能统计信息
     */
    public Map<String, Object> getPerformanceStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalMagicBlocks", globalMagicBlockIndex.size());
        stats.put("totalChunks", chunkMagicBlocks.size());
        stats.put("totalWorlds", worldsWithMagicBlocks.size());
        stats.put("totalLookups", totalLookups);
        stats.put("cacheHits", cacheHits);
        stats.put("cacheMisses", cacheMisses);
        
        double hitRate = totalLookups > 0 ? (double) cacheHits / totalLookups * 100 : 0;
        stats.put("cacheHitRate", hitRate);
        
        return stats;
    }
    
    // 辅助方法
    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," +
               loc.getBlockX() + "," +
               loc.getBlockY() + "," +
               loc.getBlockZ();
    }
    
    private String getChunkKey(Location loc) {
        return loc.getWorld().getName() + "_" + loc.getChunk().getX() + "_" + loc.getChunk().getZ();
    }
    
    private void checkAndCleanupWorld(String worldName) {
        // 检查世界是否还有魔法方块
        boolean hasBlocks = chunkMagicBlocks.keySet().stream()
                .anyMatch(chunkKey -> chunkKey.startsWith(worldName + "_"));
        
        if (!hasBlocks) {
            worldsWithMagicBlocks.remove(worldName);
        }
    }
    
    private void saveToPersistentStorage(Location location, ItemStack magicBlock) {
        // 保存到区块的持久化数据中
        String locationString = serializeLocation(location);
        PersistentDataContainer container = location.getChunk().getPersistentDataContainer();
        
        // 获取现有的位置列表
        String existingData = container.get(magicBlockKey, PersistentDataType.STRING);
        Set<String> locations = new HashSet<>();
        
        if (existingData != null && !existingData.isEmpty()) {
            locations.addAll(Arrays.asList(existingData.split(";")));
        }
        
        locations.add(locationString);
        
        // 保存更新后的位置列表
        String joinedLocations = String.join(";", locations);
        container.set(magicBlockKey, PersistentDataType.STRING, joinedLocations);
    }
    
    private void removeFromPersistentStorage(Location location) {
        String locationString = serializeLocation(location);
        PersistentDataContainer container = location.getChunk().getPersistentDataContainer();
        
        String existingData = container.get(magicBlockKey, PersistentDataType.STRING);
        if (existingData == null) return;
        
        Set<String> locations = new HashSet<>(Arrays.asList(existingData.split(";")));
        locations.remove(locationString);
        
        if (locations.isEmpty()) {
            container.remove(magicBlockKey);
        } else {
            String joinedLocations = String.join(";", locations);
            container.set(magicBlockKey, PersistentDataType.STRING, joinedLocations);
        }
    }
    
    private void loadExistingMagicBlocks() {
        plugin.getLogger().info("正在加载现有魔法方块索引...");
        
        int loadedCount = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Chunk chunk : world.getLoadedChunks()) {
                PersistentDataContainer container = chunk.getPersistentDataContainer();
                String locationsData = container.get(magicBlockKey, PersistentDataType.STRING);
                
                if (locationsData != null && !locationsData.isEmpty()) {
                    String[] locations = locationsData.split(";");
                    for (String locationStr : locations) {
                        try {
                            String[] parts = locationStr.split(",");
                            if (parts.length == 4) {
                                World locWorld = Bukkit.getWorld(parts[0]);
                                if (locWorld != null) {
                                    Location loc = new Location(locWorld, 
                                        Integer.parseInt(parts[1]),
                                        Integer.parseInt(parts[2]),
                                        Integer.parseInt(parts[3]));
                                    
                                    // 验证方块是否仍然存在
                                    Block block = loc.getBlock();
                                    if (!block.getType().isAir()) {
                                        // 添加到索引（不触发持久化）
                                        String locationKey = serializeLocation(loc);
                                        String chunkKey = getChunkKey(loc);
                                        
                                        globalMagicBlockIndex.add(locationKey);
                                        chunkMagicBlocks.computeIfAbsent(chunkKey, k -> ConcurrentHashMap.newKeySet())
                                                       .add(locationKey);
                                        worldsWithMagicBlocks.add(world.getName());
                                        
                                        loadedCount++;
                                    }
                                }
                            }
                        } catch (Exception e) {
                            plugin.debug("加载魔法方块位置时出错: " + locationStr + " - " + e.getMessage());
                        }
                    }
                }
            }
        }
        
        plugin.getLogger().info("已加载 " + loadedCount + " 个魔法方块到索引中");
    }
    
    private void startCleanupTask() {
        // 每5分钟清理一次无效的索引
        plugin.getFoliaLib().getScheduler().runTimer(() -> {
            cleanupInvalidEntries();
        }, 6000L, 6000L); // 5分钟 = 6000 ticks
    }
    
    private void cleanupInvalidEntries() {
        plugin.debug("开始清理无效的魔法方块索引...");
        
        int removedCount = 0;
        Iterator<String> iterator = globalMagicBlockIndex.iterator();
        
        while (iterator.hasNext()) {
            String locationKey = iterator.next();
            try {
                String[] parts = locationKey.split(",");
                if (parts.length == 4) {
                    World world = Bukkit.getWorld(parts[0]);
                    if (world != null) {
                        Location loc = new Location(world,
                            Integer.parseInt(parts[1]),
                            Integer.parseInt(parts[2]),
                            Integer.parseInt(parts[3]));
                        
                        // 检查方块是否仍然存在
                        if (loc.getBlock().getType().isAir()) {
                            // 方块不存在，从索引中移除
                            iterator.remove();
                            
                            // 同时从区块索引中移除
                            String chunkKey = getChunkKey(loc);
                            Set<String> chunkBlocks = chunkMagicBlocks.get(chunkKey);
                            if (chunkBlocks != null) {
                                chunkBlocks.remove(locationKey);
                                if (chunkBlocks.isEmpty()) {
                                    chunkMagicBlocks.remove(chunkKey);
                                }
                            }
                            
                            removedCount++;
                        }
                    }
                }
            } catch (Exception e) {
                // 无效的位置格式，移除
                iterator.remove();
                removedCount++;
            }
        }
        
        if (removedCount > 0) {
            plugin.debug("清理了 " + removedCount + " 个无效的魔法方块索引");
        }
    }
    
    /**
     * 重载索引系统
     */
    public void reload() {
        plugin.getLogger().info("重载魔法方块索引系统...");
        
        // 清空现有索引
        globalMagicBlockIndex.clear();
        chunkMagicBlocks.clear();
        worldsWithMagicBlocks.clear();
        
        // 重新加载
        loadExistingMagicBlocks();
        
        plugin.getLogger().info("魔法方块索引系统重载完成");
    }
}

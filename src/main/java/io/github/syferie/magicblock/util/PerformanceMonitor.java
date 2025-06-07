package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.command.CommandSender;

import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * 性能监控工具类
 * 用于跟踪和报告插件的性能指标
 */
public class PerformanceMonitor {
    private final MagicBlockPlugin plugin;

    // 性能计数器
    private final AtomicLong loreUpdates = new AtomicLong(0);
    private final AtomicLong loreCacheHits = new AtomicLong(0);
    private final AtomicLong loreCacheMisses = new AtomicLong(0);
    private final AtomicLong databaseOperations = new AtomicLong(0);
    private final AtomicLong asyncOperations = new AtomicLong(0);
    private final AtomicInteger activeTasks = new AtomicInteger(0);

    // 新增：位置检查性能计数器
    private final AtomicLong locationChecks = new AtomicLong(0);
    private final AtomicLong locationCacheHits = new AtomicLong(0);
    private final AtomicLong locationCacheMisses = new AtomicLong(0);
    private final AtomicLong physicsEvents = new AtomicLong(0);
    private final AtomicLong physicsEventsSkipped = new AtomicLong(0);

    // 时间统计
    private final AtomicLong totalLoreUpdateTime = new AtomicLong(0);
    private final AtomicLong totalDatabaseTime = new AtomicLong(0);
    private final AtomicLong totalLocationCheckTime = new AtomicLong(0);

    private final long startTime;
    
    public PerformanceMonitor(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.startTime = System.currentTimeMillis();
    }
    
    // 记录 Lore 更新
    public void recordLoreUpdate(long duration) {
        loreUpdates.incrementAndGet();
        totalLoreUpdateTime.addAndGet(duration);
    }
    
    // 记录缓存命中
    public void recordCacheHit() {
        loreCacheHits.incrementAndGet();
    }
    
    // 记录缓存未命中
    public void recordCacheMiss() {
        loreCacheMisses.incrementAndGet();
    }
    
    // 记录数据库操作
    public void recordDatabaseOperation(long duration) {
        databaseOperations.incrementAndGet();
        totalDatabaseTime.addAndGet(duration);
    }
    
    // 记录异步操作
    public void recordAsyncOperation() {
        asyncOperations.incrementAndGet();
    }
    
    // 增加活跃任务计数
    public void incrementActiveTasks() {
        activeTasks.incrementAndGet();
    }
    
    // 减少活跃任务计数
    public void decrementActiveTasks() {
        activeTasks.decrementAndGet();
    }

    // 新增：位置检查性能监控方法
    public void recordLocationCheck(long duration) {
        locationChecks.incrementAndGet();
        totalLocationCheckTime.addAndGet(duration);
    }

    public void recordLocationCacheHit() {
        locationCacheHits.incrementAndGet();
    }

    public void recordLocationCacheMiss() {
        locationCacheMisses.incrementAndGet();
    }

    public void recordPhysicsEvent() {
        physicsEvents.incrementAndGet();
    }

    public void recordPhysicsEventSkipped() {
        physicsEventsSkipped.incrementAndGet();
    }
    
    // 获取性能报告
    public void sendPerformanceReport(CommandSender sender) {
        long uptime = System.currentTimeMillis() - startTime;
        long uptimeSeconds = uptime / 1000;
        long uptimeMinutes = uptimeSeconds / 60;
        long uptimeHours = uptimeMinutes / 60;
        
        sender.sendMessage("§6=== MagicBlock 性能报告 ===");
        sender.sendMessage("§7运行时间: §a" + uptimeHours + "h " + (uptimeMinutes % 60) + "m " + (uptimeSeconds % 60) + "s");
        sender.sendMessage("");
        
        // Lore 性能统计
        long totalLoreOps = loreUpdates.get();
        long cacheHits = loreCacheHits.get();
        long cacheMisses = loreCacheMisses.get();
        double cacheHitRate = totalLoreOps > 0 ? (double) cacheHits / (cacheHits + cacheMisses) * 100 : 0;
        double avgLoreTime = totalLoreOps > 0 ? (double) totalLoreUpdateTime.get() / totalLoreOps : 0;
        
        sender.sendMessage("§6Lore 系统:");
        sender.sendMessage("§7  总更新次数: §a" + totalLoreOps);
        sender.sendMessage("§7  缓存命中率: §a" + String.format("%.1f%%", cacheHitRate));
        sender.sendMessage("§7  平均更新时间: §a" + String.format("%.2fms", avgLoreTime));
        sender.sendMessage("");
        
        // 位置检查性能统计
        long totalLocationOps = locationChecks.get();
        long locCacheHits = locationCacheHits.get();
        long locCacheMisses = locationCacheMisses.get();
        double locCacheHitRate = totalLocationOps > 0 ? (double) locCacheHits / (locCacheHits + locCacheMisses) * 100 : 0;
        double avgLocationTime = totalLocationOps > 0 ? (double) totalLocationCheckTime.get() / totalLocationOps : 0;

        sender.sendMessage("§6位置检查系统:");
        sender.sendMessage("§7  总检查次数: §a" + totalLocationOps);
        sender.sendMessage("§7  缓存命中率: §a" + String.format("%.1f%%", locCacheHitRate));
        sender.sendMessage("§7  平均检查时间: §a" + String.format("%.2fms", avgLocationTime));
        sender.sendMessage("");

        // 物理事件统计
        long totalPhysicsEvents = physicsEvents.get();
        long skippedPhysicsEvents = physicsEventsSkipped.get();
        double physicsSkipRate = totalPhysicsEvents > 0 ? (double) skippedPhysicsEvents / totalPhysicsEvents * 100 : 0;

        sender.sendMessage("§6物理事件优化:");
        sender.sendMessage("§7  总物理事件: §a" + totalPhysicsEvents);
        sender.sendMessage("§7  跳过事件数: §a" + skippedPhysicsEvents);
        sender.sendMessage("§7  优化跳过率: §a" + String.format("%.1f%%", physicsSkipRate));
        sender.sendMessage("");

        // 数据库性能统计
        long dbOps = databaseOperations.get();
        double avgDbTime = dbOps > 0 ? (double) totalDatabaseTime.get() / dbOps : 0;

        sender.sendMessage("§6数据库系统:");
        sender.sendMessage("§7  总操作次数: §a" + dbOps);
        sender.sendMessage("§7  平均操作时间: §a" + String.format("%.2fms", avgDbTime));
        sender.sendMessage("§7  异步操作次数: §a" + asyncOperations.get());
        sender.sendMessage("");

        // 任务调度统计
        sender.sendMessage("§6任务调度:");
        sender.sendMessage("§7  当前活跃任务: §a" + activeTasks.get());
        sender.sendMessage("");
        
        // 性能建议
        sender.sendMessage("§6性能建议:");
        boolean hasIssues = false;

        if (cacheHitRate < 50 && totalLoreOps > 100) {
            sender.sendMessage("§c  建议增加 Lore 缓存时间以提高命中率");
            hasIssues = true;
        }
        if (locCacheHitRate < 70 && totalLocationOps > 100) {
            sender.sendMessage("§c  建议增加位置缓存时间以提高位置检查性能");
            hasIssues = true;
        }
        if (physicsSkipRate < 30 && totalPhysicsEvents > 100) {
            sender.sendMessage("§c  建议启用物理事件优化以减少不必要的检查");
            hasIssues = true;
        }
        if (avgDbTime > 50 && dbOps > 10) {
            sender.sendMessage("§c  数据库操作较慢，建议检查数据库连接");
            hasIssues = true;
        }
        if (activeTasks.get() > 20) {
            sender.sendMessage("§c  活跃任务过多，可能存在性能问题");
            hasIssues = true;
        }

        // 正面反馈
        if (!hasIssues) {
            sender.sendMessage("§a  所有系统性能良好！");
        } else {
            if (cacheHitRate > 80 && avgLoreTime < 1.0) {
                sender.sendMessage("§a  Lore 系统性能良好！");
            }
            if (locCacheHitRate > 80 && avgLocationTime < 0.5) {
                sender.sendMessage("§a  位置检查系统性能良好！");
            }
            if (physicsSkipRate > 50) {
                sender.sendMessage("§a  物理事件优化效果显著！");
            }
        }
        
        sender.sendMessage("§6========================");
    }
    
    // 重置统计数据
    public void resetStats() {
        loreUpdates.set(0);
        loreCacheHits.set(0);
        loreCacheMisses.set(0);
        databaseOperations.set(0);
        asyncOperations.set(0);
        activeTasks.set(0);
        totalLoreUpdateTime.set(0);
        totalDatabaseTime.set(0);

        // 重置新增的统计数据
        locationChecks.set(0);
        locationCacheHits.set(0);
        locationCacheMisses.set(0);
        physicsEvents.set(0);
        physicsEventsSkipped.set(0);
        totalLocationCheckTime.set(0);
    }
    
    // 获取缓存命中率
    public double getCacheHitRate() {
        long hits = loreCacheHits.get();
        long misses = loreCacheMisses.get();
        long total = hits + misses;
        return total > 0 ? (double) hits / total * 100 : 0;
    }
    
    // 获取平均 Lore 更新时间
    public double getAverageLoreUpdateTime() {
        long updates = loreUpdates.get();
        return updates > 0 ? (double) totalLoreUpdateTime.get() / updates : 0;
    }
    
    // 获取平均数据库操作时间
    public double getAverageDatabaseTime() {
        long ops = databaseOperations.get();
        return ops > 0 ? (double) totalDatabaseTime.get() / ops : 0;
    }
}

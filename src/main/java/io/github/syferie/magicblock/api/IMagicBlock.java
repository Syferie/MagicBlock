package io.github.syferie.magicblock.api;

import org.bukkit.inventory.ItemStack;

/**
 * 魔法方块接口
 */
public interface IMagicBlock {
    /**
     * 设置使用次数
     * @param item 物品
     * @param times 次数
     */
    void setUseTimes(ItemStack item, int times);

    /**
     * 获取使用次数
     * @param item 物品
     * @return 剩余使用次数
     */
    int getUseTimes(ItemStack item);

    /**
     * 减少使用次数
     * @param item 物品
     * @return 剩余使用次数
     */
    int decrementUseTimes(ItemStack item);

    /**
     * 更新物品说明
     * @param item 物品
     * @param remainingTimes 剩余次数
     */
    void updateLore(ItemStack item, int remainingTimes);
} 
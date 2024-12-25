package io.github.syferie.magicblock.api;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

/**
 * 魔法食物接口
 */
public interface IMagicFood {
    /**
     * 创建魔法食物
     * @param material 食物类型
     * @return 魔法食物物品
     */
    ItemStack createMagicFood(Material material);

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

    /**
     * 检查是否是魔法食物
     * @param item 物品
     * @return 是否是魔法食物
     */
    boolean isMagicFood(ItemStack item);
} 
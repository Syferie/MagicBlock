package io.github.syferie.magicblock.util;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * 物品创建工具类，支持ItemsAdder自定义物品
 */
public class ItemCreator {
    private final MagicBlockPlugin plugin;
    
    public ItemCreator(MagicBlockPlugin plugin) {
        this.plugin = plugin;
    }
    
    /**
     * 根据配置创建物品
     * 支持原版材质和ItemsAdder自定义物品
     *
     * @param materialConfig 材质配置，可以是 "STONE" 或 "namespace:item_id" (如 "itemsadder:ruby_sword")
     * @param displayName 显示名称
     * @param lore 描述列表
     * @return 创建的物品
     */
    public ItemStack createItem(String materialConfig, String displayName, List<String> lore) {
        ItemStack item = null;
        
        // 检查是否是ItemsAdder物品
        if (materialConfig.contains(":")) {
            item = createItemsAdderItem(materialConfig);
        }
        
        // 如果ItemsAdder创建失败或不是ItemsAdder物品，使用原版材质
        if (item == null) {
            try {
                Material material = Material.valueOf(materialConfig.toUpperCase());
                item = new ItemStack(material);
            } catch (IllegalArgumentException e) {
                plugin.getLogger().warning("无效的材质配置: " + materialConfig + "，使用默认材质 STONE");
                item = new ItemStack(Material.STONE);
            }
        }
        
        // 设置物品元数据
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            if (displayName != null && !displayName.isEmpty()) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', displayName));
            }
            
            if (lore != null && !lore.isEmpty()) {
                List<String> coloredLore = new ArrayList<>();
                for (String line : lore) {
                    coloredLore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
                meta.setLore(coloredLore);
            }
            
            item.setItemMeta(meta);
        }
        
        return item;
    }
    
    /**
     * 创建ItemsAdder自定义物品
     *
     * @param itemId ItemsAdder物品ID，格式: "namespace:item_id" (如 "itemsadder:ruby_sword", "blocks_expansion:oak_crate")
     * @return 创建的物品，如果失败返回null
     */
    private ItemStack createItemsAdderItem(String itemId) {
        try {
            // 检查ItemsAdder是否存在
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
                plugin.debug("ItemsAdder插件未找到，无法创建自定义物品: " + itemId);
                return null;
            }

            plugin.debug("尝试创建ItemsAdder物品: " + itemId);

            // 使用反射调用ItemsAdder API
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("getInstance", String.class).invoke(null, itemId);

            if (customStack != null) {
                ItemStack itemStack = (ItemStack) customStackClass.getMethod("getItemStack").invoke(customStack);
                plugin.debug("成功创建ItemsAdder物品: " + itemId + " -> " + itemStack.getType());
                return itemStack;
            } else {
                plugin.debug("ItemsAdder物品不存在: " + itemId + "，请检查命名空间和物品ID是否正确");
                return null;
            }
        } catch (ClassNotFoundException e) {
            plugin.debug("ItemsAdder API类未找到，可能版本不兼容: " + e.getMessage());
            return null;
        } catch (Exception e) {
            plugin.debug("创建ItemsAdder物品失败: " + itemId + " - " + e.getMessage());
            plugin.debug("请确保使用正确的格式: namespace:item_id (例如: itemsadder:ruby_sword)");
            return null;
        }
    }
    
    /**
     * 检查物品是否匹配指定的材质配置
     * 
     * @param item 要检查的物品
     * @param materialConfig 材质配置
     * @return 是否匹配
     */
    public boolean matchesMaterial(ItemStack item, String materialConfig) {
        if (item == null) return false;
        
        // 检查ItemsAdder物品
        if (materialConfig.contains(":")) {
            return matchesItemsAdderItem(item, materialConfig);
        }
        
        // 检查原版材质
        try {
            Material material = Material.valueOf(materialConfig.toUpperCase());
            return item.getType() == material;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * 检查物品是否是指定的ItemsAdder物品
     * 
     * @param item 要检查的物品
     * @param itemId ItemsAdder物品ID
     * @return 是否匹配
     */
    private boolean matchesItemsAdderItem(ItemStack item, String itemId) {
        try {
            if (Bukkit.getPluginManager().getPlugin("ItemsAdder") == null) {
                return false;
            }
            
            Class<?> customStackClass = Class.forName("dev.lone.itemsadder.api.CustomStack");
            Object customStack = customStackClass.getMethod("byItemStack", ItemStack.class).invoke(null, item);
            
            if (customStack != null) {
                String namespacedID = (String) customStackClass.getMethod("getNamespacedID").invoke(customStack);
                return itemId.equals(namespacedID);
            }
            
            return false;
        } catch (Exception e) {
            plugin.debug("检查ItemsAdder物品失败: " + e.getMessage());
            return false;
        }
    }
}

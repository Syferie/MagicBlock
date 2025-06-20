package io.github.syferie.magicblock.gui;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.manager.FavoriteManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 收藏GUI
 * 显示玩家收藏的方块列表
 */
public class FavoriteGUI {
    private final MagicBlockPlugin plugin;
    private final FavoriteManager favoriteManager;
    private final GUIConfig guiConfig;
    
    // 页面状态管理
    private final Map<UUID, Integer> currentPage = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> originalItems = new ConcurrentHashMap<>();
    
    public FavoriteGUI(MagicBlockPlugin plugin, FavoriteManager favoriteManager) {
        this.plugin = plugin;
        this.favoriteManager = favoriteManager;
        this.guiConfig = new GUIConfig(plugin);
    }
    
    /**
     * 打开收藏GUI
     */
    public void openInventory(Player player) {
        // 检查使用权限
        if (!player.hasPermission("magicblock.use")) {
            plugin.sendMessage(player, "messages.no-permission-use");
            return;
        }
        
        // 记录原始物品
        originalItems.put(player.getUniqueId(), player.getInventory().getItemInMainHand().clone());
        // 重置页码
        currentPage.put(player.getUniqueId(), 1);
        // 打开界面
        updateInventory(player);
    }
    
    /**
     * 更新GUI内容
     */
    public void updateInventory(Player player) {
        // 设置GUI更新标志，防止在更新时清理数据
        GUIManager.setPlayerUpdatingGUI(player, true);
        
        String title = ChatColor.translateAlternateColorCodes('&', 
            plugin.getConfig().getString("gui.text.favorites-title", "&8⚡ &b我的收藏"));
        Inventory gui = Bukkit.createInventory(null, guiConfig.getSize(), title);
        
        UUID playerId = player.getUniqueId();
        int page = currentPage.getOrDefault(playerId, 1);
        
        List<Material> favorites = favoriteManager.getPlayerFavorites(player);
        
        if (favorites.isEmpty()) {
            // 显示空收藏提示
            ItemStack emptyItem = new ItemStack(Material.BARRIER);
            ItemMeta meta = emptyItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("gui.text.no-favorites", "&c暂无收藏")));
                meta.setLore(List.of(
                    ChatColor.translateAlternateColorCodes('&', 
                        plugin.getConfig().getString("gui.text.no-favorites-tip", "&7右键点击方块即可收藏"))
                ));
                emptyItem.setItemMeta(meta);
            }
            gui.setItem(22, emptyItem); // 中央位置
        } else {
            // 计算分页
            int itemsPerPage = calculateItemsPerPage();
            int totalPages = Math.max(1, (int) Math.ceil(favorites.size() / (double) itemsPerPage));
            
            plugin.debug("收藏GUI更新 - 玩家: " + player.getName() + ", 页面: " + page + "/" + totalPages + 
                        ", 每页物品数: " + itemsPerPage + ", 收藏数量: " + favorites.size());
            
            int startIndex = (page - 1) * itemsPerPage;
            int endIndex = Math.min(startIndex + itemsPerPage, favorites.size());
            
            // 添加收藏物品到可用槽位
            int currentSlot = 0;
            for (int i = startIndex; i < endIndex; i++) {
                // 跳过按钮槽位
                while (currentSlot < guiConfig.getSize() && guiConfig.isButtonSlot(currentSlot)) {
                    currentSlot++;
                }
                
                if (currentSlot < guiConfig.getSize()) {
                    Material material = favorites.get(i);
                    gui.setItem(currentSlot, createFavoriteBlock(material));
                    currentSlot++;
                } else {
                    break;
                }
            }
            
            // 添加导航按钮
            if (totalPages > 1) {
                gui.setItem(guiConfig.getPreviousPageSlot(), guiConfig.createPreviousPageButton(page > 1));
                gui.setItem(guiConfig.getNextPageSlot(), guiConfig.createNextPageButton(page < totalPages));
                gui.setItem(guiConfig.getPageInfoSlot(), guiConfig.createPageInfoButton(page, totalPages));
            }
        }
        
        // 添加返回按钮（使用关闭按钮的配置）
        gui.setItem(guiConfig.getCloseSlot(), createBackButton());
        
        // 添加自定义材质
        for (Map.Entry<String, GUIConfig.ButtonConfig> entry : guiConfig.getCustomMaterials().entrySet()) {
            String customKey = entry.getKey();
            GUIConfig.ButtonConfig config = entry.getValue();
            ItemStack customItem = guiConfig.createCustomMaterial(customKey);
            if (customItem != null && config.slot >= 0 && config.slot < guiConfig.getSize()) {
                gui.setItem(config.slot, customItem);
            }
        }
        
        player.openInventory(gui);
        
        // 清除GUI更新标志
        GUIManager.setPlayerUpdatingGUI(player, false);
    }
    
    /**
     * 创建收藏方块物品
     */
    private ItemStack createFavoriteBlock(Material material) {
        ItemStack block = new ItemStack(material);
        ItemMeta meta = block.getItemMeta();
        if (meta != null) {
            String blockName = plugin.getMinecraftLangManager().getItemStackName(block);
            String nameFormat = plugin.getConfig().getString("display.block-name-format", "&b✦ %s &b✦");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                String.format(nameFormat, blockName)));
            
            meta.setLore(List.of(
                ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("gui.text.favorite-select", "&7» 点击选择此收藏方块")),
                ChatColor.translateAlternateColorCodes('&', 
                    plugin.getConfig().getString("gui.text.favorite-remove", "&c» 右键点击取消收藏"))
            ));
            block.setItemMeta(meta);
        }
        return block;
    }
    
    /**
     * 创建返回按钮
     */
    private ItemStack createBackButton() {
        String material = plugin.getConfig().getString("gui.buttons.back.material", "ARROW");
        String name = ChatColor.translateAlternateColorCodes('&', 
            plugin.getConfig().getString("gui.buttons.back.name", "&a返回方块选择"));
        List<String> lore = plugin.getConfig().getStringList("gui.buttons.back.lore");
        if (lore.isEmpty()) {
            lore = List.of("&7点击返回方块选择界面");
        }
        
        return plugin.getItemCreator().createItem(material, name, lore);
    }
    
    /**
     * 计算每页可显示的物品数量
     */
    private int calculateItemsPerPage() {
        int totalSlots = guiConfig.getSize();
        int availableSlots = 0;
        
        for (int i = 0; i < totalSlots; i++) {
            if (!guiConfig.isButtonSlot(i)) {
                availableSlots++;
            }
        }
        
        return availableSlots;
    }
    
    /**
     * 处理GUI点击事件
     */
    public void handleInventoryClick(Player player, int slot, ItemStack clickedItem, boolean isRightClick) {
        UUID playerId = player.getUniqueId();
        
        synchronized (this) {
            int page = currentPage.getOrDefault(playerId, 1);
            List<Material> favorites = favoriteManager.getPlayerFavorites(player);
            
            if (favorites.isEmpty()) {
                return; // 空收藏列表，不处理点击
            }
            
            int itemsPerPage = calculateItemsPerPage();
            int totalPages = Math.max(1, (int) Math.ceil(favorites.size() / (double) itemsPerPage));
            
            // 处理导航按钮
            if (slot == guiConfig.getPreviousPageSlot() && guiConfig.matchesPreviousPageButton(clickedItem)) {
                if (page > 1) {
                    currentPage.put(playerId, page - 1);
                    updateInventory(player);
                }
                return;
            }
            
            if (slot == guiConfig.getNextPageSlot() && guiConfig.matchesNextPageButton(clickedItem)) {
                if (page < totalPages) {
                    currentPage.put(playerId, page + 1);
                    updateInventory(player);
                }
                return;
            }
            
            // 处理返回按钮
            if (slot == guiConfig.getCloseSlot()) {
                // 返回到方块选择GUI
                plugin.getGuiManager().getBlockSelectionGUI().openInventory(player);
                return;
            }
            
            // 处理收藏方块点击
            if (clickedItem != null && clickedItem.getType() != Material.AIR &&
                !guiConfig.isButtonSlot(slot) && plugin.getAllowedMaterialsForPlayer(player).contains(clickedItem.getType())) {
                
                if (isRightClick) {
                    // 右键取消收藏
                    boolean isFavorited = favoriteManager.toggleFavorite(player, clickedItem.getType());
                    if (!isFavorited) {
                        plugin.sendMessage(player, "messages.favorite-removed", 
                            plugin.getMinecraftLangManager().getItemStackName(clickedItem));
                        updateInventory(player); // 刷新GUI
                    }
                } else {
                    // 左键选择方块
                    selectBlock(player, clickedItem.getType());
                }
            }
        }
    }
    
    /**
     * 选择方块
     */
    private void selectBlock(Player player, Material material) {
        UUID playerId = player.getUniqueId();
        ItemStack originalItem = originalItems.get(playerId);
        
        if (originalItem != null && plugin.hasMagicLore(originalItem.getItemMeta())) {
            ItemStack newItem = originalItem.clone();
            newItem.setType(material);
            
            // 保持原有的附魔和其他元数据
            ItemMeta originalMeta = originalItem.getItemMeta();
            ItemMeta newMeta = newItem.getItemMeta();
            if (originalMeta != null && newMeta != null) {
                String blockName = plugin.getMinecraftLangManager().getItemStackName(newItem);
                String nameFormat = plugin.getConfig().getString("display.block-name-format", "&b✦ %s &b✦");
                newMeta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                    String.format(nameFormat, blockName)));
                
                newMeta.setLore(originalMeta.getLore());
                originalMeta.getEnchants().forEach((enchant, level) -> 
                    newMeta.addEnchant(enchant, level, true));
                originalMeta.getItemFlags().forEach(newMeta::addItemFlags);
                newItem.setItemMeta(newMeta);
            }
            
            player.getInventory().setItemInMainHand(newItem);
            plugin.sendMessage(player, "messages.success-replace", 
                plugin.getMinecraftLangManager().getItemStackName(newItem));
            
            clearPlayerData(playerId);
            player.closeInventory();
        }
    }
    
    /**
     * 清理玩家数据
     */
    public void clearPlayerData(UUID playerUUID) {
        currentPage.remove(playerUUID);
        originalItems.remove(playerUUID);
    }
}

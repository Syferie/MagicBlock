package io.github.syferie.magicblock.gui;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.ChatColor;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BlockSelectionGUI {
    private final MagicBlockPlugin plugin;
    private final GUIConfig guiConfig;
    private final Map<UUID, Integer> currentPage = new ConcurrentHashMap<>();
    private final Map<UUID, List<Material>> searchResults = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> originalItems = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGuiOpenTime = new ConcurrentHashMap<>();
    private static final long GUI_OPERATION_COOLDOWN = 500; // 0.5秒操作冷却时间

    public BlockSelectionGUI(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.guiConfig = new GUIConfig(plugin);
    }

    public void openInventory(Player player) {
        // 检查使用权限
        if (!player.hasPermission("magicblock.use")) {
            plugin.sendMessage(player, "messages.no-permission-use");
            return;
        }
        
        // 记录原始物品
        originalItems.put(player.getUniqueId(), player.getInventory().getItemInMainHand().clone());
        // 重置搜索状态
        searchResults.remove(player.getUniqueId());
        // 重置页码
        currentPage.put(player.getUniqueId(), 1);
        // 记录打开时间
        lastGuiOpenTime.put(player.getUniqueId(), System.currentTimeMillis());
        // 打开界面
        updateInventory(player);
    }

    public void updateInventory(Player player) {
        // 设置GUI更新标志，防止在更新时清理数据
        GUIManager.setPlayerUpdatingGUI(player, true);

        Inventory gui = Bukkit.createInventory(null, guiConfig.getSize(), guiConfig.getTitle());
        UUID playerId = player.getUniqueId();
        int page = currentPage.getOrDefault(playerId, 1);

        List<Material> materials = searchResults.getOrDefault(playerId, plugin.getAllowedMaterials());

        // 计算每页可显示的物品数量（排除按钮槽位）
        int itemsPerPage = calculateItemsPerPage();
        int totalPages = (int) Math.ceil(materials.size() / (double) itemsPerPage);

        plugin.debug("GUI更新 - 玩家: " + player.getName() + ", 页面: " + page + "/" + totalPages +
                    ", 每页物品数: " + itemsPerPage + ", 总物品数: " + materials.size());

        int startIndex = (page - 1) * itemsPerPage;
        int endIndex = Math.min(startIndex + itemsPerPage, materials.size());

        // 添加物品到可用槽位
        int currentSlot = 0;
        for (int i = startIndex; i < endIndex; i++) {
            // 跳过按钮槽位（包括自定义材质槽位）
            while (currentSlot < guiConfig.getSize() && guiConfig.isButtonSlot(currentSlot)) {
                currentSlot++;
            }

            if (currentSlot < guiConfig.getSize()) {
                Material material = materials.get(i);
                gui.setItem(currentSlot, createMagicBlock(material, player));
                currentSlot++;
            } else {
                // 如果没有更多可用槽位，停止添加物品
                break;
            }
        }

        // 始终添加导航按钮（保持布局一致性），根据状态显示不同样式
        gui.setItem(guiConfig.getPreviousPageSlot(), guiConfig.createPreviousPageButton(page > 1));
        gui.setItem(guiConfig.getNextPageSlot(), guiConfig.createNextPageButton(page < totalPages));

        // 添加页码信息
        gui.setItem(guiConfig.getPageInfoSlot(), guiConfig.createPageInfoButton(page, totalPages));

        // 添加搜索按钮
        gui.setItem(guiConfig.getSearchSlot(), guiConfig.createSearchButton());

        // 添加收藏按钮（如果启用）
        if (guiConfig.isFavoritesEnabled()) {
            gui.setItem(guiConfig.getFavoritesSlot(), guiConfig.createFavoritesButton());
        }

        // 添加关闭按钮
        gui.setItem(guiConfig.getCloseSlot(), guiConfig.createCloseButton());

        // 添加自定义材质
        for (Map.Entry<String, GUIConfig.ButtonConfig> entry : guiConfig.getCustomMaterials().entrySet()) {
            String customKey = entry.getKey();
            GUIConfig.ButtonConfig config = entry.getValue();
            ItemStack customItem = guiConfig.createCustomMaterial(customKey);
            if (customItem != null && config.slot >= 0 && config.slot < guiConfig.getSize()) {
                gui.setItem(config.slot, customItem);
                plugin.debug("添加自定义材质: " + customKey + " -> 槽位 " + config.slot);
            }
        }

        player.openInventory(gui);

        // 清除GUI更新标志
        GUIManager.setPlayerUpdatingGUI(player, false);
    }

    public void handleSearch(Player player, String query) {
        UUID playerId = player.getUniqueId();
        List<Material> allMaterials = plugin.getAllowedMaterials();
        
        if (query == null || query.trim().isEmpty()) {
            searchResults.remove(playerId);
        } else {
            String lowercaseQuery = query.toLowerCase();
            List<Material> results = allMaterials.stream()
                .filter(material -> {
                    String materialName = material.name().toLowerCase();
                    String localizedName = plugin.getMinecraftLangManager().getItemStackName(new ItemStack(material));
                    return materialName.contains(lowercaseQuery) || 
                           localizedName.toLowerCase().contains(lowercaseQuery);
                })
                .collect(Collectors.toList());
            
            if (!results.isEmpty()) {
                searchResults.put(playerId, results);
            } else {
                searchResults.remove(playerId);
                plugin.sendMessage(player, "messages.no-results");
            }
        }
        
        currentPage.put(playerId, 1);
        updateInventory(player);
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player) {
        handleInventoryClick(event, player, event.isRightClick());
    }

    public void handleInventoryClick(InventoryClickEvent event, Player player, boolean isRightClick) {
        // 检查使用权限
        if (!player.hasPermission("magicblock.use")) {
            plugin.sendMessage(player, "messages.no-permission-use");
            event.setCancelled(true);
            player.closeInventory();
            return;
        }
        
        // 检查冷却时间
        long currentTime = System.currentTimeMillis();
        long openTime = lastGuiOpenTime.getOrDefault(player.getUniqueId(), 0L);
        if (currentTime - openTime < GUI_OPERATION_COOLDOWN) {
            return;
        }

        // 检查点击的位置是否在GUI的有效范围内
        int slot = event.getRawSlot();
        if (slot < 0 || slot >= event.getView().getTopInventory().getSize()) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        UUID playerId = player.getUniqueId();

        // 使用synchronized块来确保线程安全
        synchronized (this) {
            // 在synchronized块内读取最新的页面状态
            int page = currentPage.getOrDefault(playerId, 1);
            List<Material> materials = searchResults.getOrDefault(playerId, plugin.getAllowedMaterials());
            int itemsPerPage = calculateItemsPerPage();
            int totalPages = Math.max(1, (int) Math.ceil(materials.size() / (double) itemsPerPage));

            plugin.debug("按钮点击 - 玩家: " + player.getName() + ", 当前页: " + page + "/" + totalPages +
                        ", 点击槽位: " + slot + ", 物品: " + clickedItem.getType());
            plugin.debug("页面状态详情 - currentPage.get(" + playerId + ") = " + currentPage.get(playerId));

            // 处理上一页按钮点击
            if (slot == guiConfig.getPreviousPageSlot() && guiConfig.matchesPreviousPageButton(clickedItem)) {
                if (page > 1) {
                    currentPage.put(playerId, page - 1);
                    updateInventory(player);
                }
                // 移除提示消息，用户可以通过按钮的视觉状态了解是否可以翻页
                return;
            }

            // 处理下一页按钮点击
            if (slot == guiConfig.getNextPageSlot() && guiConfig.matchesNextPageButton(clickedItem)) {
                if (page < totalPages) {
                    currentPage.put(playerId, page + 1);
                    updateInventory(player);
                }
                // 移除提示消息，用户可以通过按钮的视觉状态了解是否可以翻页
                return;
            }

            // 处理关闭按钮点击
            if (slot == guiConfig.getCloseSlot() && guiConfig.matchesCloseButton(clickedItem)) {
                player.closeInventory();
                return;
            }

            // 处理搜索按钮点击
            if (slot == guiConfig.getSearchSlot() && guiConfig.matchesSearchButton(clickedItem)) {
                player.closeInventory();
                plugin.sendMessage(player, "messages.search-prompt");
                GUIManager.setPlayerSearching(player, true);
                return;
            }

            // 处理收藏按钮点击
            if (guiConfig.isFavoritesEnabled() && slot == guiConfig.getFavoritesSlot() &&
                guiConfig.matchesFavoritesButton(clickedItem)) {
                // 打开收藏GUI
                if (plugin.getFavoriteManager() != null) {
                    plugin.getFavoriteGUI().openInventory(player);
                } else {
                    plugin.sendMessage(player, "messages.favorites-disabled");
                }
                return;
            }

            // 检查点击的物品是否在允许的材料列表中
            if (!plugin.getAllowedMaterials().contains(clickedItem.getType())) {
                return;
            }

            // 处理右键收藏功能
            if (isRightClick && guiConfig.isFavoritesEnabled()) {
                if (plugin.getFavoriteManager() != null) {
                    boolean isFavorited = plugin.getFavoriteManager().toggleFavorite(player, clickedItem.getType());
                    String messageKey = isFavorited ? "messages.favorite-added" : "messages.favorite-removed";
                    plugin.sendMessage(player, messageKey,
                        plugin.getMinecraftLangManager().getItemStackName(clickedItem));
                    updateInventory(player); // 刷新GUI显示收藏状态
                } else {
                    plugin.sendMessage(player, "messages.favorites-disabled");
                }
                return;
            }

            // 替换方块（左键）
            ItemStack originalItem = originalItems.get(playerId);
            if (originalItem != null && plugin.hasMagicLore(originalItem.getItemMeta())) {
                ItemStack newItem = originalItem.clone();
                newItem.setType(clickedItem.getType());
                
                // 保持原有的附魔和其他元数据
                ItemMeta originalMeta = originalItem.getItemMeta();
                ItemMeta newMeta = newItem.getItemMeta();
                if (originalMeta != null && newMeta != null) {
                    String blockName = plugin.getMinecraftLangManager().getItemStackName(clickedItem);
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
                plugin.sendMessage(player, "messages.success-replace", plugin.getMinecraftLangManager().getItemStackName(clickedItem));
                
                // 清理记录
                clearPlayerData(playerId);
                player.closeInventory();
            }
        }
    }

    /**
     * 计算每页可显示的物品数量（排除按钮槽位）
     */
    private int calculateItemsPerPage() {
        int totalSlots = guiConfig.getSize();
        int availableSlots = 0;

        // 计算实际可用的槽位数量
        for (int i = 0; i < totalSlots; i++) {
            if (!guiConfig.isButtonSlot(i)) {
                availableSlots++;
            }
        }

        return availableSlots;
    }



    /**
     * 重新加载GUI配置
     */
    public void reloadConfig() {
        guiConfig.loadConfig();
    }

    private ItemStack createMagicBlock(Material material) {
        return createMagicBlock(material, null);
    }

    private ItemStack createMagicBlock(Material material, Player player) {
        ItemStack block = new ItemStack(material);
        ItemMeta meta = block.getItemMeta();
        if (meta != null) {
            String blockName = plugin.getMinecraftLangManager().getItemStackName(block);
            // 在原有名称两侧添加装饰符号
            String nameFormat = plugin.getConfig().getString("display.block-name-format", "&b✦ %s &b✦");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                String.format(nameFormat, blockName)));

            // 创建lore列表
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("gui.text.select-block-left", "&7» 左键选择此方块")));

            if (guiConfig.isFavoritesEnabled()) {
                lore.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("gui.text.select-block-right", "&7» 右键收藏/取消收藏")));
                lore.add(""); // 空行

                // 显示收藏状态（如果有玩家信息）
                if (player != null && plugin.getFavoriteManager() != null) {
                    boolean isFavorited = plugin.getFavoriteManager().isFavorited(player, material);
                    String favoriteStatus = isFavorited ?
                        plugin.getConfig().getString("gui.text.favorited", "&e⭐ 已收藏") :
                        plugin.getConfig().getString("gui.text.not-favorited", "&8☆ 未收藏");
                    lore.add(ChatColor.translateAlternateColorCodes('&', favoriteStatus));
                }
            } else {
                lore.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("gui.text.select-block", "&7» 点击选择此方块")));
            }

            meta.setLore(lore);
            block.setItemMeta(meta);
        }
        return block;
    }

    public void clearPlayerData(UUID playerUUID) {
        currentPage.remove(playerUUID);
        searchResults.remove(playerUUID);
        originalItems.remove(playerUUID);
        lastGuiOpenTime.remove(playerUUID);
    }
}

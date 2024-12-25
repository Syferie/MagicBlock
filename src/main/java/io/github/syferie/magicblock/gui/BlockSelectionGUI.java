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
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

public class BlockSelectionGUI {
    private final MagicBlockPlugin plugin;
    private final Map<UUID, Integer> currentPage = new ConcurrentHashMap<>();
    private final Map<UUID, List<Material>> searchResults = new ConcurrentHashMap<>();
    private final Map<UUID, ItemStack> originalItems = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGuiOpenTime = new ConcurrentHashMap<>();
    private static final int ITEMS_PER_PAGE = 45;
    private static final long GUI_OPERATION_COOLDOWN = 500; // 0.5秒操作冷却时间

    public BlockSelectionGUI(MagicBlockPlugin plugin) {
        this.plugin = plugin;
    }

    public void openInventory(Player player) {
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
        Inventory gui = Bukkit.createInventory(null, 54, plugin.getMessage("gui.title"));
        UUID playerId = player.getUniqueId();
        int page = currentPage.getOrDefault(playerId, 1);

        List<Material> materials = searchResults.getOrDefault(playerId, plugin.getAllowedMaterials());
        int totalPages = (int) Math.ceil(materials.size() / (double) ITEMS_PER_PAGE);

        int startIndex = (page - 1) * ITEMS_PER_PAGE;
        int endIndex = Math.min(startIndex + ITEMS_PER_PAGE, materials.size());

        for (int i = startIndex; i < endIndex; i++) {
            Material material = materials.get(i);
            gui.setItem(i - startIndex, createMagicBlock(material));
        }

        // 添加导航按钮
        if (page > 1) {
            gui.setItem(45, createNavigationItem(plugin.getMessage("gui.previous-page"), Material.ARROW));
        }
        if (page < totalPages) {
            gui.setItem(53, createNavigationItem(plugin.getMessage("gui.next-page"), Material.ARROW));
        }

        // 添加搜索按钮
        gui.setItem(49, createSearchButton());

        player.openInventory(gui);
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
                    String localizedName = plugin.getMessage("blocks." + material.name());
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
        event.setCancelled(true);
        
        // 检查冷却时间
        long currentTime = System.currentTimeMillis();
        long openTime = lastGuiOpenTime.getOrDefault(player.getUniqueId(), 0L);
        if (currentTime - openTime < GUI_OPERATION_COOLDOWN) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        UUID playerId = player.getUniqueId();
        int page = currentPage.getOrDefault(playerId, 1);
        List<Material> materials = searchResults.getOrDefault(playerId, plugin.getAllowedMaterials());
        int totalPages = (int) Math.ceil(materials.size() / (double) ITEMS_PER_PAGE);

        if (clickedItem.getType() == Material.ARROW) {
            String itemName = clickedItem.getItemMeta().getDisplayName();
            if (itemName.equals(plugin.getMessage("gui.previous-page")) && page > 1) {
                currentPage.put(playerId, page - 1);
                updateInventory(player);
            } else if (itemName.equals(plugin.getMessage("gui.next-page")) && page < totalPages) {
                currentPage.put(playerId, page + 1);
                updateInventory(player);
            }
            return;
        }

        // 处理搜索按钮点击
        if (clickedItem.getType() == Material.COMPASS) {
            player.closeInventory();
            plugin.sendMessage(player, "messages.search-prompt");
            GUIManager.setPlayerSearching(player, true);
            return;
        }

        // 替换方块
        ItemStack originalItem = originalItems.get(playerId);
        if (originalItem != null && plugin.hasMagicLore(originalItem.getItemMeta())) {
            ItemStack newItem = originalItem.clone();
            newItem.setType(clickedItem.getType());
            
            // 保持原有的附魔和其他元数据
            ItemMeta originalMeta = originalItem.getItemMeta();
            ItemMeta newMeta = newItem.getItemMeta();
            if (originalMeta != null && newMeta != null) {
                // 在原有名称两侧添加装饰符号
                String blockName = getChineseBlockName(clickedItem.getType());
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
            plugin.sendMessage(player, "messages.success-replace", getChineseBlockName(clickedItem.getType()));
            
            // 清理记录
            clearPlayerData(playerId);
            player.closeInventory();
        }
    }

    private ItemStack createSearchButton() {
        ItemStack searchButton = new ItemStack(Material.COMPASS);
        ItemMeta meta = searchButton.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(plugin.getMessage("gui.search-button"));
            List<String> lore = new ArrayList<>();
            lore.add(plugin.getMessage("gui.search-lore"));
            meta.setLore(lore);
            searchButton.setItemMeta(meta);
        }
        return searchButton;
    }

    private ItemStack createNavigationItem(String name, Material material) {
        ItemStack button = new ItemStack(material);
        ItemMeta meta = button.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            button.setItemMeta(meta);
        }
        return button;
    }

    private ItemStack createMagicBlock(Material material) {
        ItemStack block = new ItemStack(material);
        ItemMeta meta = block.getItemMeta();
        if (meta != null) {
            String blockName = getChineseBlockName(material);
            // 在原有名称两侧添加装饰符号
            String nameFormat = plugin.getConfig().getString("display.block-name-format", "&b✦ %s &b✦");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                String.format(nameFormat, blockName)));
            meta.setLore(List.of(plugin.getMessage("gui.select-block")));
            block.setItemMeta(meta);
        }
        return block;
    }

    private String getChineseBlockName(Material material) {
        // 从语言文件获取
        String langKey = "blocks." + material.name();
        String langName = plugin.getMessage(langKey);
        if (!langName.startsWith("Missing message")) {
            return langName;
        }
        
        // 如果没有翻译，返回格式化的英文名称
        return material.name().toLowerCase().replace('_', ' ');
    }

    public void clearPlayerData(UUID playerUUID) {
        currentPage.remove(playerUUID);
        searchResults.remove(playerUUID);
        originalItems.remove(playerUUID);
        lastGuiOpenTime.remove(playerUUID);
    }
}

package io.github.syferie.magicblock.gui;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.util.ItemCreator;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * GUI配置管理器
 * 负责从配置文件中读取GUI设置并创建相应的物品
 */
public class GUIConfig {
    private final MagicBlockPlugin plugin;
    private final ItemCreator itemCreator;
    
    // GUI基础配置
    private String title;
    private int rows;

    // 按钮配置
    private ButtonConfig previousPageButton;
    private ButtonConfig nextPageButton;
    private ButtonConfig pageInfoButton;
    private ButtonConfig searchButton;
    private ButtonConfig closeButton;
    private ButtonConfig favoritesButton;

    // 自定义材质配置
    private Map<String, ButtonConfig> customMaterials;

    // GUI文本配置
    private String selectBlockText;
    private String boundBlocksTitle;
    private String retrieveBlockText;
    private String removeBlockText;
    private String removeBlockNoteText;
    private String remainingUsesText;
    
    public GUIConfig(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.itemCreator = new ItemCreator(plugin);
        this.customMaterials = new HashMap<>();
        loadConfig();
    }
    
    /**
     * 从配置文件加载GUI配置
     */
    public void loadConfig() {
        ConfigurationSection guiSection = plugin.getConfig().getConfigurationSection("gui");
        if (guiSection == null) {
            plugin.getLogger().warning("GUI配置节不存在，使用默认配置");
            loadDefaultConfig();
            return;
        }
        
        // 加载基础配置
        this.title = ChatColor.translateAlternateColorCodes('&', 
            guiSection.getString("title", "&8⚡ &bMagicBlock选择"));
        this.rows = Math.max(1, Math.min(6, guiSection.getInt("rows", 6)));
        
        // 加载按钮配置
        ConfigurationSection buttonsSection = guiSection.getConfigurationSection("buttons");
        if (buttonsSection != null) {
            this.previousPageButton = loadButtonConfig(buttonsSection, "previous-page", getDefaultPreviousPageButton());
            this.nextPageButton = loadButtonConfig(buttonsSection, "next-page", getDefaultNextPageButton());
            this.pageInfoButton = loadButtonConfig(buttonsSection, "page-info", getDefaultPageInfoButton());
            this.searchButton = loadButtonConfig(buttonsSection, "search", getDefaultSearchButton());
            this.closeButton = loadButtonConfig(buttonsSection, "close", getDefaultCloseButton());
            this.favoritesButton = loadButtonConfig(buttonsSection, "favorites", getDefaultFavoritesButton());

            // 加载自定义材质配置
            loadCustomMaterials(buttonsSection);
        } else {
            plugin.getLogger().warning("GUI按钮配置节不存在，使用默认配置");
            loadDefaultButtonConfigs();
            loadDefaultCustomMaterials();
        }

        // 加载GUI文本配置
        ConfigurationSection textSection = guiSection.getConfigurationSection("text");
        if (textSection != null) {
            this.selectBlockText = ChatColor.translateAlternateColorCodes('&',
                textSection.getString("select-block", "&7» 点击选择此方块"));
            this.boundBlocksTitle = ChatColor.translateAlternateColorCodes('&',
                textSection.getString("bound-blocks-title", "&8⚡ &b已绑定方块"));
            this.retrieveBlockText = ChatColor.translateAlternateColorCodes('&',
                textSection.getString("retrieve-block", "&a▸ &7左键点击取回此方块"));
            this.removeBlockText = ChatColor.translateAlternateColorCodes('&',
                textSection.getString("remove-block", "&c▸ &7右键点击从列表中隐藏"));
            this.removeBlockNoteText = ChatColor.translateAlternateColorCodes('&',
                textSection.getString("remove-block-note", "&8• &7(仅从列表隐藏，绑定关系保持)"));
            this.remainingUsesText = ChatColor.translateAlternateColorCodes('&',
                textSection.getString("remaining-uses", "剩余使用次数: "));
        } else {
            plugin.getLogger().warning("GUI文本配置节不存在，使用默认配置");
            loadDefaultTextConfigs();
        }

        plugin.debug("GUI配置加载完成 - 标题: " + title + ", 行数: " + rows);
    }
    
    /**
     * 从配置节加载按钮配置
     */
    private ButtonConfig loadButtonConfig(ConfigurationSection buttonsSection, String buttonName, ButtonConfig defaultConfig) {
        ConfigurationSection buttonSection = buttonsSection.getConfigurationSection(buttonName);
        if (buttonSection == null) {
            plugin.debug("按钮配置 " + buttonName + " 不存在，使用默认配置");
            return defaultConfig;
        }

        String material = buttonSection.getString("material", defaultConfig.material);
        String name = buttonSection.getString("name", defaultConfig.name);
        List<String> lore = buttonSection.getStringList("lore");
        if (lore.isEmpty()) {
            lore = defaultConfig.lore;
        }
        int slot = buttonSection.getInt("slot", defaultConfig.slot);

        // 加载禁用状态配置
        ButtonConfig disabledConfig = null;
        ConfigurationSection disabledSection = buttonSection.getConfigurationSection("disabled");
        if (disabledSection != null) {
            String disabledMaterial = disabledSection.getString("material", "GRAY_DYE");
            String disabledName = disabledSection.getString("name", name + " &7(禁用)");
            List<String> disabledLore = disabledSection.getStringList("lore");
            if (disabledLore.isEmpty()) {
                disabledLore = List.of("&8此按钮当前不可用");
            }
            disabledConfig = new ButtonConfig(disabledMaterial, disabledName, disabledLore, slot);
        } else if (defaultConfig.disabled != null) {
            disabledConfig = defaultConfig.disabled;
        }

        return new ButtonConfig(material, name, lore, slot, disabledConfig);
    }
    
    /**
     * 加载默认配置
     */
    private void loadDefaultConfig() {
        this.title = "&8⚡ &bMagicBlock选择";
        this.rows = 6;
        loadDefaultButtonConfigs();
        loadDefaultCustomMaterials();
        loadDefaultTextConfigs();
    }
    
    /**
     * 加载默认按钮配置
     */
    private void loadDefaultButtonConfigs() {
        this.previousPageButton = getDefaultPreviousPageButton();
        this.nextPageButton = getDefaultNextPageButton();
        this.pageInfoButton = getDefaultPageInfoButton();
        this.searchButton = getDefaultSearchButton();
        this.closeButton = getDefaultCloseButton();
        this.favoritesButton = getDefaultFavoritesButton();
    }

    /**
     * 加载自定义材质配置
     */
    private void loadCustomMaterials(ConfigurationSection buttonsSection) {
        customMaterials.clear();

        // 遍历所有以"custom"开头的配置节
        for (String key : buttonsSection.getKeys(false)) {
            if (key.startsWith("custom")) {
                ConfigurationSection customSection = buttonsSection.getConfigurationSection(key);
                if (customSection != null && customSection.getBoolean("enabled", true)) {
                    String material = customSection.getString("material", "AIR");
                    String name = customSection.getString("name", "");
                    List<String> lore = customSection.getStringList("lore");
                    int slot = customSection.getInt("slot", -1);

                    if (slot >= 0) {
                        ButtonConfig config = new ButtonConfig(material, name, lore, slot);
                        customMaterials.put(key, config);
                        plugin.debug("加载自定义材质: " + key + " -> " + material + " (槽位: " + slot + ")");
                    }
                }
            }
        }
    }

    /**
     * 加载默认自定义材质配置
     */
    private void loadDefaultCustomMaterials() {
        customMaterials.clear();
        // 添加默认的填充材质
        customMaterials.put("custom1", new ButtonConfig("AIR", "", List.of(), 46));
        customMaterials.put("custom2", new ButtonConfig("AIR", "", List.of(), 48));
        customMaterials.put("custom3", new ButtonConfig("AIR", "", List.of(), 50));
        customMaterials.put("custom4", new ButtonConfig("AIR", "", List.of(), 52));
    }

    /**
     * 加载默认文本配置
     */
    private void loadDefaultTextConfigs() {
        this.selectBlockText = "&7» 点击选择此方块";
        this.boundBlocksTitle = "&8⚡ &b已绑定方块";
        this.retrieveBlockText = "&a▸ &7左键点击取回此方块";
        this.removeBlockText = "&c▸ &7右键点击从列表中隐藏";
        this.removeBlockNoteText = "&8• &7(仅从列表隐藏，绑定关系保持)";
        this.remainingUsesText = "剩余使用次数: ";
    }
    
    // 默认按钮配置
    private ButtonConfig getDefaultPreviousPageButton() {
        ButtonConfig disabled = new ButtonConfig("GRAY_DYE", "&8« 上一页 &7(禁用)",
            List.of("&7已经是第一页了", "&8无法继续向前翻页"), 45);
        return new ButtonConfig("ARROW", "&a« 上一页", List.of("&7点击返回上一页"), 45, disabled);
    }

    private ButtonConfig getDefaultNextPageButton() {
        ButtonConfig disabled = new ButtonConfig("GRAY_DYE", "&8下一页 » &7(禁用)",
            List.of("&7已经是最后一页了", "&8无法继续向后翻页"), 53);
        return new ButtonConfig("ARROW", "&a下一页 »", List.of("&7点击前往下一页"), 53, disabled);
    }
    
    private ButtonConfig getDefaultPageInfoButton() {
        return new ButtonConfig("PAPER", "&e第 {page}/{total_pages} 页", List.of("&7当前页码信息"), 49);
    }
    
    private ButtonConfig getDefaultSearchButton() {
        return new ButtonConfig("COMPASS", "&e⚡ 搜索方块", List.of("&7» 点击进行搜索", "&7输入方块名称来快速查找"), 47);
    }
    
    private ButtonConfig getDefaultCloseButton() {
        return new ButtonConfig("BARRIER", "&c关闭", List.of("&7点击关闭GUI"), 51);
    }

    private ButtonConfig getDefaultFavoritesButton() {
        return new ButtonConfig("NETHER_STAR", "&e⭐ 我的收藏",
            List.of("&7查看收藏的方块", "&7点击打开收藏列表"), 49);
    }
    
    // Getter方法
    public String getTitle() {
        return title;
    }
    
    public int getRows() {
        return rows;
    }
    
    public int getSize() {
        return rows * 9;
    }
    
    // 创建按钮物品的方法
    public ItemStack createPreviousPageButton() {
        return itemCreator.createItem(previousPageButton.material, previousPageButton.name, previousPageButton.lore);
    }

    public ItemStack createPreviousPageButton(boolean enabled) {
        if (enabled) {
            return createPreviousPageButton();
        } else {
            // 使用配置的禁用状态
            if (previousPageButton.disabled != null) {
                return itemCreator.createItem(previousPageButton.disabled.material,
                    previousPageButton.disabled.name, previousPageButton.disabled.lore);
            } else {
                // 回退到默认禁用样式
                List<String> disabledLore = new ArrayList<>(previousPageButton.lore);
                disabledLore.add("");
                disabledLore.add("&8已经是第一页了");
                return itemCreator.createItem(previousPageButton.material,
                    previousPageButton.name + " &8(禁用)", disabledLore);
            }
        }
    }

    public ItemStack createNextPageButton() {
        return itemCreator.createItem(nextPageButton.material, nextPageButton.name, nextPageButton.lore);
    }

    public ItemStack createNextPageButton(boolean enabled) {
        if (enabled) {
            return createNextPageButton();
        } else {
            // 使用配置的禁用状态
            if (nextPageButton.disabled != null) {
                return itemCreator.createItem(nextPageButton.disabled.material,
                    nextPageButton.disabled.name, nextPageButton.disabled.lore);
            } else {
                // 回退到默认禁用样式
                List<String> disabledLore = new ArrayList<>(nextPageButton.lore);
                disabledLore.add("");
                disabledLore.add("&8已经是最后一页了");
                return itemCreator.createItem(nextPageButton.material,
                    nextPageButton.name + " &8(禁用)", disabledLore);
            }
        }
    }
    
    public ItemStack createPageInfoButton(int currentPage, int totalPages) {
        String name = pageInfoButton.name
            .replace("{page}", String.valueOf(currentPage))
            .replace("{total_pages}", String.valueOf(totalPages));
        return itemCreator.createItem(pageInfoButton.material, name, pageInfoButton.lore);
    }
    
    public ItemStack createSearchButton() {
        return itemCreator.createItem(searchButton.material, searchButton.name, searchButton.lore);
    }
    
    public ItemStack createCloseButton() {
        return itemCreator.createItem(closeButton.material, closeButton.name, closeButton.lore);
    }

    public ItemStack createFavoritesButton() {
        return itemCreator.createItem(favoritesButton.material, favoritesButton.name, favoritesButton.lore);
    }
    
    // 获取按钮槽位
    public int getPreviousPageSlot() {
        return previousPageButton.slot;
    }
    
    public int getNextPageSlot() {
        return nextPageButton.slot;
    }
    
    public int getPageInfoSlot() {
        return pageInfoButton.slot;
    }
    
    public int getSearchSlot() {
        return searchButton.slot;
    }
    
    public int getCloseSlot() {
        return closeButton.slot;
    }

    public int getFavoritesSlot() {
        return favoritesButton.slot;
    }
    
    // 检查物品是否匹配按钮（包括禁用状态）
    public boolean matchesPreviousPageButton(ItemStack item) {
        // 检查正常状态
        if (itemCreator.matchesMaterial(item, previousPageButton.material)) {
            return true;
        }
        // 检查禁用状态
        if (previousPageButton.disabled != null) {
            return itemCreator.matchesMaterial(item, previousPageButton.disabled.material);
        }
        return false;
    }

    public boolean matchesNextPageButton(ItemStack item) {
        // 检查正常状态
        if (itemCreator.matchesMaterial(item, nextPageButton.material)) {
            return true;
        }
        // 检查禁用状态
        if (nextPageButton.disabled != null) {
            return itemCreator.matchesMaterial(item, nextPageButton.disabled.material);
        }
        return false;
    }
    
    public boolean matchesSearchButton(ItemStack item) {
        return itemCreator.matchesMaterial(item, searchButton.material);
    }
    
    public boolean matchesCloseButton(ItemStack item) {
        return itemCreator.matchesMaterial(item, closeButton.material);
    }

    public boolean matchesFavoritesButton(ItemStack item) {
        return itemCreator.matchesMaterial(item, favoritesButton.material);
    }

    /**
     * 检查收藏功能是否启用
     */
    public boolean isFavoritesEnabled() {
        return plugin.getConfig().getBoolean("gui.buttons.favorites.enabled", true);
    }

    // GUI文本配置的getter方法
    public String getSelectBlockText() {
        return selectBlockText;
    }

    public String getBoundBlocksTitle() {
        return boundBlocksTitle;
    }

    public String getRetrieveBlockText() {
        return retrieveBlockText;
    }

    public String getRemoveBlockText() {
        return removeBlockText;
    }

    public String getRemoveBlockNoteText() {
        return removeBlockNoteText;
    }

    public String getRemainingUsesText() {
        return remainingUsesText;
    }

    // 自定义材质相关方法
    public Map<String, ButtonConfig> getCustomMaterials() {
        return customMaterials;
    }

    /**
     * 检查指定槽位是否是按钮槽位（包括自定义材质）
     */
    public boolean isButtonSlot(int slot) {
        // 检查标准按钮
        if (slot == getPreviousPageSlot() || slot == getNextPageSlot() ||
            slot == getPageInfoSlot() || slot == getSearchSlot() || slot == getCloseSlot()) {
            return true;
        }

        // 检查收藏按钮（如果启用）
        if (isFavoritesEnabled() && slot == getFavoritesSlot()) {
            return true;
        }

        // 检查自定义材质槽位
        for (ButtonConfig config : customMaterials.values()) {
            if (config.slot == slot) {
                return true;
            }
        }

        return false;
    }

    /**
     * 创建自定义材质物品
     */
    public ItemStack createCustomMaterial(String customKey) {
        ButtonConfig config = customMaterials.get(customKey);
        if (config != null) {
            return itemCreator.createItem(config.material, config.name, config.lore);
        }
        return null;
    }
    
    /**
     * 按钮配置类
     */
    public static class ButtonConfig {
        final String material;
        final String name;
        final List<String> lore;
        final int slot;
        final ButtonConfig disabled; // 禁用状态配置

        ButtonConfig(String material, String name, List<String> lore, int slot) {
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.slot = slot;
            this.disabled = null;
        }

        ButtonConfig(String material, String name, List<String> lore, int slot, ButtonConfig disabled) {
            this.material = material;
            this.name = name;
            this.lore = lore;
            this.slot = slot;
            this.disabled = disabled;
        }
    }
}

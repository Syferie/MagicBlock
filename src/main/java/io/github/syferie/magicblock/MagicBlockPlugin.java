package io.github.syferie.magicblock;

import com.tcoded.folialib.FoliaLib;
import io.github.syferie.magicblock.block.BlockManager;
import io.github.syferie.magicblock.command.CommandManager;
import io.github.syferie.magicblock.command.handler.TabCompleter;
import io.github.syferie.magicblock.database.DatabaseManager;
import io.github.syferie.magicblock.food.FoodManager;
import io.github.syferie.magicblock.food.FoodService;
import io.github.syferie.magicblock.hook.PlaceholderHook;
import io.github.syferie.magicblock.listener.BlockListener;
import io.github.syferie.magicblock.metrics.Metrics;
import io.github.syferie.magicblock.util.Statistics;
import io.github.syferie.magicblock.util.LanguageManager;
import io.github.syferie.magicblock.block.BlockBindManager;
import io.github.syferie.magicblock.util.UpdateChecker;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.Bukkit;

import java.io.*;
import java.util.ArrayList;
import java.util.List;
import java.util.HashMap;
import java.util.UUID;
import java.util.Map;
import java.util.LinkedHashMap;


public class MagicBlockPlugin extends JavaPlugin {

    private BlockListener listener;
    private BlockManager blockManager;
    private BlockBindManager blockBindManager;
    private List<String> blacklistedWorlds;
    private FoodManager magicFood;
    private FoodService foodService;
    private FileConfiguration foodConfig;
    private Statistics statistics;
    private final HashMap<UUID, Integer> playerUsage = new HashMap<>();
    private List<Material> allowedMaterials;
    private LanguageManager languageManager;
    private FoliaLib foliaLib;
    private DatabaseManager databaseManager;

    @Override
    public void onEnable() {
        // 初始化语言管理器
        this.languageManager = new LanguageManager(this);

        try {
            // 初始化FoliaLib
            this.foliaLib = new FoliaLib(this);
        } catch (Exception e) {
            getLogger().severe("Failed to initialize FoliaLib: " + e.getMessage());
            getLogger().severe("The plugin may not work correctly on this server version.");
            getLogger().severe("Please report this issue to the plugin developer.");
            // 使用语言管理器的消息
            getLogger().info(languageManager.getMessage("general.plugin-enabled"));
            return; // 不要继续初始化插件
        }

        // 初始化配置
        initializeConfig();

        // 初始化允许的材料列表
        this.allowedMaterials = loadMaterialsFromConfig();

        // 检查更新
        if(getConfig().getBoolean("check-updates")) {
            checkForUpdates();
        }

        // 初始化成员和注册事件
        initializeMembers();
        registerEventsAndCommands();
        saveFoodConfig();

        // 初始化统计
        if(getConfig().getBoolean("enable-statistics")) {
            statistics = new Statistics(this);
        }

        // 初始化食物服务
        this.foodService = new FoodService(this);

        saveDefaultConfig();
        checkAndUpdateConfig("config.yml", true);

        // 注册PlaceholderAPI扩展
        if(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new PlaceholderHook(this).register();
            getLogger().info(languageManager.getMessage("general.placeholder-registered"));
        }

        // 初始化bStats
        initBStats();

        getLogger().info(languageManager.getMessage("general.plugin-enabled"));
    }

    private void initBStats() {
        // bStats统计
        int pluginId = 24214;
        Metrics metrics = new Metrics(this, pluginId);

        // 统计在线玩家数量
        metrics.addCustomChart(new Metrics.SingleLineChart("online_players", () ->
            Bukkit.getOnlinePlayers().size()));

        // 统计使用过魔法方块的玩家数量
        metrics.addCustomChart(new Metrics.SingleLineChart("unique_users", () ->
            playerUsage.size()));

        // 统计使用的语言分布
        metrics.addCustomChart(new Metrics.SimplePie("language", () ->
            getConfig().getString("language", "en")));

        // 统计服务器版本
        metrics.addCustomChart(new Metrics.SimplePie("server_version", () ->
            Bukkit.getVersion()));

        // 统计是否启用了PlaceholderAPI
        metrics.addCustomChart(new Metrics.SimplePie("using_placeholderapi", () ->
            String.valueOf(Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null)));

        // 统计总使用次数
        metrics.addCustomChart(new Metrics.SingleLineChart("total_uses", () -> {
            int total = 0;
            for (int uses : playerUsage.values()) {
                total += uses;
            }
            return total;
        }));

        // 统计平均每个玩家的使用次数
        metrics.addCustomChart(new Metrics.SimplePie("average_uses_per_player", () -> {
            if (playerUsage.isEmpty()) return "0";
            int total = 0;
            for (int uses : playerUsage.values()) {
                total += uses;
            }
            return String.valueOf(total / playerUsage.size());
        }));

        // 统计配置的默认使用次数范围
        metrics.addCustomChart(new Metrics.SimplePie("default_uses_range", () -> {
            int defaultUses = getDefaultBlockTimes();
            if (defaultUses <= 100) return "1-100";
            if (defaultUses <= 1000) return "101-1000";
            if (defaultUses <= 10000) return "1001-10000";
            if (defaultUses <= 100000) return "10001-100000";
            return "100000+";
        }));

        // 统计是否启用了调试模式
        metrics.addCustomChart(new Metrics.SimplePie("debug_mode", () ->
            String.valueOf(getConfig().getBoolean("debug-mode"))));

        // 统计是否启用了更新检查
        metrics.addCustomChart(new Metrics.SimplePie("update_check", () ->
            String.valueOf(getConfig().getBoolean("check-updates"))));
    }

    // 调试日志方法
    public void debug(String message) {
        if(getConfig().getBoolean("debug-mode")) {
            getLogger().info("[Debug] " + message);
        }
    }

    // 发送消息方法
    public void sendMessage(CommandSender sender, String path, Object... args) {
        String message = languageManager.getMessage(path, args);
        sender.sendMessage(languageManager.getMessage("general.prefix") + message);
    }

    public LanguageManager getLanguageManager() {
        return languageManager;
    }

    @Override
    public void onDisable() {
        if (statistics != null) {
            statistics.saveStats();
        }

        // 取消所有FoliaLib任务
        if (foliaLib != null) {
            foliaLib.getScheduler().cancelAllTasks();
        }

        // 关闭数据库连接
        if (databaseManager != null) {
            databaseManager.close();
        }

        // 如果 languageManager 为 null，使用默认消息
        if (languageManager != null) {
            getLogger().info(languageManager.getMessage("general.plugin-disabled"));
        } else {
            getLogger().info("Plugin disabled.");
        }
    }

    // 检查更新
    private void checkForUpdates() {
        if(getConfig().getBoolean("check-updates")) {
            debug("正在检查更新...");
            new UpdateChecker(this, 112611).checkForUpdates();
        }
    }

    // 记录使用统计
    public void logUsage(Player player, ItemStack block) {
        if(statistics != null) {
            statistics.logBlockUse(player, block);
        }
    }

    // 获取玩家使用次数
    public int getPlayerUsage(UUID playerUUID) {
        return playerUsage.getOrDefault(playerUUID, 0);
    }

    // 增加玩家使用次数
    public void incrementPlayerUsage(UUID playerUUID) {
        playerUsage.merge(playerUUID, 1, Integer::sum);
    }

    // 生成进度条
    public String getProgressBar(int current, int max) {
        int bars = 20;
        float percent = (float) current / max;
        int filledBars = (int) (bars * percent);

        StringBuilder bar = new StringBuilder("§a");
        for(int i = 0; i < bars; i++) {
            if(i < filledBars) {
                bar.append("■");
            } else {
                bar.append("□");
            }
        }
        return bar.toString();
    }

    private void checkAndUpdateAllConfigs() {
        // 检查主配置文件
        checkAndUpdateConfig("config.yml", true);

        // 检查语言文件
        for (String langCode : languageManager.getSupportedLanguages().keySet()) {
            checkAndUpdateConfig("lang_" + langCode + ".yml", false);
        }

        // 检查食物配置文件
        checkAndUpdateConfig("foodconf.yml", false);
    }

    private void checkAndUpdateConfig(String fileName, boolean isMainConfig) {
        File configFile = new File(getDataFolder(), fileName);
        if (!configFile.exists()) {
            saveResource(fileName, false);
            getLogger().info(languageManager.getMessage("general.config-created", fileName));
            return;
        }

        FileConfiguration currentConfig = YamlConfiguration.loadConfiguration(configFile);
        InputStream defaultConfigStream = getResource(fileName);
        if (defaultConfigStream == null) {
            getLogger().warning("无法找到默认配置文件: " + fileName);
            return;
        }

        FileConfiguration defaultConfig = YamlConfiguration.loadConfiguration(new InputStreamReader(defaultConfigStream));
        boolean configUpdated = false;
        Map<String, Object> missingEntries = new LinkedHashMap<>();

        // 递归检查所有键
        for (String key : defaultConfig.getKeys(true)) {
            if (!currentConfig.contains(key, true)) {
                missingEntries.put(key, defaultConfig.get(key));
                configUpdated = true;
            }
        }

        if (configUpdated) {
            // 添加缺失的配置项
            for (Map.Entry<String, Object> entry : missingEntries.entrySet()) {
                String key = entry.getKey();
                Object value = entry.getValue();

                // 获取父节点的注释（如果有）
                String parentPath = key.contains(".") ? key.substring(0, key.lastIndexOf('.')) : "";
                if (!parentPath.isEmpty() && defaultConfig.contains(parentPath)) {
                    List<String> comments = defaultConfig.getComments(parentPath);
                    if (comments != null && !comments.isEmpty()) {
                        currentConfig.setComments(parentPath, comments);
                    }
                }

                // 获取键的注释
                List<String> comments = defaultConfig.getComments(key);
                if (comments != null && !comments.isEmpty()) {
                    currentConfig.setComments(key, comments);
                }

                currentConfig.set(key, value);
                getLogger().info(languageManager.getMessage("general.config-key-added", fileName, key));
            }

            try {
                currentConfig.save(configFile);
                getLogger().info(languageManager.getMessage("general.config-updated", fileName));
            } catch (IOException e) {
                getLogger().warning("无法保存更新后的配置文件 " + fileName + ": " + e.getMessage());
            }
        }
    }

    public BlockListener getListener() {
        return listener;
    }

    public BlockManager getBlockManager() {
        return this.blockManager;
    }

    public FoodManager getMagicFood() {
        return magicFood;
    }

    public FoodService getFoodService() {
        return this.foodService;
    }

    public String getMagicLore() {
        return ChatColor.translateAlternateColorCodes('&', getConfig().getString("magic-lore", "&e⚡ &7MagicBlock"));
    }

    public List<String> getBlacklistedWorlds() {
        return blacklistedWorlds;
    }

    public String getUsageLorePrefix() {
        return getConfig().getString("usage-lore-prefix", "Total times:");
    }

    public int getDefaultBlockTimes() {
        return this.getConfig().getInt("default-block-times", 100);
    }

    private void saveFoodConfig() {
        try {
            foodConfig.save(new File(getDataFolder(), "foodconf.yml"));
        } catch (IOException e) {
            getLogger().warning("Could not save food config: " + e.getMessage());
        }
    }

    public FileConfiguration getFoodConfig() {
        return foodConfig;
    }

    public ItemStack createMagicBlock() {
        ItemStack item = new ItemStack(Material.STONE);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            // 根据当前语言获取方块名称
            String blockName = languageManager.getMessage("blocks.STONE");

            // 在原有名称两侧添加装饰符号
            String nameFormat = getConfig().getString("display.block-name-format", "&b✦ %s &b✦");
            meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                String.format(nameFormat, blockName)));

            ArrayList<String> lore = new ArrayList<>();
            lore.add(getMagicLore());

            // 添加装饰性lore（如果启用）
            if (getConfig().getBoolean("display.decorative-lore.enabled", true)) {
                List<String> decorativeLore = getConfig().getStringList("display.decorative-lore.lines");
                for (String line : decorativeLore) {
                    lore.add(ChatColor.translateAlternateColorCodes('&', line));
                }
            }

            meta.setLore(lore);
            meta.addEnchant(Enchantment.DURABILITY, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    public boolean hasMagicLore(ItemMeta meta) {
        if (meta == null || !meta.hasLore()) return false;
        List<String> lore = meta.getLore();
        return lore != null && lore.contains(getMagicLore());
    }

    public void reloadPluginAllowedMaterials() {
        reloadConfig();
        languageManager.reloadLanguage();
        reloadFoodConfig();

        // 重新加载食物管理器
        if (magicFood != null) {
            // 不需要重新注册事件监听器，只需要重新创建实例
            magicFood = new FoodManager(this);
        }

        List<Material> newAllowedMaterials = loadMaterialsFromConfig();
        listener.setAllowedMaterials(newAllowedMaterials);
        getLogger().info(languageManager.getMessage("general.materials-updated"));
    }

    public void reloadFoodConfig() {
        File file = new File(getDataFolder(), "foodconf.yml");
        if (file.exists()) {
            foodConfig = YamlConfiguration.loadConfiguration(file);
            this.getLogger().info(languageManager.getMessage("general.food-config-reloaded"));
        } else {
            this.getLogger().warning(languageManager.getMessage("general.food-config-not-found"));
        }
    }

    public List<Material> getAllowedMaterialsForPlayer(Player player) {
        List<Material> playerMaterials = new ArrayList<>(loadMaterialsFromConfig());

        ConfigurationSection groups = getConfig().getConfigurationSection("group");
        if (groups != null) {
            for (String key : groups.getKeys(false)) {
                if (player.hasPermission("magicblock." + key.replace("-material", ""))) {
                    List<String> groupMaterials = groups.getStringList(key);
                    for (String materialName : groupMaterials) {
                        Material mat = Material.getMaterial(materialName);
                        if (mat != null && !playerMaterials.contains(mat)) {
                            playerMaterials.add(mat);
                        }
                    }
                }
            }
        }
        return playerMaterials;
    }

    private void initializeConfig() {
        saveDefaultConfig();
        reloadConfig();

        // 检查并更新所有配置文件
        checkAndUpdateAllConfigs();

        // 初始化食物配置
        File foodConfigFile = new File(getDataFolder(), "foodconf.yml");
        if (!foodConfigFile.exists()) {
            saveResource("foodconf.yml", false);
        }
        try {
            foodConfig = YamlConfiguration.loadConfiguration(foodConfigFile);
        } catch (Exception e) {
            getLogger().warning("Could not load food config: " + e.getMessage());
            foodConfig = new YamlConfiguration();
        }
    }

    private void initializeMembers() {
        this.blockManager = new BlockManager(this);
        this.blockBindManager = new BlockBindManager(this);
        this.listener = new BlockListener(this, allowedMaterials);
        this.magicFood = new FoodManager(this);
        this.blacklistedWorlds = getConfig().getStringList("blacklisted-worlds");

        // 初始化数据库管理器
        if (getConfig().getBoolean("database.enabled", false)) {
            this.databaseManager = new DatabaseManager(this);
            if (this.databaseManager.isEnabled()) {
                this.blockBindManager.setDatabaseManager(this.databaseManager);
            }
        }
    }

    private void registerEventsAndCommands() {
        getServer().getPluginManager().registerEvents(listener, this);
        getServer().getPluginManager().registerEvents(magicFood, this);
        CommandManager commandManager = new CommandManager(this);
        getCommand("magicblock").setExecutor(commandManager);
        getCommand("magicblock").setTabCompleter(new TabCompleter(this));
    }

    private List<Material> loadMaterialsFromConfig() {
        List<Material> materials = new ArrayList<>();
        List<String> configMaterials = getConfig().getStringList("allowed-materials");

        for (String materialName : configMaterials) {
            try {
                Material material = Material.valueOf(materialName.toUpperCase());
                if (material.isBlock()) {
                    materials.add(material);
                } else {
                    getLogger().warning("Material " + materialName + " is not a block!");
                }
            } catch (IllegalArgumentException e) {
                getLogger().warning("Invalid material name in config: " + materialName);
            }
        }

        return materials;
    }

    public List<Material> getAllowedMaterials() {
        return new ArrayList<>(allowedMaterials);
    }

    public String getMessage(String path) {
        return languageManager.getMessage(path);
    }

    public String getMessage(String path, Object... args) {
        return languageManager.getMessage(path, args);
    }

    public BlockBindManager getBlockBindManager() {
        return this.blockBindManager;
    }

    public FoliaLib getFoliaLib() {
        return foliaLib;
    }

    public Statistics getStatistics() {
        return statistics;
    }
}

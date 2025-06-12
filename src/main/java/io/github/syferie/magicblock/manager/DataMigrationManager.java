package io.github.syferie.magicblock.manager;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * 数据迁移管理器
 * 负责将YAML格式的绑定数据迁移到JSON格式
 */
public class DataMigrationManager {
    private final MagicBlockPlugin plugin;
    private final Gson gson;
    private final File bindingsYamlFile;
    private final File bindingsJsonFile;
    
    public DataMigrationManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
        this.bindingsYamlFile = new File(plugin.getDataFolder(), "bindings.yml");
        this.bindingsJsonFile = new File(plugin.getDataFolder(), "bindings.json");
    }
    
    /**
     * 检查是否需要迁移数据
     */
    public boolean needsMigration() {
        // 如果YAML文件存在且JSON文件不存在，则需要迁移
        return bindingsYamlFile.exists() && !bindingsJsonFile.exists();
    }
    
    /**
     * 执行数据迁移
     */
    public boolean migrateData() {
        if (!needsMigration()) {
            plugin.debug("无需迁移绑定数据");
            return true;
        }
        
        plugin.getLogger().info("开始迁移绑定数据从YAML到JSON...");
        
        try {
            // 读取YAML数据
            Map<String, Object> yamlData = readYamlBindings();
            if (yamlData.isEmpty()) {
                plugin.getLogger().info("YAML绑定文件为空，创建空的JSON文件");
                createEmptyJsonFile();
                return true;
            }
            
            // 转换为JSON格式
            Map<String, Object> jsonData = convertYamlToJson(yamlData);
            
            // 写入JSON文件
            writeJsonBindings(jsonData);
            
            // 备份原YAML文件
            backupYamlFile();
            
            plugin.getLogger().info("绑定数据迁移完成！迁移了 " + jsonData.size() + " 个玩家的数据");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "绑定数据迁移失败", e);
            return false;
        }
    }
    
    /**
     * 读取YAML绑定数据
     */
    private Map<String, Object> readYamlBindings() {
        Map<String, Object> data = new HashMap<>();

        try {
            YamlConfiguration yaml = YamlConfiguration.loadConfiguration(bindingsYamlFile);

            // 检查是否有bindings节点
            ConfigurationSection bindingsSection = yaml.getConfigurationSection("bindings");
            if (bindingsSection == null) {
                plugin.debug("YAML文件中没有找到bindings节点");
                return data;
            }

            // 遍历所有玩家UUID
            for (String playerUuid : bindingsSection.getKeys(false)) {
                ConfigurationSection playerSection = bindingsSection.getConfigurationSection(playerUuid);
                if (playerSection != null) {
                    Map<String, Object> playerData = new HashMap<>();

                    // 读取玩家的所有绑定方块
                    for (String blockId : playerSection.getKeys(false)) {
                        ConfigurationSection blockSection = playerSection.getConfigurationSection(blockId);
                        if (blockSection != null) {
                            Map<String, Object> blockData = new HashMap<>();

                            // 只读取基本数据，避免复杂对象
                            String material = blockSection.getString("material");
                            int uses = blockSection.getInt("uses", 0);
                            int maxUses = blockSection.getInt("max_uses", 0);
                            boolean hidden = blockSection.getBoolean("hidden", false);

                            if (material != null) {
                                blockData.put("material", material);
                                blockData.put("uses", uses);
                                blockData.put("max_uses", maxUses);
                                blockData.put("hidden", hidden);

                                playerData.put(blockId, blockData);
                            }
                        }
                    }

                    if (!playerData.isEmpty()) {
                        data.put(playerUuid, playerData);
                    }
                }
            }

            plugin.debug("从YAML文件读取了 " + data.size() + " 个玩家的绑定数据");

        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "读取YAML绑定数据失败", e);
        }

        return data;
    }
    
    /**
     * 转换YAML数据为JSON格式
     * 由于readYamlBindings已经提取了纯净数据，这里只需要简单验证
     */
    private Map<String, Object> convertYamlToJson(Map<String, Object> yamlData) {
        Map<String, Object> jsonData = new HashMap<>();

        for (Map.Entry<String, Object> entry : yamlData.entrySet()) {
            String playerUuid = entry.getKey();
            Object playerData = entry.getValue();

            // 验证UUID格式
            if (isValidUUID(playerUuid) && playerData instanceof Map) {
                @SuppressWarnings("unchecked")
                Map<String, Object> playerBindings = (Map<String, Object>) playerData;

                // 验证绑定数据
                Map<String, Object> validatedBindings = new HashMap<>();
                for (Map.Entry<String, Object> bindingEntry : playerBindings.entrySet()) {
                    String blockId = bindingEntry.getKey();
                    Object bindingData = bindingEntry.getValue();

                    // 验证blockId和绑定数据
                    if (isValidUUID(blockId) && bindingData instanceof Map) {
                        @SuppressWarnings("unchecked")
                        Map<String, Object> blockData = (Map<String, Object>) bindingData;

                        // 验证必要字段
                        if (blockData.containsKey("material") &&
                            blockData.containsKey("uses") &&
                            blockData.containsKey("max_uses")) {
                            validatedBindings.put(blockId, blockData);
                        }
                    }
                }

                if (!validatedBindings.isEmpty()) {
                    jsonData.put(playerUuid, validatedBindings);
                }
            }
        }

        plugin.debug("转换后的JSON数据包含 " + jsonData.size() + " 个玩家的绑定");
        return jsonData;
    }

    /**
     * 验证UUID格式
     */
    private boolean isValidUUID(String uuid) {
        if (uuid == null || uuid.trim().isEmpty()) {
            return false;
        }
        try {
            UUID.fromString(uuid);
            return true;
        } catch (IllegalArgumentException e) {
            return false;
        }
    }
    
    /**
     * 写入JSON绑定数据
     */
    private void writeJsonBindings(Map<String, Object> data) throws IOException {
        try (FileWriter writer = new FileWriter(bindingsJsonFile)) {
            gson.toJson(data, writer);
        }
        plugin.debug("JSON绑定数据已写入文件");
    }
    
    /**
     * 创建空的JSON文件
     */
    private void createEmptyJsonFile() throws IOException {
        try (FileWriter writer = new FileWriter(bindingsJsonFile)) {
            writer.write("{}");
        }
        plugin.debug("创建了空的JSON绑定文件");
    }
    
    /**
     * 备份原YAML文件
     */
    private void backupYamlFile() {
        try {
            File backupFile = new File(plugin.getDataFolder(), "bindings.yml.backup");
            if (bindingsYamlFile.renameTo(backupFile)) {
                plugin.getLogger().info("原YAML文件已备份为 bindings.yml.backup");
            } else {
                plugin.getLogger().warning("无法备份原YAML文件");
            }
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "备份YAML文件时出错", e);
        }
    }
    
    /**
     * 验证迁移结果
     */
    public boolean validateMigration() {
        if (!bindingsJsonFile.exists()) {
            plugin.getLogger().warning("迁移验证失败：JSON文件不存在");
            return false;
        }
        
        try (FileReader reader = new FileReader(bindingsJsonFile)) {
            TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
            Map<String, Object> data = gson.fromJson(reader, typeToken.getType());
            
            if (data == null) {
                plugin.getLogger().warning("迁移验证失败：JSON文件内容为空");
                return false;
            }
            
            plugin.debug("迁移验证成功：JSON文件包含 " + data.size() + " 个玩家的数据");
            return true;
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "迁移验证失败", e);
            return false;
        }
    }
    
    /**
     * 强制迁移（即使JSON文件已存在）
     */
    public boolean forceMigration() {
        if (!bindingsYamlFile.exists()) {
            plugin.getLogger().warning("强制迁移失败：YAML文件不存在");
            return false;
        }
        
        plugin.getLogger().info("开始强制迁移绑定数据...");
        
        try {
            // 如果JSON文件已存在，先备份
            if (bindingsJsonFile.exists()) {
                File jsonBackup = new File(plugin.getDataFolder(), "bindings.json.backup");
                if (bindingsJsonFile.renameTo(jsonBackup)) {
                    plugin.getLogger().info("现有JSON文件已备份为 bindings.json.backup");
                }
            }
            
            // 执行迁移
            return migrateData();
            
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, "强制迁移失败", e);
            return false;
        }
    }
    
    /**
     * 获取迁移统计信息
     */
    public String getMigrationStats() {
        StringBuilder stats = new StringBuilder();
        stats.append("=== 绑定数据迁移统计 ===\n");
        
        if (bindingsYamlFile.exists()) {
            stats.append("YAML文件: 存在 (").append(bindingsYamlFile.length()).append(" 字节)\n");
        } else {
            stats.append("YAML文件: 不存在\n");
        }
        
        if (bindingsJsonFile.exists()) {
            stats.append("JSON文件: 存在 (").append(bindingsJsonFile.length()).append(" 字节)\n");
            
            try (FileReader reader = new FileReader(bindingsJsonFile)) {
                TypeToken<Map<String, Object>> typeToken = new TypeToken<Map<String, Object>>() {};
                Map<String, Object> data = gson.fromJson(reader, typeToken.getType());
                if (data != null) {
                    stats.append("JSON数据: ").append(data.size()).append(" 个玩家\n");
                }
            } catch (Exception e) {
                stats.append("JSON数据: 读取失败\n");
            }
        } else {
            stats.append("JSON文件: 不存在\n");
        }
        
        File backupFile = new File(plugin.getDataFolder(), "bindings.yml.backup");
        if (backupFile.exists()) {
            stats.append("YAML备份: 存在 (").append(backupFile.length()).append(" 字节)\n");
        } else {
            stats.append("YAML备份: 不存在\n");
        }
        
        return stats.toString();
    }
}

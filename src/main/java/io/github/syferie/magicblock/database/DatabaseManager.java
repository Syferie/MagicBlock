package io.github.syferie.magicblock.database;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.sql.*;
import java.util.*;
import java.util.logging.Level;

/**
 * 数据库管理器，用于处理与MySQL数据库的连接和操作
 */
public class DatabaseManager {
    private final MagicBlockPlugin plugin;
    private HikariDataSource dataSource;
    private final String tablePrefix;
    private final String bindingsTable;

    /**
     * 构造函数
     * @param plugin 插件实例
     */
    public DatabaseManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        FileConfiguration config = plugin.getConfig();
        this.tablePrefix = config.getString("database.table-prefix", "mb_");
        this.bindingsTable = tablePrefix + "bindings";

        if (config.getBoolean("database.enabled", false)) {
            setupDatabase();
        }
    }

    /**
     * 设置数据库连接
     */
    private void setupDatabase() {
        FileConfiguration config = plugin.getConfig();

        try {
            // 检查HikariCP是否可用
            try {
                Class.forName("com.zaxxer.hikari.HikariConfig");
            } catch (ClassNotFoundException e) {
                plugin.getLogger().severe("HikariCP依赖缺失！请安装HikariCP到服务器的lib目录或使用带有依赖的插件版本。");
                plugin.getLogger().severe("数据库功能将被禁用。");
                return;
            }

            // 配置HikariCP连接池
            HikariConfig hikariConfig = new HikariConfig();
            hikariConfig.setJdbcUrl("jdbc:mysql://" +
                    config.getString("database.host", "localhost") + ":" +
                    config.getInt("database.port", 3306) + "/" +
                    config.getString("database.database", "magicblock") +
                    "?useSSL=false&useUnicode=true&characterEncoding=UTF-8&serverTimezone=UTC");
            hikariConfig.setUsername(config.getString("database.username", "root"));
            hikariConfig.setPassword(config.getString("database.password", ""));
            // 使用合理的默认值，适合少量数据的插件
            hikariConfig.setMaximumPoolSize(3); // 少量数据只需要少量连接
            hikariConfig.setMinimumIdle(1); // 最小空闲连接
            hikariConfig.setMaxLifetime(1800000); // 30分钟
            hikariConfig.setConnectionTimeout(5000); // 5秒
            hikariConfig.setIdleTimeout(600000); // 10分钟空闲超时
            hikariConfig.setPoolName("MagicBlockHikariPool");

            // 添加连接测试查询
            hikariConfig.setConnectionTestQuery("SELECT 1");

            // 缓存预编译语句
            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "25");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            // 创建数据源
            dataSource = new HikariDataSource(hikariConfig);

            // 创建表
            createTables();

            plugin.getLogger().info(plugin.getLanguageManager().getMessage("general.database-connected"));
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            dataSource = null;
        }
    }

    /**
     * 创建必要的数据库表
     */
    private void createTables() {
        String createBindingsTable = "CREATE TABLE IF NOT EXISTS " + bindingsTable + " (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "player_name VARCHAR(16) NOT NULL, " +
                "block_id VARCHAR(36) NOT NULL, " +
                "material VARCHAR(50) NOT NULL, " +
                "uses INT NOT NULL, " +
                "max_uses INT NOT NULL, " +
                "hidden BOOLEAN DEFAULT FALSE, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP, " +
                "INDEX idx_player_uuid (player_uuid), " +
                "INDEX idx_block_id (block_id)" +
                ") ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;";

        try (Connection conn = getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute(createBindingsTable);
            plugin.getLogger().info(plugin.getLanguageManager().getMessage("general.database-tables-created"));
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
        }
    }

    /**
     * 获取数据库连接
     * @return 数据库连接
     * @throws SQLException 如果获取连接失败
     */
    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database is not enabled or connection failed");
        }
        return dataSource.getConnection();
    }

    /**
     * 检查数据库是否已启用
     * @return 如果数据库已启用则返回true
     */
    public boolean isEnabled() {
        return dataSource != null;
    }

    /**
     * 获取表前缀
     * @return 表前缀
     */
    public String getTablePrefix() {
        return tablePrefix;
    }

    /**
     * 关闭数据库连接
     */
    public void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    /**
     * 保存方块绑定数据到数据库
     * @param playerUUID 玩家UUID
     * @param playerName 玩家名称
     * @param blockId 方块ID
     * @param material 方块材质
     * @param uses 使用次数
     * @param maxUses 最大使用次数
     * @return 是否保存成功
     */
    public boolean saveBinding(UUID playerUUID, String playerName, String blockId, String material, int uses, int maxUses) {
        if (!isEnabled()) return false;

        String sql = "INSERT INTO " + bindingsTable +
                " (player_uuid, player_name, block_id, material, uses, max_uses) VALUES (?, ?, ?, ?, ?, ?) " +
                "ON DUPLICATE KEY UPDATE material = ?, uses = ?, max_uses = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, playerName);
            stmt.setString(3, blockId);
            stmt.setString(4, material);
            stmt.setInt(5, uses);
            stmt.setInt(6, maxUses);
            stmt.setString(7, material);
            stmt.setInt(8, uses);
            stmt.setInt(9, maxUses);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            return false;
        }
    }

    /**
     * 更新方块绑定数据
     * @param playerUUID 玩家UUID
     * @param blockId 方块ID
     * @param material 方块材质
     * @param uses 使用次数
     * @param maxUses 最大使用次数
     * @return 是否更新成功
     */
    public boolean updateBinding(UUID playerUUID, String blockId, String material, int uses, int maxUses) {
        if (!isEnabled()) return false;

        String sql = "UPDATE " + bindingsTable +
                " SET material = ?, uses = ?, max_uses = ? " +
                "WHERE player_uuid = ? AND block_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, material);
            stmt.setInt(2, uses);
            stmt.setInt(3, maxUses);
            stmt.setString(4, playerUUID.toString());
            stmt.setString(5, blockId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            return false;
        }
    }

    /**
     * 获取玩家的所有绑定方块
     * @param playerUUID 玩家UUID
     * @return 绑定方块的Map，键为方块ID，值为方块数据
     */
    public Map<String, Map<String, Object>> getPlayerBindings(UUID playerUUID) {
        if (!isEnabled()) return new HashMap<>();

        Map<String, Map<String, Object>> bindings = new HashMap<>();
        String sql = "SELECT * FROM " + bindingsTable + " WHERE player_uuid = ? AND hidden = FALSE";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    String blockId = rs.getString("block_id");
                    Map<String, Object> blockData = new HashMap<>();
                    blockData.put("material", rs.getString("material"));
                    blockData.put("uses", rs.getInt("uses"));
                    blockData.put("max_uses", rs.getInt("max_uses"));
                    blockData.put("hidden", rs.getBoolean("hidden"));

                    bindings.put(blockId, blockData);
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
        }

        return bindings;
    }

    /**
     * 获取特定方块的绑定数据
     * @param blockId 方块ID
     * @return 方块数据的Map
     */
    public Map<String, Object> getBlockBinding(String blockId) {
        if (!isEnabled()) return null;

        String sql = "SELECT * FROM " + bindingsTable + " WHERE block_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, blockId);

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    Map<String, Object> blockData = new HashMap<>();
                    blockData.put("player_uuid", UUID.fromString(rs.getString("player_uuid")));
                    blockData.put("player_name", rs.getString("player_name"));
                    blockData.put("material", rs.getString("material"));
                    blockData.put("uses", rs.getInt("uses"));
                    blockData.put("max_uses", rs.getInt("max_uses"));
                    blockData.put("hidden", rs.getBoolean("hidden"));

                    return blockData;
                }
            }
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
        }

        return null;
    }

    /**
     * 设置方块的隐藏状态
     * @param playerUUID 玩家UUID
     * @param blockId 方块ID
     * @param hidden 是否隐藏
     * @return 是否设置成功
     */
    public boolean setBlockHidden(UUID playerUUID, String blockId, boolean hidden) {
        if (!isEnabled()) return false;

        String sql = "UPDATE " + bindingsTable +
                " SET hidden = ? " +
                "WHERE player_uuid = ? AND block_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setBoolean(1, hidden);
            stmt.setString(2, playerUUID.toString());
            stmt.setString(3, blockId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            return false;
        }
    }

    /**
     * 删除方块绑定
     * @param playerUUID 玩家UUID
     * @param blockId 方块ID
     * @return 是否删除成功
     */
    public boolean deleteBinding(UUID playerUUID, String blockId) {
        if (!isEnabled()) return false;

        String sql = "DELETE FROM " + bindingsTable +
                " WHERE player_uuid = ? AND block_id = ?";

        try (Connection conn = getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, blockId);

            return stmt.executeUpdate() > 0;
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            return false;
        }
    }

    /**
     * 从文件配置迁移数据到数据库
     * @param bindConfig 绑定配置
     * @return 是否迁移成功
     */
    public boolean migrateFromFile(FileConfiguration bindConfig) {
        if (!isEnabled() || bindConfig == null) return false;

        plugin.getLogger().info(plugin.getLanguageManager().getMessage("general.database-migration-start"));

        try {
            if (bindConfig.contains("bindings")) {
                for (String uuidStr : bindConfig.getConfigurationSection("bindings").getKeys(false)) {
                    UUID playerUUID = UUID.fromString(uuidStr);
                    String playerName = plugin.getServer().getOfflinePlayer(playerUUID).getName();
                    if (playerName == null) playerName = "Unknown";

                    for (String blockId : bindConfig.getConfigurationSection("bindings." + uuidStr).getKeys(false)) {
                        String path = "bindings." + uuidStr + "." + blockId;
                        String material = bindConfig.getString(path + ".material");
                        int uses = bindConfig.getInt(path + ".uses");
                        int maxUses = bindConfig.getInt(path + ".max_uses", uses);
                        boolean hidden = bindConfig.getBoolean(path + ".hidden", false);

                        // 保存到数据库
                        saveBinding(playerUUID, playerName, blockId, material, uses, maxUses);

                        // 设置隐藏状态
                        if (hidden) {
                            setBlockHidden(playerUUID, blockId, true);
                        }
                    }
                }
            }

            plugin.getLogger().info(plugin.getLanguageManager().getMessage("general.database-migration-complete"));
            return true;
        } catch (Exception e) {
            plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            return false;
        }
    }

    /**
     * 清理使用次数为0的方块
     * @param playerUUID 玩家UUID
     */
    public void cleanupZeroUsageBlocks(UUID playerUUID) {
        if (!isEnabled()) return;

        // 如果配置为移除耗尽的方块
        if (plugin.getConfig().getBoolean("remove-depleted-blocks", false)) {
            String sql = "DELETE FROM " + bindingsTable +
                    " WHERE player_uuid = ? AND uses <= 0";

            try (Connection conn = getConnection();
                 PreparedStatement stmt = conn.prepareStatement(sql)) {
                stmt.setString(1, playerUUID.toString());
                stmt.executeUpdate();
            } catch (SQLException e) {
                plugin.getLogger().log(Level.SEVERE, plugin.getLanguageManager().getMessage("general.database-error", e.getMessage()), e);
            }
        }
    }
}

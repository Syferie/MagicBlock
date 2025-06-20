package io.github.syferie.magicblock.manager;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.database.DatabaseManager;
import org.bukkit.Material;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;
import java.util.logging.Level;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

/**
 * 收藏管理器
 * 负责管理玩家的方块收藏功能
 */
public class FavoriteManager {
    private final MagicBlockPlugin plugin;
    private final DatabaseManager databaseManager;
    private final Gson gson;
    private final File favoritesFile;
    
    // 内存缓存
    private final Map<UUID, Set<Material>> playerFavorites;
    
    public FavoriteManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.databaseManager = plugin.getDatabaseManager();
        this.gson = new GsonBuilder()
            .setPrettyPrinting()
            .serializeNulls()
            .disableHtmlEscaping()
            .create();
        this.favoritesFile = new File(plugin.getDataFolder(), "favorites.json");
        this.playerFavorites = new HashMap<>();

        initializeStorage();
        loadAllFavorites();
    }
    
    /**
     * 初始化存储
     */
    private void initializeStorage() {
        if (databaseManager != null && databaseManager.isEnabled()) {
            // 数据库存储 - 创建收藏表
            createFavoritesTable();
        } else {
            // JSON文件存储 - 确保文件存在
            if (!favoritesFile.exists()) {
                try {
                    favoritesFile.createNewFile();
                    // 写入空的JSON对象
                    try (FileWriter writer = new FileWriter(favoritesFile)) {
                        writer.write("{}");
                    }
                } catch (IOException e) {
                    plugin.getLogger().log(Level.SEVERE, "无法创建收藏文件", e);
                }
            }
        }
    }
    
    /**
     * 创建收藏表
     */
    private void createFavoritesTable() {
        if (databaseManager == null || !databaseManager.isEnabled()) return;
        
        String sql = "CREATE TABLE IF NOT EXISTS " + databaseManager.getTablePrefix() + "favorites (" +
                "id INT AUTO_INCREMENT PRIMARY KEY, " +
                "player_uuid VARCHAR(36) NOT NULL, " +
                "material VARCHAR(50) NOT NULL, " +
                "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP, " +
                "UNIQUE KEY unique_favorite (player_uuid, material), " +
                "INDEX idx_player (player_uuid)" +
                ")";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.executeUpdate();
            plugin.debug("收藏表创建成功");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "创建收藏表失败", e);
        }
    }
    
    /**
     * 加载所有收藏数据
     */
    private void loadAllFavorites() {
        if (databaseManager != null && databaseManager.isEnabled()) {
            loadFavoritesFromDatabase();
        } else {
            loadFavoritesFromJson();
        }
    }
    
    /**
     * 从数据库加载收藏
     */
    private void loadFavoritesFromDatabase() {
        String sql = "SELECT player_uuid, material FROM " + databaseManager.getTablePrefix() + "favorites";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql);
             ResultSet rs = stmt.executeQuery()) {
            
            while (rs.next()) {
                UUID playerUUID = UUID.fromString(rs.getString("player_uuid"));
                Material material = Material.valueOf(rs.getString("material"));
                
                playerFavorites.computeIfAbsent(playerUUID, k -> new HashSet<>()).add(material);
            }
            
            plugin.debug("从数据库加载了 " + playerFavorites.size() + " 个玩家的收藏数据");
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "从数据库加载收藏失败", e);
        }
    }
    
    /**
     * 从JSON文件加载收藏
     */
    private void loadFavoritesFromJson() {
        if (!favoritesFile.exists()) return;
        
        try (FileReader reader = new FileReader(favoritesFile)) {
            TypeToken<Map<String, Set<String>>> typeToken = new TypeToken<Map<String, Set<String>>>() {};
            Map<String, Set<String>> data = gson.fromJson(reader, typeToken.getType());
            
            if (data != null) {
                for (Map.Entry<String, Set<String>> entry : data.entrySet()) {
                    try {
                        UUID playerUUID = UUID.fromString(entry.getKey());
                        Set<Material> materials = new HashSet<>();
                        
                        for (String materialName : entry.getValue()) {
                            try {
                                materials.add(Material.valueOf(materialName));
                            } catch (IllegalArgumentException e) {
                                plugin.debug("跳过无效材质: " + materialName);
                            }
                        }
                        
                        if (!materials.isEmpty()) {
                            playerFavorites.put(playerUUID, materials);
                        }
                    } catch (IllegalArgumentException e) {
                        plugin.debug("跳过无效UUID: " + entry.getKey());
                    }
                }
            }
            
            plugin.debug("从JSON文件加载了 " + playerFavorites.size() + " 个玩家的收藏数据");
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "从JSON文件加载收藏失败", e);
        }
    }
    
    /**
     * 保存收藏到JSON文件
     */
    private void saveFavoritesToJson() {
        Map<String, Set<String>> data = new HashMap<>();
        
        for (Map.Entry<UUID, Set<Material>> entry : playerFavorites.entrySet()) {
            Set<String> materialNames = new HashSet<>();
            for (Material material : entry.getValue()) {
                materialNames.add(material.name());
            }
            data.put(entry.getKey().toString(), materialNames);
        }
        
        try (FileWriter writer = new FileWriter(favoritesFile)) {
            gson.toJson(data, writer);
        } catch (IOException e) {
            plugin.getLogger().log(Level.SEVERE, "保存收藏到JSON文件失败", e);
        }
    }
    
    /**
     * 切换收藏状态
     */
    public boolean toggleFavorite(Player player, Material material) {
        UUID playerUUID = player.getUniqueId();
        Set<Material> favorites = playerFavorites.computeIfAbsent(playerUUID, k -> new HashSet<>());
        
        boolean isFavorited;
        if (favorites.contains(material)) {
            favorites.remove(material);
            isFavorited = false;
            removeFavoriteFromStorage(playerUUID, material);
        } else {
            favorites.add(material);
            isFavorited = true;
            addFavoriteToStorage(playerUUID, material);
        }
        
        return isFavorited;
    }
    
    /**
     * 添加收藏到存储
     */
    private void addFavoriteToStorage(UUID playerUUID, Material material) {
        if (databaseManager != null && databaseManager.isEnabled()) {
            addFavoriteToDatabase(playerUUID, material);
        } else {
            saveFavoritesToJson();
        }
    }
    
    /**
     * 从存储移除收藏
     */
    private void removeFavoriteFromStorage(UUID playerUUID, Material material) {
        if (databaseManager != null && databaseManager.isEnabled()) {
            removeFavoriteFromDatabase(playerUUID, material);
        } else {
            saveFavoritesToJson();
        }
    }
    
    /**
     * 添加收藏到数据库
     */
    private void addFavoriteToDatabase(UUID playerUUID, Material material) {
        String sql = "INSERT IGNORE INTO " + databaseManager.getTablePrefix() + "favorites " +
                "(player_uuid, material) VALUES (?, ?)";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, material.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "添加收藏到数据库失败", e);
        }
    }
    
    /**
     * 从数据库移除收藏
     */
    private void removeFavoriteFromDatabase(UUID playerUUID, Material material) {
        String sql = "DELETE FROM " + databaseManager.getTablePrefix() + "favorites " +
                "WHERE player_uuid = ? AND material = ?";
        
        try (Connection conn = databaseManager.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {
            stmt.setString(1, playerUUID.toString());
            stmt.setString(2, material.name());
            stmt.executeUpdate();
        } catch (SQLException e) {
            plugin.getLogger().log(Level.SEVERE, "从数据库移除收藏失败", e);
        }
    }
    
    /**
     * 检查是否收藏
     */
    public boolean isFavorited(Player player, Material material) {
        Set<Material> favorites = playerFavorites.get(player.getUniqueId());
        return favorites != null && favorites.contains(material);
    }
    
    /**
     * 获取玩家的收藏列表（过滤只显示允许的材质）
     */
    public List<Material> getPlayerFavorites(Player player) {
        Set<Material> favorites = playerFavorites.get(player.getUniqueId());
        if (favorites == null || favorites.isEmpty()) {
            return new ArrayList<>();
        }

        // 过滤只显示在允许列表中的材质（包括权限组材质）
        List<Material> allowedMaterials = plugin.getAllowedMaterialsForPlayer(player);
        return favorites.stream()
                .filter(allowedMaterials::contains)
                .sorted(Comparator.comparing(Material::name))
                .collect(ArrayList::new, (list, item) -> list.add(item), ArrayList::addAll);
    }
    
    /**
     * 清理玩家数据
     */
    public void clearPlayerData(UUID playerUUID) {
        playerFavorites.remove(playerUUID);
    }
}

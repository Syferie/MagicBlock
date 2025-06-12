package io.github.syferie.magicblock.block;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.database.DatabaseManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.Chunk;
import org.bukkit.block.BlockState;
import org.bukkit.block.Container;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;
import java.util.logging.Level;
import java.util.stream.Collectors;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.reflect.TypeToken;

public class BlockBindManager {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey bindKey;
    private final File bindFile;
    private final File bindJsonFile;
    private FileConfiguration bindConfig;
    private final Gson gson;
    private final Map<UUID, Map<String, Long>> lastClickTimes = new HashMap<>();
    private static final long DOUBLE_CLICK_TIME = 500; // 双击时间窗口（毫秒）
    private DatabaseManager databaseManager;

    public BlockBindManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.bindKey = new NamespacedKey(plugin, "magicblock_bind");
        this.bindFile = new File(plugin.getDataFolder(), "bindings.yml");
        this.bindJsonFile = new File(plugin.getDataFolder(), "bindings.json");
        this.gson = new GsonBuilder().setPrettyPrinting().create();
        loadBindConfig();
    }

    /**
     * 设置数据库管理器
     * @param databaseManager 数据库管理器实例
     */
    public void setDatabaseManager(DatabaseManager databaseManager) {
        this.databaseManager = databaseManager;

        // 如果数据库已启用且配置文件存在，则迁移数据
        if (databaseManager != null && databaseManager.isEnabled() && bindFile.exists()) {
            databaseManager.migrateFromFile(bindConfig);
        }
    }

    private void loadBindConfig() {
        // 检查是否存在JSON文件
        if (bindJsonFile.exists()) {
            // 优先使用JSON文件
            try (FileReader reader = new FileReader(bindJsonFile)) {
                plugin.debug("使用JSON文件加载绑定数据");
                // 创建空的YAML配置以兼容旧代码
                if (!bindFile.exists()) {
                    try {
                        bindFile.createNewFile();
                    } catch (IOException e) {
                        plugin.getLogger().warning("无法创建绑定配置文件: " + e.getMessage());
                    }
                }
                bindConfig = YamlConfiguration.loadConfiguration(bindFile);
            } catch (IOException e) {
                plugin.getLogger().warning("无法读取JSON绑定文件: " + e.getMessage());
                // 回退到YAML文件
                loadYamlBindConfig();
            }
        } else {
            // 使用YAML文件
            loadYamlBindConfig();
        }
    }

    private void loadYamlBindConfig() {
        if (!bindFile.exists()) {
            try {
                bindFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建绑定配置文件: " + e.getMessage());
            }
        }
        bindConfig = YamlConfiguration.loadConfiguration(bindFile);
        plugin.debug("使用YAML文件加载绑定数据");
    }

    public FileConfiguration getBindConfig() {
        return bindConfig;
    }

    public void saveBindConfig() {
        // 检查是否应该使用JSON存储
        if (bindJsonFile.exists()) {
            saveBindConfigToJson();
        } else {
            // 使用YAML存储
            try {
                bindConfig.save(bindFile);
                plugin.debug("绑定数据已保存到YAML文件");
            } catch (IOException e) {
                plugin.getLogger().warning("无法保存绑定配置到YAML: " + e.getMessage());
            }
        }
    }

    private void saveBindConfigToJson() {
        try {
            // 从YAML配置转换为JSON格式
            Map<String, Object> jsonData = new HashMap<>();

            // 获取所有玩家UUID
            ConfigurationSection bindingsSection = bindConfig.getConfigurationSection("bindings");
            if (bindingsSection != null) {
                for (String uuid : bindingsSection.getKeys(false)) {
                    ConfigurationSection playerSection = bindingsSection.getConfigurationSection(uuid);
                    if (playerSection != null) {
                        Map<String, Object> playerData = new HashMap<>();

                        // 获取玩家的所有绑定方块
                        for (String blockId : playerSection.getKeys(false)) {
                            ConfigurationSection blockSection = playerSection.getConfigurationSection(blockId);
                            if (blockSection != null) {
                                Map<String, Object> blockData = new HashMap<>();

                                // 复制所有方块数据
                                for (String key : blockSection.getKeys(false)) {
                                    blockData.put(key, blockSection.get(key));
                                }

                                playerData.put(blockId, blockData);
                            }
                        }

                        jsonData.put(uuid, playerData);
                    }
                }
            }

            // 写入JSON文件
            try (FileWriter writer = new FileWriter(bindJsonFile)) {
                gson.toJson(jsonData, writer);
                plugin.debug("绑定数据已保存到JSON文件");
            }

        } catch (IOException e) {
            plugin.getLogger().warning("无法保存绑定配置到JSON: " + e.getMessage());
        }
    }

    public String getBindLorePrefix() {
        return ChatColor.translateAlternateColorCodes('&',
            "&7" + plugin.getMessage("messages.bound-to") + " &b");
    }

    public void bindBlock(Player player, ItemStack item) {
        // 检查绑定系统是否启用
        if (!plugin.getConfig().getBoolean("enable-binding-system", true)) {
            plugin.sendMessage(player, "messages.binding-disabled");
            return;
        }

        if (!plugin.getBlockManager().isMagicBlock(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 检查是否已经绑定
        if (isBlockBound(item)) {
            plugin.sendMessage(player, "messages.already-bound");
            return;
        }

        // 设置绑定数据
        String uuid = player.getUniqueId().toString();
        meta.getPersistentDataContainer().set(bindKey, PersistentDataType.STRING, uuid);

        // 添加绑定说明
        List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
        // 找到magic-lore的位置
        int magicLoreIndex = -1;
        for (int i = 0; i < lore.size(); i++) {
            if (lore.get(i).equals(plugin.getMagicLore())) {
                magicLoreIndex = i;
                break;
            }
        }

        // 在magic-lore后面添加绑定信息
        if (magicLoreIndex != -1) {
            // 找到装饰性lore的结束位置
            int insertIndex = magicLoreIndex + 1;
            if (plugin.getConfig().getBoolean("display.decorative-lore.enabled", true)) {
                while (insertIndex < lore.size() && !lore.get(insertIndex).contains(plugin.getUsageLorePrefix())) {
                    insertIndex++;
                }
            }
            lore.add(insertIndex, getBindLorePrefix() + player.getName());
        } else {
            lore.add(getBindLorePrefix() + player.getName());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);

        // 获取当前使用次数和最大使用次数
        int currentUses = plugin.getBlockManager().getUseTimes(item);
        int maxUses = plugin.getBlockManager().getMaxUseTimes(item);

        // 生成方块ID
        String itemId = UUID.randomUUID().toString();

        // 存储方块ID到物品中
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "block_id"),
            PersistentDataType.STRING,
            itemId
        );
        item.setItemMeta(meta);

        // 保存绑定数据
        if (databaseManager != null && databaseManager.isEnabled()) {
            // 使用数据库存储
            databaseManager.saveBinding(
                player.getUniqueId(),
                player.getName(),
                itemId,
                item.getType().name(),
                currentUses,
                maxUses
            );
        } else {
            // 使用文件存储
            String path = "bindings." + uuid + "." + itemId;
            bindConfig.set(path + ".material", item.getType().name());
            bindConfig.set(path + ".uses", currentUses);
            bindConfig.set(path + ".max_uses", maxUses);
            saveBindConfig();
        }

        plugin.sendMessage(player, "messages.bind-success");
    }

    // 更新绑定方块的材质和使用次数
    public void updateBlockMaterial(ItemStack item) {
        if (!isBlockBound(item)) return;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String blockId = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "block_id"),
            PersistentDataType.STRING
        );
        if (blockId == null) return;

        UUID boundPlayer = getBoundPlayer(item);
        if (boundPlayer == null) return;

        // 获取当前使用次数和最大使用次数
        int currentUses = plugin.getBlockManager().getUseTimes(item);
        int maxUses = plugin.getBlockManager().getMaxUseTimes(item);

        if (databaseManager != null && databaseManager.isEnabled()) {
            // 使用数据库更新
            databaseManager.updateBinding(
                boundPlayer,
                blockId,
                item.getType().name(),
                currentUses,
                maxUses
            );
        } else {
            // 使用文件更新
            String path = "bindings." + boundPlayer.toString() + "." + blockId;
            if (bindConfig.contains(path)) {
                // 更新材质
                bindConfig.set(path + ".material", item.getType().name());
                // 同步当前使用次数
                bindConfig.set(path + ".uses", currentUses);
                // 同步最大使用次数
                bindConfig.set(path + ".max_uses", maxUses);
                saveBindConfig();
            }
        }
    }

    public boolean isBlockBound(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        return meta.getPersistentDataContainer().has(bindKey, PersistentDataType.STRING);
    }

    public UUID getBoundPlayer(ItemStack item) {
        if (!isBlockBound(item)) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        String uuid = meta.getPersistentDataContainer().get(bindKey, PersistentDataType.STRING);
        return uuid != null ? UUID.fromString(uuid) : null;
    }

    public void openBindList(Player player) {
        UUID playerUUID = player.getUniqueId();
        String uuid = playerUUID.toString();
        Map<String, Map<String, Object>> bindings;

        // 清理使用次数为0的方块
        if (databaseManager != null && databaseManager.isEnabled()) {
            databaseManager.cleanupZeroUsageBlocks(playerUUID);
            bindings = databaseManager.getPlayerBindings(playerUUID);

            if (bindings.isEmpty()) {
                plugin.sendMessage(player, "messages.no-bound-blocks");
                return;
            }
        } else {
            if (!bindConfig.contains("bindings." + uuid)) {
                plugin.sendMessage(player, "messages.no-bound-blocks");
                return;
            }

            // 清理使用次数为0的方块
            Set<String> blocks = Objects.requireNonNull(bindConfig.getConfigurationSection("bindings." + uuid)).getKeys(false);
            for (String blockId : new ArrayList<>(blocks)) {
                removeZeroUsageBlocks(uuid, blockId);
            }

            // 重新检查是否还有绑定的方块
            if (!bindConfig.contains("bindings." + uuid) ||
                bindConfig.getConfigurationSection("bindings." + uuid).getKeys(false).isEmpty()) {
                plugin.sendMessage(player, "messages.no-bound-blocks");
                return;
            }

            // 从文件中构建绑定数据
            bindings = new HashMap<>();
            blocks = Objects.requireNonNull(bindConfig.getConfigurationSection("bindings." + uuid)).getKeys(false);
            for (String blockId : blocks) {
                String path = "bindings." + uuid + "." + blockId;
                if (bindConfig.getBoolean(path + ".hidden", false)) {
                    continue;
                }

                Map<String, Object> blockData = new HashMap<>();
                blockData.put("material", bindConfig.getString(path + ".material"));
                blockData.put("uses", bindConfig.getInt(path + ".uses"));
                blockData.put("max_uses", bindConfig.getInt(path + ".max_uses"));
                blockData.put("hidden", bindConfig.getBoolean(path + ".hidden", false));

                bindings.put(blockId, blockData);
            }
        }

        String guiTitle = ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("gui.text.bound-blocks-title", "&8⚡ &b已绑定方块"));
        Inventory gui = Bukkit.createInventory(null, 54, guiTitle);

        int slot = 0;
        for (Map.Entry<String, Map<String, Object>> entry : bindings.entrySet()) {
            if (slot >= 54) break;

            String blockId = entry.getKey();
            Map<String, Object> blockData = entry.getValue();

            // 跳过被隐藏的方块
            if ((boolean) blockData.getOrDefault("hidden", false)) {
                continue;
            }

            Material material = Material.valueOf((String) blockData.get("material"));

            // 尝试从玩家背包中找到对应的方块以获取实际使用次数
            int uses = (int) blockData.get("uses");
            int maxUses = (int) blockData.get("max_uses");

            // 查找玩家背包中的对应方块
            for (ItemStack item : player.getInventory().getContents()) {
                if (item != null && plugin.getBlockManager().isMagicBlock(item) && isBlockBound(item)) {
                    ItemMeta meta = item.getItemMeta();
                    if (meta != null) {
                        String itemBlockId = meta.getPersistentDataContainer().get(
                            new NamespacedKey(plugin, "block_id"),
                            PersistentDataType.STRING
                        );
                        if (blockId.equals(itemBlockId)) {
                            // 使用实际的使用次数
                            uses = plugin.getBlockManager().getUseTimes(item);
                            maxUses = plugin.getBlockManager().getMaxUseTimes(item);

                            // 更新数据
                            if (databaseManager != null && databaseManager.isEnabled()) {
                                databaseManager.updateBinding(playerUUID, blockId, material.name(), uses, maxUses);
                            } else {
                                String path = "bindings." + uuid + "." + blockId;
                                bindConfig.set(path + ".uses", uses);
                                bindConfig.set(path + ".max_uses", maxUses);
                                saveBindConfig();
                            }
                            break;
                        }
                    }
                }
            }

            // 如果使用次数为0，跳过这个方块
            if (uses <= 0) {
                if (databaseManager != null && databaseManager.isEnabled()) {
                    databaseManager.deleteBinding(playerUUID, blockId);
                } else {
                    removeZeroUsageBlocks(uuid, blockId);
                }
                continue;
            }

            ItemStack displayItem = new ItemStack(material, 1);
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + plugin.getMinecraftLangManager().getItemStackName(displayItem));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.getMagicLore());
                lore.add(getBindLorePrefix() + player.getName());
                lore.add("");
                String remainingUsesText = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("gui.text.remaining-uses", "剩余使用次数: "));
                lore.add(ChatColor.GRAY + remainingUsesText + ChatColor.YELLOW + uses + ChatColor.GRAY + "/" + ChatColor.YELLOW + maxUses);
                lore.add("");
                // 使用配置文件中的提示文本
                lore.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("gui.text.retrieve-block", "&a▸ &7左键点击取回此方块")));
                lore.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("gui.text.remove-block", "&c▸ &7右键点击从列表中隐藏")));
                lore.add(ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("gui.text.remove-block-note", "&8• &7(仅从列表隐藏，绑定关系保持)")));
                meta.setLore(lore);
                meta.getPersistentDataContainer().set(bindKey, PersistentDataType.STRING, uuid);
                meta.getPersistentDataContainer().set(
                    new NamespacedKey(plugin, "block_id"),
                    PersistentDataType.STRING,
                    blockId
                );
                displayItem.setItemMeta(meta);
            }
            gui.setItem(slot++, displayItem);
        }

        player.openInventory(gui);
    }

    public void retrieveBlock(Player player, ItemStack displayItem) {
        if (!isBlockBound(displayItem)) return;

        UUID boundUUID = getBoundPlayer(displayItem);
        if (boundUUID == null || !boundUUID.equals(player.getUniqueId())) {
            plugin.sendMessage(player, "messages.not-bound-to-you");
            return;
        }

        // 获取方块ID
        ItemMeta displayMeta = displayItem.getItemMeta();
        if (displayMeta == null) return;

        String blockId = displayMeta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "block_id"),
            PersistentDataType.STRING
        );
        if (blockId == null) return;

        Material blockType = displayItem.getType();
        int uses;
        int maxUses;

        // 从数据库或配置文件获取使用次数信息
        if (databaseManager != null && databaseManager.isEnabled()) {
            Map<String, Object> blockData = databaseManager.getBlockBinding(blockId);
            if (blockData == null) return;

            uses = (int) blockData.get("uses");
            maxUses = (int) blockData.get("max_uses");
        } else {
            String uuid = player.getUniqueId().toString();
            String path = "bindings." + uuid + "." + blockId;
            if (!bindConfig.contains(path)) return;

            uses = bindConfig.getInt(path + ".uses");
            maxUses = bindConfig.getInt(path + ".max_uses", uses);
        }

        // 清理所有相同的绑定方块
        // 1. 清理在线玩家背包中的方块
        for (Player onlinePlayer : Bukkit.getOnlinePlayers()) {
            ItemStack[] contents = onlinePlayer.getInventory().getContents();
            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];
                if (isSameBoundBlock(item, player.getUniqueId(), blockType)) {
                    onlinePlayer.getInventory().setItem(i, null);
                    if (!onlinePlayer.equals(player)) {
                        plugin.sendMessage(onlinePlayer, "messages.block-removed-by-owner");
                    }
                }
            }
        }

        // 2. 清理掉落在地上的方块
        player.getWorld().getEntities().stream()
            .filter(entity -> entity instanceof org.bukkit.entity.Item)
            .map(entity -> (org.bukkit.entity.Item) entity)
            .forEach(item -> {
                ItemStack itemStack = item.getItemStack();
                if (isSameBoundBlock(itemStack, player.getUniqueId(), blockType)) {
                    item.remove();
                }
            });

        // 3. 清理容器中的方块（箱子等）
        for (Chunk chunk : player.getWorld().getLoadedChunks()) {
            for (BlockState blockState : chunk.getTileEntities()) {
                if (blockState instanceof Container) {
                    Container container = (Container) blockState;
                    ItemStack[] containerContents = container.getInventory().getContents();
                    boolean updated = false;

                    for (int i = 0; i < containerContents.length; i++) {
                        ItemStack item = containerContents[i];
                        if (isSameBoundBlock(item, player.getUniqueId(), blockType)) {
                            container.getInventory().setItem(i, null);
                            updated = true;
                        }
                    }

                    if (updated) {
                        container.update();
                    }
                }
            }
        }

        // 创建新的方块并给予玩家
        ItemStack newBlock = plugin.createMagicBlock();
        newBlock.setType(blockType);
        ItemMeta meta = newBlock.getItemMeta();
        if (meta != null) {
            // 设置绑定数据
            meta.getPersistentDataContainer().set(bindKey, PersistentDataType.STRING, player.getUniqueId().toString());
            meta.getPersistentDataContainer().set(
                new NamespacedKey(plugin, "block_id"),
                PersistentDataType.STRING,
                blockId
            );

            // 设置lore
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            // 找到magic-lore的位置
            int magicLoreIndex = -1;
            for (int i = 0; i < lore.size(); i++) {
                if (lore.get(i).equals(plugin.getMagicLore())) {
                    magicLoreIndex = i;
                    break;
                }
            }

            // 在magic-lore后面添加绑定信息
            if (magicLoreIndex != -1) {
                // 找到装饰性lore的结束位置
                int insertIndex = magicLoreIndex + 1;
                if (plugin.getConfig().getBoolean("display.decorative-lore.enabled", true)) {
                    while (insertIndex < lore.size() && !lore.get(insertIndex).contains(plugin.getUsageLorePrefix())) {
                        insertIndex++;
                    }
                }
                lore.add(insertIndex, getBindLorePrefix() + player.getName());
            } else {
                lore.add(getBindLorePrefix() + player.getName());
            }

            meta.setLore(lore);
            newBlock.setItemMeta(meta);
        }

        // 设置使用次数和最大使用次数
        plugin.getBlockManager().setMaxUseTimes(newBlock, maxUses);
        plugin.getBlockManager().setUseTimes(newBlock, uses);
        // 更新lore以显示使用次数和进度条
        plugin.getBlockManager().updateLore(newBlock, uses);

        // 给予玩家新的方块
        HashMap<Integer, ItemStack> leftover = player.getInventory().addItem(newBlock);
        if (!leftover.isEmpty()) {
            // 如果背包满了，掉落在玩家位置
            player.getWorld().dropItemNaturally(player.getLocation(), newBlock);
        }

        plugin.sendMessage(player, "messages.block-retrieved");
    }

    private boolean isSameBoundBlock(ItemStack item, UUID playerUUID, Material type) {
        if (item == null || !plugin.getBlockManager().isMagicBlock(item) || !isBlockBound(item)) {
            return false;
        }

        UUID boundPlayer = getBoundPlayer(item);
        return boundPlayer != null &&
               boundPlayer.equals(playerUUID) &&
               item.getType() == type;
    }

    public void removeBindings(Player player) {
        String uuid = player.getUniqueId().toString();
        bindConfig.set("bindings." + uuid, null);
        saveBindConfig();
    }

    public void cleanupBindings(ItemStack item) {
        if (!isBlockBound(item)) return;

        UUID boundUUID = getBoundPlayer(item);
        if (boundUUID == null) return;

        String uuid = boundUUID.toString();
        if (!bindConfig.contains("bindings." + uuid)) return;

        // 遍历所有绑定的方块
        Set<String> blocks = Objects.requireNonNull(bindConfig.getConfigurationSection("bindings." + uuid)).getKeys(false);
        for (String blockId : blocks) {
            String path = "bindings." + uuid + "." + blockId;
            String material = bindConfig.getString(path + ".material");
            if (material != null && material.equals(item.getType().name())) {
                bindConfig.set(path, null);
            }
        }

        // 如果该玩家没有绑定的方块了，删除整个节点
        if (bindConfig.getConfigurationSection("bindings." + uuid).getKeys(false).isEmpty()) {
            bindConfig.set("bindings." + uuid, null);
        }

        saveBindConfig();
    }

    // 检查并移除使用次数为0的方块
    private void removeZeroUsageBlocks(String uuid, String blockId) {
        String path = "bindings." + uuid + "." + blockId;
        int uses = bindConfig.getInt(path + ".uses", 0);

        if (uses <= 0) {
            bindConfig.set(path, null);
            // 如果该玩家没有绑定的方块了，删除整个节点
            if (bindConfig.getConfigurationSection("bindings." + uuid).getKeys(false).isEmpty()) {
                bindConfig.set("bindings." + uuid, null);
            }
            saveBindConfig();
        }
    }

    // 处理绑定列表中的点击事件
    public void handleBindListClick(Player player, ItemStack clickedItem) {
        if (!isBlockBound(clickedItem)) return;

        ItemMeta meta = clickedItem.getItemMeta();
        if (meta == null) return;

        String blockId = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "block_id"),
            PersistentDataType.STRING
        );
        if (blockId == null) return;

        // 获取玩家的点击记录
        Map<String, Long> playerClicks = lastClickTimes.computeIfAbsent(
            player.getUniqueId(),
            k -> new HashMap<>()
        );

        long currentTime = System.currentTimeMillis();
        Long lastClickTime = playerClicks.get(blockId);

        if (lastClickTime != null && currentTime - lastClickTime < DOUBLE_CLICK_TIME) {
            // 双击确认，隐藏方块
            hideBlockFromList(player, blockId);

            // 刷新界面
            player.closeInventory();
            openBindList(player);

            // 发送确认消息
            plugin.sendMessage(player, "messages.block-bind-removed");

            // 清除点击记录
            playerClicks.remove(blockId);
        } else {
            // 第一次点击，记录时间
            playerClicks.put(blockId, currentTime);
            // 发送提示消息
            plugin.sendMessage(player, "messages.click-again-to-remove");
        }
    }

    private void hideBlockFromList(Player player, String blockId) {
        if (databaseManager != null && databaseManager.isEnabled()) {
            databaseManager.setBlockHidden(player.getUniqueId(), blockId, true);
        } else {
            String uuid = player.getUniqueId().toString();
            String path = "bindings." + uuid + "." + blockId + ".hidden";
            bindConfig.set(path, true);
            saveBindConfig();
        }
    }

    public void handleDepleted(ItemStack item) {
        if (!isBlockBound(item)) return;

        UUID boundPlayer = getBoundPlayer(item);
        if (boundPlayer == null) return;

        // 获取方块ID
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        String blockId = meta.getPersistentDataContainer().get(
            new NamespacedKey(plugin, "block_id"),
            PersistentDataType.STRING
        );
        if (blockId == null) return;

        // 如果配置为移除耗尽的方块
        if (plugin.getConfig().getBoolean("remove-depleted-blocks", false)) {
            if (databaseManager != null && databaseManager.isEnabled()) {
                // 从数据库中移除
                databaseManager.deleteBinding(boundPlayer, blockId);
            } else {
                // 从文件中移除
                String uuid = boundPlayer.toString();
                if (!bindConfig.contains("bindings." + uuid)) return;

                String path = "bindings." + uuid + "." + blockId;
                bindConfig.set(path, null);
                saveBindConfig();
            }
        }
    }
}
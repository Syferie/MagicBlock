package io.github.syferie.magicblock.block;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

public class BlockBindManager {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey bindKey;
    private final File bindFile;
    private FileConfiguration bindConfig;
    private final Map<UUID, Map<String, Long>> lastClickTimes = new HashMap<>();
    private static final long DOUBLE_CLICK_TIME = 500; // 双击时间窗口（毫秒）

    public BlockBindManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.bindKey = new NamespacedKey(plugin, "magicblock_bind");
        this.bindFile = new File(plugin.getDataFolder(), "bindings.yml");
        loadBindConfig();
    }

    private void loadBindConfig() {
        if (!bindFile.exists()) {
            try {
                bindFile.createNewFile();
            } catch (IOException e) {
                plugin.getLogger().warning("无法创建绑定配置文件: " + e.getMessage());
            }
        }
        bindConfig = YamlConfiguration.loadConfiguration(bindFile);
    }

    public FileConfiguration getBindConfig() {
        return bindConfig;
    }

    public void saveBindConfig() {
        try {
            bindConfig.save(bindFile);
        } catch (IOException e) {
            plugin.getLogger().warning("无法保存绑定配置: " + e.getMessage());
        }
    }

    private String getBindLorePrefix() {
        return ChatColor.translateAlternateColorCodes('&', 
            "&7" + plugin.getMessage("messages.bound-to") + " &b");
    }

    public void bindBlock(Player player, ItemStack item) {
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
        lore.add(getBindLorePrefix() + player.getName());
        meta.setLore(lore);
        item.setItemMeta(meta);

        // 获取当前使用次数和最大使用次数
        int currentUses = plugin.getBlockManager().getUseTimes(item);
        int maxUses = plugin.getBlockManager().getMaxUseTimes(item);

        // 保存到配置文件
        String itemId = UUID.randomUUID().toString();
        String path = "bindings." + uuid + "." + itemId;
        bindConfig.set(path + ".material", item.getType().name());
        bindConfig.set(path + ".uses", currentUses);
        bindConfig.set(path + ".max_uses", maxUses);
        // 存储方块ID到物品中
        meta.getPersistentDataContainer().set(
            new NamespacedKey(plugin, "block_id"),
            PersistentDataType.STRING,
            itemId
        );
        item.setItemMeta(meta);
        saveBindConfig();

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

        String path = "bindings." + boundPlayer.toString() + "." + blockId;
        if (bindConfig.contains(path)) {
            // 更新材质
            bindConfig.set(path + ".material", item.getType().name());
            // 同步当前使用次数
            int currentUses = plugin.getBlockManager().getUseTimes(item);
            bindConfig.set(path + ".uses", currentUses);
            // 同步最大使用次数
            int maxUses = plugin.getBlockManager().getMaxUseTimes(item);
            bindConfig.set(path + ".max_uses", maxUses);
            saveBindConfig();
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
        String uuid = player.getUniqueId().toString();
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

        Inventory gui = Bukkit.createInventory(null, 54, plugin.getMessage("gui.bound-blocks-title"));
        blocks = Objects.requireNonNull(bindConfig.getConfigurationSection("bindings." + uuid)).getKeys(false);

        int slot = 0;
        for (String blockId : blocks) {
            if (slot >= 54) break;
            
            String path = "bindings." + uuid + "." + blockId;
            
            // 跳过被隐藏的方块
            if (bindConfig.getBoolean(path + ".hidden", false)) {
                continue;
            }

            Material material = Material.valueOf(bindConfig.getString(path + ".material"));
            
            // 尝试从玩家背包中找到对应的方块以获取实际使用次数
            int uses = bindConfig.getInt(path + ".uses");
            int maxUses = bindConfig.getInt(path + ".max_uses", uses);
            
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
                            // 更新配置中的使用次数
                            bindConfig.set(path + ".uses", uses);
                            bindConfig.set(path + ".max_uses", maxUses);
                            saveBindConfig();
                            break;
                        }
                    }
                }
            }

            // 如果使用次数为0，跳过这个方块
            if (uses <= 0) {
                removeZeroUsageBlocks(uuid, blockId);
                continue;
            }

            ItemStack displayItem = new ItemStack(material, 1);
            ItemMeta meta = displayItem.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(ChatColor.AQUA + plugin.getLanguageManager().getMessage("blocks." + material.name()));
                List<String> lore = new ArrayList<>();
                lore.add(plugin.getMagicLore());
                lore.add(getBindLorePrefix() + player.getName());
                lore.add("");
                lore.add(ChatColor.GRAY + plugin.getMessage("gui.remaining-uses") + ChatColor.YELLOW + uses + ChatColor.GRAY + "/" + ChatColor.YELLOW + maxUses);
                lore.add("");
                // 使用语言文件中的提示文本
                lore.add(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("gui.retrieve-block")));
                lore.add(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("gui.remove-block")));
                lore.add(ChatColor.translateAlternateColorCodes('&', plugin.getMessage("gui.remove-block-note")));
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

        // 从配置文件获取使用次数信息
        String uuid = player.getUniqueId().toString();
        String path = "bindings." + uuid + "." + blockId;
        if (!bindConfig.contains(path)) return;

        Material blockType = displayItem.getType();
        int uses = bindConfig.getInt(path + ".uses");
        int maxUses = bindConfig.getInt(path + ".max_uses", uses);

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
            meta.getPersistentDataContainer().set(bindKey, PersistentDataType.STRING, player.getUniqueId().toString());
            List<String> lore = meta.hasLore() ? meta.getLore() : new ArrayList<>();
            lore.add(getBindLorePrefix() + player.getName());
            lore.add(ChatColor.GRAY + plugin.getMessage("gui.remaining-uses") + ChatColor.YELLOW + uses + ChatColor.GRAY + "/" + ChatColor.YELLOW + maxUses);
            meta.setLore(lore);
            newBlock.setItemMeta(meta);
        }

        // 设置使用次数和最大使用次数
        plugin.getBlockManager().setMaxUseTimes(newBlock, maxUses);
        plugin.getBlockManager().setUseTimes(newBlock, uses);
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
        String uuid = player.getUniqueId().toString();
        String path = "bindings." + uuid + "." + blockId + ".hidden";
        bindConfig.set(path, true);
        saveBindConfig();
    }
} 
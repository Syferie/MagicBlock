package io.github.syferie.magicblock.listener;

import com.tcoded.folialib.FoliaLib;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.gui.GUIManager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryType;
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;


import java.util.*;

public class BlockListener implements Listener {
    private final MagicBlockPlugin plugin;
    private final GUIManager guiManager;
    private final List<Material> buildingMaterials;

    private static final long GUI_OPEN_COOLDOWN = 300;
    private final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();
    private final FoliaLib foliaLib;

    // 性能优化：位置缓存
    private final Map<String, Set<String>> chunkLocationCache = new HashMap<>();
    private final Map<String, Long> chunkCacheTime = new HashMap<>();

    // 性能优化：红石组件缓存
    private static final Set<Material> REDSTONE_COMPONENTS = new HashSet<>();

    static {
        // 预填充红石组件集合，避免每次字符串比较
        REDSTONE_COMPONENTS.add(Material.LEVER);
        REDSTONE_COMPONENTS.add(Material.REDSTONE_WIRE);
        REDSTONE_COMPONENTS.add(Material.REPEATER);
        REDSTONE_COMPONENTS.add(Material.COMPARATOR);
        REDSTONE_COMPONENTS.add(Material.REDSTONE_TORCH);
        REDSTONE_COMPONENTS.add(Material.REDSTONE_WALL_TORCH);
        REDSTONE_COMPONENTS.add(Material.POWERED_RAIL);
        REDSTONE_COMPONENTS.add(Material.DETECTOR_RAIL);
        REDSTONE_COMPONENTS.add(Material.ACTIVATOR_RAIL);
        REDSTONE_COMPONENTS.add(Material.REDSTONE_LAMP);
        REDSTONE_COMPONENTS.add(Material.DISPENSER);
        REDSTONE_COMPONENTS.add(Material.DROPPER);
        REDSTONE_COMPONENTS.add(Material.HOPPER);
        REDSTONE_COMPONENTS.add(Material.OBSERVER);
        REDSTONE_COMPONENTS.add(Material.PISTON);
        REDSTONE_COMPONENTS.add(Material.STICKY_PISTON);
        REDSTONE_COMPONENTS.add(Material.DAYLIGHT_DETECTOR);
        REDSTONE_COMPONENTS.add(Material.TARGET);
        REDSTONE_COMPONENTS.add(Material.TRIPWIRE);
        REDSTONE_COMPONENTS.add(Material.TRIPWIRE_HOOK);
        REDSTONE_COMPONENTS.add(Material.NOTE_BLOCK);
        REDSTONE_COMPONENTS.add(Material.BELL);

        // 添加压力板
        for (Material material : Material.values()) {
            String name = material.name();
            if (name.endsWith("_PRESSURE_PLATE") ||
                name.endsWith("_BUTTON") ||
                name.contains("DOOR") ||
                name.contains("TRAPDOOR") ||
                name.contains("GATE")) {
                REDSTONE_COMPONENTS.add(material);
            }
        }
    }

    public BlockListener(MagicBlockPlugin plugin, List<Material> allowedMaterials) {
        this.plugin = plugin;
        this.guiManager = new GUIManager(plugin, allowedMaterials);
        this.buildingMaterials = new ArrayList<>(allowedMaterials);

        this.foliaLib = plugin.getFoliaLib();
        // 移除重复的GUIManager注册，将在MagicBlockPlugin中统一注册

        // 启动缓存清理任务
        startCacheCleanupTask();
    }

    private void startCacheCleanupTask() {
        // 从配置读取清理间隔
        long cleanupInterval = plugin.getConfig().getLong("performance.location-cache.cleanup-interval", 30);
        long cleanupTicks = cleanupInterval * 20L; // 转换为 ticks

        foliaLib.getScheduler().runTimer(() -> {
            cleanExpiredCache();
        }, cleanupTicks, cleanupTicks);
    }

    private void cleanExpiredCache() {
        // 检查是否启用缓存
        boolean cacheEnabled = plugin.getConfig().getBoolean("performance.location-cache.enabled", true);
        if (!cacheEnabled) {
            // 如果缓存被禁用，清空所有缓存
            chunkLocationCache.clear();
            chunkCacheTime.clear();
            return;
        }

        long cacheDuration = plugin.getConfig().getLong("performance.location-cache.duration", 5000);
        long currentTime = System.currentTimeMillis();

        chunkCacheTime.entrySet().removeIf(entry -> {
            if (currentTime - entry.getValue() > cacheDuration) {
                chunkLocationCache.remove(entry.getKey());
                return true;
            }
            return false;
        });
    }

    public void setAllowedMaterials(List<Material> materials) {
        this.buildingMaterials.clear();
        this.buildingMaterials.addAll(materials);
    }

    public GUIManager getGuiManager() {
        return guiManager;
    }

    /**
     * 重载GUI配置
     */
    public void reloadGUIConfig() {
        if (guiManager != null && guiManager.getBlockSelectionGUI() != null) {
            guiManager.getBlockSelectionGUI().reloadConfig();
        }
    }



    private boolean isConnectableBlock(Material material) {
        return material.toString().contains("WALL") ||
               material.toString().contains("FENCE") ||
               material.toString().contains("PANE") ||
               material.toString().contains("CHAIN") ||
               material == Material.IRON_BARS;
    }

    private void updateConnectedBlocks(Block block) {
        // 获取所有相邻方块
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        List<Block> adjacentBlocks = new ArrayList<>();
        Material blockType = block.getType();

        // 收集所有需要更新的方块
        for (BlockFace face : faces) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType() == blockType) {
                adjacentBlocks.add(adjacent);
            }
        }

        // 更新当前方块和所有相邻方块的状态
        if (!adjacentBlocks.isEmpty()) {
            // 创建一个新的BlockData来应用连接状态
            org.bukkit.block.data.BlockData blockData = block.getBlockData();
            if (blockData instanceof org.bukkit.block.data.type.Wall) {
                updateWallConnections(block, adjacentBlocks);
            } else if (blockData instanceof org.bukkit.block.data.type.Fence) {
                updateFenceConnections(block, adjacentBlocks);
            } else if (blockData instanceof org.bukkit.block.data.type.GlassPane) {
                updatePaneConnections(block, adjacentBlocks);
            } else {
                // 对于其他类型的连接型方块
                block.getState().update(true, true);
                for (Block adjacent : adjacentBlocks) {
                    adjacent.getState().update(true, true);
                }
            }
        }
    }

    private void updateWallConnections(Block wall, List<Block> adjacentBlocks) {
        org.bukkit.block.data.type.Wall wallData = (org.bukkit.block.data.type.Wall) wall.getBlockData();

        // 更新当前墙的连接状态
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                face == BlockFace.EAST || face == BlockFace.WEST) {
                Block adjacent = wall.getRelative(face);
                if (adjacent.getType() == wall.getType()) {
                    wallData.setHeight(face, org.bukkit.block.data.type.Wall.Height.LOW);
                } else {
                    wallData.setHeight(face, org.bukkit.block.data.type.Wall.Height.NONE);
                }
            }
        }
        wall.setBlockData(wallData, true);

        // 更新相邻墙的连接状态
        for (Block adjacent : adjacentBlocks) {
            org.bukkit.block.data.type.Wall adjacentWallData = (org.bukkit.block.data.type.Wall) adjacent.getBlockData();
            for (BlockFace face : BlockFace.values()) {
                if (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                    face == BlockFace.EAST || face == BlockFace.WEST) {
                    Block relative = adjacent.getRelative(face);
                    if (relative.getType() == adjacent.getType()) {
                        adjacentWallData.setHeight(face, org.bukkit.block.data.type.Wall.Height.LOW);
                    } else {
                        adjacentWallData.setHeight(face, org.bukkit.block.data.type.Wall.Height.NONE);
                    }
                }
            }
            adjacent.setBlockData(adjacentWallData, true);
        }
    }

    private void updateFenceConnections(Block fence, List<Block> adjacentBlocks) {
        org.bukkit.block.data.type.Fence fenceData = (org.bukkit.block.data.type.Fence) fence.getBlockData();

        // 更新当前栅栏的连接状态
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                face == BlockFace.EAST || face == BlockFace.WEST) {
                Block adjacent = fence.getRelative(face);
                fenceData.setFace(face, adjacent.getType() == fence.getType() ||
                                     adjacent.getType().toString().contains("FENCE_GATE"));
            }
        }
        fence.setBlockData(fenceData, true);

        // 更新相邻栅栏的连接状态
        for (Block adjacent : adjacentBlocks) {
            if (adjacent.getBlockData() instanceof org.bukkit.block.data.type.Fence) {
                org.bukkit.block.data.type.Fence adjacentFenceData = (org.bukkit.block.data.type.Fence) adjacent.getBlockData();
                for (BlockFace face : BlockFace.values()) {
                    if (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                        face == BlockFace.EAST || face == BlockFace.WEST) {
                        Block relative = adjacent.getRelative(face);
                        adjacentFenceData.setFace(face, relative.getType() == adjacent.getType() ||
                                                     relative.getType().toString().contains("FENCE_GATE"));
                    }
                }
                adjacent.setBlockData(adjacentFenceData, true);
            }
        }
    }

    private void updatePaneConnections(Block pane, List<Block> adjacentBlocks) {
        org.bukkit.block.data.type.GlassPane paneData = (org.bukkit.block.data.type.GlassPane) pane.getBlockData();

        // 更新当前玻璃板的连接状态
        for (BlockFace face : BlockFace.values()) {
            if (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                face == BlockFace.EAST || face == BlockFace.WEST) {
                Block adjacent = pane.getRelative(face);
                paneData.setFace(face, adjacent.getType() == pane.getType());
            }
        }
        pane.setBlockData(paneData, true);

        // 更新相邻玻璃板的连接状态
        for (Block adjacent : adjacentBlocks) {
            if (adjacent.getBlockData() instanceof org.bukkit.block.data.type.GlassPane) {
                org.bukkit.block.data.type.GlassPane adjacentPaneData = (org.bukkit.block.data.type.GlassPane) adjacent.getBlockData();
                for (BlockFace face : BlockFace.values()) {
                    if (face == BlockFace.NORTH || face == BlockFace.SOUTH ||
                        face == BlockFace.EAST || face == BlockFace.WEST) {
                        Block relative = adjacent.getRelative(face);
                        adjacentPaneData.setFace(face, relative.getType() == adjacent.getType());
                    }
                }
                adjacent.setBlockData(adjacentPaneData, true);
            }
        }
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        // 如果是BlockMultiPlaceEvent，让专门的处理器处理
        if (event instanceof org.bukkit.event.block.BlockMultiPlaceEvent) {
            return;
        }

        ItemStack item = event.getItemInHand();
        if (plugin.hasMagicLore(item.getItemMeta())) {
            handleMagicBlockPlace(event, item);
        }
    }

    @EventHandler
    public void onBlockMultiPlace(org.bukkit.event.block.BlockMultiPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.hasMagicLore(item.getItemMeta())) {
            handleMagicBlockPlace(event, item);
        }
    }

    private void handleMagicBlockPlace(BlockPlaceEvent event, ItemStack item) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

        // 检查使用权限
        if (!player.hasPermission("magicblock.use")) {
            event.setCancelled(true);
            plugin.sendMessage(player, "messages.no-permission-use");
            return;
        }

        // 检查使用次数
        int useTimes = plugin.getBlockManager().getUseTimes(item);
        if (useTimes <= 0) {
            event.setCancelled(true);
            // 直接发送消息，不使用参数
            String message = plugin.getLanguageManager().getMessage("messages.block-removed");
            String prefix = plugin.getLanguageManager().getMessage("general.prefix");
            player.sendMessage(prefix + message);

            // 处理耗尽的方块
            plugin.getBlockBindManager().handleDepleted(item);

            // 如果配置为移除耗尽的方块
            if (plugin.getConfig().getBoolean("remove-depleted-blocks", false)) {
                // 从玩家手中移除物品
                player.getInventory().setItemInMainHand(null);
            }
            return;
        }

        // 检查绑定系统是否启用
        boolean bindingEnabled = plugin.getConfig().getBoolean("enable-binding-system", true);

        // 检查是否已绑定
        UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(item);
        if (bindingEnabled && boundPlayer == null) {
            // 第一次使用时自动绑定
            plugin.getBlockBindManager().bindBlock(player, item);
        } else if (boundPlayer != null && !boundPlayer.equals(player.getUniqueId())) {
            // 检查是否允许使用已绑定的方块
            if (!plugin.getConfig().getBoolean("allow-use-bound-blocks", false)) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.not-bound-to-you");
                return;
            }
        }

        // 🚀 性能优化：使用新的索引系统注册魔法方块
        plugin.getIndexManager().registerMagicBlock(block.getLocation(), item);



        // 性能优化：只在必要时更新连接状态
        if (isConnectableBlock(item.getType()) && hasAdjacentConnectableBlocks(block)) {
            // 使用FoliaLib延迟1tick更新连接状态，确保方块已完全放置
            foliaLib.getScheduler().runLater(() -> {
                updateConnectedBlocks(block);
            }, 1L);
        }

        // 减少使用次数
        if (useTimes > 0) { // -1表示无限使用
            plugin.getBlockManager().decrementUseTimes(item);
        }

        // 记录使用统计
        plugin.incrementPlayerUsage(player.getUniqueId());
        plugin.logUsage(player, item);
    }









    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (GUIManager.isPlayerSearching(player)) {
            event.setCancelled(true);
            String input = event.getMessage();

            if (input.equalsIgnoreCase("cancel")) {
                GUIManager.setPlayerSearching(player, false);
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    guiManager.getBlockSelectionGUI().openInventory(player);
                });
                return;
            }

            plugin.getServer().getScheduler().runTask(plugin, () -> {
                guiManager.getBlockSelectionGUI().handleSearch(player, input);
                GUIManager.setPlayerSearching(player, false);
            });
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockBreak(BlockBreakEvent event) {
        Block eventBlock = event.getBlock();
        Location blockLocation = eventBlock.getLocation();

        // 🚀 性能优化：使用新的索引系统进行 O(1) 查找
        boolean isMagicBlock = plugin.getIndexManager().isMagicBlock(blockLocation);
        Block targetBlock = eventBlock;

        // 检查是否是连接型方块
        if (isConnectableBlock(eventBlock.getType())) {
            // 保存相邻方块的引用，以便稍后更新
            final List<Block> adjacentBlocks = new ArrayList<>();
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block adjacent = eventBlock.getRelative(face);
                if (isConnectableBlock(adjacent.getType())) {
                    adjacentBlocks.add(adjacent);
                }
            }

            // 在方块被破坏后更新相邻方块的连接状态
            if (!adjacentBlocks.isEmpty()) {
                foliaLib.getScheduler().runLater(() -> {
                    for (Block adjacent : adjacentBlocks) {
                        updateAdjacentBlockConnections(adjacent);
                    }
                }, 1L);
            }
        }







        if (isMagicBlock) {
            Player player = event.getPlayer();

            // 检查破坏权限（新的独立权限）
            if (!player.hasPermission("magicblock.break")) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.no-permission-break");
                return;
            }
            
            ItemStack blockItem = new ItemStack(targetBlock.getType());

            // 检查绑定系统是否启用
            boolean bindingEnabled = plugin.getConfig().getBoolean("enable-binding-system", true);

            // 检查绑定状态
            if (bindingEnabled && plugin.getBlockBindManager().isBlockBound(blockItem)) {
                UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(blockItem);
                if (boundPlayer != null && !boundPlayer.equals(player.getUniqueId())) {
                    event.setCancelled(true);
                    plugin.sendMessage(player, "messages.not-bound-to-you");
                    return;
                }
            }

            event.setDropItems(false);
            event.setExpToDrop(0);

            // 清理绑定数据
            plugin.getBlockBindManager().cleanupBindings(blockItem);
            // Schedule a task to remove the magic block location after 1 tick,
            // only if the block is actually air (not replaced by Residence or other plugins)
            final Location finalBlockLocation = blockLocation; // Effectively final for lambda

            plugin.getFoliaLib().getScheduler().runLater(() -> {
                Block blockAtLocation = finalBlockLocation.getBlock();
                if (blockAtLocation.getType() == Material.AIR) {
                    // 🚀 性能优化：使用新的索引系统移除魔法方块
                    plugin.getIndexManager().unregisterMagicBlock(finalBlockLocation);
                }
            }, 1L);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        // 记录物理事件
        plugin.getPerformanceMonitor().recordPhysicsEvent();

        Block block = event.getBlock();
        Location location = block.getLocation();
        Material type = block.getType();

        // 🚀 性能优化：多层过滤机制

        // 第一层：世界级别过滤
        if (!plugin.getIndexManager().worldHasMagicBlocks(location.getWorld().getName())) {
            plugin.getPerformanceMonitor().recordPhysicsEventSkipped();
            return; // 这个世界没有魔法方块，直接跳过
        }

        // 第二层：区块级别过滤
        if (!plugin.getIndexManager().chunkHasMagicBlocks(location)) {
            plugin.getPerformanceMonitor().recordPhysicsEventSkipped();
            return; // 这个区块没有魔法方块，直接跳过
        }

        // 第三层：方块类型过滤
        boolean physicsOptimizationEnabled = plugin.getConfig().getBoolean("performance.physics-optimization.enabled", true);
        boolean skipUnaffectedBlocks = plugin.getConfig().getBoolean("performance.physics-optimization.skip-unaffected-blocks", true);

        if (physicsOptimizationEnabled && skipUnaffectedBlocks) {
            if (!isPhysicsAffectedBlock(type)) {
                plugin.getPerformanceMonitor().recordPhysicsEventSkipped();
                return; // 不是受物理影响的方块类型，跳过
            }
        }

        // 第四层：精确位置检查（O(1) 查找）
        if (plugin.getIndexManager().isMagicBlock(location)) {
            // 允许红石组件的状态改变，但阻止它们被破坏
            if (isRedstoneComponent(type)) {
                // 如果是由于方块更新引起的状态改变，允许它
                if (event.getChangedType() == type) {
                    return;
                }

                // 允许红石组件的状态改变
                if (isRedstoneStateChangeAllowed(type)) {
                    return;
                }
            }
            // 取消其他物理事件
            event.setCancelled(true);
        }
    }

    // 性能优化：预检查是否为可能受物理影响的方块
    private boolean isPhysicsAffectedBlock(Material type) {
        // 只检查可能受物理影响的方块类型
        return type.hasGravity() ||
               isRedstoneComponent(type) ||
               type == Material.WATER ||
               type == Material.LAVA ||
               type.name().contains("DOOR") ||
               type.name().contains("TRAPDOOR") ||
               type.name().contains("GATE") ||
               type.name().contains("FENCE") ||
               type.name().contains("WALL") ||
               type.name().contains("PANE");
    }

    // 性能优化：预定义允许状态改变的红石组件
    private boolean isRedstoneStateChangeAllowed(Material type) {
        return type == Material.POWERED_RAIL ||
               type == Material.DETECTOR_RAIL ||
               type == Material.ACTIVATOR_RAIL ||
               type == Material.REDSTONE_LAMP ||
               type == Material.DISPENSER ||
               type == Material.DROPPER ||
               type == Material.HOPPER ||
               type == Material.PISTON ||
               type == Material.STICKY_PISTON ||
               type == Material.OBSERVER ||
               type == Material.NOTE_BLOCK ||
               type == Material.DAYLIGHT_DETECTOR ||
               type.name().contains("DOOR") ||
               type.name().contains("TRAPDOOR") ||
               type.name().contains("GATE");
    }



    private boolean isRedstoneComponent(Material material) {
        // 使用预填充的 HashSet 进行 O(1) 查找，避免字符串比较
        return REDSTONE_COMPONENTS.contains(material);
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
        Block clickedBlock = event.getClickedBlock();

        // 只处理右键交互
        if (clickedBlock != null && event.getAction() == Action.RIGHT_CLICK_BLOCK) {
            Location clickedLocation = clickedBlock.getLocation();

            // 🚀 性能优化：使用新的索引系统进行 O(1) 查找
            boolean isMagicBlock = plugin.getIndexManager().isMagicBlock(clickedLocation);
            Block targetBlock = clickedBlock;



            // 处理魔法方块的特殊交互
            if (isMagicBlock) {
                ItemStack itemInHand = player.getInventory().getItemInMainHand();

                // 检查是否是斧头削皮或去氧化操作
                if (itemInHand != null && isAxe(itemInHand.getType())) {
                    Material currentType = targetBlock.getType();
                    Material newType = getStrippedOrScrapedType(currentType);

                    if (newType != null && newType != currentType) {
                        // 这是一个会改变方块状态的操作，需要特殊处理
                        event.setCancelled(true);

                        // 检查使用权限
                        if (!player.hasPermission("magicblock.use")) {
                            plugin.sendMessage(player, "messages.no-permission-use");
                            return;
                        }

                        // 检查绑定状态
                        ItemStack blockItem = new ItemStack(currentType);
                        boolean bindingEnabled = plugin.getConfig().getBoolean("enable-binding-system", true);
                        if (bindingEnabled && plugin.getBlockBindManager().isBlockBound(blockItem)) {
                            UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(blockItem);
                            if (boundPlayer != null && !boundPlayer.equals(player.getUniqueId())) {
                                plugin.sendMessage(player, "messages.not-bound-to-you");
                                return;
                            }
                        }

                        // 执行方块状态改变
                        targetBlock.setType(newType);

                        // 更新魔法方块位置记录（保持原有的魔法方块状态）
                        // 不需要移除和重新添加，因为位置没有改变，只是方块类型改变了

                        // 播放相应的声音效果
                        if (isLogType(currentType)) {
                            player.getWorld().playSound(targetBlock.getLocation(), Sound.ITEM_AXE_STRIP, 1.0f, 1.0f);
                        } else if (isCopperType(currentType)) {
                            player.getWorld().playSound(targetBlock.getLocation(), Sound.ITEM_AXE_SCRAPE, 1.0f, 1.0f);
                        }

                        // 损耗斧头耐久度
                        if (itemInHand.getType().getMaxDurability() > 0) {
                            org.bukkit.inventory.meta.Damageable damageable = (org.bukkit.inventory.meta.Damageable) itemInHand.getItemMeta();
                            if (damageable != null) {
                                damageable.setDamage(damageable.getDamage() + 1);
                                itemInHand.setItemMeta(damageable);

                                // 检查是否损坏
                                if (damageable.getDamage() >= itemInHand.getType().getMaxDurability()) {
                                    player.getInventory().setItemInMainHand(null);
                                    player.getWorld().playSound(player.getLocation(), Sound.ENTITY_ITEM_BREAK, 1.0f, 1.0f);
                                }
                            }
                        }

                        return;
                    }
                }


            }
        }

        ItemStack item = event.getItem();
        if (item == null || !plugin.getBlockManager().isMagicBlock(item)) {
            return;
        }

        // 处理Shift+左键打开GUI
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (player.isSneaking()) {
                // 检查使用权限
                if (!player.hasPermission("magicblock.use")) {
                    plugin.sendMessage(player, "messages.no-permission-use");
                    event.setCancelled(true);
                    return;
                }
                
                // 检查冷却时间
                long currentTime = System.currentTimeMillis();
                Long lastTime = lastGuiOpenTime.get(player.getUniqueId());
                if (lastTime != null && currentTime - lastTime < GUI_OPEN_COOLDOWN) {
                    return;
                }

                // 检查绑定系统是否启用
                boolean bindingEnabled = plugin.getConfig().getBoolean("enable-binding-system", true);

                // 检查绑定状态
                if (bindingEnabled) {
                    UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(item);
                    if (boundPlayer != null && !boundPlayer.equals(player.getUniqueId())) {
                        plugin.sendMessage(player, "messages.not-bound-to-you");
                        event.setCancelled(true);
                        return;
                    }
                }

                // 设置冷却时间
                lastGuiOpenTime.put(player.getUniqueId(), currentTime);
                event.setCancelled(true);

                // 使用FoliaLib的runLater方法来延迟打开GUI
                foliaLib.getScheduler().runLater(() -> {
                    // 确保玩家仍然在线
                    if (player.isOnline()) {
                        foliaLib.getScheduler().runAtEntity(
                            player,
                            task -> guiManager.getBlockSelectionGUI().openInventory(player)
                        );
                    }
                }, 2L);
            }
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlayerItemHeld(PlayerItemHeldEvent event) {
        Player player = event.getPlayer();

        if (GUIManager.isPlayerSearching(player)) {
            ItemStack item = player.getInventory().getItem(event.getNewSlot());
            ItemMeta meta = (item != null) ? item.getItemMeta() : null;
            boolean hasSpecialLore = plugin.hasMagicLore(meta);

            if (!hasSpecialLore) {
                GUIManager.setPlayerSearching(player, false);
                player.sendMessage(plugin.getMessage("messages.item-changed"));
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // 首先检查是否是功能性容器限制
        if (isFunctionalContainer(event.getInventory().getType())) {
            ItemStack currentItem = event.getCurrentItem();
            ItemStack cursorItem = event.getCursor();

            // 检查当前点击的物品是否是魔法方块
            if (currentItem != null && plugin.getBlockManager().isMagicBlock(currentItem)) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.cannot-place-in-functional-container");
                return;
            }

            // 检查鼠标上的物品是否是魔法方块
            if (cursorItem != null && plugin.getBlockManager().isMagicBlock(cursorItem)) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.cannot-place-in-functional-container");
                return;
            }

            // 检查Shift+点击操作
            if (event.isShiftClick() && event.getCurrentItem() != null) {
                ItemStack clickedItem = event.getCurrentItem();
                if (plugin.getBlockManager().isMagicBlock(clickedItem)) {
                    event.setCancelled(true);
                    plugin.sendMessage(player, "messages.cannot-place-in-functional-container");
                    return;
                }
            }
        }

        String title = ChatColor.stripColor(event.getView().getTitle());
        String boundBlocksTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("gui.text.bound-blocks-title", "&8⚡ &b已绑定方块")));

        // 只处理绑定方块GUI，其他GUI由GUIManager统一处理
        if (title.equals(boundBlocksTitle)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() != Material.AIR) {
                // 处理双击删除或找回
                if (event.isRightClick()) {
                    plugin.getBlockBindManager().handleBindListClick(player, clickedItem);
                } else {
                    // 检查玩家是否已经有相同ID的方块
                    ItemMeta clickedMeta = clickedItem.getItemMeta();
                    if (clickedMeta == null) return;

                    String blockId = clickedMeta.getPersistentDataContainer().get(
                        new NamespacedKey(plugin, "block_id"),
                        PersistentDataType.STRING
                    );
                    if (blockId == null) return;

                    // 检查玩家背包中是否有相同ID的方块
                    for (ItemStack item : player.getInventory().getContents()) {
                        if (item != null && plugin.getBlockManager().isMagicBlock(item)) {
                            ItemMeta meta = item.getItemMeta();
                            if (meta != null) {
                                String existingBlockId = meta.getPersistentDataContainer().get(
                                    new NamespacedKey(plugin, "block_id"),
                                    PersistentDataType.STRING
                                );
                                if (blockId.equals(existingBlockId)) {
                                    plugin.sendMessage(player, "messages.already-have-block");
                                    return;
                                }
                            }
                        }
                    }

                    plugin.getBlockBindManager().retrieveBlock(player, clickedItem);
                    player.closeInventory();
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onInventoryDrag(InventoryDragEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        // 检查是否是功能性容器
        if (isFunctionalContainer(event.getInventory().getType())) {
            ItemStack draggedItem = event.getOldCursor();

            // 检查拖拽的物品是否是魔法方块
            if (draggedItem != null && plugin.getBlockManager().isMagicBlock(draggedItem)) {
                // 检查拖拽的目标槽位是否在功能性容器中
                for (int slot : event.getRawSlots()) {
                    if (slot < event.getInventory().getSize()) {
                        // 拖拽到了功能性容器中，取消事件
                        event.setCancelled(true);
                        plugin.sendMessage(player, "messages.cannot-place-in-functional-container");
                        return;
                    }
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();
        Material blockType = block.getType();

        // 检查是否是魔法方块
        if (plugin.getIndexManager().isMagicBlock(blockLocation)) {
            // 取消事件，防止方块变化和掉落物生成
            event.setCancelled(true);

            // 如果是重力方块（沙子、砂砾等）或红石组件类方块，直接移除它们而不产生掉落物
            if (block.getType().hasGravity() || isRedstoneComponent(blockType)) {
                // 对于红石组件类方块，立即设置为空气，防止掉落物生成
                if (isRedstoneComponent(blockType)) {
                    block.setType(Material.AIR);

                    // 然后移除记录
                    foliaLib.getScheduler().runLater(() -> {
                        plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (plugin.getIndexManager().isMagicBlock(blockLocation)) {
                            block.setType(Material.AIR);
                            plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                        }
                    }, 1L);
                }
            }
            return;
        }


    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        // 处理液体流动事件
        Block toBlock = event.getToBlock();
        Material toBlockType = toBlock.getType();

        // 检查目标方块是否是魔法方块
        if (plugin.getIndexManager().isMagicBlock(toBlock.getLocation())) {
            // 取消事件，防止液体破坏魔法方块
            event.setCancelled(true);

            // 对于红石组件类方块，立即设置为空气，防止掉落物生成
            if (isRedstoneComponent(toBlockType)) {
                toBlock.setType(Material.AIR);

                // 然后移除记录
                final Location blockLocation = toBlock.getLocation();
                foliaLib.getScheduler().runLater(() -> {
                    plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                }, 1L);
            }

            return;
        }


    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Location blockLocation = block.getLocation();
            if (plugin.getIndexManager().isMagicBlock(blockLocation)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Location blockLocation = block.getLocation();
            if (plugin.getIndexManager().isMagicBlock(blockLocation)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocksToKeep = new ArrayList<>();

        for (Block block : event.blockList()) {
            if (plugin.getIndexManager().isMagicBlock(block.getLocation())) {
                blocksToKeep.add(block);

                // 获取方块类型，用于后续处理
                Material blockType = block.getType();

                // 延迟1tick移除方块和记录，确保不会产生掉落物
                final Location blockLocation = block.getLocation();

                // 对于红石组件类方块，需要特别处理
                if (isRedstoneComponent(blockType)) {
                    // 立即设置为空气，防止掉落物生成
                    block.setType(Material.AIR);

                    // 然后移除记录
                    foliaLib.getScheduler().runLater(() -> {
                        plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (plugin.getIndexManager().isMagicBlock(blockLocation)) {
                            block.setType(Material.AIR);
                            plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                        }
                    }, 1L);
                }
            }
        }

        // 从爆炸列表中移除魔法方块，防止它们被爆炸破坏并产生掉落物
        event.blockList().removeAll(blocksToKeep);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onEntityExplodeComplete(EntityExplodeEvent event) {
        if (event.isCancelled()) {
            return;
        }

        // 使用FoliaLib在爆炸位置执行清理工作
        foliaLib.getScheduler().runAtLocation(
            event.getLocation(),
            task -> {
                Collection<Entity> nearbyEntities = event.getLocation().getWorld().getNearbyEntities(
                        event.getLocation(), 10, 10, 10);

                for (Entity entity : nearbyEntities) {
                    if (entity instanceof Item) {
                        Item item = (Item) entity;
                        if (plugin.getBlockManager().isMagicBlock(item.getItemStack())) {
                            item.remove(); // 只移除魔法方块掉落物
                        }
                    }
                }
            }
        );
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onBlockExplode(BlockExplodeEvent event) {
        List<Block> blocksToKeep = new ArrayList<>();
        for (Block block : event.blockList()) {
            if (plugin.getIndexManager().isMagicBlock(block.getLocation())) {
                blocksToKeep.add(block);

                // 获取方块类型，用于后续处理
                Material blockType = block.getType();

                // 延迟1tick移除方块和记录，确保不会产生掉落物
                final Location blockLocation = block.getLocation();

                // 对于红石组件类方块，需要特别处理
                if (isRedstoneComponent(blockType)) {
                    // 立即设置为空气，防止掉落物生成
                    block.setType(Material.AIR);

                    // 然后移除记录
                    foliaLib.getScheduler().runLater(() -> {
                        plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (plugin.getIndexManager().isMagicBlock(blockLocation)) {
                            block.setType(Material.AIR);
                            plugin.getIndexManager().unregisterMagicBlock(blockLocation);
                        }
                    }, 1L);
                }
            }
        }
        // 从爆炸列表中移除魔法方块，防止它们被爆炸破坏并产生掉落物
        event.blockList().removeAll(blocksToKeep);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onFurnaceSmelt(FurnaceSmeltEvent event) {
        ItemStack source = event.getSource();
        if (plugin.getBlockManager().isMagicBlock(source)) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockDamage(BlockDamageEvent event) {
        Block block = event.getBlock();
        if (plugin.getIndexManager().isMagicBlock(block.getLocation())) {
            // 对于魔法方块，我们不希望它们被损坏
            // 但允许正常的破坏事件处理
            return;
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockForm(BlockFormEvent event) {
        Block block = event.getBlock();
        if (plugin.getIndexManager().isMagicBlock(block.getLocation())) {
            // 阻止魔法方块形成其他方块（如冰形成等）
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockGrow(BlockGrowEvent event) {
        Block block = event.getBlock();
        if (plugin.getIndexManager().isMagicBlock(block.getLocation())) {
            // 阻止魔法方块生长（如作物生长等）
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockSpread(BlockSpreadEvent event) {
        Block block = event.getBlock();
        if (plugin.getIndexManager().isMagicBlock(block.getLocation())) {
            // 阻止魔法方块传播（如火焰传播等）
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onCraftItem(CraftItemEvent event) {
        // 检查合成材料中是否包含魔法方块
        for (ItemStack item : event.getInventory().getMatrix()) {
            if (item != null && plugin.getBlockManager().isMagicBlock(item)) {
                event.setCancelled(true);
                if (event.getWhoClicked() instanceof Player) {
                    plugin.sendMessage((Player) event.getWhoClicked(), "messages.cannot-craft-with-magic-block");
                }
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockRedstone(BlockRedstoneEvent event) {
        Block block = event.getBlock();

        // 检查周围的红石组件
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN};
        for (BlockFace face : faces) {
            Block adjacent = block.getRelative(face);
            Material type = adjacent.getType();

            // 如果是魔法方块位置上的红石组件，确保它们可以接收红石信号
            if (plugin.getIndexManager().isMagicBlock(adjacent.getLocation()) && isRedstoneComponent(type)) {
                // 不取消事件，允许红石信号传递

                // 对于特定的方块，可能需要手动更新状态
                if (type == Material.REDSTONE_LAMP ||
                    type == Material.DISPENSER ||
                    type == Material.DROPPER ||
                    type == Material.HOPPER ||
                    type == Material.PISTON ||
                    type == Material.STICKY_PISTON ||
                    type == Material.OBSERVER ||
                    type == Material.NOTE_BLOCK ||
                    type == Material.POWERED_RAIL ||
                    type == Material.DETECTOR_RAIL ||
                    type == Material.ACTIVATOR_RAIL) {

                    // 使用FoliaLib延迟1tick更新方块状态
                    final Block targetBlock = adjacent;
                    foliaLib.getScheduler().runLater(() -> {
                        targetBlock.getState().update(true, true);
                    }, 1L);
                }
            }
        }

        // 如果当前方块本身是魔法方块位置上的红石组件，确保它可以正常工作
        if (plugin.getIndexManager().isMagicBlock(block.getLocation()) && isRedstoneComponent(block.getType())) {
            // 不取消事件，允许红石信号传递
        }
    }



    // 性能优化：检查是否有相邻的可连接方块
    private boolean hasAdjacentConnectableBlocks(Block block) {
        BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
        Material blockType = block.getType();

        for (BlockFace face : faces) {
            Block adjacent = block.getRelative(face);
            if (adjacent.getType() == blockType) {
                return true; // 找到至少一个相邻的同类型方块
            }
        }
        return false;
    }

    /**
     * 检查材料是否是斧头
     */
    private boolean isAxe(Material material) {
        return material == Material.WOODEN_AXE ||
               material == Material.STONE_AXE ||
               material == Material.IRON_AXE ||
               material == Material.GOLDEN_AXE ||
               material == Material.DIAMOND_AXE ||
               material == Material.NETHERITE_AXE;
    }

    /**
     * 检查材料是否是原木类型
     */
    private boolean isLogType(Material material) {
        return material.name().contains("_LOG") && !material.name().contains("STRIPPED");
    }

    /**
     * 检查材料是否是铜类型
     */
    private boolean isCopperType(Material material) {
        return material.name().contains("COPPER") &&
               (material.name().contains("EXPOSED") ||
                material.name().contains("WEATHERED") ||
                material.name().contains("OXIDIZED")) &&
               !material.name().contains("WAXED");
    }

    /**
     * 获取削皮或去氧化后的方块类型
     */
    private Material getStrippedOrScrapedType(Material material) {
        String materialName = material.name();

        // 处理原木削皮
        if (materialName.contains("_LOG") && !materialName.contains("STRIPPED")) {
            try {
                return Material.valueOf("STRIPPED_" + materialName);
            } catch (IllegalArgumentException e) {
                return null;
            }
        }

        // 处理铜去氧化
        if (materialName.contains("COPPER") && !materialName.contains("WAXED")) {
            if (materialName.contains("OXIDIZED")) {
                return Material.valueOf(materialName.replace("OXIDIZED_", "WEATHERED_"));
            } else if (materialName.contains("WEATHERED")) {
                return Material.valueOf(materialName.replace("WEATHERED_", "EXPOSED_"));
            } else if (materialName.contains("EXPOSED")) {
                return Material.valueOf(materialName.replace("EXPOSED_", ""));
            }
        }

        return null;
    }

    /**
     * 更新相邻方块的连接状态
     * 当一个连接型方块被破坏时，需要更新其相邻方块的连接状态
     */
    private void updateAdjacentBlockConnections(Block block) {
        if (!isConnectableBlock(block.getType())) {
            return;
        }

        org.bukkit.block.data.BlockData blockData = block.getBlockData();

        if (blockData instanceof org.bukkit.block.data.type.Wall) {
            updateSingleWallConnections(block);
        } else if (blockData instanceof org.bukkit.block.data.type.Fence) {
            updateSingleFenceConnections(block);
        } else if (blockData instanceof org.bukkit.block.data.type.GlassPane) {
            updateSinglePaneConnections(block);
        } else {
            // 对于其他类型的连接型方块，使用通用更新
            block.getState().update(true, true);
        }
    }

    /**
     * 更新单个墙方块的连接状态
     */
    private void updateSingleWallConnections(Block wall) {
        org.bukkit.block.data.type.Wall wallData = (org.bukkit.block.data.type.Wall) wall.getBlockData();

        // 重新计算所有方向的连接状态
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adjacent = wall.getRelative(face);
            boolean shouldConnect = adjacent.getType() == wall.getType() ||
                                   (adjacent.getType().isSolid() && !adjacent.getType().isTransparent());
            wallData.setHeight(face, shouldConnect ? org.bukkit.block.data.type.Wall.Height.LOW : org.bukkit.block.data.type.Wall.Height.NONE);
        }

        wall.setBlockData(wallData, true);
    }

    /**
     * 更新单个栅栏方块的连接状态
     */
    private void updateSingleFenceConnections(Block fence) {
        org.bukkit.block.data.type.Fence fenceData = (org.bukkit.block.data.type.Fence) fence.getBlockData();

        // 重新计算所有方向的连接状态
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adjacent = fence.getRelative(face);
            boolean shouldConnect = adjacent.getType() == fence.getType() ||
                                   adjacent.getType().toString().contains("FENCE_GATE") ||
                                   (adjacent.getType().isSolid() && !adjacent.getType().isTransparent());
            fenceData.setFace(face, shouldConnect);
        }

        fence.setBlockData(fenceData, true);
    }

    /**
     * 更新单个玻璃板方块的连接状态
     */
    private void updateSinglePaneConnections(Block pane) {
        org.bukkit.block.data.type.GlassPane paneData = (org.bukkit.block.data.type.GlassPane) pane.getBlockData();

        // 重新计算所有方向的连接状态
        for (BlockFace face : new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST}) {
            Block adjacent = pane.getRelative(face);
            boolean shouldConnect = adjacent.getType() == pane.getType() ||
                                   adjacent.getType() == Material.GLASS ||
                                   (adjacent.getType().isSolid() && !adjacent.getType().isTransparent());
            paneData.setFace(face, shouldConnect);
        }

        pane.setBlockData(paneData, true);
    }

    /**
     * 检查是否是功能性容器（禁止放入魔法方块的容器）
     * 允许储物类容器（箱子、末影箱），禁止功能性容器
     */
    private boolean isFunctionalContainer(InventoryType type) {
        switch (type) {
            // 允许的储物类容器
            case CHEST:
            case ENDER_CHEST:
            case SHULKER_BOX:
            case BARREL:
                return false;

            // 禁止的功能性容器
            case WORKBENCH:           // 工作台
            case FURNACE:             // 熔炉
            case BLAST_FURNACE:       // 高炉
            case SMOKER:              // 烟熏炉
            case STONECUTTER:         // 切石机
            case ANVIL:               // 铁砧
            case ENCHANTING:          // 附魔台
            case BREWING:             // 酿造台
            case BEACON:              // 信标
            case HOPPER:              // 漏斗
            case DROPPER:             // 投掷器
            case DISPENSER:           // 发射器
            case LOOM:                // 织布机
            case CARTOGRAPHY:         // 制图台
            case GRINDSTONE:          // 砂轮
            case SMITHING:            // 锻造台
            case MERCHANT:            // 村民交易
            case LECTERN:             // 讲台
            case COMPOSTER:           // 堆肥桶
                return true;

            default:
                // 默认允许其他类型
                return false;
        }
    }
}

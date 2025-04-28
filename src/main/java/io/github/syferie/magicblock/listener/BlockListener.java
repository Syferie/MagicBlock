package io.github.syferie.magicblock.listener;

import com.tcoded.folialib.FoliaLib;
import com.tcoded.folialib.wrapper.task.WrappedTask;
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
import org.bukkit.event.inventory.CraftItemEvent;
import org.bukkit.event.inventory.FurnaceSmeltEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.*;

public class BlockListener implements Listener {
    private final MagicBlockPlugin plugin;
    private final GUIManager guiManager;
    private final List<Material> buildingMaterials;
    private final NamespacedKey magicBlockKey;
    private static final long GUI_OPEN_COOLDOWN = 300;
    private final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();
    private final FoliaLib foliaLib;

    public BlockListener(MagicBlockPlugin plugin, List<Material> allowedMaterials) {
        this.plugin = plugin;
        this.guiManager = new GUIManager(plugin, allowedMaterials);
        this.buildingMaterials = new ArrayList<>(allowedMaterials);
        this.magicBlockKey = new NamespacedKey(plugin, "magicblock_location");
        this.foliaLib = plugin.getFoliaLib();
        plugin.getServer().getPluginManager().registerEvents(guiManager, plugin);
    }

    public void setAllowedMaterials(List<Material> materials) {
        this.buildingMaterials.clear();
        this.buildingMaterials.addAll(materials);
    }

    private boolean isTallBlock(Material material) {
        return material.toString().contains("DOOR") ||
               material == Material.TALL_GRASS ||
               material == Material.LARGE_FERN ||
               material == Material.TALL_SEAGRASS ||
               material == Material.SUNFLOWER ||
               material == Material.LILAC ||
               material == Material.ROSE_BUSH ||
               material == Material.PEONY;
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

            // 对于床方块，保存所有放置的方块位置
            if (item.getType().toString().contains("_BED")) {
                for (org.bukkit.block.BlockState state : event.getReplacedBlockStates()) {
                    saveMagicBlockLocation(state.getLocation());
                }
            }
        }
    }

    private void handleMagicBlockPlace(BlockPlaceEvent event, ItemStack item) {
        Block block = event.getBlock();
        Player player = event.getPlayer();

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

        // 保存方块位置
        saveMagicBlockLocation(block.getLocation());

        // 如果是双格高方块，保存上半部分的位置
        if (isTallBlock(item.getType())) {
            Block topBlock = block.getRelative(BlockFace.UP);
            saveMagicBlockLocation(topBlock.getLocation());
        }

        // 如果是连接型方块，更新连接状态
        if (isConnectableBlock(item.getType())) {
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

    private void saveMagicBlockLocation(Location loc) {
        String locationString = serializeLocation(loc);
        PersistentDataContainer container = loc.getChunk().getPersistentDataContainer();

        // 获取现有的位置列表
        List<String> locations = getLocationsFromContainer(container);
        locations.add(locationString);

        // 保存更新后的位置列表
        String joinedLocations = String.join(";", locations);
        container.set(magicBlockKey, PersistentDataType.STRING, joinedLocations);
    }

    private boolean isMagicBlockLocation(Location loc) {
        PersistentDataContainer container = loc.getChunk().getPersistentDataContainer();
        String locationsData = container.get(magicBlockKey, PersistentDataType.STRING);
        if (locationsData == null) return false;

        String targetLoc = serializeLocation(loc);
        return Arrays.asList(locationsData.split(";")).contains(targetLoc);
    }

    private void removeMagicBlockLocation(Location loc) {
        PersistentDataContainer container = loc.getChunk().getPersistentDataContainer();
        String locationsData = container.get(magicBlockKey, PersistentDataType.STRING);
        if (locationsData == null) return;

        List<String> locations = new ArrayList<>(Arrays.asList(locationsData.split(";")));
        locations.remove(serializeLocation(loc));

        if (locations.isEmpty()) {
            container.remove(magicBlockKey);
        } else {
            container.set(magicBlockKey, PersistentDataType.STRING, String.join(";", locations));
        }
    }

    private String serializeLocation(Location loc) {
        return loc.getWorld().getName() + "," +
               loc.getBlockX() + "," +
               loc.getBlockY() + "," +
               loc.getBlockZ();
    }

    private List<String> getLocationsFromContainer(PersistentDataContainer container) {
        String locationsData = container.get(magicBlockKey, PersistentDataType.STRING);
        if (locationsData == null || locationsData.isEmpty()) {
            return new ArrayList<>();
        }
        return new ArrayList<>(Arrays.asList(locationsData.split(";")));
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
        boolean isMagicBlock = isMagicBlockLocation(blockLocation);
        Block targetBlock = eventBlock;
        Block blockAbove = eventBlock.getRelative(BlockFace.UP);

        // 检查是否是连接型方块
        if (isConnectableBlock(eventBlock.getType())) {
            // 保存相邻方块的引用，以便稍后更新
            final List<Block> adjacentBlocks = new ArrayList<>();
            BlockFace[] faces = {BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            for (BlockFace face : faces) {
                Block adjacent = eventBlock.getRelative(face);
                if (adjacent.getType() == eventBlock.getType()) {
                    adjacentBlocks.add(adjacent);
                }
            }

            // 在方块被破坏后更新相邻方块
            if (!adjacentBlocks.isEmpty()) {
                final Material blockType = eventBlock.getType();
                foliaLib.getScheduler().runLater(() -> {
                    for (Block adjacent : adjacentBlocks) {
                        if (adjacent.getType() == blockType) {
                            adjacent.getState().update(true, true);
                        }
                    }
                }, 1L);
            }
        }

        // 检查上方是否有附着类方块（无论下面是不是魔术方块）
        if (isAttachable(blockAbove.getType()) && isMagicBlockLocation(blockAbove.getLocation())) {
            blockAbove.setType(Material.AIR);
            removeMagicBlockLocation(blockAbove.getLocation());
        }

        // 检查是否是床方块
        if (eventBlock.getType().toString().contains("_BED")) {
            org.bukkit.block.data.type.Bed bedData = (org.bukkit.block.data.type.Bed) eventBlock.getBlockData();
            Block otherPart;

            // 根据当前部分找到另一部分
            if (bedData.getPart() == org.bukkit.block.data.type.Bed.Part.HEAD) {
                otherPart = eventBlock.getRelative(bedData.getFacing().getOppositeFace());
            } else {
                otherPart = eventBlock.getRelative(bedData.getFacing());
            }

            // 如果任一部分是魔法方块，则两部分都视为魔法方块
            boolean isOtherPartMagic = isMagicBlockLocation(otherPart.getLocation());
            if (isOtherPartMagic || isMagicBlock) {
                isMagicBlock = true;
                Player player = event.getPlayer();
                ItemStack blockItem = new ItemStack(eventBlock.getType());

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

                // 取消原有的掉落
                event.setDropItems(false);
                event.setExpToDrop(0);

                // 清理绑定数据
                plugin.getBlockBindManager().cleanupBindings(blockItem);

                // 移除两个部分的位置记录
                removeMagicBlockLocation(blockLocation);
                removeMagicBlockLocation(otherPart.getLocation());

                // 移除另一部分，不产生掉落物
                otherPart.setType(Material.AIR);
            }
            return;
        }

        // 检查是否是双格高方块的上半部分
        if (!isMagicBlock) {
            Block blockBelow = eventBlock.getRelative(BlockFace.DOWN);
            if (isTallBlock(blockBelow.getType()) && isMagicBlockLocation(blockBelow.getLocation())) {
                isMagicBlock = true;
                targetBlock = blockBelow; // 使用下半部分的方块
            }
        }

        // 检查是否是双格高方块的下半部分
        if (!isMagicBlock && isTallBlock(eventBlock.getType())) {
            if (isMagicBlockLocation(blockLocation)) {
                removeMagicBlockLocation(blockAbove.getLocation());
            }
        }

        if (isMagicBlock) {
            Player player = event.getPlayer();
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
            removeMagicBlockLocation(blockLocation);

            // 如果是双格高方块，同时移除另一半的位置记录
            if (isTallBlock(targetBlock.getType())) {
                Block topBlock = targetBlock.getRelative(BlockFace.UP);
                removeMagicBlockLocation(topBlock.getLocation());
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isMagicBlockLocation(block.getLocation())) {
            Material type = block.getType();
            // 允许红石组件的状态改变，但阻止它们被破坏
            if (isRedstoneComponent(type)) {
                // 如果是由于方块更新引起的状态改变，允许它
                if (event.getChangedType() == type) {
                    return;
                }

                // 特殊处理需要更新红石状态的方块
                if (type == Material.POWERED_RAIL ||
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
                    type.name().contains("GATE")) {
                    // 允许这些方块的状态改变
                    return;
                }
            }
            // 取消其他物理事件
            event.setCancelled(true);
        }
    }



    private boolean isRedstoneComponent(Material material) {
        return material.name().endsWith("_PRESSURE_PLATE") ||
               material.name().endsWith("_BUTTON") ||
               material == Material.LEVER ||
               material == Material.REDSTONE_WIRE ||
               material == Material.REPEATER ||
               material == Material.COMPARATOR ||
               material == Material.REDSTONE_TORCH ||
               material == Material.REDSTONE_WALL_TORCH ||
               material == Material.POWERED_RAIL ||
               material == Material.DETECTOR_RAIL ||
               material == Material.ACTIVATOR_RAIL ||
               material == Material.REDSTONE_LAMP ||
               material == Material.DISPENSER ||
               material == Material.DROPPER ||
               material == Material.HOPPER ||
               material == Material.OBSERVER ||
               material == Material.PISTON ||
               material == Material.STICKY_PISTON ||
               material == Material.DAYLIGHT_DETECTOR ||
               material == Material.TARGET ||
               material == Material.TRIPWIRE ||
               material == Material.TRIPWIRE_HOOK ||
               material == Material.NOTE_BLOCK ||
               material == Material.BELL ||
               material.name().contains("DOOR") ||
               material.name().contains("TRAPDOOR") ||
               material.name().contains("GATE");
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
            boolean isMagicBlock = isMagicBlockLocation(clickedLocation);
            Block targetBlock = clickedBlock;

            // 检查是否是双格高方块的上半部分
            if (!isMagicBlock) {
                Block blockBelow = clickedBlock.getRelative(BlockFace.DOWN);
                if (isTallBlock(blockBelow.getType()) && isMagicBlockLocation(blockBelow.getLocation())) {
                    isMagicBlock = true;
                    targetBlock = blockBelow; // 使用下半部分的方块
                }
            }

            // 检查是否是双格高方块的下半部分
            if (!isMagicBlock && isTallBlock(clickedBlock.getType()) && isMagicBlockLocation(clickedLocation)) {
                isMagicBlock = true;
                targetBlock = clickedBlock;
            }

            // 如果是魔法方块且是门，取消原事件并手动处理门的状态
            if (isMagicBlock && targetBlock.getType().toString().contains("DOOR")) {
                org.bukkit.block.data.Bisected.Half half = ((org.bukkit.block.data.Bisected)targetBlock.getBlockData()).getHalf();
                Block otherHalf = half == org.bukkit.block.data.Bisected.Half.BOTTOM ?
                    targetBlock.getRelative(BlockFace.UP) : targetBlock.getRelative(BlockFace.DOWN);

                // 获取门的数据
                org.bukkit.block.data.type.Door doorData = (org.bukkit.block.data.type.Door)targetBlock.getBlockData();
                org.bukkit.block.data.type.Door otherDoorData = (org.bukkit.block.data.type.Door)otherHalf.getBlockData();

                // 切换门的开关状态
                boolean isOpen = !doorData.isOpen();
                doorData.setOpen(isOpen);
                otherDoorData.setOpen(isOpen);

                // 应用更改
                targetBlock.setBlockData(doorData);
                otherHalf.setBlockData(otherDoorData);

                // 播放门的声音
                player.getWorld().playSound(targetBlock.getLocation(),
                    isOpen ? Sound.BLOCK_WOODEN_DOOR_OPEN : Sound.BLOCK_WOODEN_DOOR_CLOSE,
                    1.0f, 1.0f);

                event.setCancelled(true);
                return;
            }
        }

        ItemStack item = event.getItem();
        if (item == null || !plugin.getBlockManager().isMagicBlock(item)) {
            return;
        }

        // 处理Shift+左键打开GUI
        if (event.getAction() == Action.LEFT_CLICK_AIR || event.getAction() == Action.LEFT_CLICK_BLOCK) {
            if (player.isSneaking()) {
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

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();

        String title = ChatColor.stripColor(event.getView().getTitle());
        String expectedTitle = ChatColor.stripColor(plugin.getMessage("gui.title"));
        String boundBlocksTitle = ChatColor.stripColor(plugin.getMessage("gui.bound-blocks-title"));

        if (title.equals(expectedTitle)) {
            event.setCancelled(true);
            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem == null || clickedItem.getType() == Material.AIR) return;

            // 检查GUI打开后的冷却时间
            long currentTime = System.currentTimeMillis();
            Long openTime = lastGuiOpenTime.get(player.getUniqueId());
            if (openTime != null && currentTime - openTime < GUI_OPEN_COOLDOWN) {
                return;
            }

            // 处理翻页按钮
            if (clickedItem.getType() == Material.ARROW) {
                guiManager.getBlockSelectionGUI().handleInventoryClick(event, player);
                return;
            }

            // 处理搜索按钮
            if (clickedItem.getType() == Material.COMPASS) {
                // 检查搜索冷却时间
                Long lastClick = lastGuiOpenTime.get(player.getUniqueId());
                if (lastClick != null && currentTime - lastClick < GUI_OPEN_COOLDOWN) {
                    return;
                }

                // 将搜索相关的处理委托给GUIManager
                guiManager.getBlockSelectionGUI().handleInventoryClick(event, player);
                return;
            }

            // 其他点击处理委托给GUIManager
            guiManager.getBlockSelectionGUI().handleInventoryClick(event, player);
        } else if (title.equals(boundBlocksTitle)) {
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

    private boolean isMagicBlock(ItemStack item) {
        if (item == null || !item.hasItemMeta()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        // 使用插件的hasMagicLore方法进行检查，该方法已经增强以处理格式代码
        return plugin.hasMagicLore(meta);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();
        Material blockType = block.getType();

        // 检查是否是魔法方块
        if (isMagicBlockLocation(blockLocation)) {
            // 取消事件，防止方块变化和掉落物生成
            event.setCancelled(true);

            // 如果是重力方块（沙子、砂砾等）或红石组件类方块，直接移除它们而不产生掉落物
            if (block.getType().hasGravity() || isRedstoneComponent(blockType)) {
                // 对于红石组件类方块，立即设置为空气，防止掉落物生成
                if (isRedstoneComponent(blockType)) {
                    block.setType(Material.AIR);

                    // 然后移除记录
                    foliaLib.getScheduler().runLater(() -> {
                        removeMagicBlockLocation(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (isMagicBlockLocation(blockLocation)) {
                            block.setType(Material.AIR);
                            removeMagicBlockLocation(blockLocation);
                        }
                    }, 1L);
                }
            }
            return;
        }

        // 检查是否是附着在魔法方块上的方块
        if (isAttachable(block.getType())) {
            Block blockBelow = block.getRelative(BlockFace.DOWN);
            if (isMagicBlockLocation(blockBelow.getLocation())) {
                // 取消事件，防止方块变化和掉落物生成
                event.setCancelled(true);

                // 对于红石组件类方块，立即设置为空气，防止掉落物生成
                if (isRedstoneComponent(blockType)) {
                    block.setType(Material.AIR);

                    // 然后移除记录
                    foliaLib.getScheduler().runLater(() -> {
                        removeMagicBlockLocation(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (isMagicBlockLocation(blockLocation)) {
                            block.setType(Material.AIR);
                            removeMagicBlockLocation(blockLocation);
                        }
                    }, 1L);
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockFromTo(BlockFromToEvent event) {
        // 处理液体流动事件
        Block toBlock = event.getToBlock();
        Material toBlockType = toBlock.getType();

        // 检查目标方块是否是魔法方块
        if (isMagicBlockLocation(toBlock.getLocation())) {
            // 取消事件，防止液体破坏魔法方块
            event.setCancelled(true);

            // 对于红石组件类方块，立即设置为空气，防止掉落物生成
            if (isRedstoneComponent(toBlockType)) {
                toBlock.setType(Material.AIR);

                // 然后移除记录
                final Location blockLocation = toBlock.getLocation();
                foliaLib.getScheduler().runLater(() -> {
                    removeMagicBlockLocation(blockLocation);
                }, 1L);
            }

            return;
        }

        // 检查目标方块下方是否有魔法方块，且目标方块是可附着的
        Block belowBlock = toBlock.getRelative(BlockFace.DOWN);
        if (isAttachable(toBlock.getType()) && isMagicBlockLocation(belowBlock.getLocation())) {
            // 取消事件，防止液体破坏附着在魔法方块上的方块
            event.setCancelled(true);

            // 对于红石组件类方块，立即设置为空气，防止掉落物生成
            if (isRedstoneComponent(toBlockType)) {
                toBlock.setType(Material.AIR);

                // 然后移除记录
                final Location blockLocation = toBlock.getLocation();
                foliaLib.getScheduler().runLater(() -> {
                    removeMagicBlockLocation(blockLocation);
                }, 1L);
            }
        }
    }

    @EventHandler
    public void onBlockPistonExtend(BlockPistonExtendEvent event) {
        for (Block block : event.getBlocks()) {
            Location blockLocation = block.getLocation();
            if (isMagicBlockLocation(blockLocation)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler
    public void onBlockPistonRetract(BlockPistonRetractEvent event) {
        for (Block block : event.getBlocks()) {
            Location blockLocation = block.getLocation();
            if (isMagicBlockLocation(blockLocation)) {
                event.setCancelled(true);
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocksToKeep = new ArrayList<>();

        for (Block block : event.blockList()) {
            if (isMagicBlockLocation(block.getLocation())) {
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
                        removeMagicBlockLocation(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (isMagicBlockLocation(blockLocation)) {
                            block.setType(Material.AIR);
                            removeMagicBlockLocation(blockLocation);
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
            if (isMagicBlockLocation(block.getLocation())) {
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
                        removeMagicBlockLocation(blockLocation);
                    }, 1L);
                } else {
                    // 对于其他类型的方块，使用原来的处理方式
                    foliaLib.getScheduler().runLater(() -> {
                        if (isMagicBlockLocation(blockLocation)) {
                            block.setType(Material.AIR);
                            removeMagicBlockLocation(blockLocation);
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
            if (isMagicBlockLocation(adjacent.getLocation()) && isRedstoneComponent(type)) {
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
        if (isMagicBlockLocation(block.getLocation()) && isRedstoneComponent(block.getType())) {
            // 不取消事件，允许红石信号传递
        }
    }

    private boolean isAttachable(Material material) {
        switch (material) {
            case TORCH:
            case WALL_TORCH:
            case LANTERN:
            case SOUL_LANTERN:
            case LEVER:
            case REDSTONE_TORCH:
            case REDSTONE_WALL_TORCH:
            case TRIPWIRE_HOOK:
            case VINE:
            case WHITE_CARPET:
            case ORANGE_CARPET:
            case MAGENTA_CARPET:
            case LIGHT_BLUE_CARPET:
            case YELLOW_CARPET:
            case LIME_CARPET:
            case PINK_CARPET:
            case GRAY_CARPET:
            case LIGHT_GRAY_CARPET:
            case CYAN_CARPET:
            case PURPLE_CARPET:
            case BLUE_CARPET:
            case BROWN_CARPET:
            case GREEN_CARPET:
            case RED_CARPET:
            case BLACK_CARPET:
            case SNOW:
            case RAIL:
            case POWERED_RAIL:
            case DETECTOR_RAIL:
            case ACTIVATOR_RAIL:
            case REDSTONE_WIRE:
            case REPEATER:
            case COMPARATOR:
            case OAK_SAPLING:
            case SPRUCE_SAPLING:
            case BIRCH_SAPLING:
            case JUNGLE_SAPLING:
            case ACACIA_SAPLING:
            case DARK_OAK_SAPLING:
            case DANDELION:
            case POPPY:
            case BLUE_ORCHID:
            case ALLIUM:
            case AZURE_BLUET:
            case RED_TULIP:
            case ORANGE_TULIP:
            case WHITE_TULIP:
            case PINK_TULIP:
            case OXEYE_DAISY:
            case CORNFLOWER:
            case LILY_OF_THE_VALLEY:
            case WITHER_ROSE:
            case SUGAR_CANE:
            case WHEAT:
            case CARROTS:
            case POTATOES:
            case BEETROOTS:
            case MELON_STEM:
            case PUMPKIN_STEM:
            case NETHER_WART:
            case SWEET_BERRY_BUSH:
                return true;
            default:
                return material.name().endsWith("_PRESSURE_PLATE") ||
                       material.name().endsWith("_BUTTON") ||
                       material.name().endsWith("_SIGN") ||
                       material.name().endsWith("_BANNER");
        }
    }
}

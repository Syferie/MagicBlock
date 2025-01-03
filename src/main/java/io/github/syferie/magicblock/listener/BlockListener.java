package io.github.syferie.magicblock.listener;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.gui.GUIManager;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
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
    private static final long GUI_OPEN_COOLDOWN = 500;
    private final Map<UUID, Long> lastGuiOpenTime = new HashMap<>();

    public BlockListener(MagicBlockPlugin plugin, List<Material> allowedMaterials) {
        this.plugin = plugin;
        this.guiManager = new GUIManager(plugin, allowedMaterials);
        this.buildingMaterials = new ArrayList<>(allowedMaterials);
        this.magicBlockKey = new NamespacedKey(plugin, "magicblock_location");
    }

    public void setAllowedMaterials(List<Material> materials) {
        this.buildingMaterials.clear();
        this.buildingMaterials.addAll(materials);
    }

    @EventHandler
    public void onBlockPlace(BlockPlaceEvent event) {
        ItemStack item = event.getItemInHand();
        if (plugin.hasMagicLore(item.getItemMeta())) {
            Block block = event.getBlock();
            Player player = event.getPlayer();

            // 检查使用次数
            int useTimes = plugin.getBlockManager().getUseTimes(item);
            if (useTimes == 0) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.block-removed");
                return;
            }

            // 检查是否已绑定
            UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(item);
            if (boundPlayer == null) {
                // 第一次使用时自动绑定
                plugin.getBlockBindManager().bindBlock(player, item);
            } else if (!boundPlayer.equals(player.getUniqueId())) {
                event.setCancelled(true);
                plugin.sendMessage(player, "messages.not-bound-to-you");
                return;
            }

            // 放置方块
            block.setType(item.getType());
            saveMagicBlockLocation(block.getLocation());

            // 减少使用次数
            if (useTimes > 0) { // -1表示无限使用
                plugin.getBlockManager().decrementUseTimes(item);
            }

            // 记录使用统计
            plugin.incrementPlayerUsage(player.getUniqueId());
            plugin.logUsage(player, item);
        }
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
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();

        if (isMagicBlockLocation(blockLocation)) {
            Player player = event.getPlayer();
            ItemStack blockItem = new ItemStack(block.getType());
            
            // 检查绑定状态
            if (plugin.getBlockBindManager().isBlockBound(blockItem)) {
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
        }

        // 检查相邻方块
        for (BlockFace face : BlockFace.values()) {
            Block relativeBlock = block.getRelative(face);
            if (isMagicBlockLocation(relativeBlock.getLocation())) {
                // 如果相邻方块是附着类方块且是魔法方块，则移除它
                Material type = relativeBlock.getType();
                if (isAttachable(type)) {
                    ItemStack relativeItem = new ItemStack(type);
                    
                    // 检查绑定状态
                    if (plugin.getBlockBindManager().isBlockBound(relativeItem)) {
                        UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(relativeItem);
                        if (boundPlayer != null && !boundPlayer.equals(event.getPlayer().getUniqueId())) {
                            continue; // 跳过不属于该玩家的方块
                        }
                    }
                    
                    // 清理绑定数据
                    plugin.getBlockBindManager().cleanupBindings(relativeItem);
                    relativeBlock.setType(Material.AIR);
                    removeMagicBlockLocation(relativeBlock.getLocation());
                }
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onBlockPhysics(BlockPhysicsEvent event) {
        Block block = event.getBlock();
        if (isMagicBlockLocation(block.getLocation())) {
            // 如果是魔法方块，取消物理事件
            event.setCancelled(true);
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
                return true;
            default:
                return false;
        }
    }

    @EventHandler
    public void onPlayerInteract(PlayerInteractEvent event) {
        if (event.getHand() != EquipmentSlot.HAND) {
            return;
        }

        Player player = event.getPlayer();
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

                // 检查绑定状态
                UUID boundPlayer = plugin.getBlockBindManager().getBoundPlayer(item);
                if (boundPlayer != null && !boundPlayer.equals(player.getUniqueId())) {
                    plugin.sendMessage(player, "messages.not-bound-to-you");
                    event.setCancelled(true);
                    return;
                }

                // 设置冷却时间
                lastGuiOpenTime.put(player.getUniqueId(), currentTime);
                event.setCancelled(true);

                // 延迟打开GUI
                plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
                    guiManager.getBlockSelectionGUI().openInventory(player);
                }, 3L); // 3tick ≈ 150ms的延迟
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
                    plugin.sendMessage(player, "messages.wait-cooldown");
                    return;
                }
                lastGuiOpenTime.put(player.getUniqueId(), currentTime);
                
                player.closeInventory();
                plugin.sendMessage(player, "messages.search-prompt");
                GUIManager.setPlayerSearching(player, true);
                return;
            }

            // 处理方块选择
            ItemStack heldItem = player.getInventory().getItemInMainHand();
            if (plugin.getBlockManager().isMagicBlock(heldItem)) {
                // 更新方块材质
                heldItem.setType(clickedItem.getType());
                ItemMeta meta = heldItem.getItemMeta();
                if (meta != null) {
                    // 根据当前语言获取方块名称
                    String blockName = plugin.getLanguageManager().getMessage("blocks." + clickedItem.getType().name());
                    
                    // ��原有名称两侧添加装饰符号
                    String nameFormat = plugin.getConfig().getString("display.block-name-format", "&b✦ %s &b✦");
                    meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', 
                        String.format(nameFormat, blockName)));
                    
                    heldItem.setItemMeta(meta);
                }
                
                // 如果是绑定的方块，更新配置中的材质
                plugin.getBlockBindManager().updateBlockMaterial(heldItem);
                plugin.sendMessage(player, "messages.success-replace", 
                    plugin.getLanguageManager().getMessage("blocks." + clickedItem.getType().name()));
                player.closeInventory();
            }
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
        return meta.hasLore() && meta.getLore().contains(plugin.getMagicLore());
    }

    @EventHandler
    public void onEntityChangeBlock(EntityChangeBlockEvent event) {
        Block block = event.getBlock();
        Location blockLocation = block.getLocation();

        if (isMagicBlockLocation(blockLocation)) {
            event.setCancelled(true);
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

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent event) {
        List<Block> blocksToKeep = new ArrayList<>();

        for (Block block : event.blockList()) {
            if (isMagicBlockLocation(block.getLocation())) {
                blocksToKeep.add(block);
                removeMagicBlockLocation(block.getLocation());
            }
        }

        event.blockList().removeAll(blocksToKeep);
    }
}

package io.github.syferie.magicblock.gui;

import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GUIManager {
    private final MagicBlockPlugin plugin;
    private final BlockSelectionGUI blockSelectionGUI;
    private static final Map<UUID, Boolean> searchingPlayers = new ConcurrentHashMap<>();
    private static final long GUI_CLICK_COOLDOWN = 500; // 500ms冷却时间
    private static final Map<UUID, Long> lastSearchClickTime = new ConcurrentHashMap<>();
    private static final long SEARCH_CLICK_COOLDOWN = 1000; // 1000ms冷却时间
    private static final Map<UUID, Long> lastGuiOpenTime = new ConcurrentHashMap<>();
    private static final long GUI_PROTECTION_TIME = 300; // 打开GUI后300ms内不响应点击

    public GUIManager(MagicBlockPlugin plugin, List<Material> allowedMaterials) {
        this.plugin = plugin;
        this.blockSelectionGUI = new BlockSelectionGUI(plugin);
    }

    public static void setPlayerSearching(Player player, boolean searching) {
        if (searching) {
            searchingPlayers.put(player.getUniqueId(), true);
        } else {
            searchingPlayers.remove(player.getUniqueId());
        }
    }

    public static boolean isPlayerSearching(Player player) {
        return searchingPlayers.getOrDefault(player.getUniqueId(), false);
    }

    public BlockSelectionGUI getBlockSelectionGUI() {
        return blockSelectionGUI;
    }

    public void openBlockSelectionGUI(Player player) {
        ItemStack heldItem = player.getInventory().getItemInMainHand();
        if (!plugin.hasMagicLore(heldItem.getItemMeta())) {
            plugin.sendMessage(player, "messages.must-hold-magic-block");
            return;
        }
        lastGuiOpenTime.put(player.getUniqueId(), System.currentTimeMillis());
        blockSelectionGUI.openInventory(player);
    }

    @EventHandler
    public void onPlayerChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (isPlayerSearching(player)) {
            event.setCancelled(true);
            String input = event.getMessage();

            if (input.equalsIgnoreCase("cancel")) {
                setPlayerSearching(player, false);
                // 使用调度器在主线程中执行GUI操作
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    blockSelectionGUI.openInventory(player);
                });
                return;
            }

            // 使用调度器在主线程中执行GUI操作
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                blockSelectionGUI.handleSearch(player, input);
                setPlayerSearching(player, false);
            });
        }
    }

    @EventHandler
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        if (event.getView().getTitle().equals(plugin.getMessage("gui.title"))) {
            event.setCancelled(true);
            
            // 使用computeIfAbsent来确保线程安全的获取上次打开时间
            long openTime = lastGuiOpenTime.computeIfAbsent(player.getUniqueId(), k -> 0L);
            long currentTime = System.currentTimeMillis();
            
            if (currentTime - openTime < GUI_PROTECTION_TIME) {
                return;
            }

            ItemStack clickedItem = event.getCurrentItem();
            if (clickedItem != null && clickedItem.getType() == Material.COMPASS) {
                // 使用原子操作检查冷却时间
                Long lastClick = lastSearchClickTime.get(player.getUniqueId());
                if (lastClick != null && currentTime - lastClick < SEARCH_CLICK_COOLDOWN) {
                    plugin.sendMessage(player, "messages.wait-cooldown");
                    return;
                }
                lastSearchClickTime.put(player.getUniqueId(), currentTime);
                
                player.closeInventory();
                plugin.sendMessage(player, "messages.search-prompt");
                setPlayerSearching(player, true);
                return;
            }
            
            blockSelectionGUI.handleInventoryClick(event, player);
        }
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        
        if (event.getView().getTitle().equals("魔法方块选择") && !isPlayerSearching(player)) {
            // 只有在不是因为搜索而关闭GUI时才清理数据
            blockSelectionGUI.clearPlayerData(player.getUniqueId());
        }
    }
} 
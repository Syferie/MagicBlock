package io.github.syferie.magicblock.gui;

import com.tcoded.folialib.FoliaLib;
import io.github.syferie.magicblock.MagicBlockPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class GUIManager implements Listener {
    private final MagicBlockPlugin plugin;
    private final BlockSelectionGUI blockSelectionGUI;
    private static final Map<UUID, Boolean> searchingPlayers = new ConcurrentHashMap<>();
    private static final Map<UUID, Boolean> updatingGUI = new ConcurrentHashMap<>();
    private static final long GUI_CLICK_COOLDOWN = 300;
    private static final Map<UUID, Long> lastSearchClickTime = new ConcurrentHashMap<>();
    private static final long SEARCH_CLICK_COOLDOWN = 600;
    private static final Map<UUID, Long> lastGuiOpenTime = new ConcurrentHashMap<>();
    private static final long GUI_PROTECTION_TIME = 200;
    private final FoliaLib foliaLib;

    public GUIManager(MagicBlockPlugin plugin, List<Material> allowedMaterials) {
        this.plugin = plugin;
        this.blockSelectionGUI = new BlockSelectionGUI(plugin);
        this.foliaLib = plugin.getFoliaLib();
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

    public static void setPlayerUpdatingGUI(Player player, boolean updating) {
        if (updating) {
            updatingGUI.put(player.getUniqueId(), true);
        } else {
            updatingGUI.remove(player.getUniqueId());
        }
    }

    public static boolean isPlayerUpdatingGUI(Player player) {
        return updatingGUI.getOrDefault(player.getUniqueId(), false);
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
                // 使用FoliaLib在玩家实体上执行GUI操作
                foliaLib.getScheduler().runAtEntity(
                    player,
                    task -> blockSelectionGUI.openInventory(player)
                );
                return;
            }

            // 使用FoliaLib在玩家实体上执行搜索操作
            foliaLib.getScheduler().runAtEntity(
                player,
                task -> {
                    blockSelectionGUI.handleSearch(player, input);
                    setPlayerSearching(player, false);
                }
            );
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player)) return;
        Player player = (Player) event.getWhoClicked();
        
        // 检查是否是魔法方块选择GUI（使用配置中的标题）
        String guiTitle = ChatColor.stripColor(event.getView().getTitle());
        String configTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("gui.title", "&8⚡ &bMagicBlock选择")));

        if (!guiTitle.equals(configTitle)) {
            return;
        }

        // 立即取消事件，防止传播
        event.setCancelled(true);
            
        // 使用computeIfAbsent来确保线程安全的获取上次打开时间
        long openTime = lastGuiOpenTime.computeIfAbsent(player.getUniqueId(), k -> 0L);
        long currentTime = System.currentTimeMillis();
            
        if (currentTime - openTime < GUI_PROTECTION_TIME) {
            return;
        }

        ItemStack clickedItem = event.getCurrentItem();
        if (clickedItem == null || clickedItem.getType() == Material.AIR) {
            return;
        }

        // 检查是否是搜索按钮
        if (clickedItem.getType() == Material.COMPASS) {
            // 使用原子操作检查冷却时间
            Long lastClick = lastSearchClickTime.get(player.getUniqueId());
            if (lastClick != null && currentTime - lastClick < SEARCH_CLICK_COOLDOWN) {
                plugin.sendMessage(player, "messages.wait-cooldown");
                return;
            }
            lastSearchClickTime.put(player.getUniqueId(), currentTime);
        }
            
        // 使用FoliaLib确保在主线程执行GUI操作
        foliaLib.getScheduler().runAtEntity(
            player,
            task -> blockSelectionGUI.handleInventoryClick(event, player)
        );
    }

    @EventHandler
    public void onInventoryClose(InventoryCloseEvent event) {
        if (!(event.getPlayer() instanceof Player)) return;
        Player player = (Player) event.getPlayer();
        
        // 检查是否是魔法方块选择GUI（使用配置中的标题）
        String guiTitle = ChatColor.stripColor(event.getView().getTitle());
        String configTitle = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
            plugin.getConfig().getString("gui.title", "&8⚡ &bMagicBlock选择")));

        if (guiTitle.equals(configTitle) && !isPlayerSearching(player) && !isPlayerUpdatingGUI(player)) {
            // 只有在不是因为搜索或GUI更新而关闭GUI时才清理数据
            blockSelectionGUI.clearPlayerData(player.getUniqueId());
        }
    }
} 
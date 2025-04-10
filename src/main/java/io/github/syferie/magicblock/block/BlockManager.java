package io.github.syferie.magicblock.block;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.api.IMagicBlock;
import io.github.syferie.magicblock.util.Constants;
import io.github.syferie.magicblock.util.MessageUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.Objects;
import java.util.Set;

public class BlockManager implements IMagicBlock {
    private final MagicBlockPlugin plugin;
    private final NamespacedKey useTimesKey;
    private final NamespacedKey maxTimesKey;

    public BlockManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
        this.useTimesKey = new NamespacedKey(plugin, Constants.BLOCK_TIMES_KEY);
        this.maxTimesKey = new NamespacedKey(plugin, "magicblock_maxtimes");
    }

    @Override
    public void setUseTimes(ItemStack item, int times) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        // 设置当前使用次数
        if (times == -1) {
            // 如果是无限次数，设置一个非常大的值（20亿次）
            int infiniteValue = Integer.MAX_VALUE - 100;
            meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, infiniteValue);
            meta.getPersistentDataContainer().set(maxTimesKey, PersistentDataType.INTEGER, infiniteValue);
        } else {
            meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, times);
            // 如果最大使用次数还没有设置，才设置它
            if (!meta.getPersistentDataContainer().has(maxTimesKey, PersistentDataType.INTEGER)) {
                meta.getPersistentDataContainer().set(maxTimesKey, PersistentDataType.INTEGER, times);
            }
        }

        // 更新物品说明
        updateLore(item, times == -1 ? Integer.MAX_VALUE - 100 : times);
        item.setItemMeta(meta);
    }

    @Override
    public int getUseTimes(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        return container.getOrDefault(useTimesKey, PersistentDataType.INTEGER, 0);
    }

    @Override
    public int decrementUseTimes(ItemStack item) {
        int currentTimes = getUseTimes(item);
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return currentTimes;

        // 正常减少次数
        currentTimes--;
        PersistentDataContainer container = meta.getPersistentDataContainer();
        container.set(useTimesKey, PersistentDataType.INTEGER, currentTimes);
        item.setItemMeta(meta);

        // 检查是否是"无限"次数（大数值）
        int maxTimes = getMaxUseTimes(item);
        if (maxTimes == Integer.MAX_VALUE - 100) {
            updateLore(item, currentTimes);
            return currentTimes;
        }

        // 如果是绑定的方块，更新配置文件
        if (isBlockBound(item)) {
            UUID boundPlayer = getBoundPlayer(item);
            if (boundPlayer != null) {
                String uuid = boundPlayer.toString();
                if (plugin.getBlockBindManager().getBindConfig().contains("bindings." + uuid)) {
                    Set<String> blocks = Objects.requireNonNull(plugin.getBlockBindManager().getBindConfig()
                            .getConfigurationSection("bindings." + uuid)).getKeys(false);
                    for (String blockId : blocks) {
                        String path = "bindings." + uuid + "." + blockId;
                        String material = plugin.getBlockBindManager().getBindConfig().getString(path + ".material");
                        if (material != null && material.equals(item.getType().name())) {
                            // 更新使用次数
                            plugin.getBlockBindManager().getBindConfig().set(path + ".uses", currentTimes);
                            plugin.getBlockBindManager().saveBindConfig();
                            break;
                        }
                    }
                }
            }
        }

        updateLore(item, currentTimes);
        return currentTimes;
    }

    public void setInfiniteUse(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        int defaultTimes = plugin.getDefaultBlockTimes();
        meta.getPersistentDataContainer().set(useTimesKey, PersistentDataType.INTEGER, defaultTimes);
        item.setItemMeta(meta);
    }

    @Override
    public void updateLore(ItemStack item, int remainingTimes) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        List<String> lore = new ArrayList<>();

        // 获取物品的最大使用次数
        int maxTimes = getMaxUseTimes(item);
        if (maxTimes <= 0) return;

        // 检查是否是"无限"次数（大数值）
        boolean isInfinite = maxTimes == Integer.MAX_VALUE - 100;

        // 添加魔法方块标识
        lore.add(plugin.getMagicLore());

        // 获取物品所有者（如果已绑定）用于PAPI变量解析
        Player owner = null;
        UUID boundPlayer = null;
        if (isBlockBound(item)) {
            boundPlayer = getBoundPlayer(item);
            if (boundPlayer != null) {
                owner = Bukkit.getPlayer(boundPlayer);
            }
        }

        // 添加装饰性lore（如果启用）
        if (plugin.getConfig().getBoolean("display.decorative-lore.enabled", true)) {
            List<String> configLore = plugin.getConfig().getStringList("display.decorative-lore.lines");
            for (String line : configLore) {
                String processedLine = ChatColor.translateAlternateColorCodes('&', line);
                // 如果服务器安装了PlaceholderAPI，处理变量
                if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null) {
                    // 将当前物品的使用次数信息传递给PAPI处理器
                    // 这样即使进度条显示被禁用，仍然可以通过PAPI变量使用进度条
                    processedLine = me.clip.placeholderapi.PlaceholderAPI.setPlaceholders(owner, processedLine);
                }
                lore.add(processedLine);
            }
        }

        // 添加绑定信息（如果启用且已绑定）
        if (plugin.getConfig().getBoolean("display.show-info.bound-player", true) && boundPlayer != null) {
            String bindLorePrefix = plugin.getBlockBindManager().getBindLorePrefix();
            if (owner != null) {
                lore.add(bindLorePrefix + owner.getName());
            } else {
                lore.add(bindLorePrefix + boundPlayer.toString());
            }
        }

        // 添加使用次数（如果启用）
        if (plugin.getConfig().getBoolean("display.show-info.usage-count", true)) {
            StringBuilder usageText = new StringBuilder();
            usageText.append(ChatColor.GRAY).append(plugin.getUsageLorePrefix()).append(" ");
            if (isInfinite) {
                usageText.append(ChatColor.AQUA).append("∞")
                        .append(ChatColor.GRAY).append("/")
                        .append(ChatColor.GRAY).append("∞");
            } else {
                usageText.append(ChatColor.AQUA).append(remainingTimes)
                        .append(ChatColor.GRAY).append("/")
                        .append(ChatColor.GRAY).append(maxTimes);
            }
            lore.add(usageText.toString());
        }

        // 添加进度条（如果启用且不是无限次数）
        if (!isInfinite && plugin.getConfig().getBoolean("display.show-info.progress-bar", true)) {
            double usedPercentage = (double) remainingTimes / maxTimes;
            int barLength = 10;
            int filledBars = (int) Math.round(usedPercentage * barLength);

            StringBuilder progressBar = new StringBuilder();
            progressBar.append(ChatColor.GRAY).append("[");
            for (int i = 0; i < barLength; i++) {
                if (i < filledBars) {
                    progressBar.append(ChatColor.GREEN).append("■");
                } else {
                    progressBar.append(ChatColor.GRAY).append("■");
                }
            }
            progressBar.append(ChatColor.GRAY).append("]");
            lore.add(progressBar.toString());
        }

        meta.setLore(lore);
        item.setItemMeta(meta);
    }

    public boolean isMagicBlock(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        ItemMeta meta = item.getItemMeta();
        // 使用插件的hasMagicLore方法进行检查，该方法已经增强以处理格式代码
        return plugin.hasMagicLore(meta);
    }

    public boolean isBlockBound(ItemStack item) {
        return plugin.getBlockBindManager().isBlockBound(item);
    }

    public UUID getBoundPlayer(ItemStack item) {
        return plugin.getBlockBindManager().getBoundPlayer(item);
    }

    public void setMaxUseTimes(ItemStack item, int maxTimes) {
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return;

        meta.getPersistentDataContainer().set(maxTimesKey, PersistentDataType.INTEGER, maxTimes);
        item.setItemMeta(meta);
    }

    public int getMaxUseTimes(ItemStack item) {
        if (!isMagicBlock(item)) return 0;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return 0;

        PersistentDataContainer container = meta.getPersistentDataContainer();
        Integer maxTimes = container.get(maxTimesKey, PersistentDataType.INTEGER);

        // 如果没有存储的最大次数，则使用默认值
        if (maxTimes == null) {
            maxTimes = plugin.getDefaultBlockTimes();
            // 存储默认值作为最大次数
            setMaxUseTimes(item, maxTimes);
        }

        return maxTimes;
    }
}
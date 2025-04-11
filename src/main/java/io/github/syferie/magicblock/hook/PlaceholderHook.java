package io.github.syferie.magicblock.hook;

import io.github.syferie.magicblock.MagicBlockPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
import org.bukkit.inventory.ItemStack;
import org.jetbrains.annotations.NotNull;

public class PlaceholderHook extends PlaceholderExpansion {

    private final MagicBlockPlugin plugin;

    public PlaceholderHook(MagicBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public @NotNull String getIdentifier() {
        return "magicblock";
    }

    @Override
    public @NotNull String getAuthor() {
        return "WeSif";
    }

    @Override
    public @NotNull String getVersion() {
        return plugin.getDescription().getVersion();
    }

    @Override
    public boolean persist() {
        return true;
    }

    @Override
    public String onRequest(OfflinePlayer player, @NotNull String params) {
        if (player == null) return "";

        // 获取玩家使用魔法方块的总次数
        if (params.equalsIgnoreCase("block_uses")) {
            return String.valueOf(plugin.getPlayerUsage(player.getUniqueId()));
        }

        // 获取玩家使用魔法食物的总次数
        if (params.equalsIgnoreCase("food_uses")) {
            if (plugin.getMagicFood() != null) {
                return String.valueOf(plugin.getMagicFood().getFoodUses(player.getUniqueId()));
            }
            return "0";
        }

        // 获取玩家剩余的魔法方块使用次数
        if (params.equalsIgnoreCase("remaining_uses")) {
            if (player.isOnline() && player.getPlayer() != null) {
                ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                if (plugin.getBlockManager().getUseTimes(item) > 0) {
                    return String.valueOf(plugin.getBlockManager().getUseTimes(item));
                }
            }
            return "0";
        }

        // 获取玩家是否持有魔法方块
        if (params.equalsIgnoreCase("has_block")) {
            if (player.isOnline() && player.getPlayer() != null) {
                ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                return String.valueOf(plugin.getBlockManager().isMagicBlock(item));
            }
            return "false";
        }

        // 获取玩家是否持有魔法食物
        if (params.equalsIgnoreCase("has_food")) {
            if (player.isOnline() && player.getPlayer() != null && plugin.getMagicFood() != null) {
                ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                return String.valueOf(plugin.getMagicFood().isMagicFood(item));
            }
            return "false";
        }

        // 获取玩家魔法方块的最大使用次数
        if (params.equalsIgnoreCase("max_uses")) {
            if (player.isOnline() && player.getPlayer() != null) {
                ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                if (plugin.getBlockManager().isMagicBlock(item)) {
                    return String.valueOf(plugin.getBlockManager().getMaxUseTimes(item));
                }
            }
            return "0";
        }

        // 获取玩家魔法方块的使用进度(百分比)
        if (params.equalsIgnoreCase("uses_progress")) {
            if (player.isOnline() && player.getPlayer() != null) {
                ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                if (plugin.getBlockManager().isMagicBlock(item)) {
                    int maxUses = plugin.getBlockManager().getMaxUseTimes(item);
                    int remainingUses = plugin.getBlockManager().getUseTimes(item);
                    if (maxUses > 0) {
                        double progress = ((double)(maxUses - remainingUses) / maxUses) * 100;
                        return String.format("%.1f", progress);
                    }
                }
            }
            return "0.0";
        }

        // 获取玩家魔法方块的进度条
        if (params.equalsIgnoreCase("progress_bar") || params.equalsIgnoreCase("progressbar")) {
            if (player.isOnline() && player.getPlayer() != null) {
                ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                if (plugin.getBlockManager().isMagicBlock(item)) {
                    int maxUses = plugin.getBlockManager().getMaxUseTimes(item);
                    int remainingUses = plugin.getBlockManager().getUseTimes(item);

                    // 检查是否是无限次数
                    if (maxUses == Integer.MAX_VALUE - 100) {
                        return "&a∞"; // 无限符号
                    }

                    if (maxUses > 0) {
                        // 使用插件的进度条生成方法
                        return plugin.getProgressBar(remainingUses, maxUses);
                    }
                }
            }
            return "&7无进度条"; // 默认返回空进度条
        }

        // 获取自定义长度的进度条
        if (params.startsWith("progress_bar_") || params.startsWith("progressbar_")) {
            try {
                // 从参数中提取进度条长度
                int barLength = Integer.parseInt(params.substring(params.lastIndexOf('_') + 1));
                if (barLength <= 0) barLength = 10; // 默认长度

                if (player.isOnline() && player.getPlayer() != null) {
                    ItemStack item = player.getPlayer().getInventory().getItemInMainHand();
                    if (plugin.getBlockManager().isMagicBlock(item)) {
                        int maxUses = plugin.getBlockManager().getMaxUseTimes(item);
                        int remainingUses = plugin.getBlockManager().getUseTimes(item);

                        // 检查是否是无限次数
                        if (maxUses == Integer.MAX_VALUE - 100) {
                            return "&a∞"; // 无限符号
                        }

                        if (maxUses > 0) {
                            // 生成自定义长度的进度条
                            double usedPercentage = (double) remainingUses / maxUses;
                            int filledBars = (int) Math.round(usedPercentage * barLength);

                            StringBuilder progressBar = new StringBuilder("&a");
                            for (int i = 0; i < barLength; i++) {
                                if (i < filledBars) {
                                    progressBar.append("■"); // 实心方块
                                } else {
                                    progressBar.append("□"); // 空心方块
                                }
                            }
                            return progressBar.toString();
                        }
                    }
                }
                return "&7" + "□".repeat(barLength); // 默认返回空进度条
            } catch (NumberFormatException e) {
                return "&c无效的进度条长度";
            }
        }

        return null;
    }
}
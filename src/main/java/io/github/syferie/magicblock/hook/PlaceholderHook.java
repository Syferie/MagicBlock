package io.github.syferie.magicblock.hook;

import io.github.syferie.magicblock.MagicBlockPlugin;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.OfflinePlayer;
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
                var item = player.getPlayer().getInventory().getItemInMainHand();
                if (plugin.getBlockManager().getUseTimes(item) > 0) {
                    return String.valueOf(plugin.getBlockManager().getUseTimes(item));
                }
            }
            return "0";
        }

        // 获取玩家是否持有魔法方块
        if (params.equalsIgnoreCase("has_block")) {
            if (player.isOnline() && player.getPlayer() != null) {
                var item = player.getPlayer().getInventory().getItemInMainHand();
                return String.valueOf(plugin.getBlockManager().isMagicBlock(item));
            }
            return "false";
        }

        // 获取玩家是否持有魔法食物
        if (params.equalsIgnoreCase("has_food")) {
            if (player.isOnline() && player.getPlayer() != null && plugin.getMagicFood() != null) {
                var item = player.getPlayer().getInventory().getItemInMainHand();
                return String.valueOf(plugin.getMagicFood().isMagicFood(item));
            }
            return "false";
        }

        // 获取玩家魔法方块的最大使用次数
        if (params.equalsIgnoreCase("max_uses")) {
            if (player.isOnline() && player.getPlayer() != null) {
                var item = player.getPlayer().getInventory().getItemInMainHand();
                if (plugin.getBlockManager().isMagicBlock(item)) {
                    return String.valueOf(plugin.getBlockManager().getMaxUseTimes(item));
                }
            }
            return "0";
        }

        // 获取玩家魔法方块的使用进度(百分比)
        if (params.equalsIgnoreCase("uses_progress")) {
            if (player.isOnline() && player.getPlayer() != null) {
                var item = player.getPlayer().getInventory().getItemInMainHand();
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

        return null;
    }
} 
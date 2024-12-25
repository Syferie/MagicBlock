package io.github.syferie.magicblock.command;

import io.github.syferie.magicblock.MagicBlockPlugin;
import io.github.syferie.magicblock.block.BlockManager;
import io.github.syferie.magicblock.food.FoodService;
import me.clip.placeholderapi.PlaceholderAPI;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.ConsoleCommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CommandManager implements CommandExecutor {
    private final MagicBlockPlugin plugin;

    public CommandManager(MagicBlockPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            if (sender instanceof Player) {
                sendHelpMessage((Player) sender);
            } else {
                sender.sendMessage(ChatColor.RED + "用法: /mb give <玩家> [次数]");
            }
            return true;
        }

        switch (args[0].toLowerCase()) {
            case "help":
                if (sender instanceof Player) {
                    sendHelpMessage((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
                }
                break;
            case "list":
                if (sender instanceof Player) {
                    handleList((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
                }
                break;
            case "get":
                if (sender instanceof Player) {
                    handleGet((Player) sender, args);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
                }
                break;
            case "reload":
                handleReload(sender);
                break;
            case "settimes":
                if (sender instanceof Player) {
                    handleSetTimes((Player) sender, args);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
                }
                break;
            case "addtimes":
                if (sender instanceof Player) {
                    handleAddTimes((Player) sender, args);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
                }
                break;
            case "getfood":
                if (sender instanceof Player) {
                    handleGetFood((Player) sender, args);
                } else {
                    sender.sendMessage(ChatColor.RED + "此命令只能由玩家执行。");
                }
                break;
            case "give":
                handleGive(sender, args);
                break;
            default:
                if (sender instanceof Player) {
                    sendHelpMessage((Player) sender);
                } else {
                    sender.sendMessage(ChatColor.RED + "未知的命令。");
                }
                break;
        }

        return true;
    }

    private void sendHelpMessage(Player player) {
        plugin.sendMessage(player, "commands.help.title");
        plugin.sendMessage(player, "commands.help.help");
        plugin.sendMessage(player, "commands.help.get");
        plugin.sendMessage(player, "commands.help.give");
        plugin.sendMessage(player, "commands.help.getfood");
        plugin.sendMessage(player, "commands.help.settimes");
        plugin.sendMessage(player, "commands.help.addtimes");
        plugin.sendMessage(player, "commands.help.list");
        if (player.hasPermission("magicblock.reload")) {
            plugin.sendMessage(player, "commands.help.reload");
        }
        plugin.sendMessage(player, "commands.help.tip");
    }

    private void handleGive(CommandSender sender, String[] args) {
        if (!sender.hasPermission("magicblock.give")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令。");
            return;
        }

        if (args.length < 2) {
            sender.sendMessage(ChatColor.RED + "用法: /mb give <玩家> [次数]");
            return;
        }

        // 处理变量替换
        String targetPlayerName = args[1];
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && sender instanceof Player) {
            targetPlayerName = PlaceholderAPI.setPlaceholders((Player) sender, targetPlayerName);
        }

        Player target = Bukkit.getPlayer(targetPlayerName);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "找不到玩家: " + targetPlayerName);
            return;
        }

        String timesArg = args.length > 2 ? args[2] : String.valueOf(plugin.getDefaultBlockTimes());
        // 如果有PlaceholderAPI，处理次数中的变量
        if (Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null && sender instanceof Player) {
            timesArg = PlaceholderAPI.setPlaceholders((Player) sender, timesArg);
        }

        int times;
        try {
            times = Integer.parseInt(timesArg);
            // 如果指定-1，则设置为无限次数
            if (times != -1 && times <= 0) {
                sender.sendMessage(ChatColor.RED + "无效的次数: " + timesArg + "，使用默认值。");
                times = plugin.getDefaultBlockTimes();
            }
        } catch (NumberFormatException e) {
            sender.sendMessage(ChatColor.RED + "无效的次数: " + timesArg + "，使用默认值。");
            times = plugin.getDefaultBlockTimes();
        }

        ItemStack specialBlock = plugin.createMagicBlock();
        plugin.getBlockManager().setUseTimes(specialBlock, times);
        plugin.getBlockManager().updateLore(specialBlock, times);

        target.getInventory().addItem(specialBlock);
        if (times == -1) {
            plugin.sendMessage(target, "commands.get.success-infinite");
        } else {
            plugin.sendMessage(target, "commands.get.success", times);
        }
        
        // 根据发��者类型显示不同的消息
        if (sender instanceof ConsoleCommandSender) {
            if (times == -1) {
                plugin.sendMessage(sender, "commands.give.success.console-infinite", target.getName());
            } else {
                plugin.sendMessage(sender, "commands.give.success.console", target.getName(), times);
            }
        } else if (sender instanceof Player && sender != target) {
            if (times == -1) {
                plugin.sendMessage(sender, "commands.give.success.player-infinite", target.getName());
            } else {
                plugin.sendMessage(sender, "commands.give.success.player", target.getName(), times);
            }
        }
    }

    private void handleGet(Player player, String[] args) {
        if (!player.hasPermission("magicblock.get")) {
            plugin.sendMessage(player, "commands.get.no-permission");
            return;
        }

        int times = plugin.getDefaultBlockTimes();
        if (args.length > 1) {
            try {
                times = Integer.parseInt(args[1]);
                // 如果指定-1，则设置为无限次数
                if (times != -1 && times <= 0) {
                    plugin.sendMessage(player, "commands.get.invalid-number");
                    times = plugin.getDefaultBlockTimes();
                }
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "commands.get.invalid-number");
                times = plugin.getDefaultBlockTimes();
            }
        }

        ItemStack specialBlock = plugin.createMagicBlock();
        plugin.getBlockManager().setUseTimes(specialBlock, times);
        plugin.getBlockManager().updateLore(specialBlock, times);

        player.getInventory().addItem(specialBlock);
        if (times == -1) {
            plugin.sendMessage(player, "commands.get.success-infinite");
        } else {
            plugin.sendMessage(player, "commands.get.success", times);
        }
    }

    private void handleGetFood(Player player, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "commands.getfood.usage");
            return;
        }

        Material material;
        try {
            material = Material.valueOf(args[1].toUpperCase());
        } catch (IllegalArgumentException e) {
            plugin.sendMessage(player, "commands.getfood.invalid-food");
            return;
        }

        ItemStack magicFood = plugin.getMagicFood().createMagicFood(material);
        if (magicFood == null) {
            plugin.sendMessage(player, "commands.getfood.invalid-food");
            return;
        }

        int times = 100000000;
        if (args.length >= 3) {
            try {
                times = Integer.parseInt(args[2]);
            } catch (NumberFormatException e) {
                plugin.sendMessage(player, "commands.getfood.invalid-number");
                times = plugin.getDefaultBlockTimes();
            }
        }

        plugin.getMagicFood().setUseTimes(magicFood, times);
        plugin.getMagicFood().updateLore(magicFood, times);

        player.getInventory().addItem(magicFood);
        plugin.sendMessage(player, "commands.getfood.success", times);
    }

    private void handleReload(CommandSender sender) {
        if (!sender.hasPermission("magicblock.reload")) {
            plugin.sendMessage(sender, "commands.reload.no-permission");
            return;
        }

        plugin.reloadConfig();
        plugin.sendMessage(sender, "commands.reload.success");
    }

    private void handleSetTimes(Player player, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "commands.settimes.usage");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.hasMagicLore(item.getItemMeta())) {
            plugin.sendMessage(player, "commands.settimes.must-hold");
            return;
        }

        int times;
        try {
            times = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "commands.settimes.invalid-number");
            return;
        }

        plugin.getBlockManager().setUseTimes(item, times);
        plugin.sendMessage(player, "commands.settimes.success", times);
    }

    private void handleAddTimes(Player player, String[] args) {
        if (args.length < 2) {
            plugin.sendMessage(player, "commands.addtimes.usage");
            return;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (!plugin.hasMagicLore(item.getItemMeta())) {
            plugin.sendMessage(player, "commands.addtimes.must-hold");
            return;
        }

        int addTimes;
        try {
            addTimes = Integer.parseInt(args[1]);
        } catch (NumberFormatException e) {
            plugin.sendMessage(player, "commands.addtimes.invalid-number");
            return;
        }

        BlockManager blockManager = plugin.getBlockManager();
        int currentTimes = blockManager.getUseTimes(item);
        if (currentTimes == -1) {
            plugin.sendMessage(player, "commands.addtimes.unlimited");
            return;
        }

        int newTimes = currentTimes + addTimes;
        blockManager.setUseTimes(item, newTimes);
        plugin.sendMessage(player, "commands.addtimes.success", addTimes, newTimes);
    }

    private void handleList(Player player) {
        if (!player.hasPermission("magicblock.list")) {
            plugin.sendMessage(player, "commands.list.no-permission");
            return;
        }

        plugin.getBlockBindManager().openBindList(player);
    }
}

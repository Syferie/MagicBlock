package io.github.syferie.magicblock.command.handler;

import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

public class TabCompleter implements org.bukkit.command.TabCompleter {
    private final List<String> commands = Arrays.asList("get", "reload", "settimes", "addtimes", "getfood", "help", "give", "list");

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> completions = new ArrayList<>();
        
        if (command.getName().equalsIgnoreCase("magicblock")) {
            if (args.length == 1) {
                // 基础命令
                List<String> availableCommands = new ArrayList<>();
                availableCommands.add("help");
                
                // 根据权限添加命令
                if (sender.hasPermission("magicblock.get")) {
                    availableCommands.add("get");
                }
                if (sender.hasPermission("magicblock.give")) {
                    availableCommands.add("give");
                }
                if (sender.hasPermission("magicblock.getfood")) {
                    availableCommands.add("getfood");
                }
                if (sender.hasPermission("magicblock.settimes")) {
                    availableCommands.add("settimes");
                }
                if (sender.hasPermission("magicblock.addtimes")) {
                    availableCommands.add("addtimes");
                }
                if (sender.hasPermission("magicblock.list")) {
                    availableCommands.add("list");
                }
                if (sender.hasPermission("magicblock.reload")) {
                    availableCommands.add("reload");
                }

                // 过滤并返回匹配的命令
                String input = args[0].toLowerCase();
                completions.addAll(availableCommands.stream()
                    .filter(cmd -> cmd.startsWith(input))
                    .collect(Collectors.toList()));
            } else if (args.length == 2) {
                // 针对特定命令的第二个参数提供补全
                switch (args[0].toLowerCase()) {
                    case "give":
                        if (sender.hasPermission("magicblock.give")) {
                            // 返回在线玩家列表
                            String input = args[1].toLowerCase();
                            completions.addAll(Bukkit.getOnlinePlayers().stream()
                                .map(Player::getName)
                                .filter(name -> name.toLowerCase().startsWith(input))
                                .collect(Collectors.toList()));
                        }
                        break;
                    case "getfood":
                        if (sender.hasPermission("magicblock.getfood")) {
                            // 这里可以添加食物类型的补全
                            completions.addAll(Arrays.asList("BREAD", "COOKED_BEEF", "GOLDEN_APPLE", "APPLE"));
                        }
                        break;
                }
            }
        }
        
        return completions;
    }
}






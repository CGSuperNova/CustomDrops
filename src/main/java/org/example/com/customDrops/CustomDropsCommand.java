package org.example.com.customDrops;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class CustomDropsCommand implements CommandExecutor, TabCompleter {

    private final CustomDropsPlugin plugin;

    public CustomDropsCommand(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "用法: /customdrops <reload|list>");
            return true;
        }

        if (args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("customdrops.reload")) {
                sender.sendMessage(ChatColor.RED + "你没有权限！");
                return true;
            }
            plugin.reloadConfigData();
            sender.sendMessage(ChatColor.GREEN + "配置已重载！");
            if (plugin.isCheckUpdate()) {
                sender.sendMessage(ChatColor.YELLOW + "正在检查更新...");
                UpdateChecker.checkUpdateAsync(plugin);
            }
            return true;
        }

        if (args[0].equalsIgnoreCase("list")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("该命令仅支持玩家执行");
                return true;
            }
            Player player = (Player) sender;
            int page = 1;
            if (args.length >= 2) {
                try {
                    page = Integer.parseInt(args[1]);
                } catch (NumberFormatException e) {
                    player.sendMessage(ChatColor.RED + "页码必须是数字");
                    return true;
                }
            }
            sendList(player, page);
            return true;
        }

        sender.sendMessage(ChatColor.RED + "未知子命令，可用: reload, list");
        return true;
    }

    private void sendList(Player player, int page) {
        Map<Material, BlockDropConfig> configs = plugin.getDropConfigManager().getAllConfigs(); // 需要暴露此方法
        if (configs.isEmpty()) {
            player.sendMessage(ChatColor.YELLOW + "当前没有任何方块配置");
            return;
        }

        List<Material> sortedMaterials = configs.keySet().stream()
                .sorted(Comparator.comparing(Enum::name))
                .collect(Collectors.toList());

        int pageSize = 10;
        int totalPages = (int) Math.ceil(sortedMaterials.size() / (double) pageSize);
        if (page < 1) page = 1;
        if (page > totalPages) page = totalPages;

        int start = (page - 1) * pageSize;
        int end = Math.min(start + pageSize, sortedMaterials.size());

        player.sendMessage(ChatColor.GOLD + "========== CustomDrops 配置列表 (" + page + "/" + totalPages + ") ==========");
        for (int i = start; i < end; i++) {
            Material mat = sortedMaterials.get(i);
            BlockDropConfig cfg = configs.get(mat);
            int dropCount = cfg.getDrops().size();
            player.sendMessage(ChatColor.AQUA + mat.name() +
                    ChatColor.GRAY + " - 覆盖原版: " + (cfg.isOverrideDefault() ? ChatColor.GREEN + "是" : ChatColor.RED + "否") +
                    ChatColor.GRAY + ", 掉落项: " + ChatColor.WHITE + dropCount);
        }
        if (totalPages > 1) {
            player.sendMessage(ChatColor.GRAY + "使用 /customdrops list <页码> 翻页");
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("reload", "list").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase()))
                    .collect(Collectors.toList());
        }
        return Collections.emptyList();
    }
}
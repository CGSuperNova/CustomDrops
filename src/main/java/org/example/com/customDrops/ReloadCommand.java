package org.example.com.customDrops;

import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class ReloadCommand implements CommandExecutor {

    private final CustomDropsPlugin plugin;

    public ReloadCommand(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("customdrops.reload")) {
            sender.sendMessage(ChatColor.RED + "你没有权限使用此命令！");
            return true;
        }
        plugin.reloadConfigData();  // 此方法会重新加载配置并更新 debug
        sender.sendMessage(ChatColor.GREEN + "自定义掉落配置已重载！当前调试模式: " +
                (plugin.isDebug() ? ChatColor.YELLOW + "开启" : ChatColor.GRAY + "关闭"));
        return true;
    }
}
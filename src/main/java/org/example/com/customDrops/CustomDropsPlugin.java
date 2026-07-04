package org.example.com.customDrops;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public class CustomDropsPlugin extends JavaPlugin {

    private DropConfigManager configManager;
    private boolean debug = false;
    private boolean checkUpdate = true;   // 默认开启

    @Override
    public void onEnable() {
        saveDefaultConfig();
        configManager = new DropConfigManager(this);
        reloadConfigData();  // 加载所有配置，包括 debug 和 check-update

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getCommand("customdrops").setExecutor(new ReloadCommand(this));

        // 如果开启更新检测，异步执行一次
        if (checkUpdate) {
            UpdateChecker.checkUpdateAsync(this);
        } else {
            getLogger().info("更新检测已禁用（配置 check-update: false）");
        }

        getLogger().info("自定义掉落插件已启用");
    }

    @Override
    public void onDisable() {
        getLogger().info("自定义掉落插件已禁用");
    }

    /**
     * 重新加载配置（包括 debug 和 check-update 标志）
     */
    public void reloadConfigData() {
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
        checkUpdate = getConfig().getBoolean("check-update", true);
        configManager.loadConfig();
    }

    public DropConfigManager getDropConfigManager() {
        return configManager;
    }

    public boolean isDebug() {
        return debug;
    }

    public boolean isCheckUpdate() {
        return checkUpdate;
    }
}
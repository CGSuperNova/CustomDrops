package org.example.com.customDrops;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bstats.bukkit.Metrics;

public class CustomDropsPlugin extends JavaPlugin {

    private DropConfigManager configManager;
    private boolean debug = false;
    private boolean checkUpdate = true;
    private Economy economy = null;
    private boolean papiAvailable = false;

    @Override
    public void onEnable() {
        // 现有的初始化...
        saveDefaultConfig();
        configManager = new DropConfigManager(this);
        reloadConfigData();

        // bStats服务
        int pluginId = 32385;
        Metrics metrics = new Metrics(this, pluginId);

        // 注册 Vault 经济
        setupEconomy();

        // 检测 PlaceholderAPI 是否加载
        papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (papiAvailable) {
            getLogger().info("PlaceholderAPI 已加载，控制台命令中的占位符将自动解析");
        } else {
            getLogger().info("PlaceholderAPI 未安装，控制台命令中的 PAPI 占位符将不会被解析");
        }

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getCommand("customdrops").setExecutor(new CustomDropsCommand(this));

        if (checkUpdate) {
            UpdateChecker.checkUpdateAsync(this);
        }

        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        getCommand("customdrops").setExecutor(new CustomDropsCommand(this));

        if (checkUpdate) {
            UpdateChecker.checkUpdateAsync(this);
        }

        getLogger().info("自定义掉落插件已启用");
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault 未安装，金币功能将不可用");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("没有找到经济插件（如 EssentialsX）");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("成功连接到经济系统");
    }

    public Economy getEconomy() {
        return economy;
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

    public boolean isPapiAvailable() {
        return papiAvailable;
    }
}
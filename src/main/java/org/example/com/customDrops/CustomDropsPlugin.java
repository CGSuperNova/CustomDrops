package org.example.com.customDrops;

import net.milkbowl.vault.economy.Economy;
import org.bstats.bukkit.Metrics;
import org.bukkit.Bukkit;
import org.bukkit.command.PluginCommand;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * CustomDrops 主类。
 * <p>
 * 1.1.0 修复：旧版在 {@code onEnable()} 中把监听器与命令执行器各注册了两次
 * （导致方块破坏逻辑被重复执行、命令被重复处理），现已修正。
 */
public class CustomDropsPlugin extends JavaPlugin {

    private static final String DEFAULT_REPOSITORY = UpdateChecker.DEFAULT_REPOSITORY;

    private DropConfigManager configManager;
    private UndoManager undoManager;
    private CustomDropsExpansion expansion;

    private boolean debug = false;
    private boolean checkUpdate = true;
    private String updateRepository = DEFAULT_REPOSITORY;

    private Economy economy = null;
    private boolean papiAvailable = false;

    /**
     * 本版本引入 bStats 开关时，若用户配置文件里还没有 metrics 键，
     * 沿用 1.0.x 的行为（启用 bStats），避免静默改变既有服务器的统计设置。
     */
    private boolean metricsDefault = true;

    /** 缓存的插件版本号，避免在热路径中访问已弃用的 PluginDescriptionFile */
    private String pluginVersion = "unknown";

    @Override
    public void onEnable() {
        pluginVersion = resolveVersion();

        // 判断配置文件是否已存在，用于决定新增 metrics 开关的默认值
        java.io.File configFile = new java.io.File(getDataFolder(), "config.yml");
        metricsDefault = !configFile.isFile();

        saveDefaultConfig();
        configManager = new DropConfigManager(this);
        undoManager = new UndoManager(this);
        undoManager.load();
        ConfigMigrator.migrate(this);
        if (getConfig().getBoolean("metrics", metricsDefault)) {
            try {
                new Metrics(this, 32385);
            } catch (Throwable throwable) {
                getLogger().warning("bStats 初始化失败: " + throwable.getMessage());
            }
        }
        saveConfig();
        reloadConfigData();

        setupEconomy();
        setupPlaceholderApi();

        // 事件与命令只注册一次
        getServer().getPluginManager().registerEvents(new BlockBreakListener(this), this);
        registerCommand();

        if (checkUpdate) {
            UpdateChecker.start(this);
        }

        getLogger().info("CustomDrops v" + pluginVersion + " 已启用（已加载 "
                + configManager.getBlockCount() + " 个方块配置，启用世界 "
                + configManager.getEnabledWorlds() + "）");
    }

    private void registerCommand() {
        PluginCommand command = getCommand("customdrops");
        if (command == null) {
            getLogger().severe("plugin.yml 中缺少 customdrops 命令定义，命令功能不可用");
            return;
        }
        CustomDropsCommand executor = new CustomDropsCommand(this);
        command.setExecutor(executor);
        command.setTabCompleter(executor);
    }

    private void setupEconomy() {
        if (Bukkit.getPluginManager().getPlugin("Vault") == null) {
            getLogger().warning("Vault 未安装，金币功能将不可用");
            return;
        }
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp == null) {
            getLogger().warning("没有找到经济插件（如 EssentialsX），金币功能将不可用");
            return;
        }
        economy = rsp.getProvider();
        getLogger().info("成功连接到经济系统: " + economy.getName());
    }

    private void setupPlaceholderApi() {
        papiAvailable = Bukkit.getPluginManager().getPlugin("PlaceholderAPI") != null;
        if (!papiAvailable) {
            getLogger().info("PlaceholderAPI 未安装：控制台命令中的 PAPI 占位符不会被解析，"
                    + "本插件的 %customdrops_*% 变量也不可用");
            return;
        }
        getLogger().info("PlaceholderAPI 已加载，正在注册 %customdrops_*% 变量");
        try {
            expansion = new CustomDropsExpansion(this);
            if (expansion.register()) {
                getLogger().info("成功注册 " + expansion.getIdentifier() + " 变量扩展");
            } else {
                getLogger().warning("变量扩展注册失败（可能已被其它插件占用）");
            }
        } catch (Throwable throwable) {
            getLogger().warning("注册 PlaceholderAPI 变量扩展时出错: " + throwable.getMessage());
        }
    }

    /**
     * 获取插件版本号。
     * <p>
     * 优先使用较新服务端提供的 {@code getPluginMeta()}（避免使用已弃用 API），
     * 通过反射调用以便在旧服务端（无该方法）上自动回退到 {@code getDescription()}。
     */
    @SuppressWarnings("deprecation")
    private String resolveVersion() {
        try {
            Object meta = getClass().getMethod("getPluginMeta").invoke(this);
            if (meta != null) {
                Object version = meta.getClass().getMethod("getVersion").invoke(meta);
                if (version instanceof String text && !text.isBlank()) {
                    return text;
                }
            }
        } catch (Throwable ignored) {
            // 旧服务端没有 getPluginMeta()，走下面的回退分支
        }
        String version = getDescription().getVersion();
        return version == null || version.isBlank() ? "unknown" : version;
    }

    @Override
    public void onDisable() {
        UpdateChecker.stop();
        if (expansion != null) {
            try {
                expansion.unregister();
            } catch (Throwable ignored) {
                // 关服阶段忽略
            }
        }
        getLogger().info("CustomDrops 已禁用");
    }

    /**
     * 重新加载配置（包括 debug / check-update / update-check.repository 等开关）。
     */
    public void reloadConfigData() {
        reloadConfig();
        debug = getConfig().getBoolean("debug", false);
        checkUpdate = getConfig().getBoolean("check-update", true);

        String repository = getConfig().getString("update-check.repository", DEFAULT_REPOSITORY);
        if (repository == null || repository.isBlank() || repository.contains("你的用户名")) {
            repository = DEFAULT_REPOSITORY;
        }
        updateRepository = repository.trim().replaceAll("^/+|/+$", "");

        configManager.loadConfig();
    }

    /**
     * 重启周期更新检测（reload 后调用，避免任务堆积）。
     */
    public void restartUpdateChecker() {
        if (checkUpdate) {
            UpdateChecker.start(this);
        } else {
            UpdateChecker.stop();
        }
    }

    public DropConfigManager getDropConfigManager() {
        return configManager;
    }

    public UndoManager getUndoManager() {
        return undoManager;
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

    public Economy getEconomy() {
        return economy;
    }

    /** @return 缓存的插件版本号（如 {@code 1.1.0}） */
    public String getPluginVersion() {
        return pluginVersion;
    }

    /** @return 更新检测使用的 GitHub 仓库（{@code owner/repo}） */
    public String getUpdateRepository() {
        return updateRepository;
    }

    /** @return 更新检测页面地址 */
    public String getUpdatePageUrl() {
        return "https://github.com/" + updateRepository + "/releases/latest";
    }
}

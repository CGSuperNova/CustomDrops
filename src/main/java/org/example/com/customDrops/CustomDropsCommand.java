package org.example.com.customDrops;

import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * {@code /customdrops} 命令实现（1.1.0 扩充）。
 *
 * <pre>
 * /customdrops help                                   显示帮助
 * /customdrops reload                                 重载配置
 * /customdrops list [页码]                            列出已配置方块
 * /customdrops info &lt;方块id&gt;                          查看某方块的特殊掉落详情
 * /customdrops target &lt;方块id&gt; &lt;true|false&gt;           启用 / 禁用某方块的特殊掉落
 * /customdrops delete &lt;方块id&gt;                        删除某方块的特殊掉落配置
 * /customdrops set &lt;方块id&gt; &lt;选项&gt; [参数1] [参数2]     修改 / 新建某方块的特殊掉落配置
 * /customdrops undo [步数]                            撤回前几步命令操作（最多 5 步）
 * /customdrops placeholders                           查看可用 PAPI 变量
 * /customdrops update                                 手动检查更新
 * </pre>
 *
 * @see CustomDropsPlugin
 */
@SuppressWarnings("deprecation") // ChatColor 仍是跨 1.17 ~ 最新版本最通用的文本着色方案
public class CustomDropsCommand implements CommandExecutor, TabCompleter {

    private static final int LIST_PAGE_SIZE = 10;

    /** 方块级设置：名称 -> 需要的参数个数（0 表示无参数） */
    private static final Map<String, Integer> BLOCK_SETTINGS = new LinkedHashMap<>();
    /** 掉落项级设置：名称 -> 需要的参数个数 */
    private static final Map<String, Integer> DROP_SETTINGS = new LinkedHashMap<>();

    static {
        BLOCK_SETTINGS.put("enabled", 1);
        BLOCK_SETTINGS.put("override-default", 1);
        BLOCK_SETTINGS.put("silk-touch-preserve-original", 1);
        BLOCK_SETTINGS.put("add-drop", 1);
        BLOCK_SETTINGS.put("remove-drop", 1);
        BLOCK_SETTINGS.put("clear-drops", 0);

        DROP_SETTINGS.put("item", 1);
        DROP_SETTINGS.put("amount", 1);
        DROP_SETTINGS.put("chance", 1);
        DROP_SETTINGS.put("fortune-affects-count", 1);
        DROP_SETTINGS.put("fortune-chance-bonus", 1);
        DROP_SETTINGS.put("execute-command-per-item", 1);
        DROP_SETTINGS.put("silk-touch-ignore", 1);
        DROP_SETTINGS.put("exp", 1);
        DROP_SETTINGS.put("money", 1);
        DROP_SETTINGS.put("exp-multiplier", 1);
        DROP_SETTINGS.put("money-multiplier", 1);
        DROP_SETTINGS.put("permission", 1);
        DROP_SETTINGS.put("add-command", 1);
        DROP_SETTINGS.put("add-command-op", 1);
        DROP_SETTINGS.put("add-command-player", 1);
        DROP_SETTINGS.put("remove-command", 1);
        DROP_SETTINGS.put("clear-commands", 0);
    }

    private final CustomDropsPlugin plugin;

    /** 可选方块列表缓存（用于 Tab 补全） */
    private List<String> blockSuggestions;

    public CustomDropsCommand(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // 入口
    // ------------------------------------------------------------------

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "help":
                sendHelp(sender, label);
                return true;
            case "reload":
                return handleReload(sender);
            case "list":
                return handleList(sender, args);
            case "info":
                return handleInfo(sender, args);
            case "target":
                return handleTarget(sender, args);
            case "delete":
                return handleDelete(sender, args);
            case "set":
                return handleSet(sender, args);
            case "undo":
                return handleUndo(sender, args);
            case "placeholders":
                return handlePlaceholders(sender);
            case "update":
                return handleUpdate(sender);
            default:
                error(sender, "未知子命令: " + args[0]);
                sendHelp(sender, label);
                return true;
        }
    }

    // ------------------------------------------------------------------
    // reload
    // ------------------------------------------------------------------

    private boolean handleReload(CommandSender sender) {
        if (!require(sender, "customdrops.reload")) {
            return true;
        }
        plugin.reloadConfigData();
        plugin.restartUpdateChecker();
        success(sender, "配置已重载！当前共 " + plugin.getDropConfigManager().getBlockCount() + " 个方块配置，"
                + "调试模式: " + onOff(plugin.isDebug()));
        return true;
    }

    // ------------------------------------------------------------------
    // list
    // ------------------------------------------------------------------

    private boolean handleList(CommandSender sender, String[] args) {
        if (!require(sender, "customdrops.list")) {
            return true;
        }
        Map<Material, BlockDropConfig> configs = plugin.getDropConfigManager().getAllConfigs();
        if (configs.isEmpty()) {
            info(sender, "当前没有任何方块配置。使用 /customdrops set <方块id> add-drop <物品id> 开始创建。");
            return true;
        }

        int page = 1;
        if (args.length >= 2) {
            Integer parsed = parseInt(args[1]);
            if (parsed == null) {
                error(sender, "页码必须是数字");
                return true;
            }
            page = parsed;
        }

        List<Material> sorted = new ArrayList<>(configs.keySet());
        sorted.sort(Comparator.comparing(Enum::name));

        int totalPages = Math.max(1, (int) Math.ceil(sorted.size() / (double) LIST_PAGE_SIZE));
        page = Math.max(1, Math.min(page, totalPages));
        int start = (page - 1) * LIST_PAGE_SIZE;
        int end = Math.min(start + LIST_PAGE_SIZE, sorted.size());

        sender.sendMessage(ChatColor.GOLD + "========== CustomDrops 配置列表 (" + page + "/" + totalPages + ") ==========");
        for (int i = start; i < end; i++) {
            Material material = sorted.get(i);
            BlockDropConfig config = configs.get(material);
            sender.sendMessage(ChatColor.AQUA + material.name()
                    + ChatColor.GRAY + " | 启用: " + coloredBool(config.isEnabled())
                    + ChatColor.GRAY + " | 覆盖原版: " + coloredBool(config.isOverrideDefault())
                    + ChatColor.GRAY + " | 掉落项: " + ChatColor.WHITE + config.getDropCount()
                    + (config.getPermissionDropCount() > 0
                    ? ChatColor.GRAY + " (需权限 " + ChatColor.WHITE + config.getPermissionDropCount() + ChatColor.GRAY + ")"
                    : ""));
        }
        if (totalPages > 1) {
            sender.sendMessage(ChatColor.GRAY + "使用 /customdrops list <页码> 翻页，共 " + sorted.size() + " 个方块");
        }
        return true;
    }

    // ------------------------------------------------------------------
    // info
    // ------------------------------------------------------------------

    private boolean handleInfo(CommandSender sender, String[] args) {
        if (!require(sender, "customdrops.info")) {
            return true;
        }
        if (args.length < 2) {
            error(sender, "用法: /customdrops info <方块id>");
            return true;
        }

        Material material = plugin.getDropConfigManager().resolveMaterial(args[1]);
        if (material == null) {
            error(sender, "无效的方块id: " + args[1]);
            return true;
        }

        BlockDropConfig config = plugin.getDropConfigManager().getDropConfig(material);
        ConfigurationSection section = getBlockSection(material);
        if (config == null && section == null) {
            info(sender, "方块 " + material.name() + " 在配置中没有设定特殊掉落。");
            return true;
        }

        sender.sendMessage(ChatColor.GOLD + "========== " + material.name() + " 特殊掉落信息 ==========");
        if (config == null) {
            warn(sender, "该方块在配置文件中存在，但没有有效的掉落项（请检查 drops 配置或执行 /customdrops reload）");
        }

        String key = section != null ? section.getName()
                : plugin.getDropConfigManager().getBlockKey(material);

        sender.sendMessage(ChatColor.YELLOW + "配置节点: " + ChatColor.WHITE + (key == null ? material.name() : key));
        if (config != null) {
            sender.sendMessage(ChatColor.YELLOW + "enabled: " + coloredBool(config.isEnabled())
                    + ChatColor.GRAY + "  (使用 /customdrops target " + material.name() + " <true|false> 修改)");
            sender.sendMessage(ChatColor.YELLOW + "override-default: " + coloredBool(config.isOverrideDefault()));
            sender.sendMessage(ChatColor.YELLOW + "silk-touch-preserve-original: "
                    + coloredBool(config.isSilkTouchPreserveOriginal()));
            sender.sendMessage(ChatColor.YELLOW + "掉落项数量: " + ChatColor.WHITE + config.getDropCount()
                    + ChatColor.GRAY + "（需权限 " + config.getPermissionDropCount() + " 项）");

            int index = 1;
            for (DropEntry entry : config.getDrops()) {
                sender.sendMessage("");
                sender.sendMessage(ChatColor.GOLD + "#" + index + " " + ChatColor.AQUA + entry.getMaterial().name());
                sender.sendMessage(prefix() + "基础数量: " + ChatColor.WHITE + entry.getBaseAmount());
                sender.sendMessage(prefix() + "基础概率: " + ChatColor.WHITE
                        + decimal(entry.getBaseChance(), 4) + ChatColor.GRAY + " ("
                        + decimal(entry.getBaseChance() * 100.0D, 2) + "%)");
                sender.sendMessage(prefix() + "时运影响数量: " + coloredBool(entry.isFortuneAffectsCount()));
                sender.sendMessage(prefix() + "时运增加概率: " + ChatColor.WHITE
                        + decimal(entry.getFortuneChanceBonus(), 4) + ChatColor.GRAY + " / 级");
                sender.sendMessage(prefix() + "每物品执行命令: " + coloredBool(entry.isExecuteCommandPerItem()));
                sender.sendMessage(prefix() + "精准采集时禁止掉落: " + coloredBool(entry.isSilkTouchIgnore()));
                sender.sendMessage(prefix() + "经验: " + ChatColor.WHITE + entry.getExp()
                        + ChatColor.GRAY + " (每级时运 +" + decimal(entry.getExpMultiplier() * 100.0D, 2) + "%)");
                sender.sendMessage(prefix() + "金币: " + ChatColor.WHITE + decimal(entry.getMoney(), 2)
                        + ChatColor.GRAY + " (每级时运 +" + decimal(entry.getMoneyMultiplier() * 100.0D, 2) + "%)");
                sender.sendMessage(prefix() + "所需权限: " + (entry.getPermission() == null
                        ? ChatColor.GRAY + "无 (none)"
                        : ChatColor.WHITE + entry.getPermission()));

                List<CommandEntry> commands = entry.getCommands();
                if (commands.isEmpty()) {
                    sender.sendMessage(prefix() + "命令: " + ChatColor.GRAY + "无");
                } else {
                    sender.sendMessage(prefix() + "命令 (" + commands.size() + " 条):");
                    for (int i = 0; i < commands.size(); i++) {
                        CommandEntry cmd = commands.get(i);
                        sender.sendMessage(ChatColor.DARK_GRAY + "    [" + (i + 1) + "][" + cmd.getExecutor() + "] "
                                + ChatColor.GRAY + cmd.getCommand());
                    }
                }
                index++;
            }
        } else if (section != null) {
            for (String child : section.getKeys(false)) {
                sender.sendMessage(prefix() + child + ": " + ChatColor.WHITE + section.get(child));
            }
        }
        sender.sendMessage(ChatColor.GOLD + "================================================");
        return true;
    }

    // ------------------------------------------------------------------
    // target
    // ------------------------------------------------------------------

    private boolean handleTarget(CommandSender sender, String[] args) {
        if (!require(sender, "customdrops.target")) {
            return true;
        }
        if (args.length < 2) {
            error(sender, "用法: /customdrops target <方块id> <true|false>");
            return true;
        }

        Material material = plugin.getDropConfigManager().resolveBlockMaterial(args[1]);
        if (material == null) {
            error(sender, "无效的方块id: " + args[1]);
            return true;
        }

        ConfigurationSection section = getBlockSection(material);
        if (section == null) {
            error(sender, "方块 " + material.name() + " 在配置中没有设定特殊掉落，无法切换启用状态。"
                    + "可先用 /customdrops set " + material.name() + " add-drop <物品id> 创建。");
            return true;
        }

        boolean value;
        if (args.length >= 3) {
            Boolean parsed = parseBoolean(args[2]);
            if (parsed == null) {
                error(sender, "参数必须是 true 或 false");
                return true;
            }
            value = parsed;
        } else {
            // 未提供参数时直接取反
            value = !section.getBoolean("enabled", true);
        }

        ConfigSnapshot before = ConfigSnapshot.of(section);
        section.set("enabled", value);
        ConfigSnapshot after = ConfigSnapshot.of(section);
        plugin.getUndoManager().push(sender.getName(), "target enabled=" + value, section.getName(), before, after);
        saveAndReload();

        success(sender, "方块 " + material.name() + " 的特殊掉落已" + (value ? "启用" : "禁用") + "。");
        return true;
    }

    // ------------------------------------------------------------------
    // delete
    // ------------------------------------------------------------------

    private boolean handleDelete(CommandSender sender, String[] args) {
        if (!require(sender, "customdrops.delete")) {
            return true;
        }
        if (args.length < 2) {
            error(sender, "用法: /customdrops delete <方块id>");
            return true;
        }

        Material material = plugin.getDropConfigManager().resolveMaterial(args[1]);
        if (material == null) {
            error(sender, "无效的方块id: " + args[1]);
            return true;
        }
        ConfigurationSection section = getBlockSection(material);
        if (section == null) {
            info(sender, "方块 " + material.name() + " 在配置中没有特殊掉落配置，无需删除。");
            return true;
        }

        String key = section.getName();
        ConfigSnapshot before = ConfigSnapshot.of(section);
        plugin.getConfig().set("blocks." + key, null);
        plugin.getUndoManager().push(sender.getName(), "delete", key, before, ConfigSnapshot.EMPTY);
        saveAndReload();

        success(sender, "已删除方块 " + material.name() + " 的特殊掉落配置（可使用 /customdrops undo 撤回）。");
        return true;
    }

    // ------------------------------------------------------------------
    // set
    // ------------------------------------------------------------------

    private boolean handleSet(CommandSender sender, String[] args) {
        if (!require(sender, "customdrops.set")) {
            return true;
        }
        if (args.length < 3) {
            error(sender, "用法: /customdrops set <方块id> <选项> [参数...]");
            sender.sendMessage(ChatColor.GRAY + "可用选项: " + ChatColor.WHITE
                    + String.join(", ", BLOCK_SETTINGS.keySet()));
            sender.sendMessage(ChatColor.GRAY + "掉落项选项: " + ChatColor.WHITE + "drops.<序号|物品id>.<选项>");
            sender.sendMessage(ChatColor.GRAY + "例如: /customdrops set COAL_ORE drops.1.amount 5");
            return true;
        }

        Material material = plugin.getDropConfigManager().resolveBlockMaterial(args[1]);
        if (material == null) {
            error(sender, "无效的方块id: " + args[1]
                    + "（必须是有效的方块材质名，例如 COAL_ORE / DIAMOND_ORE）");
            return true;
        }

        String option = args[2].toLowerCase(Locale.ROOT);
        List<String> params = new ArrayList<>(Arrays.asList(args).subList(3, args.length));

        ConfigurationSection blocks = plugin.getConfig().getConfigurationSection("blocks");
        if (blocks == null) {
            blocks = plugin.getConfig().createSection("blocks");
        }

        // 记录改动前的状态（整段方块快照），用于撤销
        ConfigurationSection beforeSection = getBlockSection(material);
        ConfigSnapshot before = ConfigSnapshot.of(beforeSection);
        String blockKey = beforeSection != null
                ? beforeSection.getName()
                : plugin.getDropConfigManager().getOrCreateBlockKey(material, args[1]);
        boolean created = beforeSection == null;

        ConfigurationSection section = blocks.getConfigurationSection(blockKey);
        if (section == null) {
            section = blocks.createSection(blockKey);
            section.set("enabled", true);
        }

        String action;
        if (option.startsWith("drop-")) {
            // 简写形式：drop-<序号|物品id>-<选项>
            int lastDash = option.lastIndexOf('-');
            if (lastDash <= "drop-".length()) {
                error(sender, "掉落项选项格式不正确，应为 drops.<序号|物品id>.<选项> 或 drop-<序号|物品id>-<选项>");
                return true;
            }
            String dropToken = option.substring("drop-".length(), lastDash);
            String dropOption = option.substring(lastDash + 1);
            action = applyDropSetting(section, dropToken, dropOption, params, sender);
        } else if (option.startsWith("drops.")) {
            String[] parts = option.split("\\.", 3);
            if (parts.length < 3) {
                error(sender, "掉落项选项格式应为 drops.<序号|物品id>.<选项>，例如 drops.1.amount");
                return true;
            }
            action = applyDropSetting(section, parts[1], parts[2], params, sender);
        } else if (BLOCK_SETTINGS.containsKey(option)) {
            action = applyBlockSetting(section, option, params, sender);
        } else {
            error(sender, "未知的设置选项: " + args[2]);
            sender.sendMessage(ChatColor.GRAY + "可用方块选项: " + ChatColor.WHITE
                    + String.join(", ", BLOCK_SETTINGS.keySet()));
            sender.sendMessage(ChatColor.GRAY + "可用掉落项选项: " + ChatColor.WHITE
                    + String.join(", ", DROP_SETTINGS.keySet()));
            return true;
        }

        if (action == null) {
            return true; // 具体处理方法已经输出错误信息
        }

        ConfigSnapshot after = ConfigSnapshot.of(section);
        plugin.getUndoManager().push(sender.getName(), option + " -> " + action, blockKey, before, after);
        saveAndReload();

        success(sender, (created ? "已创建方块 " + blockKey + "，并" : "")
                + "设置 " + blockKey + " 的 " + option + " = " + action + "。");
        return true;
    }

    /**
     * 应用方块级设置。
     *
     * @return 实际写入的值描述；失败时返回 null
     */
    private String applyBlockSetting(ConfigurationSection section, String option, List<String> params, CommandSender sender) {
        int required = BLOCK_SETTINGS.get(option);
        if (params.size() < required) {
            error(sender, "选项 " + option + " 需要 " + required + " 个参数");
            return null;
        }
        switch (option) {
            case "enabled": {
                Boolean value = parseBoolean(params.get(0));
                if (value == null) {
                    error(sender, "enabled 需要 true 或 false");
                    return null;
                }
                section.set("enabled", value);
                return String.valueOf(value);
            }
            case "override-default": {
                Boolean value = parseBoolean(params.get(0));
                if (value == null) {
                    error(sender, "override-default 需要 true 或 false");
                    return null;
                }
                section.set("override-default", value);
                return String.valueOf(value);
            }
            case "silk-touch-preserve-original": {
                Boolean value = parseBoolean(params.get(0));
                if (value == null) {
                    error(sender, "silk-touch-preserve-original 需要 true 或 false");
                    return null;
                }
                section.set("silk-touch-preserve-original", value);
                return String.valueOf(value);
            }
            case "add-drop": {
                Material item = plugin.getDropConfigManager().resolveMaterial(params.get(0));
                if (item == null || item.isAir()) {
                    error(sender, "无效的物品id: " + params.get(0));
                    return null;
                }
                List<Map<String, Object>> drops = readDrops(section);
                Map<String, Object> entry = new LinkedHashMap<>();
                entry.put("item", item.name());
                entry.put("amount", 1);
                entry.put("base-chance", 1.0D);
                entry.put("fortune-affects-count", true);
                entry.put("execute-command-per-item", false);
                entry.put("silk-touch-ignore", false);
                entry.put("exp", 0);
                entry.put("money", 0.0D);
                entry.put("exp-multiplier", 0.0D);
                entry.put("money-multiplier", 0.0D);
                entry.put("fortune-chance-bonus", 0.0D);
                entry.put("permission", "");
                // 不预先写入空的 commands 键，保持生成出来的 YAML 干净
                drops.add(entry);
                section.set("drops", drops);
                return item.name() + " (第 " + drops.size() + " 项)";
            }
            case "remove-drop": {
                List<Map<String, Object>> drops = readDrops(section);
                if (drops.isEmpty()) {
                    error(sender, "该方块没有可删除的掉落项");
                    return null;
                }
                int index = resolveDropIndex(drops, params.get(0));
                if (index < 0) {
                    error(sender, "找不到掉落项: " + params.get(0) + "（可用序号 1~" + drops.size() + " 或物品id）");
                    return null;
                }
                Map<String, Object> removed = drops.remove(index);
                section.set("drops", drops);
                return "移除第 " + (index + 1) + " 项 " + removed.get("item");
            }
            case "clear-drops":
                section.set("drops", new ArrayList<>());
                return "已清空全部掉落项";
            default:
                error(sender, "未实现的选项: " + option);
                return null;
        }
    }

    /**
     * 应用掉落项级设置。
     *
     * @return 实际写入的值描述；失败时返回 null
     */
    private String applyDropSetting(ConfigurationSection section, String dropToken, String option,
                                    List<String> params, CommandSender sender) {
        String normalized = normalizeDropOption(option);
        if (normalized == null || !DROP_SETTINGS.containsKey(normalized)) {
            error(sender, "未知的掉落项选项: " + option);
            sender.sendMessage(ChatColor.GRAY + "可用掉落项选项: " + ChatColor.WHITE
                    + String.join(", ", DROP_SETTINGS.keySet()));
            return null;
        }
        int required = DROP_SETTINGS.get(normalized);
        if (params.size() < required) {
            error(sender, "选项 " + normalized + " 需要 " + required + " 个参数");
            return null;
        }

        List<Map<String, Object>> drops = readDrops(section);
        int index = resolveDropIndex(drops, dropToken);
        if (index < 0) {
            if (drops.isEmpty()) {
                error(sender, "方块 " + section.getName() + " 还没有任何掉落项，"
                        + "请先执行 /customdrops set " + section.getName() + " add-drop <物品id>");
            } else {
                error(sender, "找不到掉落项: " + dropToken + "（可用序号 1~" + drops.size() + " 或物品id）");
            }
            return null;
        }

        Map<String, Object> drop = drops.get(index);
        String value;
        switch (normalized) {
            case "item": {
                Material item = plugin.getDropConfigManager().resolveMaterial(params.get(0));
                if (item == null || item.isAir()) {
                    error(sender, "无效的物品id: " + params.get(0));
                    return null;
                }
                drop.put("item", item.name());
                value = item.name();
                break;
            }
            case "amount": {
                Integer amount = parseInt(params.get(0));
                if (amount == null || amount < 1) {
                    error(sender, "amount 需要大于等于 1 的整数");
                    return null;
                }
                drop.put("amount", amount);
                value = String.valueOf(amount);
                break;
            }
            case "chance": {
                Double chance = parseDouble(params.get(0));
                if (chance == null || chance < 0.0D || chance > 1.0D) {
                    error(sender, "chance 需要 0~1 之间的数字（例如 0.1 表示 10%）");
                    return null;
                }
                drop.put("base-chance", chance);
                drop.remove("chance");
                value = String.valueOf(chance);
                break;
            }
            case "fortune-affects-count":
            case "execute-command-per-item":
            case "silk-touch-ignore": {
                Boolean flag = parseBoolean(params.get(0));
                if (flag == null) {
                    error(sender, normalized + " 需要 true 或 false");
                    return null;
                }
                drop.put(normalized, flag);
                value = String.valueOf(flag);
                break;
            }
            case "fortune-chance-bonus":
            case "exp-multiplier":
            case "money-multiplier": {
                Double number = parseDouble(params.get(0));
                if (number == null || number < 0.0D) {
                    error(sender, normalized + " 需要大于等于 0 的数字");
                    return null;
                }
                drop.put(normalized, number);
                value = String.valueOf(number);
                break;
            }
            case "exp": {
                Integer exp = parseInt(params.get(0));
                if (exp == null || exp < 0) {
                    error(sender, "exp 需要大于等于 0 的整数");
                    return null;
                }
                drop.put("exp", exp);
                value = String.valueOf(exp);
                break;
            }
            case "money": {
                Double money = parseDouble(params.get(0));
                if (money == null || money < 0.0D) {
                    error(sender, "money 需要大于等于 0 的数字");
                    return null;
                }
                drop.put("money", money);
                value = String.valueOf(money);
                break;
            }
            case "permission": {
                String permission = params.get(0).trim();
                if (permission.equalsIgnoreCase("none") || permission.equalsIgnoreCase("null")
                        || permission.equals("-") || permission.equalsIgnoreCase("false")) {
                    drop.put("permission", "");
                    value = "无 (none)";
                } else {
                    drop.put("permission", permission);
                    value = permission;
                }
                break;
            }
            case "add-command":
            case "add-command-op":
            case "add-command-player": {
                String command = params.get(0);
                if (command.isBlank()) {
                    error(sender, "命令内容不能为空");
                    return null;
                }
                String executor = normalized.equals("add-command-op") ? "OP"
                        : normalized.equals("add-command-player") ? "PLAYER" : "CONSOLE";
                List<Map<String, Object>> commands = readCommands(drop);
                Map<String, Object> commandEntry = new LinkedHashMap<>();
                commandEntry.put("command", command);
                commandEntry.put("executor", executor);
                commands.add(commandEntry);
                drop.put("commands", commands);
                value = "[" + executor + "] " + command + " (第 " + commands.size() + " 条)";
                break;
            }
            case "remove-command": {
                List<Map<String, Object>> commands = readCommands(drop);
                if (commands.isEmpty()) {
                    error(sender, "该掉落项没有任何命令");
                    return null;
                }
                Integer commandIndex = parseInt(params.get(0));
                if (commandIndex == null || commandIndex < 1 || commandIndex > commands.size()) {
                    error(sender, "remove-command 需要 1~" + commands.size() + " 之间的序号");
                    return null;
                }
                Map<String, Object> removed = commands.remove(commandIndex - 1);
                drop.put("commands", commands);
                value = "移除第 " + commandIndex + " 条命令: " + removed.get("command");
                break;
            }
            case "clear-commands":
                drop.put("commands", new ArrayList<>());
                value = "已清空全部命令";
                break;
            default:
                error(sender, "未实现的选项: " + option);
                return null;
        }
        section.set("drops", drops);
        return value;
    }

    /**
     * 兼容若干别名，统一成配置项名称。
     */
    private static String normalizeDropOption(String option) {
        switch (option.toLowerCase(Locale.ROOT)) {
            case "base-chance":
            case "drop-chance":
            case "probability":
                return "chance";
            case "base-amount":
            case "count":
                return "amount";
            case "material":
            case "type":
                return "item";
            case "fortune-affects-amount":
                return "fortune-affects-count";
            case "exp-per-item":
            case "command-per-item":
                return "execute-command-per-item";
            case "permission-node":
            case "perm":
                return "permission";
            default:
                return option.toLowerCase(Locale.ROOT);
        }
    }

    // ------------------------------------------------------------------
    // undo
    // ------------------------------------------------------------------

    private boolean handleUndo(CommandSender sender, String[] args) {
        if (!require(sender, "customdrops.undo")) {
            return true;
        }
        UndoManager undoManager = plugin.getUndoManager();
        if (undoManager.isEmpty()) {
            info(sender, "没有可撤回的命令操作。");
            return true;
        }

        int steps = 1;
        if (args.length >= 2) {
            Integer parsed = parseInt(args[1]);
            if (parsed == null || parsed < 1) {
                error(sender, "步数必须是大于等于 1 的整数");
                return true;
            }
            steps = parsed;
        }
        if (steps > UndoManager.MAX_HISTORY) {
            warn(sender, "最多只能撤回 " + UndoManager.MAX_HISTORY + " 步，已按 " + UndoManager.MAX_HISTORY + " 步执行。");
            steps = UndoManager.MAX_HISTORY;
        }

        int available = undoManager.size();
        List<String> undone = undoManager.undo(steps);
        if (undone.isEmpty()) {
            info(sender, "没有可撤回的命令操作。");
            return true;
        }
        success(sender, "已撤回 " + undone.size() + " 步操作"
                + (steps > available ? "（历史中仅剩 " + available + " 步）" : "") + "：");
        for (String description : undone) {
            sender.sendMessage(ChatColor.GRAY + "  - " + description);
        }
        sender.sendMessage(ChatColor.GRAY + "剩余可撤回步数: " + undoManager.size() + "/" + UndoManager.MAX_HISTORY);
        return true;
    }

    // ------------------------------------------------------------------
    // placeholders / update / help
    // ------------------------------------------------------------------

    private boolean handlePlaceholders(CommandSender sender) {
        if (!require(sender, "customdrops.info")) {
            return true;
        }
        if (!plugin.isPapiAvailable()) {
            warn(sender, "PlaceholderAPI 未安装，以下变量在服务器中不可用。");
        }
        sender.sendMessage(ChatColor.GOLD + "========== CustomDrops 可用变量 ==========");
        for (Map.Entry<String, String> entry : CustomDropsExpansion.describePlaceholders().entrySet()) {
            sender.sendMessage(ChatColor.AQUA + entry.getKey() + ChatColor.GRAY + " - " + entry.getValue());
        }
        sender.sendMessage(ChatColor.GRAY + "提示: 变量中的 '_' 也可以写成 '-'，例如 "
                + ChatColor.WHITE + "%customdrops_block-COAL_ORE-drop-1-amount%");
        return true;
    }

    private boolean handleUpdate(CommandSender sender) {
        if (!require(sender, "customdrops.update")) {
            return true;
        }
        if (!plugin.isCheckUpdate()) {
            warn(sender, "配置中已关闭更新检测（check-update: false）。");
        }
        info(sender, "正在检查更新...");
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            UpdateChecker.Result result = UpdateChecker.fetchNow(plugin);
            String current = plugin.getPluginVersion();
            plugin.getServer().getScheduler().runTask(plugin, () -> {
                if (result.getState() != UpdateChecker.State.OK) {
                    error(sender, "检查更新失败: " + result.getMessage());
                    return;
                }
                sender.sendMessage(ChatColor.GOLD + "========== CustomDrops 更新检查 ==========");
                sender.sendMessage(ChatColor.YELLOW + "当前版本: " + ChatColor.WHITE + current);
                sender.sendMessage(ChatColor.YELLOW + "最新版本: " + ChatColor.WHITE + result.getTagName()
                        + (result.isPrerelease() ? ChatColor.GRAY + " (预发布)" : ""));
                if (result.isNewerThan(current)) {
                    sender.sendMessage(ChatColor.GREEN + "发现新版本！下载地址: " + ChatColor.WHITE + result.getHtmlUrl());
                    for (String line : UpdateChecker.firstLines(result.getBody(), 10)) {
                        sender.sendMessage(ChatColor.GRAY + "  " + line);
                    }
                } else {
                    sender.sendMessage(ChatColor.GREEN + "当前已是最新版本。");
                }
                sender.sendMessage(ChatColor.GOLD + "=========================================");
            });
        });
        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "========== CustomDrops 命令帮助 ==========");
        sender.sendMessage(cmd(label, "reload", "") + ChatColor.GRAY + " 重载配置文件");
        sender.sendMessage(cmd(label, "list", "[页码]") + ChatColor.GRAY + " 列出已配置的方块");
        sender.sendMessage(cmd(label, "info", "<方块id>") + ChatColor.GRAY + " 查询某方块的特殊掉落信息");
        sender.sendMessage(cmd(label, "target", "<方块id> <true|false>") + ChatColor.GRAY + " 启用/禁用某方块的特殊掉落");
        sender.sendMessage(cmd(label, "delete", "<方块id>") + ChatColor.GRAY + " 删除某方块的特殊掉落配置");
        sender.sendMessage(cmd(label, "set", "<方块id> <选项> [参数...]") + ChatColor.GRAY + " 设置某方块的特殊掉落（不存在则自动创建）");
        sender.sendMessage(cmd(label, "undo", "[步数]") + ChatColor.GRAY + " 撤回前几步命令操作（最多 5 步）");
        sender.sendMessage(cmd(label, "placeholders", "") + ChatColor.GRAY + " 查看可用的 PAPI 变量");
        sender.sendMessage(cmd(label, "update", "") + ChatColor.GRAY + " 手动检查更新");

        List<String> blockOptions = new ArrayList<>(BLOCK_SETTINGS.keySet());
        sender.sendMessage(ChatColor.GOLD + "--- set 选项 ---");
        sender.sendMessage(ChatColor.AQUA + "方块级: " + ChatColor.WHITE + String.join(", ", blockOptions));
        sender.sendMessage(ChatColor.AQUA + "掉落项级: " + ChatColor.WHITE
                + "drops.<序号|物品id>.<" + String.join("|", DROP_SETTINGS.keySet()) + ">");
        sender.sendMessage(ChatColor.GRAY + "示例: /" + label + " set COAL_ORE drops.1.amount 5");
        sender.sendMessage(ChatColor.GRAY + "示例: /" + label + " set COAL_ORE add-drop DIAMOND");
        sender.sendMessage(ChatColor.GRAY + "示例: /" + label + " set COAL_ORE drops.2.permission customdrops.drop.diamond");
        sender.sendMessage(ChatColor.GOLD + "=========================================");
    }

    private static String cmd(String label, String sub, String args) {
        return ChatColor.YELLOW + "/" + label + " " + sub
                + (args.isEmpty() ? "" : " " + ChatColor.WHITE + args);
    }

    // ------------------------------------------------------------------
    // Tab 补全
    // ------------------------------------------------------------------

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            return filter(Arrays.asList("help", "reload", "list", "info", "target", "delete",
                    "set", "undo", "placeholders", "update"), args[0]);
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "info":
            case "delete":
            case "target":
                if (args.length == 2) {
                    return filter(blockSuggestions(), args[1]);
                }
                if (sub.equals("target") && args.length == 3) {
                    return filter(Arrays.asList("true", "false"), args[2]);
                }
                return Collections.emptyList();

            case "list":
                if (args.length == 2) {
                    return filter(Arrays.asList("1", "2", "3"), args[1]);
                }
                return Collections.emptyList();

            case "undo":
                if (args.length == 2) {
                    List<String> steps = new ArrayList<>();
                    for (int i = 1; i <= UndoManager.MAX_HISTORY; i++) {
                        steps.add(String.valueOf(i));
                    }
                    return filter(steps, args[1]);
                }
                return Collections.emptyList();

            case "set":
                return completeSet(args);

            default:
                return Collections.emptyList();
        }
    }

    private List<String> completeSet(String[] args) {
        if (args.length == 2) {
            return filter(blockSuggestions(), args[1]);
        }
        if (args.length == 3) {
            List<String> options = new ArrayList<>(BLOCK_SETTINGS.keySet());
            BlockDropConfig config = null;
            Material material = plugin.getDropConfigManager().resolveBlockMaterial(args[1]);
            if (material != null) {
                config = plugin.getDropConfigManager().getDropConfig(material);
            }
            int dropCount = config == null ? 0 : config.getDropCount();
            for (String dropOption : DROP_SETTINGS.keySet()) {
                for (int i = 1; i <= Math.max(dropCount, 1); i++) {
                    options.add("drops." + i + "." + dropOption);
                }
            }
            if (config != null) {
                for (DropEntry entry : config.getDrops()) {
                    for (String dropOption : DROP_SETTINGS.keySet()) {
                        options.add("drops." + entry.getMaterial().name() + "." + dropOption);
                    }
                }
            }
            return filter(options, args[2]);
        }

        // 后续参数：根据选项给出候选值
        String option = args[2].toLowerCase(Locale.ROOT);
        String setting = option;
        int dropsPrefix = -1;
        if (option.startsWith("drops.")) {
            String[] parts = option.split("\\.", 3);
            setting = parts.length >= 3 ? parts[2] : "";
            dropsPrefix = 1;
        } else if (option.startsWith("drop-")) {
            int lastDash = option.lastIndexOf('-');
            setting = lastDash > 5 ? option.substring(lastDash + 1) : "";
            dropsPrefix = 1;
        }
        if (dropsPrefix < 0 && args.length == 4) {
            switch (BLOCK_SETTINGS.getOrDefault(setting, 0)) {
                case 1:
                    if (setting.equals("enabled") || setting.equals("override-default")
                            || setting.equals("silk-touch-preserve-original")) {
                        return filter(Arrays.asList("true", "false"), args[3]);
                    }
                    if (setting.equals("add-drop")) {
                        return filter(materialSuggestions(), args[3]);
                    }
                    if (setting.equals("remove-drop")) {
                        return filter(dropSuggestions(args[1]), args[3]);
                    }
                    return Collections.emptyList();
                default:
                    return Collections.emptyList();
            }
        }
        if (dropsPrefix >= 0) {
            if (args.length == 4) {
                switch (setting) {
                    case "item":
                        return filter(materialSuggestions(), args[3]);
                    case "fortune-affects-count":
                    case "execute-command-per-item":
                    case "silk-touch-ignore":
                        return filter(Arrays.asList("true", "false"), args[3]);
                    case "permission":
                        return filter(Arrays.asList("none", "customdrops.drop."), args[3]);
                    case "amount":
                        return filter(Arrays.asList("1", "2", "3", "5", "10"), args[3]);
                    case "chance":
                        return filter(Arrays.asList("1.0", "0.5", "0.25", "0.1", "0.05"), args[3]);
                    case "exp":
                        return filter(Arrays.asList("0", "1", "5", "10", "30"), args[3]);
                    case "money":
                        return filter(Arrays.asList("0.0", "1.0", "5.0", "10.0"), args[3]);
                    case "exp-multiplier":
                    case "money-multiplier":
                    case "fortune-chance-bonus":
                        return filter(Arrays.asList("0.0", "0.1", "0.5", "1.0"), args[3]);
                    case "remove-command": {
                        List<String> indices = new ArrayList<>();
                        int count = countCommands(args[1], option);
                        for (int i = 1; i <= count; i++) {
                            indices.add(String.valueOf(i));
                        }
                        return filter(indices, args[3]);
                    }
                    case "add-command-op":
                    case "add-command-player":
                    case "add-command":
                        return filter(Collections.singletonList("say %player% "), args[3]);
                    default:
                        return Collections.emptyList();
                }
            }
            // 第 5 个参数：add-command 系列的执行者
            if (args.length == 5 && setting.startsWith("add-command")) {
                return filter(Arrays.asList("CONSOLE", "PLAYER", "OP"), args[4]);
            }
        }
        return Collections.emptyList();
    }

    private int countCommands(String blockName, String option) {
        Material material = plugin.getDropConfigManager().resolveMaterial(blockName);
        if (material == null) {
            return 0;
        }
        BlockDropConfig config = plugin.getDropConfigManager().getDropConfig(material);
        if (config == null) {
            return 0;
        }
        String dropToken;
        if (option.startsWith("drops.")) {
            String[] parts = option.split("\\.", 3);
            dropToken = parts.length >= 2 ? parts[1] : "";
        } else {
            int lastDash = option.lastIndexOf('-');
            dropToken = lastDash > 5 ? option.substring(5, lastDash) : "";
        }
        int index = resolveDropIndexFromEntries(config.getDrops(), dropToken);
        return index < 0 ? 0 : config.getDrops().get(index).getCommands().size();
    }
    private List<String> dropSuggestions(String blockName) {
        Material material = plugin.getDropConfigManager().resolveMaterial(blockName);
        if (material == null) {
            return Collections.emptyList();
        }
        BlockDropConfig config = plugin.getDropConfigManager().getDropConfig(material);
        if (config == null) {
            return Collections.emptyList();
        }
        List<String> suggestions = new ArrayList<>();
        for (int i = 1; i <= config.getDropCount(); i++) {
            suggestions.add(String.valueOf(i));
        }
        for (DropEntry entry : config.getDrops()) {
            suggestions.add(entry.getMaterial().name());
        }
        return suggestions;
    }

    /**
     * @return 可作为方块配置的材质名列表（缓存）
     */
    private List<String> blockSuggestions() {
        if (blockSuggestions == null) {
            Set<String> names = new LinkedHashSet<>();
            for (Material material : Material.values()) {
                if (material.isBlock() && !material.isAir() && material.isItem()) {
                    names.add(material.name());
                }
            }
            List<String> list = new ArrayList<>(names);
            list.sort(String.CASE_INSENSITIVE_ORDER);
            blockSuggestions = list;
        }
        return blockSuggestions;
    }

    private List<String> materialSuggestions() {
        if (blockSuggestions == null) {
            blockSuggestions();
        }
        Set<String> names = new LinkedHashSet<>();
        for (Material material : Material.values()) {
            if (material.isItem()) {
                names.add(material.name());
            }
        }
        List<String> list = new ArrayList<>(names);
        list.sort(String.CASE_INSENSITIVE_ORDER);
        return list;
    }

    private static List<String> filter(List<String> options, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        List<String> result = new ArrayList<>();
        for (String option : options) {
            if (option.toLowerCase(Locale.ROOT).startsWith(lower)) {
                result.add(option);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------
    // 通用工具
    // ------------------------------------------------------------------

    /**
     * 取得某方块在配置中的原始节点（不存在时返回 null）。
     */
    private ConfigurationSection getBlockSection(Material material) {
        ConfigurationSection blocks = plugin.getConfig().getConfigurationSection("blocks");
        if (blocks == null) {
            return null;
        }
        String key = plugin.getDropConfigManager().getBlockKey(material);
        if (key != null) {
            ConfigurationSection section = blocks.getConfigurationSection(key);
            if (section != null) {
                return section;
            }
        }
        String found = plugin.getDropConfigManager().findExistingBlockKey(material.name());
        return found == null ? null : blocks.getConfigurationSection(found);
    }

    /**
     * 把 drops 节点读成「Map 列表」，兼容服务器写入的 MemorySection 结构。
     */
    private static List<Map<String, Object>> readDrops(ConfigurationSection section) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object rawObject = section.get("drops");
        if (!(rawObject instanceof List)) {
            // drops 缺失或类型不对（例如被误写成键值对）时按空列表处理
            return result;
        }
        List<?> raw = (List<?>) rawObject;
        for (Object element : raw) {
            if (element instanceof ConfigurationSection) {
                result.add(new LinkedHashMap<>(((ConfigurationSection) element).getValues(false)));
            } else if (element instanceof Map) {
                Map<String, Object> map = new LinkedHashMap<>();
                for (Map.Entry<?, ?> entry : ((Map<?, ?>) element).entrySet()) {
                    map.put(String.valueOf(entry.getKey()), entry.getValue());
                }
                result.add(map);
            }
        }
        return result;
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> readCommands(Map<String, Object> drop) {
        List<Map<String, Object>> result = new ArrayList<>();
        Object raw = drop.get("commands");
        if (raw instanceof List) {
            for (Object element : (List<?>) raw) {
                if (element instanceof ConfigurationSection) {
                    result.add(new LinkedHashMap<>(((ConfigurationSection) element).getValues(false)));
                } else if (element instanceof Map) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    for (Map.Entry<?, ?> entry : ((Map<?, ?>) element).entrySet()) {
                        map.put(String.valueOf(entry.getKey()), entry.getValue());
                    }
                    result.add(map);
                } else if (element instanceof String) {
                    Map<String, Object> map = new LinkedHashMap<>();
                    map.put("command", element);
                    map.put("executor", "CONSOLE");
                    result.add(map);
                }
            }
        } else if (drop.get("command") instanceof String) {
            Map<String, Object> map = new LinkedHashMap<>();
            map.put("command", drop.get("command"));
            map.put("executor", "CONSOLE");
            result.add(map);
            drop.remove("command");
        }
        return result;
    }

    /**
     * 解析掉落项下标（支持 1 开始的序号或物品 id）。
     *
     * @return 0 开始的下标；未找到返回 -1
     */
    private static int resolveDropIndex(List<Map<String, Object>> drops, String token) {
        if (token == null || token.isBlank()) {
            return -1;
        }
        try {
            int index = Integer.parseInt(token.trim()) - 1;
            return (index >= 0 && index < drops.size()) ? index : -1;
        } catch (NumberFormatException ignored) {
            // 继续按物品 id 匹配
        }
        String wanted = token.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < drops.size(); i++) {
            Object item = drops.get(i).get("item");
            if (item != null && String.valueOf(item).toUpperCase(Locale.ROOT).equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    /**
     * 解析掉落项下标（编译后的 {@link DropEntry} 列表版本，用于 Tab 补全）。
     *
     * @return 0 开始的下标；未找到返回 -1
     */
    private static int resolveDropIndexFromEntries(List<DropEntry> drops, String token) {
        if (drops == null || drops.isEmpty() || token == null || token.isBlank()) {
            return -1;
        }
        try {
            int index = Integer.parseInt(token.trim()) - 1;
            return (index >= 0 && index < drops.size()) ? index : -1;
        } catch (NumberFormatException ignored) {
            // 继续按物品 id 匹配
        }
        String wanted = token.trim().toUpperCase(Locale.ROOT);
        for (int i = 0; i < drops.size(); i++) {
            if (drops.get(i).getMaterial().name().equals(wanted)) {
                return i;
            }
        }
        return -1;
    }

    private void saveAndReload() {
        try {
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().warning("保存配置文件失败: " + e.getMessage());
        }
        plugin.reloadConfigData();
    }

    private boolean require(CommandSender sender, String permission) {
        if (sender.hasPermission(permission) || sender.hasPermission("customdrops.admin")) {
            return true;
        }
        error(sender, "你没有权限执行该命令（需要 " + permission + "）");
        return false;
    }

    private static Boolean parseBoolean(String text) {
        if (text == null) {
            return null;
        }
        String value = text.trim();
        if (value.equalsIgnoreCase("true") || value.equalsIgnoreCase("yes")
                || value.equalsIgnoreCase("on") || value.equals("1")) {
            return Boolean.TRUE;
        }
        if (value.equalsIgnoreCase("false") || value.equalsIgnoreCase("no")
                || value.equalsIgnoreCase("off") || value.equals("0")) {
            return Boolean.FALSE;
        }
        return null;
    }

    private static Integer parseInt(String text) {
        try {
            return Integer.valueOf(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static Double parseDouble(String text) {
        try {
            return Double.valueOf(text.trim());
        } catch (NumberFormatException | NullPointerException e) {
            return null;
        }
    }

    private static String decimal(double value, int decimals) {
        return String.format(Locale.ROOT, "%." + decimals + "f", value);
    }

    private static String onOff(boolean value) {
        return value ? "开启" : "关闭";
    }

    private static String coloredBool(boolean value) {
        return value ? ChatColor.GREEN + "true" : ChatColor.RED + "false";
    }

    private static String prefix() {
        return ChatColor.GRAY + "  ";
    }

    private static void success(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GREEN + "[CustomDrops] " + message);
    }

    private static void info(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.YELLOW + "[CustomDrops] " + message);
    }

    private static void warn(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.GOLD + "[CustomDrops] " + message);
    }

    private static void error(CommandSender sender, String message) {
        sender.sendMessage(ChatColor.RED + "[CustomDrops] " + message);
    }
}

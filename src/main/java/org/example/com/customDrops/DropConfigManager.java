package org.example.com.customDrops;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;

/**
 * 配置加载 / 保存 / 查询中心。
 * <p>
 * 说明：本类同时在「事件热路径」与「命令」两条链路上被访问，
 * 因此所有查询方法都只做常数级查找，任何解析、字符串处理与日志输出都放在
 * {@link #loadConfig()} 中一次性完成。
 */
public class DropConfigManager {

    private final JavaPlugin plugin;

    /** 启用自定义掉落的世界名（O(1) 查找） */
    private final Set<String> enabledWorlds = new HashSet<>();

    /** 方块 -> 编译后的掉落配置 */
    private final Map<Material, BlockDropConfig> blockConfigs = new EnumMap<>(Material.class);

    /** 方块 -> 配置文件中的原始节点名（保留玩家书写的大小写） */
    private final Map<Material, String> blockKeys = new EnumMap<>(Material.class);

    /** 小写物品名 -> Material 的解析缓存（避免重复调用 Material#matchMaterial） */
    private final Map<String, Material> materialCache = new HashMap<>();

    public DropConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    // ------------------------------------------------------------------
    // 加载
    // ------------------------------------------------------------------

    /**
     * 重新加载配置并编译全部方块掉落配置。
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();

        enabledWorlds.clear();
        for (String world : config.getStringList("enabled-worlds")) {
            if (world != null && !world.isBlank()) {
                enabledWorlds.add(world.trim());
            }
        }

        blockConfigs.clear();
        blockKeys.clear();

        ConfigurationSection blocksSection = config.getConfigurationSection("blocks");
        if (blocksSection == null) {
            plugin.getLogger().warning("配置中未找到 'blocks' 节点，跳过加载");
            return;
        }

        int loaded = 0;
        int failed = 0;
        for (String blockName : blocksSection.getKeys(false)) {
            ConfigurationSection blockCfg = blocksSection.getConfigurationSection(blockName);
            if (blockCfg == null) {
                plugin.getLogger().warning("方块节点 '" + blockName + "' 格式不正确（应为键值对），已跳过");
                failed++;
                continue;
            }

            Material material = resolveMaterial(blockName);
            if (material == null) {
                plugin.getLogger().warning("无效的方块类型: " + blockName);
                failed++;
                continue;
            }

            boolean enabled = blockCfg.getBoolean("enabled", true);
            boolean overrideDefault = blockCfg.getBoolean("override-default", true);
            boolean silkTouchPreserveOriginal = blockCfg.getBoolean("silk-touch-preserve-original", false);

            // 允许 drops 为空（例如用命令刚创建、还没加掉落项），此时该方块不会产生任何特殊掉落
            List<DropEntry> drops = parseDrops(blockName, blockCfg);
            List<?> rawDrops = blockCfg.getList("drops");
            if (drops.isEmpty() && rawDrops != null && !rawDrops.isEmpty()) {
                // 写了 drops 但全部无效，说明配置有问题，值得提示
                plugin.getLogger().warning("方块 " + blockName + " 的 drops 中没有有效掉落项（已跳过该方块）");
                failed++;
                continue;
            }

            blockConfigs.put(material, new BlockDropConfig(enabled, overrideDefault, silkTouchPreserveOriginal, drops));
            blockKeys.put(material, blockName);
            loaded++;

            if (plugin instanceof CustomDropsPlugin && ((CustomDropsPlugin) plugin).isDebug()) {
                plugin.getLogger().info("已加载方块掉落配置: " + blockName
                        + " (启用=" + enabled
                        + ", 覆盖原版=" + overrideDefault
                        + ", 掉落项数=" + drops.size() + ")");
            }
        }
        plugin.getLogger().info("已加载 " + loaded + " 个方块的特殊掉落配置"
                + (failed > 0 ? "，跳过 " + failed + " 个无效配置" : "")
                + "，启用世界: " + enabledWorlds);
    }

    /**
     * 解析单个方块的掉落项列表。
     */
    private List<DropEntry> parseDrops(String blockName, ConfigurationSection blockCfg) {
        List<DropEntry> drops = new ArrayList<>();
        List<Map<?, ?>> dropList = blockCfg.getMapList("drops");
        if (dropList.isEmpty()) {
            return drops;
        }

        for (int index = 0; index < dropList.size(); index++) {
            Map<?, ?> dropMap = dropList.get(index);
            if (dropMap == null) {
                continue;
            }

            String itemName = asString(dropMap.get("item"));
            if (itemName == null) {
                plugin.getLogger().warning("第 " + (index + 1) + " 个掉落项缺少 'item' 字段 (方块: " + blockName + ")，已跳过");
                continue;
            }
            Material itemMat = resolveMaterial(itemName);
            if (itemMat == null || itemMat.isAir()) {
                plugin.getLogger().warning("无效的物品类型: " + itemName + " (方块: " + blockName + ")");
                continue;
            }

            int baseAmount = Math.max(1, asInt(dropMap.get("amount"), 1));

            // 概率：优先 base-chance，兼容旧字段 chance
            double baseChance = asDouble(dropMap.get("base-chance"), Double.NaN);
            if (Double.isNaN(baseChance)) {
                baseChance = asDouble(dropMap.get("chance"), 1.0D);
            }
            baseChance = Math.min(1.0D, Math.max(0.0D, baseChance));

            boolean fortuneAffectsCount = asBoolean(dropMap.get("fortune-affects-count"), true);
            boolean execPerItem = asBoolean(dropMap.get("execute-command-per-item"), false);
            boolean silkTouchIgnore = asBoolean(dropMap.get("silk-touch-ignore"), false);

            int exp = Math.max(0, asInt(dropMap.get("exp"), 0));
            double money = Math.max(0.0D, asDouble(dropMap.get("money"), 0.0D));
            double expMultiplier = Math.max(0.0D, asDouble(dropMap.get("exp-multiplier"), 0.0D));
            double moneyMultiplier = Math.max(0.0D, asDouble(dropMap.get("money-multiplier"), 0.0D));
            double fortuneChanceBonus = Math.max(0.0D, asDouble(dropMap.get("fortune-chance-bonus"), 0.0D));

            String permission = asString(dropMap.get("permission"));
            if (permission != null) {
                permission = permission.trim();
                if (permission.isEmpty()) {
                    permission = null;
                }
            }

            List<CommandEntry> commands = parseCommands(blockName, dropMap);

            drops.add(new DropEntry(itemMat, baseAmount, baseChance, execPerItem, fortuneAffectsCount,
                    commands, exp, money, expMultiplier, moneyMultiplier, fortuneChanceBonus,
                    silkTouchIgnore, permission));
        }
        return drops;
    }

    /**
     * 解析掉落命令列表，支持列表写法与旧的单条 command 写法。
     */
    private List<CommandEntry> parseCommands(String blockName, Map<?, ?> dropMap) {
        List<CommandEntry> commands = new ArrayList<>();
        Object commandsObj = dropMap.get("commands");

        if (commandsObj instanceof List) {
            for (Object cmdObj : (List<?>) commandsObj) {
                if (cmdObj instanceof String) {
                    String command = ((String) cmdObj).trim();
                    if (!command.isEmpty()) {
                        commands.add(new CommandEntry(command, CommandEntry.ExecutorType.CONSOLE));
                    }
                } else if (cmdObj instanceof Map) {
                    Map<?, ?> cmdMap = (Map<?, ?>) cmdObj;
                    String command = asString(cmdMap.get("command"));
                    if (command == null || command.isBlank()) {
                        continue;
                    }
                    CommandEntry.ExecutorType executor = CommandEntry.parseExecutor(asString(cmdMap.get("executor")));
                    if (executor == null) {
                        if (cmdMap.get("executor") != null) {
                            plugin.getLogger().warning("无效的 executor 类型: " + cmdMap.get("executor")
                                    + " (方块: " + blockName + ")，已回退为 CONSOLE");
                        }
                        executor = CommandEntry.ExecutorType.CONSOLE;
                    }
                    commands.add(new CommandEntry(command, executor));
                }
            }
        } else if (dropMap.containsKey("command")) {
            String oldCmd = asString(dropMap.get("command"));
            if (oldCmd != null && !oldCmd.isBlank()) {
                commands.add(new CommandEntry(oldCmd, CommandEntry.ExecutorType.CONSOLE));
            }
        }
        return commands;
    }

    // ------------------------------------------------------------------
    // 类型安全的读取辅助（配置文件由玩家手工编辑，类型可能任意）
    // ------------------------------------------------------------------

    private static String asString(Object value) {
        return value == null ? null : String.valueOf(value);
    }

    private static int asInt(Object value, int defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).intValue();
        }
        if (value instanceof String) {
            try {
                return (int) Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static double asDouble(Object value, double defaultValue) {
        if (value instanceof Number) {
            return ((Number) value).doubleValue();
        }
        if (value instanceof String) {
            try {
                return Double.parseDouble(((String) value).trim());
            } catch (NumberFormatException ignored) {
                return defaultValue;
            }
        }
        return defaultValue;
    }

    private static boolean asBoolean(Object value, boolean defaultValue) {
        if (value instanceof Boolean) {
            return (Boolean) value;
        }
        if (value instanceof String) {
            String text = ((String) value).trim();
            if (text.equalsIgnoreCase("true")) return true;
            if (text.equalsIgnoreCase("false")) return false;
        }
        return defaultValue;
    }

    // ------------------------------------------------------------------
    // 物品 / 方块名解析
    // ------------------------------------------------------------------

    /**
     * 解析材质名（带缓存）。
     * <p>
     * 与 1.0.x 相比额外支持 {@code minecraft:coal_ore}、{@code COAL ORE} 等写法，
     * 且避免在每次方块破坏时重复匹配。
     *
     * @param name 材质名，可为 null
     * @return 对应的 Material；无法解析时返回 null
     */
    public Material resolveMaterial(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String normalized = name.trim();
        Material cached = materialCache.get(normalized);
        if (cached != null) {
            return cached;
        }

        Material material = null;
        String lookup = normalized;
        int namespace = lookup.indexOf(':');
        if (namespace >= 0) {
            lookup = lookup.substring(namespace + 1);
        }
        if (lookup.startsWith("minecraft.")) {
            lookup = lookup.substring("minecraft.".length());
        }

        material = Material.matchMaterial(lookup);
        if (material == null) {
            material = Material.matchMaterial(lookup.replace(' ', '_'));
        }
        if (material == null) {
            material = Material.matchMaterial(lookup.replace(' ', '_').toUpperCase(Locale.ROOT));
        }
        if (material != null) {
            materialCache.put(normalized, material);
        }
        return material;
    }

    /**
     * 判断材质是否可以作为一个「方块」被破坏并触发掉落。
     *
     * @param material 材质
     * @return true 表示可用于 blocks 节点
     */
    public boolean isValidBlock(Material material) {
        return material != null && material.isBlock() && !material.isAir();
    }

    /**
     * 把方块名解析为可用于 blocks 节点的 Material，并校验其是否为方块。
     *
     * @param name 方块名
     * @return Material；无效时返回 null
     */
    public Material resolveBlockMaterial(String name) {
        Material material = resolveMaterial(name);
        return isValidBlock(material) ? material : null;
    }

    // ------------------------------------------------------------------
    // 查询（事件热路径）
    // ------------------------------------------------------------------

    /**
     * 检查指定世界是否启用了本插件。
     */
    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    /**
     * 获取指定方块的自定义掉落配置。
     *
     * @param material 方块材质
     * @return BlockDropConfig；未配置时返回 null
     */
    public BlockDropConfig getDropConfig(Material material) {
        return blockConfigs.get(material);
    }

    /**
     * 事件热路径专用：一次性完成「世界检查 + 方块配置检查 + 启用检查」。
     *
     * @param worldName 世界名
     * @param material  方块材质
     * @return 可用配置；不满足条件时返回 null
     */
    public BlockDropConfig getActiveConfig(String worldName, Material material) {
        if (!enabledWorlds.contains(worldName)) {
            return null;
        }
        BlockDropConfig config = blockConfigs.get(material);
        if (config == null || !config.isEnabled()) {
            return null;
        }
        if (config.getDrops().isEmpty()) {
            return null;
        }
        return config;
    }

    public Map<Material, BlockDropConfig> getAllConfigs() {
        return Collections.unmodifiableMap(blockConfigs);
    }

    /** @return 已配置的方块数量 */
    public int getBlockCount() {
        return blockConfigs.size();
    }

    /** @return 已启用的世界名集合（只读） */
    public Set<String> getEnabledWorlds() {
        return Collections.unmodifiableSet(enabledWorlds);
    }

    /** @return 所有已配置方块的配置文件节点名，按字母序 */
    public List<String> getBlockKeys() {
        List<String> keys = new ArrayList<>(new LinkedHashSet<>(blockKeys.values()));
        keys.sort(String.CASE_INSENSITIVE_ORDER);
        return keys;
    }

    /**
     * 获取某方块在配置文件中的原始节点名。
     *
     * @param material 方块材质
     * @return 节点名；未配置时返回 null
     */
    public String getBlockKey(Material material) {
        return blockKeys.get(material);
    }

    /**
     * 查找配置文件中已存在的方块节点名（忽略大小写）。
     *
     * @param name 任意写法的方块名
     * @return 已存在的节点名；不存在时返回 null
     */
    public String findExistingBlockKey(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        String wanted = name.trim();
        ConfigurationSection blocks = plugin.getConfig().getConfigurationSection("blocks");
        if (blocks == null) {
            return null;
        }
        for (String key : blocks.getKeys(false)) {
            if (key.equalsIgnoreCase(wanted)) {
                return key;
            }
        }
        return null;
    }

    /**
     * @return 所有已配置方块的掉落项总数
     */
    public int getTotalDropCount() {
        int total = 0;
        for (BlockDropConfig config : blockConfigs.values()) {
            total += config.getDropCount();
        }
        return total;
    }

    // ------------------------------------------------------------------
    // 写入（命令使用）
    // ------------------------------------------------------------------

    /**
     * 获取（不存在则创建）某个方块在配置中的节点名。
     *
     * @param material 方块材质
     * @param preferredKey 首选节点名（通常来自命令输入）
     * @return 实际使用的节点名
     */
    public String getOrCreateBlockKey(Material material, String preferredKey) {
        String existing = blockKeys.get(material);
        if (existing != null) {
            return existing;
        }
        String found = findExistingBlockKey(preferredKey);
        if (found != null) {
            return found;
        }
        return material.name();
    }

    /**
     * 保存配置并重新编译内存中的配置表。
     *
     * @param debugTag 调试标签，用于日志
     */
    public void saveAndReload(String debugTag) {
        try {
            plugin.saveConfig();
        } catch (Exception e) {
            plugin.getLogger().log(Level.WARNING, "保存配置失败 (" + debugTag + ")", e);
        }
        if (plugin instanceof CustomDropsPlugin) {
            ((CustomDropsPlugin) plugin).reloadConfigData();
        } else {
            plugin.reloadConfig();
            loadConfig();
        }
    }
}

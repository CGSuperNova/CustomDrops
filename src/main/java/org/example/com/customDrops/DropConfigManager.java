package org.example.com.customDrops;

import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class DropConfigManager {

    private final JavaPlugin plugin;
    private Set<String> enabledWorlds = new HashSet<>();
    private Map<Material, BlockDropConfig> blockConfigs = new HashMap<>();

    public DropConfigManager(JavaPlugin plugin) {
        this.plugin = plugin;
    }

    /**
     * 加载配置文件，解析所有方块掉落配置
     */
    public void loadConfig() {
        FileConfiguration config = plugin.getConfig();
        enabledWorlds.clear();
        enabledWorlds.addAll(config.getStringList("enabled-worlds"));

        blockConfigs.clear();
        ConfigurationSection blocksSection = config.getConfigurationSection("blocks");
        if (blocksSection == null) {
            plugin.getLogger().warning("配置中未找到 'blocks' 节点，跳过加载");
            return;
        }

        for (String blockName : blocksSection.getKeys(false)) {
            Material material = Material.getMaterial(blockName.toUpperCase());
            if (material == null) {
                plugin.getLogger().warning("无效的方块类型: " + blockName);
                continue;
            }

            ConfigurationSection blockCfg = blocksSection.getConfigurationSection(blockName);
            boolean overrideDefault = blockCfg.getBoolean("override-default", true);

            List<DropEntry> drops = new ArrayList<>();
            List<Map<?, ?>> dropList = blockCfg.getMapList("drops");

            if (dropList.isEmpty()) {
                plugin.getLogger().warning("方块 " + blockName + " 的掉落列表为空，已跳过");
                continue;
            }

            for (Map<?, ?> dropMap : dropList) {
                // 1. 物品类型
                String itemName = (String) dropMap.get("item");
                if (itemName == null) {
                    plugin.getLogger().warning("掉落项缺少 'item' 字段 (方块: " + blockName + ")，已跳过此掉落项");
                    continue;
                }
                Material itemMat = Material.getMaterial(itemName.toUpperCase());
                if (itemMat == null) {
                    plugin.getLogger().warning("无效的物品类型: " + itemName + " (方块: " + blockName + ")");
                    continue;
                }

                // 2. 基础数量（默认1）
                Object amountObj = dropMap.get("amount");
                int baseAmount = (amountObj instanceof Number) ? ((Number) amountObj).intValue() : 1;
                if (baseAmount < 1) baseAmount = 1;

                // 3. 基础概率（优先 base-chance，兼容旧字段 chance）
                double baseChance = 1.0;
                Object baseChanceObj = dropMap.get("base-chance");
                if (baseChanceObj instanceof Number) {
                    baseChance = ((Number) baseChanceObj).doubleValue();
                } else {
                    Object chanceObj = dropMap.get("chance");
                    if (chanceObj instanceof Number) {
                        baseChance = ((Number) chanceObj).doubleValue();
                    }
                }
                baseChance = Math.min(1.0, Math.max(0.0, baseChance));

                // 4. 是否按每个物品执行一次命令（默认 false）
                boolean execPerItem = false;
                Object execObj = dropMap.get("execute-command-per-item");
                if (execObj instanceof Boolean) {
                    execPerItem = (Boolean) execObj;
                }

                // 5. 是否受时运影响数量（默认 true）
                boolean fortuneAffectsCount = true;
                Object fortuneObj = dropMap.get("fortune-affects-count");
                if (fortuneObj instanceof Boolean) {
                    fortuneAffectsCount = (Boolean) fortuneObj;
                }

                // 6. 命令列表解析
                List<CommandEntry> commands = new ArrayList<>();
                Object commandsObj = dropMap.get("commands");

                if (commandsObj instanceof List) {
                    // 新格式：commands 列表
                    List<?> rawList = (List<?>) commandsObj;
                    for (Object cmdObj : rawList) {
                        String command = null;
                        CommandEntry.ExecutorType executor = CommandEntry.ExecutorType.CONSOLE;

                        if (cmdObj instanceof String) {
                            // 简写：仅命令字符串，默认控制台执行
                            command = (String) cmdObj;
                        } else if (cmdObj instanceof Map) {
                            // 完整格式：{ command: "...", executor: "CONSOLE|PLAYER|OP" }
                            Map<?, ?> cmdMap = (Map<?, ?>) cmdObj;
                            command = (String) cmdMap.get("command");
                            String execStr = (String) cmdMap.get("executor");
                            if (execStr != null) {
                                try {
                                    executor = CommandEntry.ExecutorType.valueOf(execStr.toUpperCase());
                                } catch (IllegalArgumentException e) {
                                    plugin.getLogger().warning("无效的 executor 类型: " + execStr +
                                            " (方块: " + blockName + ")，使用 CONSOLE 代替");
                                }
                            }
                        }

                        if (command != null && !command.isEmpty()) {
                            commands.add(new CommandEntry(command, executor));
                        }
                    }
                } else if (dropMap.containsKey("command")) {
                    // 兼容旧配置：单条命令，默认控制台执行
                    String oldCmd = (String) dropMap.get("command");
                    if (oldCmd != null && !oldCmd.isEmpty()) {
                        commands.add(new CommandEntry(oldCmd, CommandEntry.ExecutorType.CONSOLE));
                    }
                }

                // 7. 创建 DropEntry 对象
                DropEntry entry = new DropEntry(
                        new ItemStack(itemMat, baseAmount),
                        baseChance,
                        execPerItem,
                        fortuneAffectsCount,
                        commands
                );
                drops.add(entry);
            }

            // 创建方块配置
            BlockDropConfig blockConfig = new BlockDropConfig(overrideDefault, drops);
            blockConfigs.put(material, blockConfig);
            plugin.getLogger().info("已加载方块掉落配置: " + blockName +
                    " (覆盖原版=" + overrideDefault + ", 掉落项数=" + drops.size() + ")");
        }
    }

    /**
     * 检查指定世界是否启用了本插件
     * @param worldName 世界名称
     * @return true 表示启用
     */
    public boolean isWorldEnabled(String worldName) {
        return enabledWorlds.contains(worldName);
    }

    /**
     * 获取指定方块的自定义掉落配置
     * @param material 方块材质
     * @return BlockDropConfig 对象，若未配置则返回 null
     */
    public BlockDropConfig getDropConfig(Material material) {
        return blockConfigs.get(material);
    }
}
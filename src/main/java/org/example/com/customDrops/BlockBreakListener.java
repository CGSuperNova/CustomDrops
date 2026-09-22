package org.example.com.customDrops;

import me.clip.placeholderapi.PlaceholderAPI;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 方块破坏监听器。
 * <p>
 * 性能要点（1.1.0 优化）：
 * <ul>
 *   <li>使用 {@link EventPriority#HIGH}（而非 HIGHEST），避免在已被其它插件取消的事件上做无用功，
 *       同时也不再覆盖其它插件在 HIGHEST/MONITOR 上看到的掉落物状态。</li>
 *   <li>所有附魔对象在启用时解析一次并缓存，事件中不再做注册表查询。</li>
 *   <li>世界判断、方块配置判断、禁用判断合并为一次 Map 查找（{@link DropConfigManager#getActiveConfig}）。</li>
 *   <li>掉落物不再提前 clone；只在真正投放到世界时创建 {@link ItemStack}。</li>
 *   <li>同一方块掉落的全部物品合并到 <b>一次</b> 调度任务中处理（命令/经济），
 *       从「掉落项 × 命令数」次调度降为 1 次。</li>
 *   <li>未配置的目标方块以最短路径返回，不产生任何对象分配与日志开销。</li>
 * </ul>
 */
public class BlockBreakListener implements Listener {

    private final CustomDropsPlugin plugin;

    /** 在启用阶段解析一次的附魔对象，避免事件中反复查表 */
    private final Enchantment fortuneEnchantment;
    private final Enchantment silkTouchEnchantment;

    public BlockBreakListener(CustomDropsPlugin plugin) {
        this.plugin = plugin;
        this.fortuneEnchantment = resolveEnchantment("fortune");
        this.silkTouchEnchantment = resolveEnchantment("silk_touch");
        if (fortuneEnchantment == null || silkTouchEnchantment == null) {
            plugin.getLogger().warning("无法解析时运/精准采集附魔对象，相关判断将回退为默认值"
                    + "（时运 0 级、无精准采集）");
        }
    }

    /**
     * 解析原版附魔。
     * <p>
     * 优先使用较新服务端（1.20+）的 {@link org.bukkit.Registry} 接口，
     * 在不支持的服务端上回退到已弃用但仍可用的 {@code Enchantment#getByKey}，
     * 从而同时兼容 1.17 ~ 最新版本，并且不会在热路径上重复查表。
     *
     * @param key 附魔键名（如 {@code fortune}）
     * @return 附魔对象；无法解析时返回 null
     */
    private static Enchantment resolveEnchantment(String key) {
        NamespacedKey namespacedKey = NamespacedKey.minecraft(key);
        try {
            Enchantment modern = org.bukkit.Registry.ENCHANTMENT.get(namespacedKey);
            if (modern != null) {
                return modern;
            }
        } catch (Throwable ignored) {
            // 旧服务端没有 Registry.ENCHANTMENT，走下面的回退分支
        }
        return legacyEnchantmentLookup(namespacedKey);
    }

    @SuppressWarnings("deprecation")
    private static Enchantment legacyEnchantmentLookup(NamespacedKey key) {
        return Enchantment.getByKey(key);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Block block = event.getBlock();
        String worldName = block.getWorld().getName();

        // 最快路径退出：世界未启用 / 方块无配置 / 方块已禁用
        BlockDropConfig blockConfig = plugin.getDropConfigManager().getActiveConfig(worldName, block.getType());
        if (blockConfig == null) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 跳过 " + block.getType().name() + " @ " + worldName
                        + "（世界未启用 / 无配置 / 已禁用 / 无掉落项）");
            }
            return;
        }

        Player player = event.getPlayer();

        // 读取时运与精准采集（附魔对象已缓存）
        int fortuneLevel = 0;
        boolean hasSilkTouch = false;
        ItemStack tool = player.getInventory().getItemInMainHand();
        if (fortuneEnchantment != null) {
            fortuneLevel = tool.getEnchantmentLevel(fortuneEnchantment);
        }
        if (silkTouchEnchantment != null) {
            hasSilkTouch = tool.containsEnchantment(silkTouchEnchantment);
        }

        boolean debug = plugin.isDebug();
        if (debug) {
            plugin.getLogger().info("[Debug] 玩家 " + player.getName() + " 破坏了 " + block.getType().name()
                    + " @ " + worldName + " " + formatLocation(block.getLocation())
                    + " (时运=" + fortuneLevel + ", 精准采集=" + hasSilkTouch + ")");
        }

        // 是否覆盖原版掉落（精准采集保留原版时不覆盖）
        boolean shouldOverride = blockConfig.isOverrideDefault()
                && !(hasSilkTouch && blockConfig.isSilkTouchPreserveOriginal());
        if (shouldOverride) {
            event.setDropItems(false);
            event.setExpToDrop(0);
        }

        World world = block.getWorld();
        Location dropLocation = block.getLocation().add(0.5D, 0.5D, 0.5D);

        // 需要在下一 tick 统一处理的任务（命令 / 经济），仅在确有内容时才创建
        List<Runnable> deferredTasks = null;
        List<CommandExecution> commandExecutions = null;

        for (DropEntry entry : blockConfig.getDrops()) {
            // 精准采集跳过判断
            if (hasSilkTouch && entry.isSilkTouchIgnore()) {
                if (debug) {
                    plugin.getLogger().info("[Debug] 精准采集，跳过掉落项 " + entry.getDisplayName());
                }
                continue;
            }

            // 权限判断（未配置 permission 时无需任何权限）
            String permission = entry.getPermission();
            if (permission != null && !player.hasPermission(permission)) {
                if (debug) {
                    plugin.getLogger().info("[Debug] 玩家缺少权限 " + permission + "，跳过掉落项 "
                            + entry.getDisplayName());
                }
                continue;
            }

            // 概率判定（100% 掉落且时运不影响概率时跳过随机数）
            double chance = entry.computeChance(fortuneLevel);
            boolean dropHappens = entry.isGuaranteed() || ThreadLocalRandom.current().nextDouble() < chance;
            if (debug) {
                plugin.getLogger().info("[Debug] 掉落项 " + entry.getDisplayName()
                        + " 概率=" + chance + " 判定=" + dropHappens);
            }
            if (!dropHappens) {
                continue;
            }

            // 最终数量
            int baseAmount = entry.getBaseAmount();
            int multiplier = entry.isFortuneAffectsCount() ? FortuneMultiplier.getMultiplier(fortuneLevel) : 1;
            int finalAmount = baseAmount * multiplier;

            // 投放物品（数量可能超过最大堆叠，需要拆栈）
            dropItems(world, dropLocation, entry, finalAmount);

            if (debug) {
                plugin.getLogger().info("[Debug] 已掉落 " + finalAmount + " 个 " + entry.getDisplayName());
            }

            // 经验
            if (entry.getExp() > 0) {
                int finalExp = (int) (entry.getExp() * (1 + fortuneLevel * entry.getExpMultiplier()));
                if (finalExp > 0) {
                    player.giveExp(finalExp);
                    if (debug) {
                        plugin.getLogger().info("[Debug] 给予经验 " + finalExp);
                    }
                }
            }

            // 金币（异步安全：经济 API 在下一 tick 主线程执行）
            if (entry.getMoney() > 0) {
                if (plugin.getEconomy() != null) {
                    double finalMoney = entry.getMoney() * (1 + fortuneLevel * entry.getMoneyMultiplier());
                    if (finalMoney > 0) {
                        if (deferredTasks == null) {
                            deferredTasks = new ArrayList<>(4);
                        }
                        final double money = finalMoney;
                        deferredTasks.add(() -> {
                            plugin.getEconomy().depositPlayer(player, money);
                            if (plugin.isDebug()) {
                                plugin.getLogger().info("[Debug] 给予金币 " + money);
                            }
                        });
                    }
                } else if (debug) {
                    plugin.getLogger().warning("无法给予金币：Vault 未加载或没有经济插件");
                }
            }

            // 掉落命令
            List<CommandEntry> commands = entry.getCommands();
            if (commands.isEmpty()) {
                continue;
            }
            int executionTimes = entry.isExecuteCommandPerItem() ? finalAmount : 1;
            if (commandExecutions == null) {
                commandExecutions = new ArrayList<>(4);
            }
            for (int i = 0; i < executionTimes; i++) {
                for (CommandEntry cmdEntry : commands) {
                    commandExecutions.add(new CommandExecution(player, cmdEntry));
                }
            }
        }

        // 统一提交：每个方块破坏最多只产生一次调度
        if (deferredTasks == null && commandExecutions == null) {
            return;
        }
        final List<Runnable> tasks = deferredTasks;
        final List<CommandExecution> executions = commandExecutions;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (tasks != null) {
                for (Runnable task : tasks) {
                    try {
                        task.run();
                    } catch (Exception e) {
                        plugin.getLogger().warning("执行金币发放任务时出错: " + e.getMessage());
                    }
                }
            }
            if (executions != null) {
                for (CommandExecution execution : executions) {
                    execution.dispatch(plugin);
                }
            }
        });
    }

    /**
     * 把掉落物投放进世界（超过最大堆叠时拆分为多份）。
     */
    @SuppressWarnings("deprecation") // Material#getMaxStackSize 在旧 API 上仍是最稳定的取法
    private static void dropItems(World world, Location location, DropEntry entry, int amount) {
        int remaining = amount;
        int maxStackSize = Math.max(1, entry.getMaterial().getMaxStackSize());
        while (remaining > 0) {
            int stackSize = Math.min(remaining, maxStackSize);
            world.dropItemNaturally(location, entry.createItemStack(stackSize));
            remaining -= stackSize;
        }
    }

    private static String formatLocation(Location location) {
        return "(" + location.getBlockX() + ", " + location.getBlockY() + ", " + location.getBlockZ() + ")";
    }

    /**
     * 一条待执行的命令（玩家 + 命令配置）。
     */
    private static final class CommandExecution {
        private final Player player;
        private final CommandEntry entry;

        private CommandExecution(Player player, CommandEntry entry) {
            this.player = player;
            this.entry = entry;
        }

        private void dispatch(CustomDropsPlugin plugin) {
            String command = entry.getCommand().replace("%player%", player.getName());
            // 仅当 PAPI 可用且执行者为 CONSOLE 时才解析占位符
            if (entry.getExecutor() == CommandEntry.ExecutorType.CONSOLE && plugin.isPapiAvailable()) {
                try {
                    command = PlaceholderAPI.setPlaceholders(player, command);
                } catch (Throwable throwable) {
                    plugin.getLogger().warning("解析 PAPI 占位符失败: " + throwable.getMessage());
                }
            }

            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 执行命令: " + command + " (执行者=" + entry.getExecutor() + ")");
            }

            try {
                switch (entry.getExecutor()) {
                    case CONSOLE:
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                        break;
                    case PLAYER:
                        player.performCommand(command);
                        break;
                    case OP:
                        boolean wasOp = player.isOp();
                        if (!wasOp) {
                            player.setOp(true);
                        }
                        try {
                            player.performCommand(command);
                        } finally {
                            if (!wasOp) {
                                player.setOp(false);
                            }
                        }
                        break;
                    default:
                        break;
                }
            } catch (Exception e) {
                plugin.getLogger().warning("执行掉落命令失败 [" + command + "]: " + e.getMessage());
            }
        }
    }
}

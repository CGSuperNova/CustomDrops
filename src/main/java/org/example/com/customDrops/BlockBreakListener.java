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

import java.util.Random;

public class BlockBreakListener implements Listener {

    private final CustomDropsPlugin plugin;
    private final Random random = new Random();

    public BlockBreakListener(CustomDropsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        Block block = event.getBlock();
        World world = block.getWorld();
        String worldName = world.getName();

        if (plugin.isDebug()) {
            plugin.getLogger().info("[Debug] 玩家 " + player.getName() + " 在 " + worldName +
                    " 破坏了 " + block.getType().name() + " (坐标: " + block.getLocation() + ")");
        }

        if (!plugin.getDropConfigManager().isWorldEnabled(worldName)) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 世界 " + worldName + " 未启用，跳过处理");
            }
            return;
        }

        BlockDropConfig blockConfig = plugin.getDropConfigManager().getDropConfig(block.getType());
        if (blockConfig == null) {
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 方块 " + block.getType().name() + " 无自定义掉落配置");
            }
            return;
        }

        // 获取时运等级和精准采集状态
        int fortuneLevel = 0;
        boolean hasSilkTouch = false;
        ItemStack tool = player.getInventory().getItemInMainHand();
        Enchantment fortuneEnchant = Enchantment.getByKey(NamespacedKey.minecraft("fortune"));
        if (fortuneEnchant != null && tool.containsEnchantment(fortuneEnchant)) {
            fortuneLevel = tool.getEnchantmentLevel(fortuneEnchant);
        }
        Enchantment silkTouchEnchant = Enchantment.getByKey(NamespacedKey.minecraft("silk_touch"));
        if (silkTouchEnchant != null && tool.containsEnchantment(silkTouchEnchant)) {
            hasSilkTouch = true;
        }

        if (plugin.isDebug()) {
            plugin.getLogger().info("[Debug] 时运等级: " + fortuneLevel + ", 精准采集: " + hasSilkTouch);
        }

        // 是否覆盖原版掉落（考虑精准采集保留原版）
        boolean shouldOverride = blockConfig.isOverrideDefault() &&
                !(hasSilkTouch && blockConfig.isSilkTouchPreserveOriginal());
        if (shouldOverride) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 已覆盖原版掉落");
            }
        }

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        for (DropEntry entry : blockConfig.getDrops()) {
            // 精准采集跳过判断
            if (hasSilkTouch && entry.isSilkTouchIgnore()) {
                if (plugin.isDebug()) {
                    plugin.getLogger().info("[Debug] 精准采集跳过掉落项: " + entry.getItemStack().getType().name());
                }
                continue;
            }

            // 概率判定
            double chance = entry.getBaseChance();
            boolean dropHappens = random.nextDouble() < chance;
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 物品 " + entry.getItemStack().getType().name() +
                        " 基础概率=" + chance + ", 判定结果=" + dropHappens);
            }
            if (!dropHappens) continue;

            // 计算最终数量
            int baseAmount = entry.getBaseAmount();
            int multiplier = entry.isFortuneAffectsCount() ? FortuneMultiplier.getMultiplier(fortuneLevel) : 1;
            int finalAmount = baseAmount * multiplier;
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 基础数量=" + baseAmount +
                        ", 倍率=" + multiplier + ", 最终数量=" + finalAmount);
            }

            // 掉落物品（如果数量超过64，拆分）
            ItemStack dropItem = entry.getItemStack();
            int remaining = finalAmount;
            while (remaining > 0) {
                int stackSize = Math.min(remaining, 64);
                ItemStack stack = dropItem.clone();
                stack.setAmount(stackSize);
                world.dropItemNaturally(dropLoc, stack);
                remaining -= stackSize;
            }
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 已掉落 " + finalAmount + " 个 " + dropItem.getType().name());
            }

            // ---- 经验和金币处理（存款异步） ----
            if (entry.getExp() > 0) {
                double expMultiplier = entry.getExpMultiplier();
                int finalExp = (int)(entry.getExp() * (1 + fortuneLevel * expMultiplier));
                if (finalExp > 0) {
                    player.giveExp(finalExp);
                    if (plugin.isDebug()) {
                        plugin.getLogger().info("[Debug] 给予 " + finalExp + " 经验值");
                    }
                }
            }

            if (entry.getMoney() > 0 && plugin.getEconomy() != null) {
                double moneyMultiplier = entry.getMoneyMultiplier();
                double finalMoney = entry.getMoney() * (1 + fortuneLevel * moneyMultiplier);
                if (finalMoney > 0) {
                    // 存款延迟到下一 tick 执行，避免阻塞事件
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        plugin.getEconomy().depositPlayer(player, finalMoney);
                        if (plugin.isDebug()) {
                            plugin.getLogger().info("[Debug] 给予 " + finalMoney + " 金币");
                        }
                    });
                }
            } else if (entry.getMoney() > 0 && plugin.getEconomy() == null && plugin.isDebug()) {
                plugin.getLogger().warning("无法给予金币：Vault 未加载或没有经济插件");
            }

            // 执行命令（延迟到下一 tick）
            java.util.List<CommandEntry> commands = entry.getCommands();
            if (commands.isEmpty()) continue;

            int commandExecutionTimes = entry.isExecuteCommandPerItem() ? finalAmount : 1;
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 命令执行次数: " + commandExecutionTimes +
                        " (每物品=" + entry.isExecuteCommandPerItem() + ")");
            }

            for (int i = 0; i < commandExecutionTimes; i++) {
                for (CommandEntry cmdEntry : commands) {
                    String rawCommand = cmdEntry.getCommand().replace("%player%", player.getName());
                    String finalCommand;
                    // 仅当 PAPI 可用且执行者为 CONSOLE 时才解析
                    if (cmdEntry.getExecutor() == CommandEntry.ExecutorType.CONSOLE && plugin.isPapiAvailable()) {
                        finalCommand = PlaceholderAPI.setPlaceholders(player, rawCommand);
                    } else {
                        finalCommand = rawCommand;
                    }

                    if (plugin.isDebug()) {
                        plugin.getLogger().info("[Debug] 将执行命令: " + finalCommand +
                                " (执行者=" + cmdEntry.getExecutor() + ")");
                    }

                    // 延迟执行命令
                    Bukkit.getScheduler().runTask(plugin, () -> {
                        switch (cmdEntry.getExecutor()) {
                            case CONSOLE:
                                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), finalCommand);
                                break;
                            case PLAYER:
                                player.performCommand(finalCommand);
                                break;
                            case OP:
                                boolean wasOp = player.isOp();
                                if (!wasOp) player.setOp(true);
                                player.performCommand(finalCommand);
                                if (!wasOp) player.setOp(false);
                                break;
                        }
                    });
                }
            }
        }
    }
}
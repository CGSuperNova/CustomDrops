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
import java.util.logging.Level;

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

        // Debug: 世界检查
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

        // 获取时运等级
        int fortuneLevel = 0;
        ItemStack tool = player.getInventory().getItemInMainHand();
        Enchantment fortuneEnchant = Enchantment.getByKey(NamespacedKey.minecraft("fortune"));
        if (fortuneEnchant != null && tool.containsEnchantment(fortuneEnchant)) {
            fortuneLevel = tool.getEnchantmentLevel(fortuneEnchant);
        }
        if (plugin.isDebug()) {
            plugin.getLogger().info("[Debug] 时运等级: " + fortuneLevel);
        }

        // 是否覆盖原版掉落
        if (blockConfig.isOverrideDefault()) {
            event.setDropItems(false);
            event.setExpToDrop(0);
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 已覆盖原版掉落");
            }
        }

        Location dropLoc = block.getLocation().add(0.5, 0.5, 0.5);

        for (DropEntry entry : blockConfig.getDrops()) {
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

            // 掉落物品
            ItemStack dropItem = entry.getItemStack();
            dropItem.setAmount(finalAmount);
            world.dropItemNaturally(dropLoc, dropItem);
            if (plugin.isDebug()) {
                plugin.getLogger().info("[Debug] 已掉落 " + finalAmount + " 个 " + dropItem.getType().name());
            }

            // 执行命令
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
                    if (cmdEntry.getExecutor() == CommandEntry.ExecutorType.CONSOLE) {
                        finalCommand = PlaceholderAPI.setPlaceholders(player, rawCommand);
                    } else {
                        finalCommand = rawCommand;
                    }

                    if (plugin.isDebug()) {
                        plugin.getLogger().info("[Debug] 执行命令: " + finalCommand +
                                " (执行者=" + cmdEntry.getExecutor() + ")");
                    }

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
                }
            }
        }
    }
}
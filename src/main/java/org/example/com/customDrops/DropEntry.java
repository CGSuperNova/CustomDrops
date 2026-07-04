package org.example.com.customDrops;

import org.bukkit.inventory.ItemStack;
import java.util.Collections;
import java.util.List;

public class DropEntry {
    private final ItemStack itemStack;
    private final double baseChance;
    private final boolean executeCommandPerItem;
    private final boolean fortuneAffectsCount;   // 新增：是否受时运影响数量
    private final List<CommandEntry> commands;

    // 构造器增加 fortuneAffectsCount 参数
    public DropEntry(ItemStack itemStack, double baseChance, boolean executeCommandPerItem,
                     boolean fortuneAffectsCount, List<CommandEntry> commands) {
        this.itemStack = itemStack.clone();
        this.baseChance = baseChance;
        this.executeCommandPerItem = executeCommandPerItem;
        this.fortuneAffectsCount = fortuneAffectsCount;
        this.commands = commands != null ? commands : Collections.emptyList();
    }

    public ItemStack getItemStack() { return itemStack.clone(); }
    public double getBaseChance() { return baseChance; }
    public boolean isExecuteCommandPerItem() { return executeCommandPerItem; }
    public boolean isFortuneAffectsCount() { return fortuneAffectsCount; }
    public List<CommandEntry> getCommands() { return commands; }
    public int getBaseAmount() { return itemStack.getAmount(); }
}
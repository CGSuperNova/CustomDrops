package org.example.com.customDrops;

import org.bukkit.inventory.ItemStack;
import java.util.Collections;
import java.util.List;

public class DropEntry {
    private final ItemStack itemStack;
    private final double baseChance;
    private final boolean executeCommandPerItem;
    private final boolean fortuneAffectsCount;
    private final List<CommandEntry> commands;

    private final int exp;
    private final double money;
    private final double expMultiplier;   // 每级时运增加的经验百分比（0.5 = 50%）
    private final double moneyMultiplier; // 每级时运增加的金币百分比

    public DropEntry(ItemStack itemStack, double baseChance, boolean executeCommandPerItem,
                     boolean fortuneAffectsCount, List<CommandEntry> commands,
                     int exp, double money, double expMultiplier, double moneyMultiplier) {
        this.itemStack = itemStack.clone();
        this.baseChance = baseChance;
        this.executeCommandPerItem = executeCommandPerItem;
        this.fortuneAffectsCount = fortuneAffectsCount;
        this.commands = commands != null ? commands : Collections.emptyList();
        this.exp = exp;
        this.money = money;
        this.expMultiplier = expMultiplier;
        this.moneyMultiplier = moneyMultiplier;
    }

    public ItemStack getItemStack() { return itemStack.clone(); }
    public double getBaseChance() { return baseChance; }
    public boolean isExecuteCommandPerItem() { return executeCommandPerItem; }
    public boolean isFortuneAffectsCount() { return fortuneAffectsCount; }
    public List<CommandEntry> getCommands() { return commands; }
    public int getBaseAmount() { return itemStack.getAmount(); }

    public int getExp() { return exp; }
    public double getMoney() { return money; }
    public double getExpMultiplier() { return expMultiplier; }
    public double getMoneyMultiplier() { return moneyMultiplier; }
}
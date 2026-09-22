package org.example.com.customDrops;

import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个「特殊掉落物」配置。
 * <p>
 * 该类为不可变对象。为了性能考虑，内部只保存 {@link Material} 与数量，
 * 仅在真正需要把物品丢到世界里时才创建 {@link ItemStack}（见 {@link #createItemStack(int)}）。
 */
public class DropEntry {

    private final Material material;
    private final int baseAmount;
    private final double baseChance;
    private final boolean executeCommandPerItem;
    private final boolean fortuneAffectsCount;
    private final List<CommandEntry> commands;

    private final int exp;
    private final double money;
    private final double expMultiplier;   // 每级时运增加的经验百分比（0.5 = 50%）
    private final double moneyMultiplier; // 每级时运增加的金币百分比
    private final double fortuneChanceBonus; // 每级时运增加的概率
    private final boolean silkTouchIgnore;

    /** 触发该掉落所需权限，null 或空字符串表示无需权限 */
    private final String permission;

    public DropEntry(Material material, int baseAmount, double baseChance, boolean executeCommandPerItem,
                     boolean fortuneAffectsCount, List<CommandEntry> commands,
                     int exp, double money, double expMultiplier, double moneyMultiplier,
                     double fortuneChanceBonus, boolean silkTouchIgnore, String permission) {
        this.material = material;
        this.baseAmount = Math.max(1, baseAmount);
        this.baseChance = clampChance(baseChance);
        this.executeCommandPerItem = executeCommandPerItem;
        this.fortuneAffectsCount = fortuneAffectsCount;
        this.commands = commands == null || commands.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(commands));
        this.exp = Math.max(0, exp);
        this.money = Math.max(0.0D, money);
        this.expMultiplier = Math.max(0.0D, expMultiplier);
        this.moneyMultiplier = Math.max(0.0D, moneyMultiplier);
        this.fortuneChanceBonus = Math.max(0.0D, fortuneChanceBonus);
        this.silkTouchIgnore = silkTouchIgnore;
        this.permission = (permission == null || permission.isBlank()) ? null : permission.trim();
    }

    /**
     * 兼容旧构造器（1.0.x 的 10 参数版本），新字段使用默认值。
     */
    public DropEntry(ItemStack itemStack, double baseChance, boolean executeCommandPerItem,
                     boolean fortuneAffectsCount, List<CommandEntry> commands,
                     int exp, double money, double expMultiplier, double moneyMultiplier,
                     boolean silkTouchIgnore) {
        this(itemStack.getType(), itemStack.getAmount(), baseChance, executeCommandPerItem,
                fortuneAffectsCount, commands, exp, money, expMultiplier, moneyMultiplier,
                0.0D, silkTouchIgnore, null);
    }

    private static double clampChance(double value) {
        if (Double.isNaN(value)) {
            return 0.0D;
        }
        return Math.min(1.0D, Math.max(0.0D, value));
    }

    /**
     * 创建一个指定数量的物品实例。这是本类中唯一会创建 {@link ItemStack} 的地方，
     * 避免在事件热路径上做无谓的对象分配与 clone。
     */
    public ItemStack createItemStack(int amount) {
        return new ItemStack(material, Math.max(1, amount));
    }

    /**
     * 计算在指定时运等级下的最终掉落概率。
     */
    public double computeChance(int fortuneLevel) {
        if (fortuneLevel <= 0 || fortuneChanceBonus <= 0.0D) {
            return baseChance;
        }
        return clampChance(baseChance + fortuneChanceBonus * fortuneLevel);
    }

    /**
     * 判断该掉落项是否必定掉落。
     * 基础概率为 100% 且时运不会继续提升概率时，可以直接跳过随机数生成开销。
     */
    public boolean isGuaranteed() {
        return baseChance >= 1.0D && fortuneChanceBonus <= 0.0D;
    }

    public Material getMaterial() { return material; }

    public int getBaseAmount() { return baseAmount; }

    public double getBaseChance() { return baseChance; }

    public boolean isExecuteCommandPerItem() { return executeCommandPerItem; }

    public boolean isFortuneAffectsCount() { return fortuneAffectsCount; }

    public List<CommandEntry> getCommands() { return commands; }

    public int getExp() { return exp; }

    public double getMoney() { return money; }

    public double getExpMultiplier() { return expMultiplier; }

    public double getMoneyMultiplier() { return moneyMultiplier; }

    public double getFortuneChanceBonus() { return fortuneChanceBonus; }

    public boolean isSilkTouchIgnore() { return silkTouchIgnore; }

    /** @return 所需权限节点，null 表示无需权限 */
    public String getPermission() { return permission; }

    public boolean hasPermissionRequirement() { return permission != null; }

    /** @return 掉落物的展示名称（方便日志与命令输出） */
    public String getDisplayName() {
        return material.name();
    }

    @Override
    public String toString() {
        return "DropEntry{" + material + " x" + baseAmount + " @" + baseChance + '}';
    }
}

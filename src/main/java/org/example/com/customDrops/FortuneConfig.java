package org.example.com.customDrops;

public class FortuneConfig {
    private final double maxChance;
    private final double incrementPerLevel;

    public FortuneConfig(double maxChance, double incrementPerLevel) {
        this.maxChance = Math.min(maxChance, 0.999); // 永远小于100%
        this.incrementPerLevel = incrementPerLevel;
    }

    public double computeChance(double baseChance, int fortuneLevel) {
        // 线性公式: base + fortuneLevel * increment
        double chance = baseChance + fortuneLevel * incrementPerLevel;
        if (chance > maxChance) chance = maxChance;
        if (chance < 0) chance = 0;
        return chance;
    }
}
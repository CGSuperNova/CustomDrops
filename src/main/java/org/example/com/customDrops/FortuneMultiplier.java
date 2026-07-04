package org.example.com.customDrops;

import java.util.Random;

public class FortuneMultiplier {
    private static final Random random = new Random();

    /**
     * 根据时运等级计算掉落数量倍率。
     * 等级0：固定1倍
     * 等级1：1倍(权重2)，2倍(权重1)
     * 等级2：1倍(权重2)，2倍(权重1)，3倍(权重1)
     * 等级3：1倍(权重2)，2倍(权重1)，3倍(权重1)，4倍(权重1)
     * 更高等级类推：倍率范围1~(L+1)，倍率1权重2，其余权重1。
     */
    public static int getMultiplier(int fortuneLevel) {
        if (fortuneLevel <= 0) {
            return 1;
        }
        int maxMultiplier = fortuneLevel + 1;
        // 总权重 = 2（倍率1） + (maxMultiplier - 1)（倍率2到maxMultiplier各1）
        int totalWeight = 2 + (maxMultiplier - 1); // = maxMultiplier + 1
        int roll = random.nextInt(totalWeight);
        if (roll < 2) {
            return 1; // 倍率1，权重2
        } else {
            // roll在2到totalWeight-1之间，对应倍率2到maxMultiplier
            return roll - 1; // roll=2 => 2, roll=3 => 3, ...
        }
    }
}
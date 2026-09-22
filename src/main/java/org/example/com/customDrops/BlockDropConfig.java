package org.example.com.customDrops;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 单个方块的特殊掉落配置。
 */
public class BlockDropConfig {

    private final boolean enabled;
    private final boolean overrideDefault;
    private final boolean silkTouchPreserveOriginal;
    private final List<DropEntry> drops;

    public BlockDropConfig(boolean enabled, boolean overrideDefault, boolean silkTouchPreserveOriginal,
                           List<DropEntry> drops) {
        this.enabled = enabled;
        this.overrideDefault = overrideDefault;
        this.silkTouchPreserveOriginal = silkTouchPreserveOriginal;
        this.drops = drops == null || drops.isEmpty()
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(drops));
    }

    /** 兼容 1.0.x 构造器：默认启用 */
    public BlockDropConfig(boolean overrideDefault, boolean silkTouchPreserveOriginal, List<DropEntry> drops) {
        this(true, overrideDefault, silkTouchPreserveOriginal, drops);
    }

    /** 是否启用该方块的特殊掉落（enabled: true/false） */
    public boolean isEnabled() {
        return enabled;
    }

    public boolean isOverrideDefault() {
        return overrideDefault;
    }

    public boolean isSilkTouchPreserveOriginal() {
        return silkTouchPreserveOriginal;
    }

    public List<DropEntry> getDrops() {
        return drops;
    }

    public int getDropCount() {
        return drops.size();
    }

    /**
     * @return 该方块配置中需要权限才能触发的掉落项数量
     */
    public int getPermissionDropCount() {
        int count = 0;
        for (DropEntry entry : drops) {
            if (entry.hasPermissionRequirement()) {
                count++;
            }
        }
        return count;
    }
}

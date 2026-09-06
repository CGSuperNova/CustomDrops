package org.example.com.customDrops;

import java.util.List;

public class BlockDropConfig {
    private final boolean overrideDefault;
    private final boolean silkTouchPreserveOriginal;
    private final List<DropEntry> drops;

    public BlockDropConfig(boolean overrideDefault, boolean silkTouchPreserveOriginal, List<DropEntry> drops) {
        this.overrideDefault = overrideDefault;
        this.silkTouchPreserveOriginal = silkTouchPreserveOriginal;
        this.drops = drops;
    }

    public boolean isOverrideDefault() {
        return overrideDefault;
    }

    public List<DropEntry> getDrops() {
        return drops;
    }
    public boolean isSilkTouchPreserveOriginal() { return silkTouchPreserveOriginal; }
}
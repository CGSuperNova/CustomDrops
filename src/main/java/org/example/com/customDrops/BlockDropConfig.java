package org.example.com.customDrops;

import java.util.List;

public class BlockDropConfig {
    private final boolean overrideDefault;
    private final List<DropEntry> drops;

    public BlockDropConfig(boolean overrideDefault, List<DropEntry> drops) {
        this.overrideDefault = overrideDefault;
        this.drops = drops;
    }

    public boolean isOverrideDefault() {
        return overrideDefault;
    }

    public List<DropEntry> getDrops() {
        return drops;
    }
}
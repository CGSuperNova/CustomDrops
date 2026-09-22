package org.example.com.customDrops;

/**
 * 单条掉落命令配置。
 * 同时兼容两种写法：
 * <pre>
 * commands:
 *   - command: "say hello %player%"
 *     executor: CONSOLE
 * </pre>
 * 或简写为纯字符串（默认以 CONSOLE 身份执行）：
 * <pre>
 * commands:
 *   - "say hello %player%"
 * </pre>
 */
public class CommandEntry {

    private final String command;
    private final ExecutorType executor;

    public CommandEntry(String command, ExecutorType executor) {
        this.command = command == null ? "" : command;
        this.executor = executor == null ? ExecutorType.CONSOLE : executor;
    }

    public CommandEntry(String command) {
        this(command, ExecutorType.CONSOLE);
    }

    public String getCommand() {
        return command;
    }

    public ExecutorType getExecutor() {
        return executor;
    }

    /**
     * 解析执行者名称。
     *
     * @param raw 原始字符串，可为 null
     * @return 解析结果，无法识别时返回 null（由调用方决定回退策略）
     */
    public static ExecutorType parseExecutor(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        for (ExecutorType type : ExecutorType.values()) {
            if (type.name().equalsIgnoreCase(raw.trim())) {
                return type;
            }
        }
        return null;
    }

    @Override
    public String toString() {
        return command + "@" + executor;
    }

    public enum ExecutorType {
        /** 由控制台执行（支持 PlaceholderAPI 占位符解析） */
        CONSOLE,
        /** 由触发玩家执行（以玩家自身权限为准） */
        PLAYER,
        /** 临时提升为 OP 执行，执行完毕后立即恢复原状态 */
        OP
    }
}

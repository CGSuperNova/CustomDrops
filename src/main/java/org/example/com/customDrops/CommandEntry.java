package org.example.com.customDrops;

public class CommandEntry {
    private final String command;
    private final ExecutorType executor;

    public CommandEntry(String command, ExecutorType executor) {
        this.command = command;
        this.executor = executor;
    }

    public String getCommand() {
        return command;
    }

    public ExecutorType getExecutor() {
        return executor;
    }

    public enum ExecutorType {
        CONSOLE, PLAYER, OP
    }
}
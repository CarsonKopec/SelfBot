package com.github.imagineforgee.selfbotlib.commands;

import java.util.concurrent.ConcurrentHashMap;
import java.util.Map;

public class CommandContextStore {
    private static final Map<String, CommandContext> contextMap = new ConcurrentHashMap<>();

    public static void put(String id, CommandContext context) {
        contextMap.put(id, context);
    }

    public static CommandContext get(String id) {
        return contextMap.get(id);
    }

    public static void remove(String id) {
        contextMap.remove(id);
    }
}

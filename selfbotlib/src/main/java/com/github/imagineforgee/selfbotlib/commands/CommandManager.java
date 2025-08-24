package com.github.imagineforgee.selfbotlib.commands;

import com.clawsoftstudios.purrfectlib.scanner.CommandInfo;
import com.github.imagineforgee.selfbotlib.dispatch.EventDispatcher;
import com.google.gson.JsonObject;

import java.lang.reflect.InvocationTargetException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class CommandManager {

    private final Map<String, CommandInfo> commands = new HashMap<>();

    public void registerCommands(List<CommandInfo> commandList) {
        for (CommandInfo cmd : commandList) {
            String key = cmd.getGroups().isEmpty()
                    ? cmd.getName()
                    : cmd.getGroups().getFirst() + ":" + cmd.getName();

            commands.put(key.toLowerCase(), cmd);
        }
    }

    public void handleCommand(String rawCommand, CommandContext context) {
        String userId  = context.getAuthorId();
        String guildId = context.getGuildId();
        EventDispatcher dispatcher = context.getBot().getDispatcher();

        String contextId = UUID.randomUUID().toString();
        CommandContextStore.put(contextId, context);

        JsonObject commandData = new JsonObject();
        commandData.addProperty("userId", userId);
        commandData.addProperty("guildId", guildId);
        commandData.addProperty("commandName", rawCommand);
        commandData.addProperty("contextId", contextId);

        JsonObject simulatedPayload = new JsonObject();
        simulatedPayload.addProperty("t", "COMMAND_EXECUTE");
        simulatedPayload.add("d", commandData);

        dispatcher.dispatch(simulatedPayload);

        String lookupKey = rawCommand.toLowerCase();
        CommandInfo cmd = commands.get(lookupKey);

        if (cmd == null) {
            context.reply("❌ Unknown command: " + rawCommand);
            return;
        }

        try {
            Object cmdInstance = cmd.getCommandClass().getDeclaredConstructor().newInstance();

            String[] parts = lookupKey.split(":");
            String group = parts.length > 1 ? parts[0] : "general";
            String action = parts.length > 1 ? parts[1] : lookupKey;

            context.setGroup(group);
            context.setAction(action);

            cmd.getCommandClass()
                    .getMethod("execute", CommandContext.class)
                    .invoke(cmdInstance, context);

        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            context.reply("❌ Failed to execute command: " + rawCommand);
        }
    }
}

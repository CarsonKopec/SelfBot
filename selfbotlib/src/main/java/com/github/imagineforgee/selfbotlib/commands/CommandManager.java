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
            commands.put(cmd.getName(), cmd);
        }
    }

    public void handleCommand(String commandName, String args, CommandContext context) {
        String userId  = context.getAuthorId();
        String guildId = context.getGuildId();
        EventDispatcher dispatcher = context.getBot().getDispatcher();

        String contextId = UUID.randomUUID().toString();
        CommandContextStore.put(contextId, context);

        JsonObject commandData = new JsonObject();
        commandData.addProperty("userId", userId);
        commandData.addProperty("guildId", guildId);
        commandData.addProperty("commandName", commandName);
        commandData.addProperty("contextId", contextId);

        JsonObject simulatedPayload = new JsonObject();
        simulatedPayload.addProperty("t", "COMMAND_EXECUTE");
        simulatedPayload.add("d", commandData);

        dispatcher.dispatch(simulatedPayload);

        CommandInfo cmd = commands.get(commandName);
        if (cmd == null) {
            context.reply("Unknown command: " + commandName);
            return;
        }

        try {
            Object cmdInstance = cmd.getCommandClass().getDeclaredConstructor().newInstance();
            CommandArgs parsedArgs = new CommandArgs(args);
            cmd.getCommandClass()
                    .getMethod("execute", CommandArgs.class, CommandContext.class)
                    .invoke(cmdInstance, parsedArgs, context);

        } catch (NoSuchMethodException | IllegalAccessException | InstantiationException | InvocationTargetException e) {
            e.printStackTrace();
            context.reply("Failed to execute command: " + commandName);
        }
    }
}

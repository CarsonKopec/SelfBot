package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.commands.CommandContextStore;
import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.google.gson.JsonObject;

public class CommandExecuteEvent implements Event {
    private final JsonObject data;

    public CommandExecuteEvent(JsonObject data) {
        this.data = data;
    }
    @Override
    public String getType() {
        return "COMMAND_EXECUTE";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public String getUserId() {
        return data.get("userId").getAsString();
    }

    public String getGuildId() {
        if (data.has("guildId") && !data.get("guildId").isJsonNull()) {
            return data.get("guildId").getAsString();
        }
        return "";
    }

    public String getContextId() {
        return data.get("contextId").getAsString();
    }

    public CommandContext getContext() {
        return CommandContextStore.get(getContextId());
    }
}

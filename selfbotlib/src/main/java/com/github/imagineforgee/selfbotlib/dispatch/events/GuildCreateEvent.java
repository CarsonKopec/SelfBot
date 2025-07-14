package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class GuildCreateEvent implements Event {
    private final JsonObject data;

    public GuildCreateEvent(JsonObject data) {
        this.data = data;
    }

    @Override
    public String getType() {
        return "GUILD_CREATE";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public JsonArray getVoiceStates() {
        return data.has("voice_states") ? data.getAsJsonArray("voice_states") : new JsonArray();
    }

    public String getGuildId() {
        return data.get("id").getAsString();
    }
}

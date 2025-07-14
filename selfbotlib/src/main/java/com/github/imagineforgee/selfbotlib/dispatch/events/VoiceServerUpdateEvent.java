package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.google.gson.JsonObject;

public class VoiceServerUpdateEvent implements Event {

    private final JsonObject data;
    private final String token;
    private final String guildId;
    private final String endpoint;

    public VoiceServerUpdateEvent(JsonObject data) {
        this.data = data;

        this.token = data.has("token") && !data.get("token").isJsonNull()
                ? data.get("token").getAsString()
                : null;
        this.guildId = data.has("guild_id") && !data.get("guild_id").isJsonNull()
                ? data.get("guild_id").getAsString()
                : null;
        this.endpoint = data.has("endpoint") && !data.get("endpoint").isJsonNull()
                ? data.get("endpoint").getAsString()
                : null;
    }

    @Override
    public String getType() {
        return "VOICE_SERVER_UPDATE";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public String getToken() {
        return token;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getEndpoint() {
        return endpoint;
    }
}
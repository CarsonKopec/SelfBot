package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.google.gson.JsonObject;

public class VoiceStateUpdateEvent implements Event {

    private final JsonObject data;
    private final String userId;
    private final String guildId;
    private final String channelId;
    private final String sessionId;
    private final boolean selfMute;
    private final boolean selfDeaf;

    public VoiceStateUpdateEvent(JsonObject data) {
        this.data = data;
        this.userId = data.get("user_id").getAsString();
        this.guildId = data.has("guild_id") && !data.get("guild_id").isJsonNull()
                ? data.get("guild_id").getAsString()
                : null;
        this.channelId = data.has("channel_id") && !data.get("channel_id").isJsonNull()
                ? data.get("channel_id").getAsString()
                : null;
        this.sessionId = data.has("session_id") && !data.get("session_id").isJsonNull()
                ? data.get("session_id").getAsString()
                : null;
        this.selfMute = data.has("self_mute") && data.get("self_mute").getAsBoolean();
        this.selfDeaf = data.has("self_deaf") && data.get("self_deaf").getAsBoolean();
    }

    @Override
    public String getType() {
        return "VOICE_STATE_UPDATE";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public String getUserId() {
        return userId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getSessionId() {
        return sessionId;
    }

    public boolean isSelfMute() {
        return selfMute;
    }

    public boolean isSelfDeaf() {
        return selfDeaf;
    }
}

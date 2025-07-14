package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.github.imagineforgee.selfbotlib.util.VoiceStateRegistry;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

public class MessageCreateEvent implements Event {

    private final JsonObject data;

    public MessageCreateEvent(JsonObject data) {
        this.data = data;
    }

    @Override
    public String getType() {
        return "MESSAGE_CREATE";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public String getContent() {
        return data.has("content") ? data.get("content").getAsString() : "";
    }

    public String getChannelId() {
        return data.has("channel_id") ? data.get("channel_id").getAsString() : null;
    }

    public String getAuthorId() {
        return data.has("author") ? data.getAsJsonObject("author").get("id").getAsString() : null;
    }

    public String getMessageId() {
        return data.get("id").getAsString();
    }

    public String getUserVoiceChannelId() {
        return VoiceStateRegistry.get(this.getAuthorId());
    }

    public String getGuildId() {
        return data.has("guild_id") ? data.get("guild_id").getAsString() : null;
    }

    public List<String> getMentionedUserIds() {
        if (!data.has("mentions")) return Collections.emptyList();
        JsonArray mentions = data.getAsJsonArray("mentions");
        return StreamSupport.stream(mentions.spliterator(), false)
                .map(e -> e.getAsJsonObject().get("id").getAsString())
                .collect(Collectors.toList());
    }

    public boolean mentionsUser(String userId) {
        return getMentionedUserIds().contains(userId);
    }
}

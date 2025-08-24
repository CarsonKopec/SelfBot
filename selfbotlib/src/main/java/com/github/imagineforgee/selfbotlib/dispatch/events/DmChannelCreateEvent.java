package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

public class DmChannelCreateEvent implements Event {
    private final JsonObject data;

    public DmChannelCreateEvent(JsonObject data) {
        this.data = data;
    }

    @Override
    public String getType() {
        return "PRIVATE_CHANNEL_CREATE";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public String getChannelId() {
        return data.get("id").getAsString();
    }

    public int getChannelTypeRaw() {
        return data.get("type").getAsInt();
    }

    public String getOwnerId() {
        return data.has("owner_id") ? data.get("owner_id").getAsString() : null;
    }

    public JsonArray getRecipients() {
        return data.getAsJsonArray("recipients");
    }

    public ChannelType getChannelType() {
        return switch (getChannelTypeRaw()) {
            case 1 -> ChannelType.DIRECT_MESSAGE;
            case 3 -> ChannelType.GROUP_DM;
            default -> ChannelType.UNKNOWN;
        };
    }

    public enum ChannelType {
        DIRECT_MESSAGE,
        GROUP_DM,
        UNKNOWN
    }
}

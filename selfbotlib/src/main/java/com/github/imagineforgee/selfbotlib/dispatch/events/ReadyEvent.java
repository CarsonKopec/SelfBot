package com.github.imagineforgee.selfbotlib.dispatch.events;

import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.google.gson.JsonObject;

public class ReadyEvent implements Event {
    private final JsonObject data;
    private final String userId;

    public ReadyEvent(JsonObject data) {
        this.data = data;
        this.userId = data.getAsJsonObject("user")
                .get("id").getAsString();
    }

    @Override
    public String getType() {
        return "READY";
    }

    @Override
    public JsonObject getData() {
        return data;
    }

    public String getUserId() {
        return userId;
    }
}

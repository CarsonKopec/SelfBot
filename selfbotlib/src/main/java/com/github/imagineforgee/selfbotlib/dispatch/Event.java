package com.github.imagineforgee.selfbotlib.dispatch;

import com.google.gson.JsonObject;

public interface Event {
    /**
     * The raw event type from Discord's gateway payload (e.g., MESSAGE_CREATE, READY)
     */
    String getType();

    /**
     * The raw JSON payload (`d` from the gateway packet)
     */
    JsonObject getData();
}

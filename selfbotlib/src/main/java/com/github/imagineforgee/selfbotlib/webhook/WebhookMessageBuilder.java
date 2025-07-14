package com.github.imagineforgee.selfbotlib.webhook;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.File;

public class WebhookMessageBuilder {

    private final JsonObject payload = new JsonObject();
    private final JsonArray embeds = new JsonArray();

    private File file;
    private String fileName;


    public WebhookMessageBuilder content(String content) {
        payload.addProperty("content", content);
        return this;
    }

    public WebhookMessageBuilder username(String username) {
        payload.addProperty("username", username);
        return this;
    }

    public WebhookMessageBuilder avatarUrl(String url) {
        payload.addProperty("avatar_url", url);
        return this;
    }

    public WebhookMessageBuilder addEmbed(JsonObject embed) {
        embeds.add(embed);
        return this;
    }


    public WebhookMessageBuilder withFile(File file, String fileName) {
        this.file = file;
        this.fileName = fileName;
        return this;
    }

    public JsonObject buildJson() {
        if (!embeds.isEmpty()) {
            payload.add("embeds", embeds);
        }
        System.out.println(payload);
        return payload;
    }

    public boolean hasFile() {
        return file != null;
    }

    public File getFile() {
        return file;
    }

    public String getFileName() {
        return fileName;
    }
}

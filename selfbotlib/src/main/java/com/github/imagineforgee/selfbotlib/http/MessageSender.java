package com.github.imagineforgee.selfbotlib.http;

import com.google.gson.JsonObject;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class MessageSender {

    private static final String BASE_URL = "https://discord.com/api/v10";
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String token;

    public MessageSender(String token) {
        this.token = token;
    }

    /**
     * Basic text message send.
     */
    public CompletableFuture<Void> sendMessage(String channelId, String content) {
        return sendMessage(channelId, content, null);
    }

    /**
     * Text message with optional reply.
     */
    public CompletableFuture<Void> sendMessage(String channelId, String content, String replyToMsgId) {
        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("content", content);

        if (replyToMsgId != null) {
            JsonObject reference = new JsonObject();
            reference.addProperty("message_id", replyToMsgId);
            reference.addProperty("channel_id", channelId);
            bodyJson.add("message_reference", reference);
        }

        return executeRequest(channelId, bodyJson);
    }

    /**
     * Send a fully-custom payload (e.g. poll, embed, attachment, etc).
     * @param channelId the target channel
     * @param payload   the exact JSON you want to POST under {"content", "poll", ...}
     */
    public CompletableFuture<Void> sendRawMessage(String channelId, JsonObject payload) {
        return executeRequest(channelId, payload);
    }

    /**
     * Internal helper to build & enqueue the HTTP request from a JsonObject.
     */
    private CompletableFuture<Void> executeRequest(String channelId, JsonObject bodyJson) {
        CompletableFuture<Void> future = new CompletableFuture<>();

        RequestBody body = RequestBody.create(
                bodyJson.toString(),
                MediaType.parse("application/json")
        );
        Request request = new Request.Builder()
                .url(BASE_URL + "/channels/" + channelId + "/messages")
                .header("Authorization", token)
                .header("Content-Type", "application/json")
                .post(body)
                .build();

        httpClient.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(Call call, IOException e) {
                future.completeExceptionally(e);
            }
            @Override
            public void onResponse(Call call, Response response) throws IOException {
                String resp = response.body() != null ? response.body().string() : "";
                if (!response.isSuccessful()) {
                    System.err.println("Discord error " + response.code() + ": " + resp);
                    future.completeExceptionally(new IOException("Failed: " + response.code()));
                } else {
                    future.complete(null);
                }
            }
        });

        return future;
    }
}

package com.github.imagineforgee.selfbotlib.http;

import com.google.gson.*;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class DmAndChannelService {

    private static final String BASE_URL = "https://discord.com/api/v10";
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String token;

    public DmAndChannelService(String token) {
        this.token = token;
    }

    public CompletableFuture<String> createDmChannel(String recipientId) {
        CompletableFuture<String> future = new CompletableFuture<>();

        JsonObject bodyJson = new JsonObject();
        bodyJson.addProperty("recipient_id", recipientId);

        RequestBody body = RequestBody.create(bodyJson.toString(), MediaType.parse("application/json"));
        Request request = new Request.Builder()
                .url(BASE_URL + "/users/@me/channels")
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
                if (response.isSuccessful()) {
                    try (ResponseBody responseBody = response.body()) {
                        if (responseBody != null) {
                            JsonObject json = JsonParser.parseString(responseBody.string()).getAsJsonObject();
                            String channelId = json.get("id").getAsString();
                            future.complete(channelId);
                        } else {
                            future.completeExceptionally(new IOException("Empty response body"));
                        }
                    }
                } else {
                    future.completeExceptionally(new IOException("Failed to create DM: " + response.code()));
                }
            }
        });

        return future;
    }
}

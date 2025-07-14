package com.github.imagineforgee.selfbotlib.http;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;

import java.io.IOException;
import java.util.concurrent.CompletableFuture;

public class ChannelLookupService {

    private static final String BASE_URL = "https://discord.com/api/v10";
    private final OkHttpClient httpClient = new OkHttpClient();
    private final String token;

    public ChannelLookupService(String token) {
        this.token = token;
    }

    public CompletableFuture<JsonObject> getChannel(String channelId) {
        CompletableFuture<JsonObject> future = new CompletableFuture<>();

        Request request = new Request.Builder()
                .url(BASE_URL + "/channels/" + channelId)
                .header("Authorization", token)
                .get()
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
                            future.complete(json);
                        } else {
                            future.completeExceptionally(new IOException("Empty response body"));
                        }
                    }
                } else {
                    future.completeExceptionally(new IOException("Failed to fetch channel: " + response.code()));
                }
            }
        });

        return future;
    }

    public enum ChannelType {
        GUILD_TEXT,
        DM,
        GROUP_DM,
        UNKNOWN;

        public static ChannelType fromInt(int type) {
            return switch (type) {
                case 0 -> GUILD_TEXT;
                case 1 -> DM;
                case 3 -> GROUP_DM;
                default -> UNKNOWN;
            };
        }
    }

    public CompletableFuture<ChannelType> resolveChannelType(String channelId) {
        return getChannel(channelId)
                .thenApply(json -> {
                    if (json.has("type")) {
                        return ChannelType.fromInt(json.get("type").getAsInt());
                    } else {
                        return ChannelType.UNKNOWN;
                    }
                });
    }
}

package com.github.imagineforgee.selfbotlib.webhook;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import okhttp3.*;
import reactor.core.publisher.Mono;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

public class WebhookClient {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private static final MediaType OCTET_STREAM = MediaType.get("application/octet-stream");
    private final OkHttpClient httpClient = new OkHttpClient();

    private final String webhookId;
    private final String webhookToken;
    private final String baseUrl;

    public WebhookClient(String webhookId, String webhookToken) {
        this.webhookId = webhookId;
        this.webhookToken = webhookToken;
        this.baseUrl = String.format("https://discord.com/api/v10/webhooks/%s/%s?with_components=true", webhookId, webhookToken);
    }

    public static WebhookClient fromUrl(String url) {
        String[] parts = url.replace("https://discord.com/api/webhooks/", "").split("/");
        if (parts.length < 2) {
            throw new IllegalArgumentException("Invalid webhook URL");
        }
        return new WebhookClient(parts[0], parts[1]);
    }

    public Mono<JsonObject> send(JsonObject payload) {
        return sendRequest(buildPostRequest(baseUrl, payload));
    }

    public Mono<JsonObject> sendWithThread(JsonObject payload, String threadId) {
        String threadUrl = baseUrl + "?thread_id=" + threadId;
        return sendRequest(buildPostRequest(threadUrl, payload));
    }

    public Mono<JsonObject> editMessage(String messageId, JsonObject payload) {
        String editUrl = baseUrl + "/messages/" + messageId;
        Request request = new Request.Builder()
                .url(editUrl)
                .patch(RequestBody.create(payload.toString(), JSON))
                .build();
        return sendRequest(request);
    }

    public Mono<Void> deleteMessage(String messageId) {
        String deleteUrl = baseUrl + "/messages/" + messageId;
        Request request = new Request.Builder().url(deleteUrl).delete().build();
        return sendRequest(request).then();
    }

    public Mono<JsonObject> sendWithFile(JsonObject payload, File file, String fileName) {
        MultipartBody.Builder multipartBuilder = new MultipartBody.Builder()
                .setType(MultipartBody.FORM);

        // Add file part
        RequestBody fileBody;
        try {
            fileBody = RequestBody.create(Files.readAllBytes(file.toPath()), OCTET_STREAM);
        } catch (IOException e) {
            return Mono.error(e);
        }

        multipartBuilder.addFormDataPart("file", fileName, fileBody);
        multipartBuilder.addFormDataPart("payload_json", payload.toString());

        Request request = new Request.Builder()
                .url(baseUrl)
                .post(multipartBuilder.build())
                .build();

        return sendRequest(request);
    }

    private Request buildPostRequest(String url, JsonObject payload) {
        RequestBody body = RequestBody.create(payload.toString(), JSON);
        return new Request.Builder().url(url).post(body).build();
    }

    private Mono<JsonObject> sendRequest(Request request) {
        return Mono.create(sink -> {
            httpClient.newCall(request).enqueue(new Callback() {
                @Override
                public void onFailure(Call call, IOException e) {
                    sink.error(e);
                }

                @Override
                public void onResponse(Call call, Response response) {
                    try (ResponseBody responseBody = response.body()) {
                        String body = responseBody != null ? responseBody.string() : "";
                        JsonElement json = JsonParser.parseString(body.isEmpty() ? "{}" : body);
                        JsonObject result = new JsonObject();
                        result.addProperty("status", response.code());
                        result.add("body", json);

                        if (response.isSuccessful()) {
                            sink.success(result);
                        } else {
                            sink.error(new IOException("HTTP " + response.code() + ": " + body));
                        }
                    } catch (Exception e) {
                        sink.error(e);
                    }
                }
            });
        });
    }

    public String getWebhookId() {
        return webhookId;
    }

    public String getWebhookToken() {
        return webhookToken;
    }

    public String getBaseUrl() {
        return baseUrl;
    }
}

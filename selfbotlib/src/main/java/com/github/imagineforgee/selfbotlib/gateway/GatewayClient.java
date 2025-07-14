package com.github.imagineforgee.selfbotlib.gateway;

import com.google.gson.*;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import reactor.core.publisher.*;
import reactor.core.scheduler.Schedulers;

import java.net.URI;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicInteger;

public class GatewayClient {

    private final String token;
    private final URI gatewayUri = URI.create("wss://gateway.discord.gg/?v=10&encoding=json");
    private WebSocketClient client;

    private final Sinks.Many<JsonObject> sink = Sinks.many().multicast().onBackpressureBuffer();
    private final Flux<JsonObject> eventFlux = sink.asFlux().publishOn(Schedulers.boundedElastic());

    private Timer heartbeatTimer;
    private final AtomicInteger sequence = new AtomicInteger(-1);

    public GatewayClient(String token) {
        this.token = token;
    }

    public Flux<JsonObject> getEventFlux() {
        return eventFlux;
    }

    public Mono<Void> connect() {
        return Mono.create(sink -> {
            this.client = new WebSocketClient(gatewayUri) {
                @Override
                public void onOpen(ServerHandshake handshake) {
                    System.out.println("[Gateway] Connected.");
                }

                @Override
                public void onMessage(String message) {
                    JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                    if (json.has("s") && !json.get("s").isJsonNull()) {
                        sequence.set(json.get("s").getAsInt());
                    }

                    int op = json.get("op").getAsInt();
                    String t = json.has("t") && !json.get("t").isJsonNull() ? json.get("t").getAsString() : "UNKNOWN";
                    System.out.printf("[Gateway] Event: %s | Op: %d%n", t, op);
                    switch (op) {
                        case 10 -> handleHello(json.getAsJsonObject("d"));
                        case 11 -> System.out.println("[Gateway] Heartbeat ACK");
                        default -> this.sendToSink(json);
                    }
                }


                @Override
                public void onClose(int code, String reason, boolean remote) {
                    System.out.println("[Gateway] Disconnected: " + reason);
                    if (heartbeatTimer != null) heartbeatTimer.cancel();
                }

                @Override
                public void onError(Exception ex) {
                    ex.printStackTrace();
                }

                private void sendToSink(JsonObject payload) {
                    GatewayClient.this.sink.tryEmitNext(payload);
                }
            };

            try {
                client.connectBlocking();
                sink.success();
            } catch (InterruptedException e) {
                sink.error(e);
            }
        });
    }

    private void handleHello(JsonObject helloData) {
        int interval = helloData.get("heartbeat_interval").getAsInt();
        startHeartbeat(interval);
        sendIdentify();
    }

    private void startHeartbeat(int intervalMs) {
        heartbeatTimer = new Timer();
        heartbeatTimer.scheduleAtFixedRate(new TimerTask() {
            @Override
            public void run() {
                JsonObject heartbeat = new JsonObject();
                heartbeat.addProperty("op", 1);
                if (sequence.get() == -1) heartbeat.add("d", JsonNull.INSTANCE);
                else heartbeat.addProperty("d", sequence.get());
                client.send(heartbeat.toString());
                System.out.println("[Gateway] Heartbeat sent.");
            }
        }, 0, intervalMs);
    }

    public Mono<Void> send(JsonObject payload) {
        return Mono.create(sink -> {
            if (client != null && client.isOpen()) {
                client.send(payload.toString());
                sink.success();
            } else {
                sink.error(new IllegalStateException("WebSocket is not open"));
            }
        });
    }

    private void sendIdentify() {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 2);

        JsonObject data = new JsonObject();
        data.addProperty("token", token);

        JsonObject props = new JsonObject();
        props.addProperty("$os", "windows");
        props.addProperty("$browser", "userbot");
        props.addProperty("$device", "userbot");
        data.add("properties", props);

        data.addProperty("compress", false);
        data.addProperty("large_threshold", 250);

        payload.add("d", data);
        client.send(payload.toString());

        System.out.println("[Gateway] Identify sent.");
    }

    public String getToken() {
        return token;
    }
}
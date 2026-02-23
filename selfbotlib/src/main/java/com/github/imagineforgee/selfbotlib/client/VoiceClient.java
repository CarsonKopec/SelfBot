package com.github.imagineforgee.selfbotlib.client;

import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.dispatch.events.VoiceServerUpdateEvent;
import com.github.imagineforgee.selfbotlib.dispatch.events.VoiceStateUpdateEvent;
import com.github.imagineforgee.selfbotlib.gateway.GatewayClient;
import com.github.imagineforgee.selfbotlib.util.VoiceStateRegistry;
import com.github.imagineforgee.selfbotlib.video.VideoMode;
import com.github.imagineforgee.selfbotlib.voice.OpusUdpStreamer;
import com.github.imagineforgee.selfbotlib.voice.SpeakingFlag;
import com.github.imagineforgee.selfbotlib.voice.VoiceConnectionState;
import com.github.imagineforgee.selfbotlib.voice.VoiceMode;
import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonPrimitive;
import org.java_websocket.client.WebSocketClient;
import org.java_websocket.handshake.ServerHandshake;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.URI;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

public class VoiceClient {
    private final GatewayClient gateway;
    private final UserBotClient botClient;

    private final Sinks.Many<VoiceConnectionState> connectionStateSink = Sinks.many().replay().latest();
    private final Sinks.Many<String> heartbeatSink = Sinks.many().multicast().onBackpressureBuffer();
    private final Sinks.Many<JsonObject> voiceMessageSink = Sinks.many().multicast().onBackpressureBuffer();

    private final AtomicReference<VoiceConnectionState> currentState = new AtomicReference<>(new VoiceConnectionState());
    private final AtomicBoolean isConnecting = new AtomicBoolean(false);
    private final AtomicBoolean isConnected = new AtomicBoolean(false);
    private final AtomicBoolean initialized = new AtomicBoolean(false);
    private final AtomicInteger heartbeatSequence = new AtomicInteger(0);

    private final Map<String, VoiceMode> voiceModes = new ConcurrentHashMap<>();
    private final Map<String, VideoMode> videoModes = new ConcurrentHashMap<>();
    private final AtomicReference<String> activeVoiceModeId = new AtomicReference<>();
    private final AtomicReference<String> activeVideoModeId = new AtomicReference<>();

    private volatile WebSocketClient voiceSocket;
    private volatile DatagramSocket udp;
    private OpusUdpStreamer udpStreamer;

    private volatile String encryptionMode = "aead_xchacha20_poly1305_rtpsize";

    public VoiceClient(UserBotClient botClient) {
        this.botClient = botClient;
        this.gateway = botClient.getGatewayClient();
        setupReactiveEventHandling();
        setupHeartbeatHandling();
        setupVoiceMessageHandling();
    }

    private boolean isDmOrGroupChannel(String guildId) {
        return guildId == null;
    }

    private String getContextId(String guildId, String channelId) {
        return isDmOrGroupChannel(guildId) ? channelId : guildId;
    }

    public void registerVoiceMode(String modeId, VoiceMode voiceMode) {
        VoiceMode existing = voiceModes.put(modeId, voiceMode);
        if (existing != null) {
            System.out.println("[Voice] Replaced existing voice mode: " + modeId);
        }
        System.out.println("[Voice] Registered voice mode: " + modeId + " (" + voiceMode.getClass().getSimpleName() + ")");
    }

    public void registerVideoMode(String modeId, VideoMode videoMode) {
        VideoMode existing = videoModes.put(modeId, videoMode);
        if (existing != null) {
            System.out.println("[Voice] Replaced existing video mode: " + modeId);
            cleanupVideoMode(existing);
        }
        System.out.println("[Voice] Registered video mode: " + modeId + " (" + videoMode.getClass().getSimpleName() + ")");
    }

    public void switchToVoiceMode(String modeId) {
        VoiceMode newMode = voiceModes.get(modeId);
        if (newMode == null) {
            System.err.println("[Voice] Voice mode not found: " + modeId);
            return;
        }

        String currentModeId = activeVoiceModeId.get();
        if (modeId.equals(currentModeId)) {
            System.out.println("[Voice] Already using voice mode: " + modeId);
            return;
        }

        if (currentModeId != null) {
            VoiceMode currentMode = voiceModes.get(currentModeId);
            if (currentMode != null) {
                try {
                    currentMode.stop();
                    System.out.println("[Voice] Stopped voice mode: " + currentModeId);
                } catch (Exception e) {
                    System.err.println("[Voice] Error stopping voice mode " + currentModeId + ": " + e.getMessage());
                }
            }
        }

        try {
            activeVoiceModeId.set(modeId);
            VoiceConnectionState state = currentState.get();

            if (isConnected.get() && state.channelId != null) {
                newMode.joinChannel(state.guildId, state.channelId);

                if (udpStreamer != null) {
                    newMode.setUdpStreamer(udpStreamer);
                }
            }

            System.out.println("[Voice] Switched to voice mode: " + modeId);
        } catch (Exception e) {
            System.err.println("[Voice] Error switching to voice mode " + modeId + ": " + e.getMessage());
            activeVoiceModeId.set(currentModeId);
        }
    }

    public boolean switchToVideoMode(String modeId) {
        VideoMode newMode = videoModes.get(modeId);
        if (newMode == null) {
            System.err.println("[Voice] Video mode not found: " + modeId);
            return false;
        }

        String currentModeId = activeVideoModeId.get();
        if (modeId.equals(currentModeId)) {
            System.out.println("[Voice] Already using video mode: " + modeId);
            return true;
        }

        if (currentModeId != null) {
            VideoMode currentMode = videoModes.get(currentModeId);
            if (currentMode != null) {
                try {
                    currentMode.stop();
                    System.out.println("[Voice] Stopped video mode: " + currentModeId);
                } catch (Exception e) {
                    System.err.println("[Voice] Error stopping video mode " + currentModeId + ": " + e.getMessage());
                }
            }
        }

        try {
            activeVideoModeId.set(modeId);
            VoiceConnectionState state = currentState.get();

            if (isConnected.get() && state.channelId != null) {
                newMode.joinChannel(state.guildId, state.channelId);
            }

            System.out.println("[Voice] Switched to video mode: " + modeId);
            return true;
        } catch (Exception e) {
            System.err.println("[Voice] Error switching to video mode " + modeId + ": " + e.getMessage());
            activeVideoModeId.set(currentModeId);
            return false;
        }
    }

    public void unregisterVoiceMode(String modeId) {
        VoiceMode mode = voiceModes.remove(modeId);
        if (mode != null) {
            if (modeId.equals(activeVoiceModeId.get())) {
                activeVoiceModeId.set(null);
            }
            cleanupVoiceMode(mode);
            System.out.println("[Voice] Unregistered voice mode: " + modeId);
        }
    }

    public void unregisterVideoMode(String modeId) {
        VideoMode mode = videoModes.remove(modeId);
        if (mode != null) {
            if (modeId.equals(activeVideoModeId.get())) {
                activeVideoModeId.set(null);
            }
            cleanupVideoMode(mode);
            System.out.println("[Voice] Unregistered video mode: " + modeId);
        }
    }

    public Set<String> getRegisteredVoiceModes() {
        return new HashSet<>(voiceModes.keySet());
    }

    public Set<String> getRegisteredVideoModes() {
        return new HashSet<>(videoModes.keySet());
    }

    public String getActiveVoiceModeId() {
        return activeVoiceModeId.get();
    }

    public String getActiveVideoModeId() {
        return activeVideoModeId.get();
    }

    public VoiceMode getActiveVoiceModeModel() {
        String modeId = activeVoiceModeId.get();
        return modeId != null ? voiceModes.get(modeId) : null;
    }

    public String getActiveVoiceMode() {
        String modeId = activeVoiceModeId.get();
        return modeId;
    }

    public VideoMode getActiveVideoMode() {
        String modeId = activeVideoModeId.get();
        return modeId != null ? videoModes.get(modeId) : null;
    }

    private void cleanupVoiceMode(VoiceMode mode) {
        try {
            mode.stop();
        } catch (Exception e) {
            System.err.println("[Voice] Error cleaning up voice mode: " + e.getMessage());
        }
    }

    private void cleanupVideoMode(VideoMode mode) {
        try {
            mode.stop();
        } catch (Exception e) {
            System.err.println("[Voice] Error cleaning up video mode: " + e.getMessage());
        }
    }

    private void setupReactiveEventHandling() {
        Flux<VoiceConnectionState> serverUpdates = botClient.onEvent(VoiceServerUpdateEvent.class)
                .filter(e -> shouldProcessEvent(e.getGuildId()))
                .map(this::updateStateFromServerEvent)
                .doOnNext(state -> System.out.println("[Voice] Server update: " + state));

        Flux<VoiceConnectionState> stateUpdates = botClient.onEvent(VoiceStateUpdateEvent.class)
                .filter(e -> e.getUserId().equals(botClient.getSelfId()))
                .doOnNext(e -> VoiceStateRegistry.update(e.getUserId(), e.getChannelId()))
                .filter(e -> shouldProcessEvent(e.getGuildId()))
                .map(this::updateStateFromStateEvent)
                .doOnNext(state -> System.out.println("[Voice] State update: " + state));

        Flux.merge(serverUpdates, stateUpdates)
                .distinctUntilChanged()
                .filter(VoiceConnectionState::isReadyToConnect)
                .delayElements(Duration.ofMillis(500))
                .subscribe(this::handleConnectionStateChange,
                        error -> System.err.println("[Voice] Error in event handling: " + error.getMessage()));

        stateUpdates
                .filter(state -> state.channelId == null)
                .subscribe(state -> {
                    System.out.println("[Voice] Bot left voice channel, disconnecting");
                    disconnect().subscribe();
                });
    }

    private void setupHeartbeatHandling() {
        heartbeatSink.asFlux()
                .map(Integer::parseInt)
                .flatMap(interval ->
                        Flux.interval(Duration.ofMillis(interval))
                                .takeUntilOther(heartbeatSink.asFlux().skip(1))
                                .map(tick -> createHeartbeatPayload())
                )
                .filter(payload -> isConnected.get() && voiceSocket != null && voiceSocket.isOpen())
                .subscribe(this::sendHeartbeat,
                        error -> System.err.println("[Voice] Heartbeat error: " + error.getMessage()));
    }

    private void setupVoiceMessageHandling() {
        voiceMessageSink.asFlux()
                .subscribe(this::processVoiceMessage,
                        error -> System.err.println("[Voice] Message processing error: " + error.getMessage()));
    }

    private boolean shouldProcessEvent(String guildId) {
        VoiceConnectionState state = currentState.get();
        String currentContext = getContextId(state.guildId, state.channelId);
        String eventContext = getContextId(guildId, null);

        return currentContext == null ||
                currentContext.equals(eventContext) ||
                (state.guildId != null && state.guildId.equals(guildId)) ||
                (isDmOrGroupChannel(guildId) && isDmOrGroupChannel(state.guildId));
    }

    private VoiceConnectionState updateStateFromServerEvent(VoiceServerUpdateEvent event) {
        VoiceConnectionState oldState = currentState.get();
        VoiceConnectionState newState = oldState.withServerUpdate(
                event.getGuildId(),
                event.getToken(),
                event.getEndpoint() != null ? event.getEndpoint().split(":")[0] : null
        );
        currentState.set(newState);
        connectionStateSink.tryEmitNext(newState);
        return newState;
    }

    private VoiceConnectionState updateStateFromStateEvent(VoiceStateUpdateEvent event) {
        VoiceConnectionState oldState = currentState.get();
        VoiceConnectionState newState = oldState.withStateUpdate(
                event.getGuildId(),
                event.getChannelId(),
                event.getSessionId()
        );
        currentState.set(newState);
        connectionStateSink.tryEmitNext(newState);
        return newState;
    }

    private void handleConnectionStateChange(VoiceConnectionState state) {
        if (isConnecting.get() || isConnected.get()) {
            System.out.println("[Voice] Already connecting/connected, ignoring state change");
            return;
        }

        String channelType = isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";
        System.out.println("[Voice] Attempting " + channelType + " connection with state: " + state);

        connectToVoiceWebSocket(state)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        success -> System.out.println("[Voice] " + channelType + " connection established successfully"),
                        error -> {
                            System.err.println("[Voice] " + channelType + " connection failed: " + error.getMessage());
                            isConnecting.set(false);
                        }
                );
    }

    private Mono<Void> connectToVoiceWebSocket(VoiceConnectionState state) {
        return Mono.fromRunnable(() -> {
            if (!isConnecting.compareAndSet(false, true)) {
                return;
            }

            try {
                cleanup();
                URI uri = new URI("wss://" + state.endpoint + "/?v=4");
                String channelType = isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";
                System.out.println("[Voice] Connecting to " + channelType + " voice: " + uri);

                voiceSocket = new WebSocketClient(uri) {
                    @Override
                    public void onOpen(ServerHandshake handshake) {
                        System.out.println("[Voice] WebSocket connected (" + channelType + ")");
                        isConnecting.set(false);
                        isConnected.set(true);
                        sendVoiceIdentify(state);
                    }

                    @Override
                    public void onMessage(String message) {
                        try {
                            JsonObject json = JsonParser.parseString(message).getAsJsonObject();
                            voiceMessageSink.tryEmitNext(json);
                        } catch (Exception e) {
                            System.err.println("[Voice] Error parsing message: " + e.getMessage());
                        }
                    }

                    @Override
                    public void onClose(int code, String reason, boolean remote) {
                        System.out.println("[Voice] Disconnected (" + channelType + "): " + code + " - " + reason);
                        isConnected.set(false);
                        isConnecting.set(false);
                        initialized.set(false);

                        handleDisconnection(code, reason, state)
                                .subscribeOn(Schedulers.boundedElastic())
                                .subscribe();
                    }

                    @Override
                    public void onError(Exception ex) {
                        System.err.println("[Voice] WebSocket error (" + channelType + "): " + ex.getMessage());
                        isConnected.set(false);
                        isConnecting.set(false);
                    }
                };

                voiceSocket.setConnectionLostTimeout(30);
                voiceSocket.connect();

            } catch (Exception e) {
                isConnecting.set(false);
                throw new RuntimeException("Failed to connect to voice WebSocket", e);
            }
        });
    }

    private Mono<Void> handleDisconnection(int code, String reason, VoiceConnectionState state) {
        cleanup();
        return switch (code) {
            case 1006 -> {
                System.out.println("[Voice] Abnormal closure (1006) — reconnecting...");
                yield Mono.delay(Duration.ofSeconds(3))
                        .then(joinAndConnect(state.guildId, state.channelId))
                        .then();
            }
            case 4015 -> {
                System.out.println("[Voice] Voice server crashed, reconnecting...");
                yield Mono.delay(Duration.ofSeconds(5))
                        .then(connectToVoiceWebSocket(state))
                        .then();
            }
            case 4014 -> {
                System.out.println("[Voice] Disconnected from voice channel");
                yield Mono.empty();
            }
            default -> {
                System.out.println("[Voice] Disconnected: code=" + code + ", reason=" + reason);
                yield Mono.empty();
            }
        };
    }

    private void processVoiceMessage(JsonObject json) {
        int op = json.get("op").getAsInt();
        JsonObject d = json.has("d") && !json.get("d").isJsonNull() && json.get("d").isJsonObject()
                ? json.getAsJsonObject("d") : null;

        switch (op) {
            case 8: // HELLO
                if (d != null && d.has("heartbeat_interval")) {
                    int interval = d.get("heartbeat_interval").getAsInt();
                    System.out.println("[Voice] Starting heartbeat: " + interval + "ms");
                    heartbeatSink.tryEmitNext(String.valueOf(interval));
                }
                break;

            case 2: // READY
                if (d != null) {
                    handleVoiceReady(d);
                }
                break;

            case 4: // SESSION_DESCRIPTION
                if (d != null && d.has("secret_key")) {
                    handleSessionDescription(d.getAsJsonArray("secret_key"));
                }
                break;

            case 6: // HEARTBEAT_ACK
                System.out.println("[Voice] Heartbeat ACK");
                break;

            default:
                System.out.println("[Voice] Unknown opcode: " + op);
        }
    }


    private void handleVoiceReady(JsonObject d) {
        int ssrc = d.get("ssrc").getAsInt();
        String ip = d.get("ip").getAsString();
        int port = d.get("port").getAsInt();

        if (d.has("modes")) {
            List<String> preferred = List.of(
                    "aead_aes256_gcm_rtpsize",
                    "aead_xchacha20_poly1305_rtpsize"
            );
            d.getAsJsonArray("modes").forEach(m -> {
                String mode = m.getAsString();
                for (String p : preferred) {
                    if (p.equals(mode)) {
                        encryptionMode = p;
                        return;
                    }
                }
            });
        }
        System.out.println("[Voice] Selected encryption mode: " + encryptionMode);

        VoiceConnectionState state = currentState.get();
        VoiceConnectionState newState = state.withVoiceReady(ssrc, ip, port);
        currentState.set(newState);

        String channelType = isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";
        System.out.println("[Voice] " + channelType + " Voice READY - SSRC: " + ssrc + ", IP: " + ip + ", Port: " + port);

        performUdpDiscovery(ip, port, ssrc)
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(
                        success -> System.out.println("[Voice] UDP discovery completed"),
                        error -> System.err.println("[Voice] UDP discovery failed: " + error.getMessage())
                );
    }

    private Mono<Void> performUdpDiscovery(String ip, int port, int ssrc) {
        return Mono.fromCallable(() -> {
                    System.out.println("[Voice] Starting UDP discovery");

                    if (udp != null && !udp.isClosed()) {
                        udp.close();
                    }

                    udp = new DatagramSocket();
                    InetAddress address = InetAddress.getByName(ip);

                    byte[] packet = new byte[70];
                    packet[0] = (byte) ((ssrc >>> 24) & 0xFF);
                    packet[1] = (byte) ((ssrc >>> 16) & 0xFF);
                    packet[2] = (byte) ((ssrc >>> 8) & 0xFF);
                    packet[3] = (byte) (ssrc & 0xFF);

                    DatagramPacket sendPacket = new DatagramPacket(packet, packet.length, address, port);
                    udp.send(sendPacket);
                    udp.setSoTimeout(10000);

                    byte[] response = new byte[70];
                    DatagramPacket received = new DatagramPacket(response, response.length);
                    udp.receive(received);
                    StringBuilder ipBuilder = new StringBuilder();
                    for (int i = 4; i < 68 && response[i] != 0; i++) {
                        ipBuilder.append((char) (response[i] & 0xFF));
                    }

                    String discoveredIp = ipBuilder.toString();
                    int discoveredPort = ((response[68] & 0xFF) << 8) | (response[69] & 0xFF);

                    if (discoveredIp.isEmpty()) {
                        throw new RuntimeException("Empty IP discovered");
                    }

                    System.out.println("[Voice] Discovered IP: " + discoveredIp + ", Port: " + discoveredPort);
                    sendSelectProtocol(discoveredIp, discoveredPort);

                    return null;
                })
                .onErrorResume(error -> {
                    System.err.println("[Voice] UDP discovery failed: " + error.getMessage());
                    System.out.println("[Voice] Using fallback IP/port");
                    sendSelectProtocol(ip, port);
                    return Mono.empty();
                })
                .then();
    }

    private JsonObject createVoiceStatePayload(String guildId, String channelId) {
        JsonObject p = new JsonObject();
        p.addProperty("op", 4);
        JsonObject d = new JsonObject();

        if (isDmOrGroupChannel(guildId)) {
            d.add("guild_id", null);
        } else {
            d.addProperty("guild_id", guildId);
        }

        d.addProperty("channel_id", channelId);
        d.addProperty("self_mute", false);
        d.addProperty("self_deaf", false);
        p.add("d", d);
        return p;
    }

    public Mono<Void> joinAndConnect(String channelId) {
        return joinAndConnect(null, channelId);
    }

    public Mono<Void> joinAndConnect(String guildId, String channelId) {
        String channelType = isDmOrGroupChannel(guildId) ? "DM/Group" : "Guild";
        System.out.println("[Voice] Joining " + channelType + " voice channel: " + channelId);

        VoiceConnectionState base = new VoiceConnectionState().withTargetChannel(guildId, channelId);
        currentState.set(base);
        cleanup();

        Mono<Void> sendJoin = gateway.send(createVoiceStatePayload(guildId, channelId));

        Mono<VoiceStateUpdateEvent> gotState = botClient.onEvent(VoiceStateUpdateEvent.class)
                .filter(e -> e.getUserId().equals(botClient.getSelfId()))
                .filter(e -> {
                    if (isDmOrGroupChannel(guildId)) {
                        return channelId.equals(e.getChannelId()) && e.getGuildId() == null;
                    } else {
                        return guildId.equals(e.getGuildId()) && channelId.equals(e.getChannelId());
                    }
                })
                .next();

        Mono<VoiceServerUpdateEvent> gotServer = botClient.onEvent(VoiceServerUpdateEvent.class)
                .filter(e -> {
                    if (isDmOrGroupChannel(guildId)) {
                        return e.getGuildId() == null || channelId.equals(e.getGuildId());
                    } else {
                        return guildId.equals(e.getGuildId());
                    }
                })
                .next();

        return sendJoin.then(Mono.zip(gotState, gotServer))
                .flatMap(tuple -> {
                    VoiceStateUpdateEvent state = tuple.getT1();
                    VoiceServerUpdateEvent server = tuple.getT2();

                    VoiceConnectionState full = base
                            .withStateUpdate(guildId, channelId, state.getSessionId())
                            .withServerUpdate(guildId, server.getToken(), server.getEndpoint());

                    currentState.set(full);
                    return connectToVoiceWebSocket(full).then(Mono.fromRunnable(() -> {
                        notifyModesOfChannelJoin(guildId, channelId);
                    }));
                });
    }

    private void notifyModesOfChannelJoin(String guildId, String channelId) {
        VoiceMode activeVoice = getActiveVoiceModeModel();
        if (activeVoice != null && isConnected.get()) {
            try {
                activeVoice.joinChannel(guildId, channelId);
                if (udpStreamer != null) {
                    activeVoice.setUdpStreamer(udpStreamer);
                }
            } catch (Exception e) {
                System.err.println("[Voice] Error notifying voice mode of channel join: " + e.getMessage());
            }
        }

        VideoMode activeVideo = getActiveVideoMode();
        if (activeVideo != null && isConnected.get()) {
            try {
                activeVideo.joinChannel(guildId, channelId);
            } catch (Exception e) {
                System.err.println("[Voice] Error notifying video mode of channel join: " + e.getMessage());
            }
        }
    }

    public Mono<Void> leaveVoice() {
        VoiceConnectionState state = currentState.get();
        if (isDmOrGroupChannel(state.guildId)) {
            return leaveVoice(null);
        } else {
            return leaveVoice(state.guildId);
        }
    }

    public Mono<Void> leaveVoice(String guildId) {
        return disconnect()
                .then(Mono.fromRunnable(() -> {
                    JsonObject payload = new JsonObject();
                    payload.addProperty("op", 4);
                    JsonObject d = new JsonObject();

                    if (isDmOrGroupChannel(guildId)) {
                        d.add("guild_id", null);
                    } else {
                        d.addProperty("guild_id", guildId);
                    }

                    d.add("channel_id", null);
                    d.addProperty("self_mute", false);
                    d.addProperty("self_deaf", false);
                    payload.add("d", d);

                    gateway.send(payload).subscribe();
                }));
    }

    public Mono<Void> disconnect() {
        return Mono.fromRunnable(() -> {
            isConnected.set(false);
            isConnecting.set(false);
            activeVoiceModeId.set(null);
            activeVideoModeId.set(null);
            initialized.set(false);
            currentState.set(new VoiceConnectionState());
            cleanup();
        });
    }
    
    public void stop() {
        VoiceMode activeVoice = getActiveVoiceModeModel();
        if (activeVoice != null) {
            System.out.println("[Voice] Stopping active VoiceMode: " + activeVoiceModeId.get());
            activeVoice.stop();
        }

        VideoMode activeVideo = getActiveVideoMode();
        if (activeVideo != null) {
            System.out.println("[Voice] Stopping active VideoMode: " + activeVideoModeId.get());
            activeVideo.stop();
        }
    }

    public Mono<Void> debugStatus() {
        return Mono.fromRunnable(() -> {
            VoiceConnectionState state = currentState.get();
            String channelType = isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";

            System.out.println("[Voice] === DEBUG STATUS ===");
            System.out.println("[Voice] Channel Type: " + channelType);
            System.out.println("[Voice] Connected: " + isConnected.get());
            System.out.println("[Voice] Connecting: " + isConnecting.get());
            System.out.println("[Voice] State: " + state);
            System.out.println("[Voice] WebSocket open: " + (voiceSocket != null && voiceSocket.isOpen()));
            System.out.println("[Voice] UDP closed: " + (udp == null || udp.isClosed()));
            System.out.println("[Voice] Active VoiceMode: " + activeVoiceModeId.get());
            System.out.println("[Voice] Active VideoMode: " + activeVideoModeId.get());
            System.out.println("[Voice] Registered VoiceModes: " + voiceModes.keySet());
            System.out.println("[Voice] Registered VideoModes: " + videoModes.keySet());
            System.out.println("[Voice] UDP Streamer ready: " + (udpStreamer != null));
            System.out.println("[Voice] ========================");
        });
    }

    private void sendVoiceIdentify(VoiceConnectionState state) {
        JsonObject identify = new JsonObject();
        identify.addProperty("op", 0);
        JsonObject data = new JsonObject();

        if (isDmOrGroupChannel(state.guildId)) {
            data.addProperty("server_id", state.channelId);
        } else {
            data.addProperty("server_id", state.guildId);
        }

        data.addProperty("user_id", botClient.getSelfId());
        data.addProperty("session_id", state.sessionId);
        data.addProperty("token", state.token);
        identify.add("d", data);

        voiceSocket.send(identify.toString());
        System.out.println("[Voice] Sent IDENTIFY");
    }

    private JsonObject createHeartbeatPayload() {
        JsonObject heartbeat = new JsonObject();
        heartbeat.addProperty("op", 3);
        heartbeat.add("d", new JsonPrimitive(System.currentTimeMillis()));
        return heartbeat;
    }

    private void sendHeartbeat(JsonObject heartbeat) {
        if (voiceSocket != null && voiceSocket.isOpen()) {
            voiceSocket.send(heartbeat.toString());
            System.out.println("[Voice] Sent heartbeat #" + heartbeatSequence.incrementAndGet());
        }
    }

    private void sendSelectProtocol(String ip, int port) {
        JsonObject payload = new JsonObject();
        payload.addProperty("op", 1);
        JsonObject data = new JsonObject();
        data.addProperty("protocol", "udp");
        JsonObject address = new JsonObject();
        address.addProperty("address", ip);
        address.addProperty("port", port);
        address.addProperty("mode", encryptionMode);
        data.add("data", address);
        payload.add("d", data);

        voiceSocket.send(payload.toString());
        System.out.println("[Voice] Sent SELECT_PROTOCOL");
    }

    private void extractAndLoad(String libName) throws Exception {
        InputStream is = getClass().getClassLoader().getResourceAsStream("natives/" + libName);
        if (is == null) throw new RuntimeException(libName + " not found in resources/natives/");

        File tempFile = File.createTempFile(libName.replace(".dll", ""), ".dll");
        tempFile.deleteOnExit();

        try (FileOutputStream fos = new FileOutputStream(tempFile)) {
            byte[] buf = new byte[4096];
            int n;
            while ((n = is.read(buf)) != -1) fos.write(buf, 0, n);
        }

        System.load(tempFile.getAbsolutePath());
        System.out.println("[Voice] Loaded: " + libName);
    }

    private void handleSessionDescription(JsonArray keyArray) {
        byte[] secretKey = new byte[keyArray.size()];
        for (int i = 0; i < keyArray.size(); i++) {
            secretKey[i] = keyArray.get(i).getAsByte();
        }

        VoiceConnectionState state = currentState.get();
        String channelType = isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";
        System.out.println("[Voice] Received encryption key for " + channelType + " channel, initializing voice");

        try {
            InetAddress address = InetAddress.getByName(state.voiceServerIp);
            udpStreamer = new OpusUdpStreamer(udp, address, state.voiceServerPort, state.ssrc, secretKey, isConnected);
            System.out.println("[Voice] UDP Streamer initialized for " + channelType + " channel");

            VoiceMode activeVoice = getActiveVoiceModeModel();
            if (activeVoice != null) {
                activeVoice.setUdpStreamer(udpStreamer);
            }
            initialized.set(true);
            System.out.println("[Voice] " + channelType + " voice connection fully established");
        } catch (Exception e) {
            System.err.println("[Voice] Failed to initialize voice streamer: " + e.getMessage());
        }
    }

    public void setSpeaking(SpeakingFlag... flags) {
        if (!isConnected.get() || voiceSocket == null || !voiceSocket.isOpen()) {
            System.err.println("[Voice] Cannot set speaking - not connected");
            return;
        }

        VoiceConnectionState state = currentState.get();
        if (state.ssrc == 0) {
            System.err.println("[Voice] Cannot set speaking - SSRC not set");
            return;
        }

        int bitmask = 0;
        for (SpeakingFlag flag : flags) {
            bitmask |= flag.getBit();
        }

        JsonObject payload = new JsonObject();
        payload.addProperty("op", 5);

        JsonObject data = new JsonObject();
        data.addProperty("speaking", bitmask);
        data.addProperty("delay", 0);
        data.addProperty("ssrc", state.ssrc);

        payload.add("d", data);
        voiceSocket.send(payload.toString());

        String channelType = isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";
        System.out.println("[Voice] Set speaking for " + channelType + " channel: " + (bitmask != 0 ? bitmask : "off") + " (" + Arrays.toString(flags) + ")");
    }

    private void cleanup() {
        if (voiceSocket != null && voiceSocket.isOpen()) {
            voiceSocket.close();
        }
        if (udp != null && !udp.isClosed()) {
            udp.close();
            udp = null;
        }

        if (udpStreamer != null) {
            udpStreamer.stop();
            udpStreamer = null;
        }

        if (initialized.getAndSet(false)) {
            VoiceMode activeVoice = getActiveVoiceModeModel();
            if (activeVoice != null) {
                activeVoice.shutdown();
            }

            VideoMode activeVideo = getActiveVideoMode();
            if (activeVideo != null) {
                activeVideo.shutdown();
            }
        } else {
            System.out.println("[Voice] Skipped mode shutdown: not yet initialized");
        }
    }

    public boolean isInDmOrGroupChannel() {
        VoiceConnectionState state = currentState.get();
        return isDmOrGroupChannel(state.guildId);
    }

    public String getCurrentChannelType() {
        VoiceConnectionState state = currentState.get();
        return isDmOrGroupChannel(state.guildId) ? "DM/Group" : "Guild";
    }

    // Getters
    public AtomicBoolean getIsConnected() {
        return isConnected;
    }

    public AtomicBoolean getInitialized() {
        return initialized;
    }

    public OpusUdpStreamer getUdpStreamer() {
        return udpStreamer;
    }
}
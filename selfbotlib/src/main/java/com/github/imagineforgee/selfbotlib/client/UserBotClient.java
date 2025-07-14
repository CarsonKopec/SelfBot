package com.github.imagineforgee.selfbotlib.client;

import com.clawsoftstudios.purrfectlib.scanner.CommandInfo;
import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.commands.CommandManager;
import com.github.imagineforgee.selfbotlib.dispatch.EventDispatcher;
import com.github.imagineforgee.selfbotlib.dispatch.events.*;
import com.github.imagineforgee.selfbotlib.gateway.GatewayClient;
import com.github.imagineforgee.selfbotlib.dispatch.Event;
import com.github.imagineforgee.selfbotlib.http.DmAndChannelService;
import com.github.imagineforgee.selfbotlib.http.MessageSender;
import com.github.imagineforgee.selfbotlib.util.VoiceStateRegistry;
import com.github.imagineforgee.selfbotlib.webhook.WebhookClient;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class UserBotClient {

    private final GatewayClient gatewayClient;
    private final EventDispatcher dispatcher;
    private final MessageSender messageSender;
    private final DmAndChannelService dmService;
    private final VoiceClient voiceClient;
    private final WebhookClient webhookClient;
    private final CommandManager commandManager = new CommandManager();

    private String selfId;

    public UserBotClient(String token) {
        this(token, null);
    }

    public UserBotClient(String token, String webhook_url) {
        this.gatewayClient = new GatewayClient(token);
        this.dispatcher = new EventDispatcher();
        this.messageSender = new MessageSender(token);
        this.dmService = new DmAndChannelService(token);
        this.voiceClient = new VoiceClient(this);
        if (webhook_url != null && !webhook_url.isBlank()) {
            this.webhookClient = WebhookClient.fromUrl(webhook_url);
        } else {
            this.webhookClient = null;
        }

        dispatcher.registerParser("READY", ReadyEvent::new);
        this.onEvent(ReadyEvent.class)
                .subscribe(evt -> {
                    this.selfId = evt.getUserId();
                    System.out.println("[READY] Bot user ID set: " + selfId);

                    JsonArray guilds = evt.getData().getAsJsonArray("guilds");

                    for (JsonElement el : guilds) {
                        JsonObject guildData = el.getAsJsonObject();

                        JsonObject simulatedPayload = new JsonObject();
                        simulatedPayload.addProperty("t", "GUILD_CREATE");
                        simulatedPayload.add("d", guildData);

                        dispatcher.dispatch(simulatedPayload);
                    }
                });

        dispatcher.registerParser("GUILD_CREATE", GuildCreateEvent::new);
        this.onEvent(GuildCreateEvent.class)
                .subscribe(evt -> {
                    JsonArray voiceStates = evt.getVoiceStates();

                    for (JsonElement el : voiceStates) {
                        JsonObject vs = el.getAsJsonObject();

                        if (vs.has("user_id") && vs.has("channel_id") && !vs.get("channel_id").isJsonNull()) {
                            String userId = vs.get("user_id").getAsString();
                            String channelId = vs.get("channel_id").getAsString();

                            VoiceStateRegistry.update(userId, channelId);
                            System.out.printf("[VOICE_STATE] Updated user %s in channel %s%n", userId, channelId);
                        }
                    }
                });

        dispatcher.registerParser("MESSAGE_CREATE", MessageCreateEvent::new);
        dispatcher.registerParser("COMMAND_EXECUTE", CommandExecuteEvent::new);
        dispatcher.registerParser("VOICE_STATE_UPDATE", VoiceStateUpdateEvent::new);
        this.onEvent(VoiceStateUpdateEvent.class)
                .subscribe(evt -> {
                    String userId = evt.getUserId();
                    String channelId = evt.getChannelId();
                    System.out.printf("[VOICE_STATE_UPDATE] %s -> %s%n", userId, channelId);
                    VoiceStateRegistry.update(userId, channelId);
                });

        dispatcher.registerParser("VOICE_SERVER_UPDATE", VoiceServerUpdateEvent::new);

        gatewayClient.getEventFlux()
                .filter(json -> json.has("op") && json.get("op").getAsInt() == 0)
                .subscribe(dispatcher::dispatch);
    }

    public GatewayClient getGatewayClient() {
        return gatewayClient;
    }

    public Mono<Void> connect() {
        return gatewayClient.connect();
    }

    public Flux<Event> getEvents() {
        return dispatcher.getEventFlux();
    }

    public <T extends Event> Flux<T> onEvent(Class<T> clazz) {
        return dispatcher.onEvent(clazz);
    }

    public Flux<MessageCreateEvent> onMessageCreate() {
        return onEvent(MessageCreateEvent.class);
    }

    public Mono<Void> sendMessage(String channelId, String content) {
        return Mono.fromFuture(messageSender.sendMessage(channelId, content));
    }

    public Mono<Void> replyToMessage(String channelId, String messageId, String content) {
        return Mono.fromFuture(
                messageSender.sendMessage(channelId, content, messageId)
        );
    }

    public Mono<Void> sendRawMessage(String channelId, JsonObject object) {
        return Mono.fromFuture(
                messageSender.sendRawMessage(channelId, object)
        );
    }

    public Mono<Void> reactToMessage(String channelId, String messageId, String emoji) {
        try {
            String encodedEmoji = URLEncoder.encode(emoji, StandardCharsets.UTF_8);
            String url = String.format("https://discord.com/api/v10/channels/%s/messages/%s/reactions/%s/@me",
                    channelId, messageId, encodedEmoji);

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Authorization", gatewayClient.getToken())
                    .header("Content-Type", "application/json")
                    .PUT(HttpRequest.BodyPublishers.noBody())
                    .build();

            HttpClient client = HttpClient.newHttpClient();

            return Mono.fromCallable(() -> {
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
                if (response.statusCode() != 204) {
                    throw new RuntimeException("Failed to react: " + response.statusCode() + " - " + response.body());
                }
                return null;
            });
        } catch (Exception e) {
            return Mono.error(e);
        }
    }

    public Mono<Void> sendDm(String userId, String content) {
        return Mono.fromFuture(dmService.createDmChannel(userId))
                .flatMap(channelId -> sendMessage(channelId, content));
    }

    public void registerCommands(List<CommandInfo> cmds) {
        commandManager.registerCommands(cmds);
    }

    public void startCommandListener() {
        this.onMessageCreate().subscribe(event -> {
            String content = event.getContent();

            String mention = String.format("<@%s>", selfId);

            String message = content.trim();
            String cmdName = null;
            String args = "";

            if (message.startsWith(mention)) {
                message = message.substring(mention.length()).trim();
                String[] parts = message.split("\\s+", 2);
                cmdName = parts[0];
                if (parts.length > 1) args = parts[1];
            } else {
                return;
            }

            CommandContext ctx = new CommandContext(event, this);
            commandManager.handleCommand(cmdName, args, ctx);
        });
    }

    public VoiceClient getVoiceClient() {
        return voiceClient;
    }

    public WebhookClient getWebhookClient() { return webhookClient; }

    public String getSelfId() {
        return selfId;
    }

    public EventDispatcher getDispatcher() {
        return dispatcher;
    }
}

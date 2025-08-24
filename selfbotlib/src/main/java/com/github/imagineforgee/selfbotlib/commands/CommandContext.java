package com.github.imagineforgee.selfbotlib.commands;

import com.github.imagineforgee.selfbotlib.client.UserBotClient;
import com.github.imagineforgee.selfbotlib.dispatch.events.MessageCreateEvent;
import com.google.gson.JsonObject;
import reactor.core.publisher.Mono;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CommandContext {
    private final String channelId;
    private final String userId;
    private final String messageId;
    private final String guildId;
    private final String voiceChannelId;
    private final MessageCreateEvent msgEvent;
    private final UserBotClient botClient;
    private final String rawArgs;
    private String group;
    private String action;
    private final List<String> positionalArgs = new ArrayList<>();
    private final Map<String, String> options = new HashMap<>();

    public CommandContext(String rawArgs, MessageCreateEvent event, UserBotClient botClient) {
        this.rawArgs = rawArgs;
        this.channelId = event.getChannelId();
        this.userId    = event.getAuthorId();
        this.messageId = event.getMessageId();
        this.guildId = event.getGuildId();
        this.voiceChannelId = event.getUserVoiceChannelId();
        this.msgEvent = event;
        this.botClient = botClient;
        parseArgs();
    }

    private void parseArgs() {
        String[] parts = rawArgs.split("\\s+");
        for (String part : parts) {
            if (part.contains("=")) {
                String[] kv = part.split("=", 2);
                options.put(kv[0], kv[1]);
            } else {
                positionalArgs.add(part);
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getOption(String key, Class<T> type, T defaultValue) {
        String value = options.get(key);
        if (value == null) return defaultValue;

        try {
            if (type == Integer.class) return (T) Integer.valueOf(value);
            if (type == Long.class) return (T) Long.valueOf(value);
            if (type == Boolean.class) return (T) Boolean.valueOf(value);
            if (type == Double.class) return (T) Double.valueOf(value);
            return (T) value;
        } catch (Exception e) {
            return defaultValue;
        }
    }

    @SuppressWarnings("unchecked")
    public <T> T getPositional(int index, Class<T> type, T defaultValue) {
        if (index >= positionalArgs.size()) return defaultValue;

        String value = positionalArgs.get(index);
        try {
            if (type == Integer.class) return (T) Integer.valueOf(value);
            if (type == Long.class) return (T) Long.valueOf(value);
            if (type == Boolean.class) return (T) Boolean.valueOf(value);
            if (type == Double.class) return (T) Double.valueOf(value);
            return (T) value; // fallback: String
        } catch (Exception e) {
            return defaultValue;
        }
    }


    public String getRawArgs() {
        return rawArgs;
    }

    public String getGroup() { return group; }
    public String getAction() { return action; }

    public void setGroup(String group) {
        this.group = group;
    }

    public void setAction(String action) {
        this.action = action;
    }

    public String getChannelId() {
        return channelId;
    }

    public String getGuildId() {
        return guildId;
    }

    public String getMemberVoiceChannelId() {
        return voiceChannelId;
    }

    public String getAuthorId() {
        return userId;
    }

    public String getMessageId() {
        return messageId;
    }

    public MessageCreateEvent getMsgEvent() {
        return msgEvent;
    }

    public UserBotClient getBot() {
        return botClient;
    }

    public Mono<Void> sendMessage(String message) {
        return botClient.sendMessage(channelId, message);
    }

    public Mono<Void> reply(String message) {
        return botClient.replyToMessage(channelId, messageId, message);
    }

    public Mono<Void> react(String emoji) {return  botClient.reactToMessage(channelId, messageId, emoji);}

}

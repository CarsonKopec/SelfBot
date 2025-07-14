package com.github.imagineforgee.selfbotlib.commands;

import com.github.imagineforgee.selfbotlib.client.UserBotClient;
import com.github.imagineforgee.selfbotlib.dispatch.events.MessageCreateEvent;
import com.google.gson.JsonObject;
import reactor.core.publisher.Mono;

public class CommandContext {
    private final String channelId;
    private final String userId;
    private final String messageId;
    private final String guildId;
    private final String voiceChannelId;
    private final MessageCreateEvent msgEvent;
    private final UserBotClient botClient;

    public CommandContext(MessageCreateEvent event, UserBotClient botClient) {
        this.channelId = event.getChannelId();
        this.userId    = event.getAuthorId();
        this.messageId = event.getMessageId();
        this.guildId = event.getGuildId();
        this.voiceChannelId = event.getUserVoiceChannelId();
        this.msgEvent = event;
        this.botClient = botClient;
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

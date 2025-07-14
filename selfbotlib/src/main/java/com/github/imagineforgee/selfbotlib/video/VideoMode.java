package com.github.imagineforgee.selfbotlib.video;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;

public interface VideoMode {
    default void setVoiceClient(VoiceClient client) {}
    void start(String source); // e.g., video file or stream URL
    void stop();
    void initialize(); // e.g., encoder setup
    void joinChannel(String guildId, String channelId);
    void shutdown();
    boolean isActive();
}

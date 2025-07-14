package com.github.imagineforgee.selfbotlib.voice;


import com.github.imagineforgee.selfbotlib.client.VoiceClient;

public interface VoiceMode {
    default void setVoiceClient(VoiceClient client) {}
    void start(String url);
    void stop();

    /**
     * Called when the voice mode is fully initialized and can start transmitting.
     */
    void initialize();

    /**
     * Called when the bot joins a channel.
     * @param guildId Guild ID of the target voice channel.
     * @param channelId Channel ID of the target voice channel.
     */
    void joinChannel(String guildId, String channelId);

    /**
     * Called when the voice connection is shutting down.
     */
    void shutdown();

    /**
     * Indicates whether this voice mode is currently active (e.g., playing or streaming).
     */
    boolean isActive();
    void setUdpStreamer(OpusUdpStreamer udpStreamer);
}


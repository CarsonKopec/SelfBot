package com.github.imagineforgee.selfbotlib.voice.audio.music;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;
import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.voice.OpusUdpStreamer;
import com.github.imagineforgee.selfbotlib.voice.VoiceMode;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Queue;

public interface MusicMode extends VoiceMode {
    default void setVoiceClient(VoiceClient client) {}
    void start(String url, CommandContext ctx);
    void stop();
    void skip();
    void clear();
    Queue<AudioTrack> getQueue();

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

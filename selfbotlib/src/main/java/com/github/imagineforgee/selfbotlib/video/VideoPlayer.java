package com.github.imagineforgee.selfbotlib.video;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;

public class VideoPlayer implements VideoMode {
    private final VideoStreamer streamer;
    private final VoiceClient voiceClient;

    public VideoPlayer(VoiceClient voiceClient, VideoStreamer streamer) {
        this.voiceClient = voiceClient;
        this.streamer = streamer;
    }

    @Override
    public void start(String videoPath) {
        // encode video to RTP-ready Opus/H.264 stream using FFmpeg or similar
        // then send Flux<byte[]> to streamer.start()
    }

    @Override
    public void stop() {
        streamer.stop();
    }

    @Override
    public void initialize() {
        // Optional: preload FFmpeg/encoder
    }

    @Override
    public void joinChannel(String guildId, String channelId) {
        // Not needed unless video channel metadata exists
    }

    @Override
    public void shutdown() {
        stop();
    }

    @Override
    public boolean isActive() {
        return false; // track based on stream state
    }
}


package com.github.imagineforgee.selfbotlib.voice;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;
import com.sedmelluq.discord.lavaplayer.format.StandardAudioDataFormats;
import com.sedmelluq.discord.lavaplayer.player.AudioLoadResultHandler;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.player.DefaultAudioPlayerManager;
import com.sedmelluq.discord.lavaplayer.tools.FriendlyException;
import com.sedmelluq.discord.lavaplayer.track.AudioPlaylist;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.playback.AudioFrame;
import dev.lavalink.youtube.YoutubeAudioSourceManager;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Duration;

public class LavaPlayer implements VoiceMode {
    private final AudioPlayerManager playerManager;
    private final com.sedmelluq.discord.lavaplayer.player.AudioPlayer lavaPlayer;
    private OpusUdpStreamer streamer;
    private VoiceClient voiceClient;

    public LavaPlayer(OpusUdpStreamer streamer) {
        this.playerManager = new DefaultAudioPlayerManager();
        this.playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_OPUS);
        this.playerManager.registerSourceManager(new YoutubeAudioSourceManager(true));
        this.lavaPlayer = playerManager.createPlayer();
        this.streamer = streamer;
    }

    @Override
    public void setVoiceClient(VoiceClient client) {
        this.voiceClient = client;
        if (client.getUdpStreamer() != null) {
            this.streamer = client.getUdpStreamer();
        }
    }

    @Override
    public void start(String url) {
        System.out.println("[Voice] Loading track: " + url);
        stopStreaming();

        playerManager.loadItem(url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                System.out.println("[Voice] Track loaded successfully: " + track.getInfo().title);
                lavaPlayer.playTrack(track);
                startAudioStream();
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (!playlist.getTracks().isEmpty()) {
                    AudioTrack firstTrack = playlist.getTracks().get(0);
                    lavaPlayer.playTrack(firstTrack);
                    startAudioStream();
                }
            }

            @Override
            public void noMatches() {
                System.err.println("[Voice] No matches found for: " + url);
            }

            @Override
            public void loadFailed(FriendlyException exception) {
                System.err.println("[Voice] Failed to load track: " + exception.getMessage());
            }
        });
    }

    private void startAudioStream() {
        System.out.println("[Voice] Starting audio stream");

        OpusUdpStreamer udpStreamer = voiceClient.getUdpStreamer();
        if (udpStreamer == null) {
            System.err.println("[LavaPlayer] Cannot start audio stream: streamer not set in VoiceClient");
            return;
        }

        stopStreaming();

        Flux<byte[]> opusFrames = Flux.interval(Duration.ofMillis(20))
                .onBackpressureDrop()
                .takeWhile(tick -> voiceClient.getIsConnected().get())
                .flatMap(tick -> {
                    AudioFrame frame = lavaPlayer.provide();
                    if (frame != null && frame.getData() != null && frame.getData().length > 0) {
                        return Mono.just(frame.getData());
                    }
                    return Mono.empty();
                });

        voiceClient.setSpeaking(SpeakingFlag.MICROPHONE);
        udpStreamer.start(opusFrames);
    }


    @Override
    public void stop() {
        lavaPlayer.stopTrack();
        stopStreaming();
        voiceClient.setSpeaking();
    }

    @Override
    public void initialize() {
        System.out.println("[Voice] LavaPlayer initialized and ready to stream");
    }

    @Override
    public void joinChannel(String guildId, String channelId) {
        System.out.println("[Voice] LavaPlayer aware of joined channel: " + guildId + "/" + channelId);
    }

    private void stopStreaming() {
        if (streamer != null) {
            streamer.stop();
            streamer = null;
        }
    }

    @Override
    public void shutdown() {
        stop();
        playerManager.shutdown();
    }

    @Override
    public boolean isActive() {
        return lavaPlayer.getPlayingTrack() != null;
    }

    @Override
    public void setUdpStreamer(OpusUdpStreamer udpStreamer) {
        this.streamer = udpStreamer;
    }
}

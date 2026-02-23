package com.github.imagineforgee.selfbotlib.voice.audio.music;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;
import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.voice.OpusUdpStreamer;
import com.github.imagineforgee.selfbotlib.voice.SpeakingFlag;
import com.github.imagineforgee.selfbotlib.voice.audio.music.track.TrackQueue;
import com.github.imagineforgee.selfbotlib.voice.audio.music.track.TrackScheduler;
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

import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class LavaPlayer implements MusicMode {
    private final AudioPlayerManager playerManager;
    private final com.sedmelluq.discord.lavaplayer.player.AudioPlayer lavaPlayer;
    private OpusUdpStreamer streamer;
    private VoiceClient voiceClient;
    private final TrackQueue queue;
    private final TrackScheduler scheduler;
    private final AtomicBoolean isStreaming = new AtomicBoolean(false);

    public LavaPlayer(OpusUdpStreamer streamer) {
        this.playerManager = new DefaultAudioPlayerManager();
        this.playerManager.getConfiguration().setOutputFormat(StandardAudioDataFormats.DISCORD_OPUS);
        this.playerManager.registerSourceManager(new YoutubeAudioSourceManager(true));
        this.lavaPlayer = playerManager.createPlayer();
        this.streamer = streamer;
        this.queue = new TrackQueue();
        this.scheduler = new TrackScheduler(lavaPlayer, queue, this);
        lavaPlayer.addListener(scheduler);
    }

    @Override
    public void setVoiceClient(VoiceClient client) {
        this.voiceClient = client;
        if (client.getUdpStreamer() != null) {
            this.streamer = client.getUdpStreamer();
        }
    }

    @Override
    public void start(String url, CommandContext ctx) {
        System.out.println("[Voice] Loading track: " + url);
        lavaPlayer.stopTrack();
        String key = ctx.getGuildId() != null ? ctx.getGuildId() : "group:" + ctx.getChannelId();
        playerManager.loadItemOrdered(key, url, new AudioLoadResultHandler() {
            @Override
            public void trackLoaded(AudioTrack track) {
                System.out.println("[Voice] Track loaded successfully: " + track.getInfo().title);
                scheduler.queue(track);
            }

            @Override
            public void playlistLoaded(AudioPlaylist playlist) {
                if (!playlist.getTracks().isEmpty()) {
                    for (AudioTrack track : playlist.getTracks()) {
                        scheduler.queue(track);
                    }
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

    public void startAudioStream() {
        if (!isStreaming.compareAndSet(false, true)) {
            System.out.println("[LavaPlayer] Already streaming, ignoring duplicate call");
            return;
        }

        OpusUdpStreamer udpStreamer = voiceClient.getUdpStreamer();
        if (udpStreamer == null) {
            System.err.println("[LavaPlayer] Cannot start audio stream: streamer not ready");
            isStreaming.set(false);
            return;
        }

        streamer = udpStreamer;
        int[] frameCount = {0};

        Flux<byte[]> opusFrames = Flux.<byte[]>generate(sink -> {
            try {
                AudioFrame frame = lavaPlayer.provide(1000, java.util.concurrent.TimeUnit.MILLISECONDS);
                if (frame != null && !frame.isTerminator()) {
                    frameCount[0]++;
                    if (frameCount[0] % 50 == 0) {
                        System.out.println("[LavaPlayer] Frames sent: " + frameCount[0]);
                    }
                    sink.next(frame.getData());
                } else {
                    System.out.println("[LavaPlayer] Null/terminator frame, player playing: " + (lavaPlayer.getPlayingTrack() != null));
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                sink.complete();
            } catch (Exception e) {
                System.err.println("[LavaPlayer] Frame error: " + e.getMessage());
                sink.complete();
            }
        }).doFinally(signal -> {
            System.out.println("[LavaPlayer] Stream ended: " + signal + ", total frames: " + frameCount[0]);
            isStreaming.set(false);
        });

        voiceClient.setSpeaking(SpeakingFlag.MICROPHONE);
        udpStreamer.start(opusFrames);
        System.out.println("[LavaPlayer] Stream started");
    }


    @Override
    public void stop() {
        lavaPlayer.stopTrack();
        stopStreaming();
        isStreaming.set(false);
        voiceClient.setSpeaking();
    }

    @Override
    public void skip() {
        scheduler.skip();
    }

    @Override
    public void clear() {
		queue.clear();
    }

    @Override
    public Queue<AudioTrack> getQueue() {
		return queue.getSnapshot();
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

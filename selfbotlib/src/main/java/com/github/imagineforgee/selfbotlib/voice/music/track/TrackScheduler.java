package com.github.imagineforgee.selfbotlib.voice.music.track;

import com.github.imagineforgee.selfbotlib.voice.music.LavaPlayer;
import com.sedmelluq.discord.lavaplayer.player.AudioPlayer;
import com.sedmelluq.discord.lavaplayer.player.event.AudioEventAdapter;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;
import com.sedmelluq.discord.lavaplayer.track.AudioTrackEndReason;

public class TrackScheduler extends AudioEventAdapter {
    private final AudioPlayer player;
    private final TrackQueue queue;
    private final LavaPlayer lavaPlayer;

    public TrackScheduler(AudioPlayer player, TrackQueue queue, LavaPlayer lavaPlayer) {
        this.player = player;
        this.queue = queue;
        this.lavaPlayer = lavaPlayer;
    }

    public void queue(AudioTrack track) {
        if (!player.startTrack(track, true)) {
            queue.add(track);
        } else {
            lavaPlayer.startAudioStream();
        }
    }

    @Override
    public void onTrackEnd(AudioPlayer player, AudioTrack track, AudioTrackEndReason endReason) {
		if (endReason.mayStartNext) {
            AudioTrack next = queue.poll();
            if (next != null) {
                player.startTrack(next, false);
                lavaPlayer.startAudioStream();
            } else {
                lavaPlayer.stop();
            }
        }
    }

    public void skip() {
        AudioTrack next = queue.poll();
        if (next != null) {
            player.startTrack(next, false);
            lavaPlayer.startAudioStream();
        } else {
            player.stopTrack();
            lavaPlayer.stop();
        }
    }

}

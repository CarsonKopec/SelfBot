package com.github.imagineforgee.selfbotlib.voice.music.track;

import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.LinkedList;
import java.util.Queue;

public class TrackQueue {
    private final Queue<AudioTrack> queue = new LinkedList<>();

    public void add(AudioTrack track) {
        queue.offer(track);
    }

    public void addAll(Iterable<AudioTrack> tracks) {
        for (AudioTrack track : tracks) {
        	queue.offer(track);
        }
    }

    public AudioTrack poll() {
        return queue.poll();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    public void clear() {
        queue.clear();
    }

    public Queue<AudioTrack> getSnapshot() {
        return new LinkedList<>(queue);
    }
}

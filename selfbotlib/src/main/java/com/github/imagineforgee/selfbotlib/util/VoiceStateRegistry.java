package com.github.imagineforgee.selfbotlib.util;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public class VoiceStateRegistry {
    private static final ConcurrentMap<String,String> userToChannel = new ConcurrentHashMap<>();

    public static void update(String userId, String channelId) {
        if (channelId == null) userToChannel.remove(userId);
        else userToChannel.put(userId, channelId);
    }

    public static String get(String userId) {
        return userToChannel.get(userId);
    }
}


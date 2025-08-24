package com.github.imagineforgee.selfbotlib.voice.music;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;
import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.voice.VoiceMode;
import com.sedmelluq.discord.lavaplayer.track.AudioTrack;

import java.util.Queue;

public class MusicManager {
    VoiceClient voiceClient;
    public void playTrack(String url, CommandContext ctx) {
        voiceClient = ctx.getBot().getVoiceClient();
        VoiceMode activeMode = voiceClient.getActiveVoiceMode();
        if (activeMode != null && voiceClient.getIsConnected().get()) {
            System.out.println("[Voice] Delegating play to active VoiceMode: " + voiceClient.getActiveVoiceModeId());
            activeMode.start(url, ctx);
        } else {
            System.err.println("[Voice] Cannot play - not connected or no active VoiceMode");
        }
    }

    public void skip() {
        VoiceMode activeVoice = voiceClient.getActiveVoiceMode();
        activeVoice.skip();
    }

    public void clear() {
        VoiceMode activeVoice = voiceClient.getActiveVoiceMode();
        activeVoice.clear();
    }

}

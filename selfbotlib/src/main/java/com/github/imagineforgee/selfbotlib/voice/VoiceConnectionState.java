package com.github.imagineforgee.selfbotlib.voice;

public class VoiceConnectionState {
    public final String guildId;
    public final String channelId;
    public final String sessionId;
    public final String token;
    public final String endpoint;
    public final int ssrc;
    public final String voiceServerIp;
    public final int voiceServerPort;
    public final boolean hasServerUpdate;
    public final boolean hasStateUpdate;

    public VoiceConnectionState() {
        this(null, null, null, null, null, 0, null, 0, false, false);
    }

    private VoiceConnectionState(String guildId, String channelId, String sessionId,
                                 String token, String endpoint, int ssrc,
                                 String voiceServerIp, int voiceServerPort,
                                 boolean hasServerUpdate, boolean hasStateUpdate) {
        this.guildId = guildId;
        this.channelId = channelId;
        this.sessionId = sessionId;
        this.token = token;
        this.endpoint = endpoint;
        this.ssrc = ssrc;
        this.voiceServerIp = voiceServerIp;
        this.voiceServerPort = voiceServerPort;
        this.hasServerUpdate = hasServerUpdate;
        this.hasStateUpdate = hasStateUpdate;
    }

    public VoiceConnectionState withServerUpdate(String guildId, String token, String endpoint) {
        return new VoiceConnectionState(guildId, this.channelId, this.sessionId,
                token, endpoint, this.ssrc, this.voiceServerIp,
                this.voiceServerPort, true, this.hasStateUpdate);
    }

    public VoiceConnectionState withStateUpdate(String guildId, String channelId, String sessionId) {
        return new VoiceConnectionState(guildId, channelId, sessionId, this.token,
                this.endpoint, this.ssrc, this.voiceServerIp,
                this.voiceServerPort, this.hasServerUpdate, true);
    }

    public VoiceConnectionState withVoiceReady(int ssrc, String ip, int port) {
        return new VoiceConnectionState(this.guildId, this.channelId, this.sessionId,
                this.token, this.endpoint, ssrc, ip, port,
                this.hasServerUpdate, this.hasStateUpdate);
    }

    public VoiceConnectionState withTargetChannel(String guildId, String channelId) {
        return new VoiceConnectionState(guildId, channelId, this.sessionId, this.token,
                this.endpoint, this.ssrc, this.voiceServerIp,
                this.voiceServerPort, this.hasServerUpdate, this.hasStateUpdate);
    }

    public boolean isReadyToConnect() {
        return guildId != null && token != null && sessionId != null &&
                endpoint != null && hasServerUpdate && hasStateUpdate;
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (!(obj instanceof VoiceConnectionState)) return false;
        VoiceConnectionState other = (VoiceConnectionState) obj;
        return java.util.Objects.equals(guildId, other.guildId) &&
                java.util.Objects.equals(channelId, other.channelId) &&
                java.util.Objects.equals(sessionId, other.sessionId) &&
                java.util.Objects.equals(token, other.token) &&
                java.util.Objects.equals(endpoint, other.endpoint) &&
                ssrc == other.ssrc &&
                hasServerUpdate == other.hasServerUpdate &&
                hasStateUpdate == other.hasStateUpdate;
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(guildId, channelId, sessionId, token, endpoint,
                ssrc, hasServerUpdate, hasStateUpdate);
    }

    @Override
    public String toString() {
        return String.format("VoiceConnectionState{guild=%s, channel=%s, session=%s, " +
                        "token=%s, endpoint=%s, ssrc=%d, serverUpdate=%s, stateUpdate=%s}",
                guildId, channelId, sessionId != null ? "***" : null,
                token != null ? "***" : null, endpoint, ssrc,
                hasServerUpdate, hasStateUpdate);
    }
}

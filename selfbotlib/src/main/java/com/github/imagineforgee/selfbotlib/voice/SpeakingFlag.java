package com.github.imagineforgee.selfbotlib.voice;

public enum SpeakingFlag {
    MICROPHONE(0x01),
    SOUNDSHARE(0x02),
    VIDEO(0x04);

    private final int bit;

    SpeakingFlag(int bit) {
        this.bit = bit;
    }

    public int getBit() {
        return bit;
    }
}


package com.github.imagineforgee.selfbotlib.video;

import com.goterl.lazysodium.LazySodiumJava;
import reactor.core.publisher.Flux;

import java.net.DatagramSocket;
import java.net.InetAddress;

public class VideoStreamer {
    private final DatagramSocket udp;
    private final InetAddress address;
    private final int port;
    private final int ssrc;
    private final LazySodiumJava sodium;
    private final byte[] secretKey;

    public VideoStreamer(DatagramSocket udp, InetAddress address, int port, int ssrc,
                         LazySodiumJava sodium, byte[] secretKey) {
        this.udp = udp;
        this.address = address;
        this.port = port;
        this.ssrc = ssrc;
        this.sodium = sodium;
        this.secretKey = secretKey;
    }

    public void start(Flux<byte[]> encodedFrames) {
        // send as RTP packets encrypted via XSalsa20 Poly1305
    }

    public void stop() {
        // handle shutdown of stream
    }
}


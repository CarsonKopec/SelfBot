package com.github.imagineforgee.selfbotlib.voice;

import com.goterl.lazysodium.LazySodiumJava;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Schedulers;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

public class OpusUdpStreamer {
    private final LazySodiumJava sodium;
    private final DatagramSocket udp;
    private final InetAddress address;
    private final int port;
    private final int ssrc;
    private final byte[] secretKey;
    private final AtomicBoolean isConnected;
    private Disposable stream;

    public OpusUdpStreamer(LazySodiumJava sodium, DatagramSocket udp, InetAddress address, int port,
                           int ssrc, byte[] secretKey, AtomicBoolean isConnected) {
        this.sodium = sodium;
        this.udp = udp;
        this.address = address;
        this.port = port;
        this.ssrc = ssrc;
        this.secretKey = secretKey;
        this.isConnected = isConnected;
    }

    public void start(Flux<byte[]> opusFrames) {
        AtomicInteger seq = new AtomicInteger(0);
        AtomicInteger ts = new AtomicInteger((int) (System.currentTimeMillis() & 0xFFFFFFFF));

        stop(); // cancel existing stream

        this.stream = opusFrames
                .takeWhile(frame -> isConnected.get())
                .subscribeOn(Schedulers.boundedElastic()) // run async
                .subscribe(frame -> {
                    int sequence = seq.getAndUpdate(s -> (s + 1) & 0xFFFF);
                    int timestamp = ts.getAndUpdate(t -> (t + 960) & 0xFFFFFFFF);
                    sendFrame(sequence, timestamp, frame);
                });
    }


    private void sendFrame(int sequence, int timestamp, byte[] opusFrame) {
        try {
            byte[] rtp = createRtpHeader(sequence, timestamp);
            byte[] nonce = new byte[24];
            System.arraycopy(rtp, 0, nonce, 0, 12);
            byte[] encrypted = new byte[opusFrame.length + 16];

            if (!sodium.cryptoSecretBoxEasy(encrypted, opusFrame, opusFrame.length, nonce, secretKey)) {
                System.err.println("[Streamer] Encryption failed.");
                return;
            }

            byte[] packet = new byte[rtp.length + encrypted.length];
            System.arraycopy(rtp, 0, packet, 0, 12);
            System.arraycopy(encrypted, 0, packet, 12, encrypted.length);

            udp.send(new DatagramPacket(packet, packet.length, address, port));
        } catch (Exception e) {
            System.err.println("[Streamer] Packet send error: " + e.getMessage());
        }
    }

    private byte[] createRtpHeader(int sequence, int timestamp) {
        return new byte[]{
                (byte) 0x80, (byte) 0x78,
                (byte) (sequence >> 8), (byte) sequence,
                (byte) (timestamp >> 24), (byte) (timestamp >> 16),
                (byte) (timestamp >> 8), (byte) timestamp,
                (byte) (ssrc >> 24), (byte) (ssrc >> 16),
                (byte) (ssrc >> 8), (byte) ssrc
        };
    }

    public void stop() {
        if (stream != null && !stream.isDisposed()) {
            stream.dispose();
            stream = null;
        }
    }
}

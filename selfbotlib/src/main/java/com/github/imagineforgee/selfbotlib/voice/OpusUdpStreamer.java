package com.github.imagineforgee.selfbotlib.voice;

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
    private final DatagramSocket udp;
    private final InetAddress address;
    private final int port;
    private final int ssrc;
    private final byte[] secretKey;
    private final AtomicBoolean isConnected;
    private final AtomicInteger nonceCounter = new AtomicInteger(0);
    private Disposable stream;

    public OpusUdpStreamer(DatagramSocket udp, InetAddress address, int port,
                           int ssrc, byte[] secretKey, AtomicBoolean isConnected) {
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

        stop();

        this.stream = opusFrames
                .takeWhile(frame -> isConnected.get())
                .zipWith(Flux.interval(Duration.ofMillis(20)))
                .map(tuple -> tuple.getT1())
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(frame -> {
                    int sequence = seq.getAndUpdate(s -> (s + 1) & 0xFFFF);
                    int timestamp = ts.getAndUpdate(t -> (t + 960) & 0xFFFFFFFF);
                    sendFrame(sequence, timestamp, frame);
                });
    }

    private void sendFrame(int sequence, int timestamp, byte[] opusFrame) {
        try {
            byte[] rtp = createRtpHeader(sequence, timestamp);

            int counter = nonceCounter.getAndIncrement();
            byte[] nonce = new byte[24];
            nonce[0] = (byte) (counter >> 24);
            nonce[1] = (byte) (counter >> 16);
            nonce[2] = (byte) (counter >> 8);
            nonce[3] = (byte) counter;
            byte[] encrypted = SodiumEncryption.encrypt(opusFrame, rtp, nonce, secretKey);

            byte[] packet = new byte[rtp.length + encrypted.length + 4];
            System.arraycopy(rtp, 0, packet, 0, rtp.length);
            System.arraycopy(encrypted, 0, packet, rtp.length, encrypted.length);
            packet[packet.length - 4] = nonce[0];
            packet[packet.length - 3] = nonce[1];
            packet[packet.length - 2] = nonce[2];
            packet[packet.length - 1] = nonce[3];

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
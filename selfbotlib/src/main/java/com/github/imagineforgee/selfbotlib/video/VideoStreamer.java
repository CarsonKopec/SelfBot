package com.github.imagineforgee.selfbotlib.video;

import com.goterl.lazysodium.LazySodiumJava;
import reactor.core.Disposable;
import reactor.core.publisher.Flux;
import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

public class VideoStreamer {
    private final DatagramSocket udp;
    private final InetAddress address;
    private final int port;
    private final int ssrc;
    private final LazySodiumJava sodium;
    private final byte[] secretKey;
    private Disposable stream;

    public VideoStreamer(DatagramSocket udp, InetAddress address, int port, int ssrc,
                         LazySodiumJava sodium, byte[] secretKey) {
        this.udp = udp;
        this.address = address;
        this.port = port;
        this.ssrc = ssrc;
        this.sodium = sodium;
        this.secretKey = secretKey;
    }

    public void start(Flux<byte[]> nalUnits) {
        stop();

        AtomicInteger seq = new AtomicInteger(0);
        AtomicInteger ts = new AtomicInteger(0);
        final int clockRate = 90000;

        this.stream = nalUnits
                .subscribeOn(Schedulers.boundedElastic())
                .subscribe(nal -> {
                    List<byte[]> rtpPackets = packetizeNal(nal, seq, ts, clockRate);
                    for (byte[] packet : rtpPackets) {
                        sendEncrypted(packet);
                    }
                    ts.addAndGet(clockRate / 30);
                });
    }

    private List<byte[]> packetizeNal(byte[] nal, AtomicInteger seq, AtomicInteger ts, int clockRate) {
        List<byte[]> packets = new ArrayList<>();
        int mtu = 1200;

        if (nal.length <= mtu) {
            packets.add(createRtpPacket(nal, seq.getAndIncrement(), ts.get()));
        } else {
            int nalType = nal[0] & 0x1F;
            int nri = nal[0] & 0x60;
            int offset = 1;
            boolean start = true;
            while (offset < nal.length) {
                int size = Math.min(mtu - 2, nal.length - offset);
                byte fuIndicator = (byte)(nri | 28);
                byte fuHeader = (byte)(nalType & 0x1F);
                if (start) fuHeader |= 0x80;
                if (offset + size >= nal.length) fuHeader |= 0x40;

                byte[] fragment = new byte[size + 2];
                fragment[0] = fuIndicator;
                fragment[1] = fuHeader;
                System.arraycopy(nal, offset, fragment, 2, size);
                packets.add(createRtpPacket(fragment, seq.getAndIncrement(), ts.get()));
                offset += size;
                start = false;
            }
        }
        return packets;
    }

    private byte[] createRtpPacket(byte[] payload, int sequence, int timestamp) {
        byte[] rtp = new byte[12 + payload.length];
        rtp[0] = (byte)0x80;
        rtp[1] = (byte)0x60; // PT=96
        rtp[2] = (byte)(sequence >> 8);
        rtp[3] = (byte)sequence;
        rtp[4] = (byte)(timestamp >> 24);
        rtp[5] = (byte)(timestamp >> 16);
        rtp[6] = (byte)(timestamp >> 8);
        rtp[7] = (byte)timestamp;
        rtp[8] = (byte)(ssrc >> 24);
        rtp[9] = (byte)(ssrc >> 16);
        rtp[10] = (byte)(ssrc >> 8);
        rtp[11] = (byte)ssrc;
        System.arraycopy(payload, 0, rtp, 12, payload.length);
        return rtp;
    }

    private void sendEncrypted(byte[] rtp) {
        try {
            byte[] nonce = new byte[24];
            System.arraycopy(rtp, 0, nonce, 0, 12);
            byte[] encrypted = new byte[rtp.length - 12 + 16];
            if (!sodium.cryptoSecretBoxEasy(encrypted, Arrays.copyOfRange(rtp, 12, rtp.length),
                    rtp.length - 12, nonce, secretKey)) {
                System.err.println("[VideoStreamer] Encryption failed.");
                return;
            }
            byte[] packet = new byte[12 + encrypted.length];
            System.arraycopy(rtp, 0, packet, 0, 12);
            System.arraycopy(encrypted, 0, packet, 12, encrypted.length);

            udp.send(new DatagramPacket(packet, packet.length, address, port));
        } catch (Exception e) {
            System.err.println("[VideoStreamer] Send error: " + e.getMessage());
        }
    }

    public void stop() {
        if (stream != null && !stream.isDisposed()) {
            stream.dispose();
            stream = null;
        }
    }
}


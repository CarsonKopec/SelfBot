package com.github.imagineforgee.selfbotlib.voice.audio.source;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;
import com.github.imagineforgee.selfbotlib.commands.CommandContext;
import com.github.imagineforgee.selfbotlib.voice.OpusUdpStreamer;
import com.github.imagineforgee.selfbotlib.voice.SpeakingFlag;
import com.github.imagineforgee.selfbotlib.voice.VoiceMode;
import com.sun.jna.ptr.IntByReference;
import com.sun.jna.ptr.PointerByReference;
import reactor.core.publisher.Sinks;
import tomp2p.opuswrapper.Opus;

import javax.sound.sampled.*;
import java.nio.IntBuffer;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicBoolean;

public class DesktopAudioMode implements VoiceMode {
    private VoiceClient voiceClient;
    private OpusUdpStreamer streamer;
    private Thread captureThread;
    private final AtomicBoolean capturing = new AtomicBoolean(false);
    private Sinks.Many<byte[]> frameSink;
    private String deviceName = "CABLE Output";

    private static final AudioFormat FORMAT = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            48000f, 16, 2, 4, 48000f, false
    );
    private static final int FRAME_SIZE = 960;
    private static final int FRAME_BYTES = FRAME_SIZE * 2 * 2; // 960 * stereo * 2 bytes

    public DesktopAudioMode() {}

    public DesktopAudioMode(String deviceName) {
        this.deviceName = deviceName;
    }

    public static void listDevices() {
        System.out.println("[Desktop] Available audio devices:");
        for (Mixer.Info info : AudioSystem.getMixerInfo()) {
            Mixer mixer = AudioSystem.getMixer(info);
            DataLine.Info lineInfo = new DataLine.Info(TargetDataLine.class, FORMAT);
            boolean supported = mixer.isLineSupported(lineInfo);
            System.out.println("  [" + (supported ? "CAPTURE" : "      ") + "] " + info.getName());
        }
    }

    @Override
    public void setVoiceClient(VoiceClient client) {
        this.voiceClient = client;
    }

    @Override
    public void setUdpStreamer(OpusUdpStreamer udpStreamer) {
        this.streamer = udpStreamer;
    }

    @Override
    public void start(String ignored, CommandContext ctx) {
        startCapture();
    }

    public void startCapture() {
        if (!capturing.compareAndSet(false, true)) {
            System.out.println("[Desktop] Already capturing");
            return;
        }

        OpusUdpStreamer udpStreamer = voiceClient.getUdpStreamer();
        if (udpStreamer == null) {
            System.err.println("[Desktop] Streamer not ready");
            capturing.set(false);
            return;
        }
        streamer = udpStreamer;

        TargetDataLine line = findDevice(deviceName);
        if (line == null) {
            System.err.println("[Desktop] Device not found: " + deviceName);
            listDevices();
            capturing.set(false);
            return;
        }

        // Create Opus encoder
        IntBuffer error = IntBuffer.allocate(1);
        PointerByReference encoder = Opus.INSTANCE.opus_encoder_create(48000, 2, Opus.OPUS_APPLICATION_AUDIO, error);
        if (error.get(0) != Opus.OPUS_OK || encoder == null) {
            System.err.println("[Desktop] Failed to create Opus encoder, error code: " + error.get(0));
            capturing.set(false);
            return;
        }

        frameSink = Sinks.many().unicast().onBackpressureBuffer();

        TargetDataLine finalLine = line;
        PointerByReference finalEncoder = encoder;

        captureThread = new Thread(() -> {
            try {
                finalLine.open(FORMAT);
                finalLine.start();
                System.out.println("[Desktop] Capturing from: " + deviceName);

                byte[] pcmBuf = new byte[FRAME_BYTES];
                byte[] encodedBuf = new byte[4096];

                while (capturing.get()) {
                    int read = finalLine.read(pcmBuf, 0, pcmBuf.length);
                    if (read == FRAME_BYTES) {
                        short[] pcmShorts = new short[FRAME_SIZE * 2];
                        for (int i = 0; i < pcmShorts.length; i++) {
                            pcmShorts[i] = (short) ((pcmBuf[i * 2] & 0xFF) | (pcmBuf[i * 2 + 1] << 8));
                        }

                        java.nio.ShortBuffer shortBuffer = java.nio.ShortBuffer.wrap(pcmShorts);
                        java.nio.ByteBuffer outBuffer = java.nio.ByteBuffer.wrap(encodedBuf);

                        int result = Opus.INSTANCE.opus_encode(
                                finalEncoder,
                                shortBuffer,
                                FRAME_SIZE,
                                outBuffer,
                                encodedBuf.length
                        );

                        if (result > 0) {
                            byte[] encoded = new byte[result];
                            System.arraycopy(encodedBuf, 0, encoded, 0, result);
                            frameSink.tryEmitNext(encoded);
                        } else {
                            System.err.println("[Desktop] Encode error: " + result);
                        }
                    }
                }
            } catch (Exception e) {
                System.err.println("[Desktop] Capture error: " + e.getMessage());
            } finally {
                finalLine.stop();
                finalLine.close();
                Opus.INSTANCE.opus_encoder_destroy(finalEncoder);
                frameSink.tryEmitComplete();
                capturing.set(false);
                System.out.println("[Desktop] Capture stopped");
            }
        }, "desktop-audio-capture");

        captureThread.setDaemon(true);
        captureThread.start();

        voiceClient.setSpeaking(SpeakingFlag.MICROPHONE);
        streamer.start(frameSink.asFlux());
    }

    private TargetDataLine findDevice(String name) {
        for (Mixer.Info mixerInfo : AudioSystem.getMixerInfo()) {
            if (mixerInfo.getName().toLowerCase().contains(name.toLowerCase())) {
                try {
                    Mixer mixer = AudioSystem.getMixer(mixerInfo);
                    DataLine.Info info = new DataLine.Info(TargetDataLine.class, FORMAT);
                    if (mixer.isLineSupported(info)) {
                        System.out.println("[Desktop] Found device: " + mixerInfo.getName());
                        return (TargetDataLine) mixer.getLine(info);
                    }
                } catch (Exception e) {
                    System.err.println("[Desktop] Failed to open " + mixerInfo.getName() + ": " + e.getMessage());
                }
            }
        }
        return null;
    }

    @Override
    public void stop() {
        capturing.set(false);
        if (captureThread != null) captureThread.interrupt();
        if (streamer != null) streamer.stop();
        if (voiceClient != null) voiceClient.setSpeaking();
    }

    @Override public void joinChannel(String guildId, String channelId) {}
    @Override public void shutdown() { stop(); }
    @Override public boolean isActive() { return capturing.get(); }
    @Override public void skip() {}
    @Override public void clear() {}
    @Override public void initialize() { System.out.println("[Desktop] DesktopAudioMode ready"); }

}
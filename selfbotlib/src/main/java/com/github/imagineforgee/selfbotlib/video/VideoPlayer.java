package com.github.imagineforgee.selfbotlib.video;

import com.github.imagineforgee.selfbotlib.client.VoiceClient;
import org.jetbrains.annotations.NotNull;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URISyntaxException;
import java.util.concurrent.atomic.AtomicBoolean;

public class VideoPlayer implements VideoMode {
    private final VideoStreamer streamer;
    private final VoiceClient voiceClient;
    private final AtomicBoolean active = new AtomicBoolean(false);
    private Process ffmpegProcess;

    public VideoPlayer(VoiceClient voiceClient, VideoStreamer streamer) {
        this.voiceClient = voiceClient;
        this.streamer = streamer;
    }

    @Override
    public void start(String videoPath) {
        if (active.get()) {
            System.err.println("[VideoPlayer] Video already playing.");
            return;
        }

        try {
            ProcessBuilder pb = getProcessBuilder(videoPath);
            ffmpegProcess = pb.start();

            InputStream ffmpegOut = ffmpegProcess.getInputStream();
            Sinks.Many<byte[]> sink = Sinks.many().unicast().onBackpressureBuffer();

            Flux.<byte[]>create(emitter -> {
                        try {
                            byte[] buffer = new byte[4096];
                            ByteArrayBuffer nalBuffer = new ByteArrayBuffer();
                            int bytesRead;
                            while ((bytesRead = ffmpegOut.read(buffer)) != -1) {
                                nalBuffer.append(buffer, 0, bytesRead);

                                int startIndex;
                                while ((startIndex = nalBuffer.indexOfStartCode()) != -1) {
                                    byte[] nal = nalBuffer.extractNal(startIndex);
                                    if (nal != null && nal.length > 0) {
                                        emitter.next(nal);
                                    }
                                }
                            }
                            emitter.complete();
                        } catch (IOException e) {
                            emitter.error(e);
                        }
                    })
                    .subscribeOn(Schedulers.boundedElastic())
                    .subscribe(sink::tryEmitNext);

            streamer.start(sink.asFlux());

            active.set(true);
            System.out.println("[VideoPlayer] Video streaming started.");
        } catch (IOException e) {
            System.err.println("[VideoPlayer] Failed to start FFmpeg: " + e.getMessage());
        } catch (URISyntaxException e) {
            throw new RuntimeException(e);
        }
    }

    @NotNull
    private static ProcessBuilder getProcessBuilder(String videoPath) throws URISyntaxException {
        String jarDir = new File(VideoPlayer.class.getProtectionDomain()
                .getCodeSource()
                .getLocation()
                .toURI()).getParent();
        String ffmpegPath = jarDir + File.separator + "ffmpeg.exe";

        ProcessBuilder pb = new ProcessBuilder(
                ffmpegPath, "-re",
                "-i", videoPath,
                "-c:v", "libx264",
                "-preset", "ultrafast",
                "-tune", "zerolatency",
                "-profile:v", "baseline",
                "-level", "4.2",
                "-pix_fmt", "yuv420p",
                "-f", "h264", "-"
        );
        pb.redirectErrorStream(true);
        return pb;
    }

    @Override
    public void stop() {
        streamer.stop();
        if (ffmpegProcess != null) {
            ffmpegProcess.destroy();
            ffmpegProcess = null;
        }
        active.set(false);
        System.out.println("[VideoPlayer] Video streaming stopped.");
    }

    @Override
    public void initialize() {
        // Optional: preload FFmpeg
    }

    @Override
    public void joinChannel(String guildId, String channelId) {
        // If needed for signaling
    }

    @Override
    public void shutdown() {
        stop();
    }

    @Override
    public boolean isActive() {
        return active.get();
    }

    private static class ByteArrayBuffer {
        private byte[] buf = new byte[8192];
        private int len = 0;

        void append(byte[] data, int off, int length) {
            if (len + length > buf.length) {
                byte[] newBuf = new byte[(len + length) * 2];
                System.arraycopy(buf, 0, newBuf, 0, len);
                buf = newBuf;
            }
            System.arraycopy(data, off, buf, len, length);
            len += length;
        }

        int indexOfStartCode() {
            for (int i = 0; i < len - 3; i++) {
                if (buf[i] == 0x00 && buf[i+1] == 0x00) {
                    if (buf[i+2] == 0x01) return i;
                    if (i < len - 4 && buf[i+2] == 0x00 && buf[i+3] == 0x01) return i;
                }
            }
            return -1;
        }

        byte[] extractNal(int startIndex) {
            int scSize = (buf[startIndex+2] == 0x01) ? 3 : 4;
            int nextStart = -1;
            for (int i = startIndex + scSize; i < len - 3; i++) {
                if (buf[i] == 0x00 && buf[i+1] == 0x00 &&
                        (buf[i+2] == 0x01 || (buf[i+2] == 0x00 && buf[i+3] == 0x01))) {
                    nextStart = i;
                    break;
                }
            }

            int nalLength = (nextStart == -1 ? len : nextStart) - (startIndex + scSize);
            byte[] nal = new byte[nalLength];
            System.arraycopy(buf, startIndex + scSize, nal, 0, nalLength);

            if (nextStart != -1) {
                System.arraycopy(buf, nextStart, buf, 0, len - nextStart);
                len -= nextStart;
            } else {
                len = 0;
            }
            return nal;
        }
    }
}

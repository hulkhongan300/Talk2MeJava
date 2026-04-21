import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.function.*;

import javax.sound.sampled.*;

public class AudioPipeline {
    private static final int BUFFER_FRAMES = 2048;
    private static final int RING_CAPACITY = 480000;
    private static final int TRANSCRIBE_INTERVAL_S = 2;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private volatile Consumer<Integer> levelCallback;
    private volatile Consumer2<byte[], Integer> transcriptionCallback;

    public AudioPipeline(int sampleRate, Config config) {
    }

    public interface Consumer2<T, U> {
        void accept(T data, U frames);
    }

    public void capture(int sampleRate, int vadSilenceFrames,
                       Consumer<Integer> levelCallback,
                       Consumer2<byte[], Integer> transcriptionCallback)
                       throws MicrophoneException {
        this.levelCallback = levelCallback;
        this.transcriptionCallback = transcriptionCallback;
        this.running.set(true);

        AudioFormat format = new AudioFormat(
            AudioFormat.Encoding.PCM_SIGNED,
            sampleRate, 16, 1, 2, sampleRate, false);

        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);
        if (!AudioSystem.isLineSupported(info)) {
            throw new MicrophoneException("Line not supported");
        }

        TargetDataLine mic;
        try {
            mic = (TargetDataLine) AudioSystem.getLine(info);
        } catch (LineUnavailableException e) {
            throw new MicrophoneException("Line unavailable: " + e.getMessage());
        }
        try {
            mic.open(format);
        } catch (LineUnavailableException e) {
            throw new MicrophoneException("Line unavailable: " + e.getMessage());
        }
        try {
            mic.start();

            byte[] ringBuffer = new byte[RING_CAPACITY];
            int writePos = 0;
            int lastTranscribeAt = 0;
            long transcribeIntervalMs = TRANSCRIBE_INTERVAL_S * 1000L;
            int minSpeechFrames = (sampleRate * TRANSCRIBE_INTERVAL_S) / 2;

            byte[] buffer = new byte[BUFFER_FRAMES * 2];
            long startTime = System.currentTimeMillis();

            while (running.get()) {
                int got = mic.read(buffer, 0, buffer.length);
                if (got <= 0) {
                    Thread.yield();
                    continue;
                }

                int level = calculateLevel(buffer, got);
                if (levelCallback != null) {
                    levelCallback.accept(level);
                }

                int frameCount = got / 2;
                for (int i = 0; i < got; i++) {
                    ringBuffer[writePos % RING_CAPACITY] = buffer[i];
                    writePos++;
                }

                long elapsedMs = System.currentTimeMillis() - startTime;
                long sinceLastTranscribe = elapsedMs - (long) lastTranscribeAt;

                if (sinceLastTranscribe >= transcribeIntervalMs
                    && (writePos - lastTranscribeAt) >= minSpeechFrames) {
                    int available = writePos - lastTranscribeAt;
                    int transcriptFrames = Math.min(available / 2, RING_CAPACITY / 2);
                    transcriptFrames = Math.max(transcriptFrames, sampleRate / 4);

                    byte[] pcm = new byte[transcriptFrames * 2];
                    int start = (writePos - transcriptFrames * 2);
                    for (int i = 0; i < transcriptFrames * 2; i++) {
                        pcm[i] = ringBuffer[(start + i) % RING_CAPACITY];
                    }
                    lastTranscribeAt = (int) elapsedMs;

                    if (transcriptionCallback != null) {
                        transcriptionCallback.accept(pcm, transcriptFrames);
                    }
                }
            }

            if (writePos > 0 && (writePos - lastTranscribeAt) >= minSpeechFrames / 4) {
                int available = writePos - lastTranscribeAt;
                int transcriptFrames = Math.min(available / 2, RING_CAPACITY / 2);
                transcriptFrames = Math.max(transcriptFrames, sampleRate / 10);

                byte[] pcm = new byte[transcriptFrames * 2];
                int start = (writePos - transcriptFrames * 2);
                for (int i = 0; i < transcriptFrames * 2; i++) {
                    pcm[i] = ringBuffer[(start + i) % RING_CAPACITY];
                }

                if (transcriptionCallback != null) {
                    transcriptionCallback.accept(pcm, transcriptFrames);
                }
            }
        } finally {
            mic.stop();
            mic.close();
        }
    }

    public void stop() {
        running.set(false);
    }

    private int calculateLevel(byte[] buf, int len) {
        int max = 0;
        for (int i = 0; i < len - 1; i += 2) {
            int low = buf[i] & 0xFF;
            int high = buf[i + 1];
            short sample = (short) ((high << 8) | low);
            int abs = Math.abs(sample);
            if (abs > max) max = abs;
        }
        return Math.min(100, max * 100 / 32768);
    }

    public static class MicrophoneException extends Exception {
        public MicrophoneException(String msg) { super(msg); }
    }
}
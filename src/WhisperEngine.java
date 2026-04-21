import io.github.ggerganov.whispercpp.WhisperCpp;
import io.github.ggerganov.whispercpp.params.WhisperFullParams;
import io.github.ggerganov.whispercpp.params.CBool;
import io.github.ggerganov.whispercpp.params.WhisperSamplingStrategy;

import java.nio.file.*;

public class WhisperEngine {
    private final Config config;
    private WhisperCpp whisper;

    public WhisperEngine(Config config) {
        this.config = config;
        loadModel();
    }

    private void loadModel() {
        Path modelPath = Paths.get(config.modelPath());
        if (!Files.exists(modelPath)) {
            System.err.println("Warning: Whisper model not found at " + modelPath);
            return;
        }

        try {
            whisper = new WhisperCpp();
            whisper.initContext(modelPath.toAbsolutePath().toString());
            System.err.println("Whisper model loaded: " + config.whisperModel());
        } catch (Exception e) {
            System.err.println("Warning: Could not load Whisper: " + e.getMessage());
        }
    }

    public String transcribe(byte[] pcmData, int sampleRate, String language) {
        if (whisper == null) {
            return fallbackTranscribe(pcmData);
        }

        try {
            var params = whisper.getFullDefaultParams(
                WhisperSamplingStrategy.WHISPER_SAMPLING_GREEDY);
            params.language = language;
            params.no_context = CBool.TRUE;
            params.single_segment = CBool.TRUE;
            params.n_threads = 2;

            float[] samples = toFloats(pcmData);
            String result = whisper.fullTranscribe(params, samples);
            return result != null ? result.trim() : null;
        } catch (Exception e) {
            System.err.println("Transcription error: " + e.getMessage());
            return null;
        }
    }

    private float[] toFloats(byte[] pcm) {
        float[] out = new float[pcm.length / 2];
        for (int i = 0; i < out.length; i++) {
            int low = pcm[i * 2] & 0xFF;
            int high = pcm[i * 2 + 1];
            out[i] = ((short) ((high << 8) | low)) / 32768.0f;
        }
        return out;
    }

    private String fallbackTranscribe(byte[] pcmData) {
        System.err.println("Using Python fallback.");
        try {
            Path tempWav = Files.createTempFile("whisper_temp", ".wav");
            try {
                Files.write(tempWav, makeWav(pcmData));
                ProcessBuilder pb = new ProcessBuilder(
                    "venv/bin/python3", "-c",
                    "import whisper; m = whisper.load_model('" + config.whisperModel() +
                    "'); print(m.transcribe('" + tempWav +
                    "', language='" + config.language() + "')['text'])");
                pb.redirectErrorStream(true);
                Process p = pb.start();
                String output = new String(p.getInputStream().readAllBytes());
                p.waitFor();
                return output.trim();
            } finally {
                Files.deleteIfExists(tempWav);
            }
        } catch (Exception e) {
            System.err.println("Fallback failed: " + e.getMessage());
            return null;
        }
    }

    private byte[] makeWav(byte[] pcm) {
        byte[] header = new byte[44];
        String riff = "RIFF";
        String wave = "WAVE";
        String fmt = "fmt ";
        String data = "data";
        for (int i = 0; i < 4; i++) {
            header[i] = (byte) riff.charAt(i);
            header[8 + i] = (byte) wave.charAt(i);
            header[12 + i] = (byte) fmt.charAt(i);
            header[36 + i] = (byte) data.charAt(i);
        }
        int totalSize = pcm.length + 36;
        header[4] = (byte) (totalSize & 0xFF);
        header[5] = (byte) ((totalSize >> 8) & 0xFF);
        header[6] = (byte) ((totalSize >> 16) & 0xFF);
        header[7] = (byte) ((totalSize >> 24) & 0xFF);
        header[16] = 16; header[17] = 0; header[18] = 0; header[19] = 0;
        header[20] = 1; header[21] = 0;
        header[22] = 1; header[23] = 0;
        header[24] = (byte) 0x40; header[25] = (byte) 0x1F;
        header[26] = 0; header[27] = 0;
        header[28] = (byte) 0xE0; header[29] = (byte) 0x3F;
        header[30] = 0; header[31] = 0;
        header[32] = 2; header[33] = 0;
        header[34] = 16; header[35] = 0;
        header[40] = (byte) (pcm.length & 0xFF);
        header[41] = (byte) ((pcm.length >> 8) & 0xFF);
        header[42] = (byte) ((pcm.length >> 16) & 0xFF);
        header[43] = (byte) ((pcm.length >> 24) & 0xFF);

        byte[] out = new byte[44 + pcm.length];
        System.arraycopy(header, 0, out, 0, 44);
        System.arraycopy(pcm, 0, out, 44, pcm.length);
        return out;
    }

    public void shutdown() {
        if (whisper != null) {
            try { whisper.close(); } catch (Exception ignored) {}
        }
    }
}
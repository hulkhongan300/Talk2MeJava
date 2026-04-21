import java.io.*;
import java.nio.file.*;
import java.util.*;

public class Config {
    private static final String DEFAULT_WHISPER_MODEL = "base";
    private static final String DEFAULT_LANGUAGE = "en";
    private static final int DEFAULT_VAD_SILENCE_FRAMES = 30;
    private static final String DEFAULT_VAD_MODEL = "tiny";
    private static final int DEFAULT_SAMPLE_RATE = 16000;

    private final String whisperModel;
    private final String language;
    private final int vadSilenceFrames;
    private final String vadModel;
    private final int sampleRate;
    private final String modelPath;
    private final int gpuDevice;

    private Config(String whisperModel, String language, int vadSilenceFrames,
                 String vadModel, int sampleRate, String modelPath, int gpuDevice) {
        this.whisperModel = whisperModel;
        this.language = language;
        this.vadSilenceFrames = vadSilenceFrames;
        this.vadModel = vadModel;
        this.sampleRate = sampleRate;
        this.modelPath = modelPath;
        this.gpuDevice = gpuDevice;
    }

    public static Config load(String path) {
        Properties props = new Properties();
        try (InputStream in = Files.newInputStream(Paths.get(path))) {
            props.load(in);
        } catch (IOException e) {
        }

        String whisperModel = props.getProperty("whisper.model", DEFAULT_WHISPER_MODEL);
        String language = props.getProperty("language", DEFAULT_LANGUAGE);
        int vadSilenceFrames = Integer.parseInt(
            props.getProperty("vad.silence.frames", "" + DEFAULT_VAD_SILENCE_FRAMES));
        String vadModel = props.getProperty("vad.model", DEFAULT_VAD_MODEL);
        int sampleRate = Integer.parseInt(
            props.getProperty("sample.rate", "" + DEFAULT_SAMPLE_RATE));
        String modelPath = props.getProperty("model.path",
            "models/ggml-" + whisperModel + ".bin");
        int gpuDevice = Integer.parseInt(
            props.getProperty("gpu.device", "0"));

        return new Config(whisperModel, language, vadSilenceFrames,
            vadModel, sampleRate, modelPath, gpuDevice);
    }

    public static Config defaults() {
        return new Config(DEFAULT_WHISPER_MODEL, DEFAULT_LANGUAGE,
            DEFAULT_VAD_SILENCE_FRAMES, DEFAULT_VAD_MODEL,
            DEFAULT_SAMPLE_RATE, "models/ggml-base.bin", 0);
    }

    public String whisperModel() { return whisperModel; }
    public String language() { return language; }
    public int vadSilenceFrames() { return vadSilenceFrames; }
    public String vadModel() { return vadModel; }
    public int sampleRate() { return sampleRate; }
    public String modelPath() { return modelPath; }
    public int gpuDevice() { return gpuDevice; }
}
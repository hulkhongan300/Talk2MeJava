# AGENTS.md

## Project
Voice-to-Clipboard Java application using local Whisper for speech recognition.

## Build & Run

```bash
# Compile (requires whisper-jni.jar in libs/)
javac -cp "libs/whisper-jni.jar" src/*.java

# Run
java -Dio.github.givimad.whisperjni.libdir="$PWD/libs-native" \
    -cp "libs/whisper-jni.jar:src" Main
```

## Prerequisites

### Native libs (libs-native/)
The custom-built whisper.cpp native libraries should be in `libs-native/`:

```bash
# Build whisper.cpp first, then copy:
cp whisper.cpp/build/libwhisper.so libs-native/
cp whisper.cpp/build/libwhisper-jni.so libs-native/
```

### whisper-jni JAR (libs/)
Download from Maven Central:

```bash
mkdir -p libs && curl -L "https://repo1.maven.org/maven2/io/github/givimad/whisper-jni/1.7.1/whisper-jni-1.7.1.jar" -o libs/whisper-jni.jar
```

### Whisper model (models/)
Download the ggml model:

```bash
mkdir -p models
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin" -o models/ggml-base.bin
```

### Python fallback (venv/) — optional
If the native lib fails to load, the app falls back to Python:

```bash
python3 -m venv venv
./venv/bin/pip install openai-whisper
```

## Key Details
- **Entry point**: `src/Main.java`
- **Whisper JNI wrapper**: `WhisperEngine.java` — uses whisper-jni JAR, with Python fallback
- **Audio pipeline**: `AudioPipeline.java` — ring buffer, fixed interval transcription
- **Configuration**: `config.properties`
- **Model loaded once** at startup, reused for all transcriptions
- **No temp WAV files** — PCM stays in memory

## IDE
IntelliJ IDEA project file: `Talk2MeJava.iml` - open folder directly in IntelliJ.
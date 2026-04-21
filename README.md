# Voice-to-Clipboard Java App

## Overview

Java desktop app that captures audio, transcribes via local Whisper, and auto-copies to clipboard. Press and hold G to record.

---

## Project Structure

```
src/
├── Main.java               # Entry point, UI orchestration
├── Config.java             # Properties-based configuration
├── AudioPipeline.java    # Audio capture + VAD (dedicated thread)
├── WhisperEngine.java     # whisper.cpp JNA wrapper (singleton)
├── TranscriptionHistory.java  # Circular buffer (last N entries)
config.properties      # Runtime configuration
setup.sh             # First-time setup script
libs/                # External JARs (whisper-jni, jna)
libs-native/         # Built native libs
models/              # Whisper model files
```

---

## Build & Run

### 1. Download Dependencies

```bash
# Create directories
mkdir -p libs libs-native models

# Download whisper.cpp JNA JAR and JNA dependency
curl -L "https://repo1.maven.org/maven2/net/java/dev/jna/jna/5.15.0/jna-5.15.0.jar" -o libs/jna-5.15.0.jar

# Build whisper.cpp (see below) OR use prebuilt JAR:
curl -L "https://repo1.maven.org/maven2/io/github/givimad/whisper-jni/1.7.1/whisper-jni-1.7.1.jar" -o libs/whisper-jni.jar
```

### 2. Download Whisper Model

```bash
curl -L "https://huggingface.co/ggerganov/whisper.cpp/resolve/main/ggml-base.bin" -o models/ggml-base.bin
```

### 3. Build whisper.cpp (Recommended for CPU acceleration)

```bash
git clone --depth 1 https://github.com/ggerganov/whisper.cpp.git
cd whisper.cpp
mkdir build && cd build
cmake .. -DCMAKE_BUILD_TYPE=Release
make -j$(nproc)
cp libwhisper.so /path/to/Talk2MeJava/libs-native/

cd ../bindings/java
./gradlew jar
cp build/libs/whispercpp-*.jar /path/to/Talk2MeJava/libs-native/
```

### 4. Python Fallback (Optional - for when native fails)

If native libs are unavailable, the app falls back to Python:

```bash
# Create venv and install OpenAI Whisper
python3 -m venv venv
./venv/bin/pip install openai-whisper
```

### 5. Compile & Run

```bash
javac -cp "libs-native/whispercpp-1.4.0.jar:libs/jna-5.15.0.jar" src/*.java

java -Djava.library.path=libs-native \
    -cp "libs-native/whispercpp-1.4.0.jar:libs/jna-5.15.0.jar:src" Main
```

---

## Configuration (config.properties)

```properties
whisper.model=base         # tiny, base, small, medium, large
language=en               # BCP-47 language code
sample.rate=16000         # Target sample rate
model.path=models/ggml-base.bin
```

---

## Architecture

```
Main
├── Config         ← Properties file, no hardcoded values
├── AudioPipeline  ← Ring buffer, fixed-interval transcription
├── WhisperEngine  ← Model loaded once, JNA or Python fallback
└── TranscriptionHistory ← Circular buffer, last 10 entries
```

### Key design decisions

| Concern | Solution |
|---------|---------|
| JNA lib loaded | Uses libwhisper.so from libs-native |
| Java binding | whisper.cpp JNA (not JNI) - no native bridge needed |
| CPU fallback | Python subprocess (slower) if JNA fails |
| Audio buffer | Ring buffer + PCM in byte[] — no disk I/O |
| Transcribe interval | Every 2 seconds during recording |
| State machine | AtomicReference<AppState> — IDLE, RECORDING |
| Thread safety | ExecutorService; UI via SwingUtilities |
| Resource cleanup | finally on mic.close() |
| Clipboard history | TranscriptionHistory synchronized list |

---

## Usage

1. Run the app — small window with G button appears
2. Press and hold G (or click G button) to record
3. Speak — audio levels visualized in real-time
4. Release G when done — transcription copied to clipboard
5. Paste anywhere with Ctrl+V
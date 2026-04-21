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
├── WhisperEngine.java     # whisper.cpp JNI wrapper (singleton)
└── TranscriptionHistory.java  # Circular buffer (last N entries)
config.properties         # Runtime configuration
setup.sh                  # First-time setup script
```

## Build & Run

```bash
# Setup (first time)
chmod +x setup.sh && ./setup.sh

# Compile
javac src/*.java

# Run
java -cp src Main
```

## Configuration (config.properties)

```properties
whisper.model=base         # tiny, base, small, medium, large
language=en               # BCP-47 language code
vad.silence.frames=30     # Silence frames before transcribe
sample.rate=16000         # Target sample rate
model.path=models/ggml-base.bin  # Path to ggml model
```

## Architecture

```
Main
├── Config         ← Properties file, no hardcoded values
├── AudioPipeline  ← Ring buffer, VAD, dedicated thread
│   └── (no file writes — PCM stays in memory)
├── WhisperEngine ← Model loaded once, JNI or Python fallback
└── TranscriptionHistory ← Circular buffer, last 10 entries
```

### Key design decisions

| Concern | Solution |
|---------|---------|
| JNI native lib not found | WhisperEngine falls back to Python subprocess transparently |
| Temp WAV files | Ring buffer + PCM in byte[] — no disk I/O |
| Fixed 3s interval | VAD: silence count triggers transcription only after speech |
| State machine | `AtomicReference<AppState>` — IDLE, RECORDING, PROCESSING |
| Thread safety | Separate executor thread; UI updates via `SwingUtilities.invokeLater` |
| Resource cleanup | `finally` on mic.close(); shutdown hook closes Whisper model |
| Clipboard history | `TranscriptionHistory` — `synchronized` circular list |
| Level visualization | Throttled to 50ms updates to avoid EDT overload |

## whisper.cpp JNI Setup (optional, recommended)

For production performance, build whisper.cpp native bindings:

```bash
# Clone whisper.cpp
git clone https://github.com/ggerganov/whisper.cpp
cd whisper.cpp && mkdir build && cd build && cmake .. && make -j

# Build JNI bindings
cd bindings/jni
mkdir build && cd build
cmake ../..
make

# Copy native lib
cp libwhisper-jni.so ../libs/
mkdir -p ../models
./models/download-ggml-model.sh base
```

The app detects `libwhisper-jni.so` automatically and uses JNI. If not found, it falls back to Python subprocess (slower — model reloaded each time).
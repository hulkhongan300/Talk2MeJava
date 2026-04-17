# AGENTS.md

## Project
Voice-to-Clipboard Java application using local Whisper for speech recognition.

## Build & Run

```bash
javac -cp "libs/*" src/Main.java
java -cp "libs/*:src" Main
```

## Prerequisites
Python venv with Whisper must exist at `venv/`. First-time run downloads ~140MB Whisper base model.

```bash
python3 -m venv venv
./venv/bin/pip install openai-whisper
```

## Key Details
- **Entry point**: `src/Main.java`
- **No external libs needed** - uses only Java standard library
- **Python invocation**: Direct path `venv/bin/python3` (not activated via `source`)
- **Recording limit**: 30 seconds max, saved as temp WAV file
- **GUI**: Simple Swing with Start/Stop buttons, text auto-copied to clipboard
- **transcribe.py**: Standalone script (optional, not used by main app)

## IDE
IntelliJ IDEA project file: `Talk2MeJava.iml` - open folder directly in IntelliJ.
# AGENTS.md

## Project Overview
- **Project**: Voice-to-Clipboard Java application
- **Language**: Java + Python (Whisper)
- **Entry Point**: `src/Main.java`
- **Build Tool**: Manual javac/java (no Gradle/Maven)

## How to Build & Run

### Prerequisites
1. Create Python virtual environment and install Whisper:
```bash
python3 -m venv venv
source venv/bin/activate
pip install openai-whisper
```

### Compile
```bash
javac -cp "libs/*" src/Main.java
```

### Run
```bash
java -cp "libs/*:src" Main
```

## Key Files
- `src/Main.java` - Main application (Java GUI + calls Python for transcription)
- `transcribe.py` - Standalone transcription script (optional)
- `venv/` - Python virtual environment with Whisper installed

## Non-Obvious Behavior
- Program uses a simple GUI (no command-line mode)
- Click "Start Listening" then speak - max 30 seconds
- Click "Stop & Copy" to stop early, or it stops automatically
- Recognized text is copied to clipboard automatically
- Uses local Whisper (no API key needed)
- First run downloads Whisper model (~140MB for base model)

## IDE
- IntelliJ IDEA project file: `Talk2MeJava.iml`
- Open project folder directly in IntelliJ to work
#!/bin/bash
set -e

if [ ! -d "venv" ]; then
    echo "Creating Python venv..."
    python3 -m venv venv
fi

if [ ! -f "venv/bin/whisper" ]; then
    echo "Installing whisper..."
    ./venv/bin/pip install openai-whisper
fi

echo ""
echo "Setup complete. Run the app with:"
echo "  java -cp src Main"
echo ""
echo "For whisper.cpp JNI (recommended for performance):"
echo "  1. Build whisper.cpp: git clone https://github.com/ggerganov/whisper.cpp && cd whisper.cpp && mkdir build && cd build && cmake .. && make"
echo "  2. Build JNI bindings: cd bindings/jni && mkdir -p build && cd build && cmake ../.. && make"
echo "  3. Copy libwhisper-jni.so to libs/"
echo "  4. Download a model: ./models/download-ggml-model.sh base"
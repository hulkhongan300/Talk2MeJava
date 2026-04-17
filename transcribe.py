#!/usr/bin/env python3
import sys
import whisper

model = whisper.load_model("base")
result = model.transcribe(sys.argv[1], language="en")
print(result["text"])
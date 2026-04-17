# 🎤 Voice-to-Clipboard Java App

## 📌 Overview

This project is a simple Java application that captures audio input from a microphone, converts it into text using speech recognition, and automatically copies the result to the system clipboard for easy pasting.

The goal is to create a lightweight and efficient tool that allows hands-free text input.

---

## 🚀 Features

* 🎙️ Capture live audio from microphone
* 🧠 Convert speech to text using local Whisper
* 📋 Auto-copy transcription to clipboard
* ⚡ Live transcription while speaking
* 📊 Real-time audio level visualization
* 🎨 Modern dark-themed GUI

---

## 🛠️ Technologies Used

* Java
* Java Sound API (for microphone input)
* Whisper (local speech recognition)
* AWT Toolkit (for clipboard access)

---

## 📂 Project Structure

```
project-root/
│
├── src/
│   └── Main.java   # Main application entry point
│
├── README.md
└── (optional) libs/  # External libraries (if needed)
```

---

## ⚙️ Setup Instructions

### 1. Clone the Repository

```bash
git clone https://github.com/hulkhongan300/Talk2MeJava?tab=readme-ov-file
cd voice-to-clipboard
```

### 2. Install Whisper

```bash
python3 -m venv venv
./venv/bin/pip install openai-whisper
```

The first run downloads the Whisper base model (~140MB).

---

### 3. Compile

```bash
javac -cp "libs/*" src/Main.java
```

### 4. Run

```bash
java -cp "libs/*:src" Main
```

---

## 🧪 How It Works

1. Run the app - a small window appears with a G button.
2. Press and hold G (or click and hold the G button) to record.
3. Speak into your microphone - see audio levels in real-time.
4. Transcription starts while you speak and clipboard updates live.
5. Release G when done - final transcription is copied.
6. Paste anywhere with `Ctrl + V`.

---

## ✏️ Example Usage

1. Run the program - small dark window appears
2. Press and hold G button
3. Say: "Hello world, this is a test"
4. Watch the level bars respond to your voice
5. Release G when finished
6. Clipboard already has the text - just paste!

---

## ⚠️ Notes

* Microphone permissions must be enabled on your system.
* Accuracy depends on the speech recognition library used.
* Background noise may affect performance.

---

## 🔮 Future Improvements

* Support multiple languages
* Minimize to system tray
* Keyboard shortcuts customization
* Save transcription history

---

## 🤝 Contributing

Feel free to fork this project and submit pull requests.

---

## 📄 License

See [LICENSE](LICENSE) for details.

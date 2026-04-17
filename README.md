# 🎤 Voice-to-Clipboard Java App

## 📌 Overview

This project is a simple Java application that captures audio input from a microphone, converts it into text using speech recognition, and automatically copies the result to the system clipboard for easy pasting.

The goal is to create a lightweight and efficient tool that allows hands-free text input.

---

## 🚀 Features

* 🎙️ Capture live audio from microphone
* 🧠 Convert speech to text (speech recognition)
* 📋 Automatically copy recognized text to clipboard
* ⚡ Simple and minimal Java implementation

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

1. The program listens to microphone input.
2. Audio is processed by a speech recognition engine.
3. Recognized text is captured.
4. Text is copied directly to your clipboard.
5. You can paste it anywhere using `Ctrl + V`.

---

## ✏️ Example Usage

1. Run the program
2. Speak into your microphone:

   > "Hello world, this is a test"
3. Open any text editor and paste
4. Output:

   ```
   Hello world, this is a test
   ```

---

## ⚠️ Notes

* Microphone permissions must be enabled on your system.
* Accuracy depends on the speech recognition library used.
* Background noise may affect performance.

---

## 🔮 Future Improvements

* Add GUI interface
* Support multiple languages
* Add real-time transcription display
* Add start/stop recording controls

---

## 🤝 Contributing

Feel free to fork this project and submit pull requests.

---

## 📄 License

See [LICENSE](LICENSE) for details.

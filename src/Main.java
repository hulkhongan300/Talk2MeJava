import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.sound.sampled.*;

public class Main {
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    private static volatile boolean recording = false;
    private static volatile boolean inputReceived = false;
    private static TrayIcon trayIcon;

    public static void main(String[] args) {
        startApp();
    }

    private static void startApp() {
        if (!SystemTray.isSupported()) {
            System.err.println("SystemTray not supported");
            System.exit(1);
        }

        PopupMenu popupMenu = new PopupMenu();
        popupMenu.add(new MenuItem("Press & hold to record"));

        trayIcon = new TrayIcon(Toolkit.getDefaultToolkit().getImage(""), "Voice to Clipboard", popupMenu);
        trayIcon.setImageAutoSize(true);
        trayIcon.addMouseListener(new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                if (!inputReceived) {
                    inputReceived = true;
                    recording = true;
                    showStatus("Recording... Speak now!");
                    processRecording();
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (recording) {
                    recording = false;
                    showStatus("Stopped");
                }
            }
        });

        try {
            SystemTray.getSystemTray().add(trayIcon);
        } catch (AWTException e) {
            System.err.println("Failed to add tray icon: " + e.getMessage());
            System.exit(1);
        }

        showStatus("Ready - press & hold tray icon to record");
    }

    private static void showStatus(String message) {
        if (trayIcon != null) {
            trayIcon.setToolTip(message);
        }
        System.out.println(message);
    }

    private static void processRecording() {
        new Thread(() -> {
            try {
                Path tempFile = captureAudio();
                if (tempFile != null) {
                    showStatus("Transcribing...");
                    String result = transcribeWithWhisper(tempFile.toFile().getAbsolutePath());
                    Files.deleteIfExists(tempFile);
                    if (result != null && !result.isEmpty()) {
                        copyToClipboard(result);
                        showStatus("Copied: " + result);
                        SystemTray.getSystemTray().remove(trayIcon);
                        System.exit(0);
                    } else {
                        showStatus("No speech detected");
                    }
                }
                inputReceived = false;
            } catch (Exception e) {
                System.err.println("Error: " + e.getMessage());
                showStatus("Error: " + e.getMessage());
                inputReceived = false;
            }
        }).start();
    }

    private static Path captureAudio() throws Exception {
        AudioFormat format = new AudioFormat(AudioFormat.Encoding.PCM_SIGNED,
            SAMPLE_RATE, 16, 1, 2, SAMPLE_RATE, false);
        DataLine.Info info = new DataLine.Info(TargetDataLine.class, format);

        if (!AudioSystem.isLineSupported(info)) {
            throw new Exception("Microphone not supported");
        }

        TargetDataLine microphone = (TargetDataLine) AudioSystem.getLine(info);
        microphone.open(format);
        microphone.start();

        ByteArrayOutputStream audioData = new ByteArrayOutputStream();
        byte[] buffer = new byte[BUFFER_SIZE];

        long startTime = System.currentTimeMillis();
        long maxDuration = 30000;

        while (recording && (System.currentTimeMillis() - startTime) < maxDuration) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                audioData.write(buffer, 0, bytesRead);
            }
        }

        microphone.stop();
        microphone.close();

        byte[] audioBytes = audioData.toByteArray();
        if (audioBytes.length < 1000) {
            return null;
        }

        return writeWavFile(audioBytes, format);
    }

    private static Path writeWavFile(byte[] pcmData, AudioFormat format) throws Exception {
        Path tempFile = Files.createTempFile("audio", ".wav");

        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData);
             ByteArrayOutputStream baos = new ByteArrayOutputStream()) {

            AudioInputStream audioInputStream = new AudioInputStream(
                new BufferedInputStream(bais), format, pcmData.length / format.getFrameSize());

            if (AudioSystem.isFileTypeSupported(AudioFileFormat.Type.WAVE, audioInputStream)) {
                AudioSystem.write(audioInputStream, AudioFileFormat.Type.WAVE, tempFile.toFile());
            }
        }

        return tempFile;
    }

    private static String transcribeWithWhisper(String audioFilePath) throws Exception {
        String venvPython = Paths.get("venv/bin/python3").toAbsolutePath().toString();

        ProcessBuilder pb = new ProcessBuilder(
            venvPython, "-W", "ignore", "-c",
            "import whisper; model = whisper.load_model('base', device='cpu'); print(model.transcribe('" + audioFilePath + "', language='en')['text'])"
        );
        pb.redirectErrorStream(true);
        Process p = pb.start();

        BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()));
        StringBuilder output = new StringBuilder();
        String line;
        while ((line = reader.readLine()) != null) {
            output.append(line);
        }

        int exitCode = p.waitFor();
        if (exitCode != 0) {
            throw new Exception("Transcription failed");
        }

        return output.toString().trim();
    }

    private static void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, selection);
    }
}
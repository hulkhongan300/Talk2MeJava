import javax.swing.*;
import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import javax.sound.sampled.*;

public class Main {
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    private static volatile boolean listening = false;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createAndShowGUI());
    }

    private static void createAndShowGUI() {
        JFrame frame = new JFrame("Voice to Clipboard - Whisper");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(450, 220);
        frame.setLocationRelativeTo(null);

        JLabel statusLabel = new JLabel("Click 'Start' to begin listening", SwingConstants.CENTER);
        statusLabel.setFont(new Font("Arial", Font.PLAIN, 14));

        JButton startButton = new JButton("Start Listening");
        JButton stopButton = new JButton("Stop & Copy");
        stopButton.setEnabled(false);

        JPanel buttonPanel = new JPanel(new FlowLayout());
        buttonPanel.add(startButton);
        buttonPanel.add(stopButton);

        frame.setLayout(new BorderLayout(10, 10));
        frame.add(statusLabel, BorderLayout.CENTER);
        frame.add(buttonPanel, BorderLayout.SOUTH);
        frame.setVisible(true);

        startButton.addActionListener(e -> {
            listening = true;
            statusLabel.setText("Listening... Speak now!");
            startButton.setEnabled(false);
            stopButton.setEnabled(true);

            new Thread(() -> {
                try {
                    statusLabel.setText("Recording...");
                    Path tempFile = captureAudio();
                    if (tempFile != null) {
                        statusLabel.setText("Transcribing...");
                        String result = transcribeWithWhisper(tempFile.toFile().getAbsolutePath());
                        Files.deleteIfExists(tempFile);
                        if (result != null && !result.isEmpty()) {
                            copyToClipboard(result);
                            SwingUtilities.invokeLater(() ->
                                statusLabel.setText("Copied: \"" + result + "\""));
                        } else {
                            SwingUtilities.invokeLater(() ->
                                statusLabel.setText("No speech detected"));
                        }
                    }
                } catch (Exception ex) {
                    ex.printStackTrace();
                    SwingUtilities.invokeLater(() ->
                        statusLabel.setText("Error: " + ex.getMessage()));
                } finally {
                    SwingUtilities.invokeLater(() -> {
                        startButton.setEnabled(true);
                        stopButton.setEnabled(false);
                    });
                }
            }).start();
        });

        stopButton.addActionListener(e -> {
            listening = false;
            startButton.setEnabled(true);
            stopButton.setEnabled(false);
            statusLabel.setText("Click 'Start' to begin listening");
        });
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

        while (listening && (System.currentTimeMillis() - startTime) < maxDuration) {
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
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
import javax.swing.*;

public class Main {
    private static final int SAMPLE_RATE = 16000;
    private static final int BUFFER_SIZE = 4096;
    private static volatile boolean recording = false;
    private static volatile boolean inputReceived = false;
    private static JFrame frame;
    private static LevelPanel levelPanel;
    private static JLabel statusLabel;
    private static JButton keyButton;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> createGUI());
    }

    private static void createGUI() {
        frame = new JFrame("Voice to Clipboard");
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(220, 160);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.setBackground(new Color(45, 45, 50));

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(true);
        mainPanel.setBackground(new Color(45, 45, 50));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        levelPanel = new LevelPanel();
        levelPanel.setMaximumSize(new Dimension(190, 40));
        levelPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        keyButton = createKeyButton();
        keyButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel = new JLabel("Hold G to record");
        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 180, 185));

        mainPanel.add(levelPanel);
        mainPanel.add(Box.createVerticalStrut(12));
        mainPanel.add(keyButton);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(statusLabel);

        frame.add(mainPanel);
        frame.setVisible(true);

        setupKeyListener();
    }

    private static JButton createKeyButton() {
        JButton btn = new JButton("G") {
            private boolean hover = false;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                int w = getWidth();
                int h = getHeight();

                if (getModel().isPressed()) {
                    g2.setColor(new Color(80, 200, 120));
                    g2.fillOval(0, 0, w - 1, h - 1);
                } else if (hover) {
                    g2.setColor(new Color(100, 220, 150));
                    g2.fillOval(0, 0, w - 1, h - 1);
                } else {
                    g2.setColor(new Color(60, 180, 100));
                    g2.fillOval(0, 0, w - 1, h - 1);
                }

                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
                FontMetrics fm = g2.getFontMetrics();
                int x = (w - fm.stringWidth("G")) / 2;
                int y = (h + fm.getAscent()) / 2 - 2;
                g2.drawString("G", x, y);
            }

            @Override
            public void addNotify() {
                super.addNotify();
                addMouseListener(new MouseAdapter() {
                    @Override
                    public void mouseEntered(MouseEvent e) {
                        hover = true;
                        repaint();
                    }

                    @Override
                    public void mouseExited(MouseEvent e) {
                        hover = false;
                        repaint();
                    }

                    @Override
                    public void mousePressed(MouseEvent e) {
                        startRecording();
                    }

                    @Override
                    public void mouseReleased(MouseEvent e) {
                        stopRecording();
                    }
                });
            }
        };

        btn.setPreferredSize(new Dimension(70, 70));
        btn.setMaximumSize(new Dimension(70, 70));
        btn.setContentAreaFilled(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setBorderPainted(false);

        return btn;
    }

    private static void startRecording() {
        if (!inputReceived) {
            inputReceived = true;
            recording = true;
            statusLabel.setText("Recording...");
            statusLabel.setForeground(new Color(80, 200, 120));
            levelPanel.reset();
            processRecording();
        }
    }

    private static void stopRecording() {
        if (recording) {
            recording = false;
        }
    }

    private static void setupKeyListener() {
        InputMap im = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = frame.getRootPane().getActionMap();

        im.put(KeyStroke.getKeyStroke("pressed G"), "startRecord");
        im.put(KeyStroke.getKeyStroke("released G"), "stopRecord");

        am.put("startRecord", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                startRecording();
            }
        });

        am.put("stopRecord", new AbstractAction() {
            @Override
            public void actionPerformed(java.awt.event.ActionEvent e) {
                stopRecording();
            }
        });
    }

    private static void processRecording() {
        new Thread(() -> {
            try {
                String result = captureAndTranscribeLive();
                if (result != null && !result.isEmpty()) {
                    copyToClipboard(result);
                    statusLabel.setText("Copied: " + truncate(result, 20));
                    statusLabel.setForeground(new Color(100, 180, 220));
                } else {
                    statusLabel.setText("No speech detected");
                    statusLabel.setForeground(new Color(255, 180, 100));
                }
                inputReceived = false;
            } catch (Exception ex) {
                System.err.println("Error: " + ex.getMessage());
                statusLabel.setText("Error: " + ex.getMessage());
                statusLabel.setForeground(new Color(255, 100, 100));
                inputReceived = false;
            }
        }).start();
    }

    private static String truncate(String s, int len) {
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    private static String captureAndTranscribeLive() throws Exception {
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
        StringBuilder fullResult = new StringBuilder();

        long startTime = System.currentTimeMillis();
        long maxDuration = 30000;
        long lastTranscribe = 0;
        long transcribeInterval = 3000;

        while (recording && (System.currentTimeMillis() - startTime) < maxDuration) {
            int bytesRead = microphone.read(buffer, 0, buffer.length);
            if (bytesRead > 0) {
                audioData.write(buffer, 0, bytesRead);

                int level = calculateLevel(buffer, bytesRead);
                final int finalLevel = level;
                SwingUtilities.invokeLater(() -> {
                    levelPanel.setLevel(finalLevel);
                    levelPanel.repaint();
                });

                long elapsed = System.currentTimeMillis() - startTime;
                if (elapsed - lastTranscribe >= transcribeInterval && audioData.size() > 4000) {
                    byte[] currentAudio = audioData.toByteArray();
                    String partial = transcribeChunk(currentAudio, format);
                    if (partial != null && !partial.isEmpty()) {
                        fullResult.setLength(0);
                        fullResult.append(partial);
                        final String text = fullResult.toString();
                        SwingUtilities.invokeLater(() -> {
                            statusLabel.setText("Live: " + truncate(text, 15));
                        });
                        copyToClipboard(text);
                    }
                    lastTranscribe = elapsed;
                }
            }
        }

        microphone.stop();
        microphone.close();

        byte[] audioBytes = audioData.toByteArray();
        if (audioBytes.length < 1000) {
            return fullResult.toString();
        }

        String finalResult = transcribeWithWhisper(audioBytes, format);
        if (finalResult != null && !finalResult.isEmpty()) {
            return finalResult;
        }

        return fullResult.toString();
    }

    private static int calculateLevel(byte[] buffer, int length) {
        int max = 0;
        for (int i = 0; i < length - 1; i += 2) {
            int low = buffer[i] & 0xFF;
            int high = buffer[i + 1];
            short sample = (short) ((high << 8) | low);
            int abs = Math.abs(sample);
            if (abs > max) max = abs;
        }
        return Math.min(100, max * 100 / 32000);
    }

    private static String transcribeChunk(byte[] pcmData, AudioFormat format) {
        try {
            Path tempFile = Files.createTempFile("audio_chunk", ".wav");
            try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData)) {
                AudioInputStream ais = new AudioInputStream(
                    new BufferedInputStream(bais), format, pcmData.length / format.getFrameSize());
                AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile.toFile());
            }

            String result = transcribeWithWhisper(tempFile.toFile().getAbsolutePath());
            Files.deleteIfExists(tempFile);
            return result;
        } catch (Exception e) {
            return null;
        }
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

    private static String transcribeWithWhisper(byte[] pcmData, AudioFormat format) throws Exception {
        Path tempFile = Files.createTempFile("audio_final", ".wav");
        try (ByteArrayInputStream bais = new ByteArrayInputStream(pcmData)) {
            AudioInputStream ais = new AudioInputStream(
                new BufferedInputStream(bais), format, pcmData.length / format.getFrameSize());
            AudioSystem.write(ais, AudioFileFormat.Type.WAVE, tempFile.toFile());
        }

        String result = transcribeWithWhisper(tempFile.toFile().getAbsolutePath());
        Files.deleteIfExists(tempFile);
        return result;
    }

    private static void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        StringSelection selection = new StringSelection(text);
        clipboard.setContents(selection, selection);
    }

    static class LevelPanel extends JPanel {
        private int currentLevel = 0;
        private int peakLevel = 0;
        private long lastUpdate = 0;

        public void setLevel(int level) {
            long now = System.currentTimeMillis();
            if (now - lastUpdate > 50) {
                currentLevel = level;
                if (level > peakLevel) {
                    peakLevel = level;
                }
                lastUpdate = now;
            }
        }

        public void reset() {
            currentLevel = 0;
            peakLevel = 0;
            lastUpdate = 0;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int barW = 22;
            int gap = 6;
            int totalW = 6 * barW + 5 * gap;
            int startX = (w - totalW) / 2;
            int maxH = h - 8;
            int bottom = h - 4;

            for (int i = 0; i < 6; i++) {
                int x = startX + i * (barW + gap);
                int threshold = (i + 1) * 16;
                boolean active = peakLevel >= threshold;
                int height = active ? maxH : 4;

                if (active) {
                    float ratio = (float) (i + 1) / 6f;
                    Color c = lerp(new Color(80, 200, 120), new Color(255, 200, 80), ratio);
                    g2.setColor(c);
                } else {
                    g2.setColor(new Color(70, 70, 75));
                }

                g2.fillRoundRect(x, bottom - height, barW, height, 4, 4);
            }
        }

        private Color lerp(Color a, Color b, float t) {
            return new Color(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t)
            );
        }
    }
}
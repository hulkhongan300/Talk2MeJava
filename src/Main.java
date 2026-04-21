import java.awt.*;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.concurrent.atomic.AtomicInteger;

import javax.sound.sampled.*;
import javax.swing.*;

public class Main {
    private static final int SAMPLE_RATE = 16000;
    private static final int MAX_HISTORY = 10;

    private static final String CONFIG_PATH = "config.properties";

    private final AtomicReference<AppState> state = new AtomicReference<>(AppState.IDLE);
    private volatile CountDownLatch recordingLatch = new CountDownLatch(1);
    private final JLabel statusLabel = new JLabel();
    private final LevelPanel levelPanel = new LevelPanel();
    private final JButton recordButton;
    private final JFrame frame;
    private final AudioPipeline pipeline;
    private final WhisperEngine whisper;
    private final TranscriptionHistory history;
    private final Config config;
    private static ExecutorService executor;

    private enum AppState { IDLE, RECORDING, PROCESSING }

    public static void main(String[] args) {
        Config config = Config.load(CONFIG_PATH);
        WhisperEngine whisper = new WhisperEngine(config);
        TranscriptionHistory history = new TranscriptionHistory(MAX_HISTORY);
        AudioPipeline pipeline = new AudioPipeline(SAMPLE_RATE, config);

        Main app = new Main(config, whisper, history, pipeline);

        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            whisper.shutdown();
            if (executor != null) {
                executor.shutdownNow();
            }
        }));

        SwingUtilities.invokeLater(() -> app.initGUI());
    }

    private Main(Config config, WhisperEngine whisper,
                TranscriptionHistory history, AudioPipeline pipeline) {
        this.config = config;
        this.whisper = whisper;
        this.history = history;
        this.pipeline = pipeline;
        this.recordButton = createRecordButton();
        this.frame = new JFrame("Voice to Clipboard");
    }

    private void initGUI() {
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setSize(260, 180);
        frame.setLocationRelativeTo(null);
        frame.setResizable(false);
        frame.getContentPane().setBackground(new Color(45, 45, 50));

        JPanel mainPanel = new JPanel();
        mainPanel.setOpaque(true);
        mainPanel.setBackground(new Color(45, 45, 50));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(15, 15, 15, 15));

        levelPanel.setMaximumSize(new Dimension(230, 40));
        levelPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
        levelPanel.setOpaque(false);

        recordButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        statusLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        statusLabel.setFont(new Font("Segoe UI", Font.PLAIN, 13));
        statusLabel.setForeground(new Color(180, 180, 185));

        mainPanel.add(levelPanel);
        mainPanel.add(Box.createVerticalStrut(12));
        mainPanel.add(recordButton);
        mainPanel.add(Box.createVerticalStrut(8));
        mainPanel.add(statusLabel);

        frame.add(mainPanel);
        frame.setVisible(true);

        setupKeyBindings();
        updateStatus("Hold G to record", new Color(180, 180, 185));

        executor = Executors.newSingleThreadExecutor(r -> {
            Thread t = new Thread(r, "audio-pipeline");
            t.setDaemon(true);
            return t;
        });
    }

    private JButton createRecordButton() {
        JButton btn = new JButton("G") {
            private volatile boolean hover = false;

            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                                   RenderingHints.VALUE_ANTIALIAS_ON);
                int w = getWidth();
                int h = getHeight();
                AppState s = state.get();
                if (s == AppState.RECORDING) {
                    g2.setColor(new Color(80, 200, 120));
                } else if (hover) {
                    g2.setColor(new Color(100, 220, 150));
                } else {
                    g2.setColor(new Color(60, 180, 100));
                }
                g2.fillOval(0, 0, w - 1, h - 1);
                g2.setColor(Color.WHITE);
                g2.setFont(new Font("Segoe UI", Font.BOLD, 28));
                FontMetrics fm = g2.getFontMetrics();
                String label = s == AppState.RECORDING ? "\u25A0" : "G";
                int x = (w - fm.stringWidth(label)) / 2;
                int y = (h + fm.getAscent()) / 2 - 2;
                g2.drawString(label, x, y);
            }

            @Override
            public void addNotify() {
                super.addNotify();
                addMouseListener(new MouseAdapter() {
                    @Override public void mouseEntered(MouseEvent e) { hover = true; repaint(); }
                    @Override public void mouseExited(MouseEvent e) { hover = false; repaint(); }
                    @Override public void mousePressed(MouseEvent e) { startRecording(); }
                    @Override public void mouseReleased(MouseEvent e) { stopRecording(); }
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

    private void setupKeyBindings() {
        InputMap im = frame.getRootPane().getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW);
        ActionMap am = frame.getRootPane().getActionMap();
        im.put(KeyStroke.getKeyStroke("pressed G"), "startRecord");
        im.put(KeyStroke.getKeyStroke("released G"), "stopRecord");
        am.put("startRecord", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { startRecording(); }
        });
        am.put("stopRecord", new AbstractAction() {
            @Override public void actionPerformed(java.awt.event.ActionEvent e) { stopRecording(); }
        });
    }

    private void startRecording() {
        if (!state.compareAndSet(AppState.IDLE, AppState.RECORDING)) {
            return;
        }
        recordingLatch = new CountDownLatch(1);
        updateStatus("Recording...", new Color(80, 200, 120));
        levelPanel.reset();
        recordButton.repaint();
        executor.submit(() -> runCapture());
    }

    private void runCapture() {
        try {
            pipeline.capture(SAMPLE_RATE, config.vadSilenceFrames(),
                (level) -> SwingUtilities.invokeLater(() -> {
                    levelPanel.setLevel(level);
                    levelPanel.repaint();
                }),
                (pcmData, frames) -> {
                    if (frames < SAMPLE_RATE / 4) return;
                    String text = whisper.transcribe(pcmData, SAMPLE_RATE, config.language());
                    if (text != null && !text.isBlank()) {
                        copyToClipboard(text);
                        history.add(text);
                        SwingUtilities.invokeLater(() -> updateStatus(
                            "Copied: " + truncate(text, 20),
                            new Color(100, 180, 220)));
                    }
                });
            onIdle();
        } catch (AudioPipeline.MicrophoneException e) {
            onError("Mic access denied", e);
        } catch (Exception e) {
            onError("Recording error", e);
        }
    }

    private void stopRecording() {
        if (!state.compareAndSet(AppState.RECORDING, AppState.IDLE)) {
            return;
        }
        pipeline.stop();
        recordButton.repaint();
    }

    private void onIdle() {
        state.set(AppState.IDLE);
        recordingLatch.countDown();
        SwingUtilities.invokeLater(() -> {
            updateStatus("Hold G to record", new Color(180, 180, 185));
            levelPanel.reset();
            recordButton.repaint();
        });
    }

    private void onError(String msg, Exception e) {
        state.set(AppState.IDLE);
        recordingLatch.countDown();
        System.err.println(msg + ": " + e.getMessage());
        SwingUtilities.invokeLater(() -> {
            updateStatus(msg, new Color(255, 100, 100));
            levelPanel.reset();
            recordButton.repaint();
        });
    }

    private void updateStatus(String text, Color color) {
        statusLabel.setText(text);
        statusLabel.setForeground(color);
    }

    private void copyToClipboard(String text) {
        Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
        clipboard.setContents(new StringSelection(text), null);
    }

    private String truncate(String s, int len) {
        return s.length() > len ? s.substring(0, len) + "..." : s;
    }

    static class LevelPanel extends JPanel {
        private int currentLevel = 0;
        private int peakLevel = 0;
        private long lastUpdate = 0;

        LevelPanel() { setOpaque(false); }

        void setLevel(int level) {
            long now = System.currentTimeMillis();
            if (now - lastUpdate > 50) {
                currentLevel = level;
                if (level > peakLevel) peakLevel = level;
                lastUpdate = now;
            }
        }

        void reset() { currentLevel = 0; peakLevel = 0; repaint(); }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g;
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                               RenderingHints.VALUE_ANTIALIAS_ON);
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
                    float ratio = (float)(i + 1) / 6f;
                    g2.setColor(lerp(new Color(80, 200, 120),
                                     new Color(255, 200, 80), ratio));
                } else {
                    g2.setColor(new Color(70, 70, 75));
                }
                g2.fillRoundRect(x, bottom - height, barW, height, 4, 4);
            }
        }

        private Color lerp(Color a, Color b, float t) {
            return new Color(
                (int)(a.getRed()   + (b.getRed()   - a.getRed())   * t),
                (int)(a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int)(a.getBlue()  + (b.getBlue()  - a.getBlue()) * t)
            );
        }
    }
}
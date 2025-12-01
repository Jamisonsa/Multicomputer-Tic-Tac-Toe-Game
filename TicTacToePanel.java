import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.function.BiConsumer;
import javax.sound.sampled.*;
import java.io.File;
import java.awt.RenderingHints;

public class TicTacToePanel extends JLayeredPane {

    private static final int WIDTH = 400;
    private static final int HEIGHT = 400;

    private GamerGirlTileButton[][] buttons = new GamerGirlTileButton[3][3];

    private JPanel boardPanel;
    private JPanel overlayPanel;
    private JLabel resultLabel;
    private JButton retryButton;
    private JLabel retryStatusLabel;

    private BiConsumer<Integer, Integer> onMove;
    private Runnable onRetry;

    // Animated background
    private Timer bgTimer;
    private float hueOffset = 0f;

    // Simple starfield
    private final List<Point> stars = new ArrayList<>();

    // Confetti
    private List<Confetto> confetti = new ArrayList<>();
    private Timer confettiTimer;

    public TicTacToePanel(BiConsumer<Integer, Integer> onMove, Runnable onRetry) {
        this.onMove = onMove;
        this.onRetry = onRetry;

        setPreferredSize(new Dimension(WIDTH, HEIGHT));
        setLayout(null);
        setOpaque(true);

        // --- Generate simple starfield once ---
        generateStars();

        // --- Animated background timer (slow drift) ---
        bgTimer = new Timer(40, e -> {
            hueOffset += 0.0015f;
            if (hueOffset > 1f) hueOffset -= 1f;
            repaint();
        });
        bgTimer.start();

        // --- BOARD PANEL (transparent, sits on top of gradient) ---
        boardPanel = new JPanel(new GridLayout(3, 3)) {
            @Override
            public boolean isOpaque() {
                return false; // let gradient show through
            }
        };
        boardPanel.setBounds(0, 0, WIDTH, HEIGHT);

        // Emoji-friendly font for ⭐ / 🌙
        Font font = new Font("Segoe UI Emoji", Font.PLAIN, 52);
        Color tileColor = new Color(12, 12, 26); // deep space tile

        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                GamerGirlTileButton btn = new GamerGirlTileButton(tileColor);
                btn.setFont(font);
                btn.setFocusable(false);
                btn.setForeground(new Color(230, 235, 255)); // default star-text

                int row = r;
                int col = c;

                btn.addActionListener(e -> {
                    if (btn.getText().isEmpty() && onMove != null) {
                        onMove.accept(row, col);
                        btn.triggerGlow();
                        SoundManager.play("sounds/click.wav");
                    }
                });

                // Hover sound & subtle hover glow
                btn.addMouseListener(new java.awt.event.MouseAdapter() {
                    @Override
                    public void mouseEntered(java.awt.event.MouseEvent e) {
                        if (btn.getText().isEmpty()) {
                            btn.setHover(true);
                            SoundManager.play("sounds/hover.wav");
                        }
                    }

                    @Override
                    public void mouseExited(java.awt.event.MouseEvent e) {
                        btn.setHover(false);
                    }
                });

                buttons[r][c] = btn;
                boardPanel.add(btn);
            }
        }

        add(boardPanel, DEFAULT_LAYER);

        // --- OVERLAY PANEL (dark, confetti, result, retry) ---
        overlayPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;

                g2.setColor(new Color(2, 2, 12, 210));
                g2.fillRect(0, 0, getWidth(), getHeight());

                for (Confetto cf : confetti) {
                    g2.setColor(cf.color);
                    g2.fillOval((int) cf.x, (int) cf.y, (int) cf.size, (int) cf.size);
                }
            }
        };

        overlayPanel.setLayout(new BoxLayout(overlayPanel, BoxLayout.Y_AXIS));
        overlayPanel.setBounds(0, 0, WIDTH, HEIGHT);
        overlayPanel.setOpaque(false);

        // Block mouse events passing through
        overlayPanel.addMouseListener(new java.awt.event.MouseAdapter() {});
        overlayPanel.addMouseMotionListener(new java.awt.event.MouseMotionAdapter() {});

        resultLabel = new JLabel("You Won! ✨");
        resultLabel.setFont(new Font("Serif", Font.BOLD, 32));
        resultLabel.setForeground(new Color(220, 230, 255));
        resultLabel.setAlignmentX(0.5f);

        retryStatusLabel = new JLabel("Retry (0/2)");
        retryStatusLabel.setFont(new Font("Serif", Font.PLAIN, 20));
        retryStatusLabel.setForeground(new Color(200, 210, 240));
        retryStatusLabel.setAlignmentX(0.5f);

        retryButton = new JButton("Retry");
        retryButton.setAlignmentX(0.5f);
        retryButton.setBackground(new Color(90, 120, 210));
        retryButton.setForeground(Color.WHITE);
        retryButton.setFocusPainted(false);
        retryButton.setBorder(BorderFactory.createEmptyBorder(8, 24, 8, 24));
        retryButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        retryButton.addActionListener(e -> {
            SoundManager.play("sounds/retry.wav");
            if (onRetry != null) onRetry.run();
        });

        overlayPanel.add(Box.createVerticalGlue());
        overlayPanel.add(resultLabel);
        overlayPanel.add(Box.createVerticalStrut(10));
        overlayPanel.add(retryStatusLabel);
        overlayPanel.add(Box.createVerticalStrut(10));
        overlayPanel.add(retryButton);
        overlayPanel.add(Box.createVerticalGlue());

        overlayPanel.setVisible(false);

        add(overlayPanel, MODAL_LAYER);
    }

    // --------- BACKGROUND: celestial gradient + stars ---------
    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);

        Graphics2D g2 = (Graphics2D) g.create();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        // Deep space gradient: navy → violet
        Color c1 = new Color(6, 6, 18);
        Color c2 = new Color(26, 20, 60);
        GradientPaint gp = new GradientPaint(0, 0, c1, 0, h, c2);
        g2.setPaint(gp);
        g2.fillRect(0, 0, w, h);

        // Twinkling stars (from precomputed list → NO flicker)
        g2.setColor(new Color(235, 240, 255, 180));
        for (Point p : stars) {
            int size = 2;
            g2.fillOval(p.x, p.y, size, size);
        }

        // Very subtle grid lines (constellation-like)
        g2.setColor(new Color(255, 255, 255, 25));
        int step = Math.max(1, w / 6);
        for (int x = 0; x < w; x += step) {
            g2.drawLine(x, 0, x, h);
        }
        for (int y = 0; y < h; y += step) {
            g2.drawLine(0, y, w, y);
        }

        g2.dispose();
    }

    private void generateStars() {
        int count = 90;
        for (int i = 0; i < count; i++) {
            int x = (int) (Math.random() * WIDTH);
            int y = (int) (Math.random() * HEIGHT);
            stars.add(new Point(x, y));
        }
    }

    // --------- BOARD UPDATES FROM SERVER ---------
    public void updateCell(int r, int c, char val) {
        if (r < 0 || r > 2 || c < 0 || c > 2) return;

        GamerGirlTileButton btn = buttons[r][c];

        if (val == ' ') {
            btn.setText("");
            return;
        }

        // Celestial symbols: X = ⭐, O = 🌙
        if (val == 'X') {
            btn.setText("⭐");
            btn.setForeground(new Color(255, 240, 170));   // warm gold star
        } else if (val == 'O') {
            btn.setText("🌙");
            btn.setForeground(new Color(170, 200, 255));   // soft moon blue
        }
    }

    public void resetBoard() {
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                buttons[r][c].setText("");
                buttons[r][c].setHover(false);
            }
        }
    }

    // --------- GAME OVER OVERLAY ---------
    public void showGameOver(String resultType) {
        switch (resultType) {
            case "WIN":
                resultLabel.setText("⭐ Victory Among the Stars");
                SoundManager.play("sounds/win.wav");
                break;
            case "LOSE":
                resultLabel.setText("☄️ A Falling Star…");
                SoundManager.play("sounds/lose.wav");
                break;
            case "DRAW":
                resultLabel.setText("🌙 Cosmic Balance Achieved");
                SoundManager.play("sounds/win.wav");
                break;
            default:
                resultLabel.setText("Game Over");
        }

        retryStatusLabel.setText("Retry (0/2)");
        startConfetti();
        overlayPanel.setVisible(true);
        repaint();
    }

    public void updateRetryStatus(int readyCount) {
        retryStatusLabel.setText("Retry (" + readyCount + "/2)");
        if (readyCount >= 2) {
            overlayPanel.setVisible(false);
            stopConfetti();
            resetBoard();
        }
        repaint();
    }

    public void hideOverlay() {
        overlayPanel.setVisible(false);
        stopConfetti();
        repaint();
    }

    // --------- CONFETTI ---------
    private void startConfetti() {
        confetti.clear();
        int num = 90;

        for (int i = 0; i < num; i++) {
            Confetto c = new Confetto();
            c.x = (float) (Math.random() * WIDTH);
            c.y = (float) (Math.random() * -HEIGHT);
            c.dy = 2f + (float) (Math.random() * 3f);
            c.size = 4f + (float) (Math.random() * 6f);

            // Star-like confetti (pale blue/white)
            c.color = new Color(
                    210 + (int) (Math.random() * 40),
                    220 + (int) (Math.random() * 35),
                    255
            );

            confetti.add(c);
        }

        if (confettiTimer != null && confettiTimer.isRunning()) {
            confettiTimer.stop();
        }

        confettiTimer = new Timer(40, e -> {
            for (Confetto cf : confetti) {
                cf.y += cf.dy;
                if (cf.y > HEIGHT) {
                    cf.y = -cf.size;
                    cf.x = (float) (Math.random() * WIDTH);
                }
            }
            overlayPanel.repaint();
        });
        confettiTimer.start();
    }

    private void stopConfetti() {
        if (confettiTimer != null) confettiTimer.stop();
        confetti.clear();
    }

    private static class Confetto {
        float x, y, dy, size;
        Color color;
    }

    @Override
    public void doLayout() {
        super.doLayout();
        int w = getWidth();
        int h = getHeight();
        if (boardPanel != null) {
            boardPanel.setBounds(0, 0, w, h);
        }
        if (overlayPanel != null) {
            overlayPanel.setBounds(0, 0, w, h);
        }
    }


    // --------- CUSTOM TILE BUTTON CLASS ---------
    private static class GamerGirlTileButton extends JButton {

        private Color baseColor;
        private boolean glowing = false;
        private boolean hover = false;

        public GamerGirlTileButton(Color baseColor) {
            this.baseColor = baseColor;
            setContentAreaFilled(false);
            setBorderPainted(false);
            setFocusPainted(false);
            setOpaque(false);
            setForeground(new Color(230, 235, 255));
        }

        public void triggerGlow() {
            glowing = true;
            repaint();

            Timer t = new Timer(180, e -> {
                glowing = false;
                repaint();
            });
            t.setRepeats(false);
            t.start();
        }

        public void setHover(boolean hover) {
            this.hover = hover;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics g) {
            Graphics2D g2 = (Graphics2D) g.create();
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

            int w = getWidth();
            int h = getHeight();
            int arc = 26;

            // Glow halo
            if (glowing || hover) {
                g2.setColor(new Color(190, 210, 255, 150));
                g2.fillRoundRect(2, 2, w - 4, h - 4, arc + 10, arc + 10);
            }

            // Base tile – deep space tile
            g2.setColor(baseColor);
            g2.fillRoundRect(6, 6, w - 12, h - 12, arc, arc);

            // Border – subtle starlight
            g2.setColor(new Color(180, 200, 255));
            g2.setStroke(new BasicStroke(2.0f));
            g2.drawRoundRect(6, 6, w - 12, h - 12, arc, arc);

            g2.dispose();

            super.paintComponent(g);
        }

        @Override
        public boolean isContentAreaFilled() {
            return false;
        }
    }
}

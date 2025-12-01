import javax.swing.*;
import java.awt.*;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.Enumeration;

public class LoginWindow extends JFrame {

    private JTextField ipField;
    private JTextField usernameField;
    private JButton connectButton;

    public interface LoginListener {
        void onLogin(String ip, String username);
    }

    public LoginWindow(LoginListener listener) {

        setTitle("Celestial Gate ✦ Login");
        setSize(420, 260);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLocationRelativeTo(null);

        // MAIN LAYERED CONTAINER
        JLayeredPane layered = new JLayeredPane();
        setContentPane(layered);

        // ===== BACKGROUND PANEL (ONLY PAINTS) =====
        JPanel bg = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);

                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setPaint(new GradientPaint(0, 0,
                        new Color(10, 8, 30),
                        0, getHeight(),
                        new Color(35, 25, 70)));
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(230, 230, 255, 170));
                for (int i = 0; i < 85; i++) {
                    int x = (int) (Math.random() * getWidth());
                    int y = (int) (Math.random() * getHeight());
                    g2.fillOval(x, y, 2, 2);
                }
            }
        };
        bg.setBounds(0, 0, 420, 260);
        layered.add(bg, JLayeredPane.DEFAULT_LAYER);

        // ===== FOREGROUND PANEL (REAL COMPONENTS) =====
        JPanel inner = new JPanel(new GridBagLayout());
        inner.setOpaque(false);
        inner.setBounds(0, 0, 420, 260);
        layered.add(inner, JLayeredPane.PALETTE_LAYER);

        GridBagConstraints c = new GridBagConstraints();
        c.insets = new Insets(8, 8, 8, 8);
        c.gridx = 0;
        c.gridy = 0;

        JLabel title = new JLabel("✦ Enter the Celestial Network ✦");
        title.setFont(new Font("Serif", Font.BOLD, 20));
        title.setForeground(new Color(225, 230, 255));
        inner.add(title, c);

        // IP label
        c.gridy++;
        JLabel ipLabel = new JLabel("Server IP:");
        ipLabel.setForeground(new Color(210, 220, 255));
        inner.add(ipLabel, c);

        // IP field (auto-detect but editable)
        c.gridy++;
        ipField = createInputField(getLocalIPAddress());
        inner.add(ipField, c);

        // Username label
        c.gridy++;
        JLabel userLabel = new JLabel("Username:");
        userLabel.setForeground(new Color(210, 220, 255));
        inner.add(userLabel, c);

        // Username field
        c.gridy++;
        usernameField = createInputField("");
        inner.add(usernameField, c);

        // Connect button
        c.gridy++;
        connectButton = new JButton("Enter ✦");
        styleButton(connectButton);
        inner.add(connectButton, c);

        connectButton.addActionListener(e -> {
            String ip = ipField.getText().trim();
            String user = usernameField.getText().trim();

            if (ip.isEmpty() || user.isEmpty()) {
                JOptionPane.showMessageDialog(this,
                        "Please fill in both fields.",
                        "Missing Information",
                        JOptionPane.WARNING_MESSAGE
                );
                return;
            }

            listener.onLogin(ip, user);
            dispose();
        });

        SwingUtilities.invokeLater(() -> usernameField.requestFocusInWindow());

        setVisible(true);
    }

    private JTextField createInputField(String defaultText) {
        JTextField field = new JTextField(defaultText, 16);
        field.setBackground(new Color(25, 20, 45));
        field.setForeground(new Color(230, 230, 255));
        field.setCaretColor(Color.WHITE);
        field.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 200)));
        return field;
    }

    private void styleButton(JButton b) {
        b.setBackground(new Color(60, 50, 120));
        b.setForeground(new Color(230, 230, 255));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(150, 150, 230)));
        b.setFont(new Font("Serif", Font.BOLD, 15));
        b.setPreferredSize(new Dimension(140, 32));
        b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }

    // Try to find a non-loopback IPv4 as default; fall back to localhost
    private String getLocalIPAddress() {
        try {
            Enumeration<NetworkInterface> nets = NetworkInterface.getNetworkInterfaces();
            while (nets.hasMoreElements()) {
                NetworkInterface net = nets.nextElement();
                if (!net.isUp() || net.isLoopback() || net.isVirtual()) continue;

                Enumeration<InetAddress> addrs = net.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress addr = addrs.nextElement();
                    String host = addr.getHostAddress();
                    if (!addr.isLoopbackAddress() && host.contains(".")) {
                        return host;
                    }
                }
            }
        } catch (Exception ignored) {}

        return "127.0.0.1";
    }
}

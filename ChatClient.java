import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.Socket;

public class ChatClient extends JFrame {

    private static Socket socket;
    private static BufferedReader serverIn;
    private static PrintWriter serverOut;

    private JPanel chatPanel;
    private JScrollPane scrollPane;
    private JTextField inputField;
    private JLabel typingLabel;
    private Timer typingDisplayTimer;

    private String username;
    private boolean typingSent = false;

    private String lastMessageSent = "";   // prevents duplicates
    private DefaultListModel<String> userListModel;
    private JList<String> userList;

    private TicTacToePanel gamePanel;

    private JSplitPane splitPane;
    private JButton toggleChatButton;
    private boolean chatVisible = true;
    private int lastDividerLocation = 850;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            new LoginWindow((ip, username) -> {
                ChatClient client = new ChatClient();
                client.start(ip, username);
            });
        });
    }

    public void start(String ip, String username) {
        this.username = username;
        setTitle("Celestial TicTacToe ✦ " + username);
        setSize(1100, 720);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // -------------------- GAME PANEL --------------------
        gamePanel = new TicTacToePanel(
                (row, col) -> {
                    if (serverOut != null) {
                        serverOut.println("/move " + (row + 1) + " " + (col + 1));
                        serverOut.flush();
                    }
                },
                () -> {
                    if (serverOut != null) {
                        serverOut.println("/ttt retry");
                        serverOut.flush();
                    }
                }
        );

        // -------------------- CHAT PANEL --------------------
        chatPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                Graphics2D g2 = (Graphics2D) g;

                Color top = new Color(6, 6, 18);
                Color bottom = new Color(20, 16, 48);
                g2.setPaint(new GradientPaint(0, 0, top, 0, getHeight(), bottom));
                g2.fillRect(0, 0, getWidth(), getHeight());

                g2.setColor(new Color(230, 235, 255, 140));
                for (int i = 0; i < 80; i++) {
                    int x = (int) (Math.random() * getWidth());
                    int y = (int) (Math.random() * getHeight());
                    g2.fillOval(x, y, 2, 2);
                }
            }
        };
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));
        scrollPane = new JScrollPane(chatPanel);
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_ALWAYS);

        // -------------------- USER LIST --------------------
        userListModel = new DefaultListModel<>();
        userList = new JList<>(userListModel);
        userList.setBackground(new Color(20, 15, 40));
        userList.setForeground(new Color(225, 230, 255));
        userList.setBorder(BorderFactory.createTitledBorder(
                BorderFactory.createLineBorder(new Color(120, 140, 220)),
                "Constellations ✦",
                0, 0,
                new Font("Serif", Font.BOLD, 12),
                new Color(200, 210, 255)
        ));
        userList.setPreferredSize(new Dimension(170, 120));

        // -------------------- INPUT BAR --------------------
        JPanel bottomPanel = new JPanel(new BorderLayout());
        bottomPanel.setBackground(new Color(12, 8, 25));
        JPanel centerInputPanel = new JPanel(new BorderLayout());
        centerInputPanel.setOpaque(false);

        inputField = new JTextField();
        inputField.setBackground(new Color(20, 16, 35));
        inputField.setForeground(new Color(230, 230, 255));
        inputField.setCaretColor(Color.WHITE);

        inputField.addKeyListener(new java.awt.event.KeyAdapter() {
            @Override
            public void keyTyped(java.awt.event.KeyEvent evt) {
                if (!typingSent && serverOut != null) {
                    serverOut.println("/typing " + username);
                    serverOut.flush();
                    typingSent = true;
                }
            }
        });

        typingLabel = new JLabel(" ");
        typingLabel.setFont(new Font("Serif", Font.ITALIC, 11));
        typingLabel.setForeground(new Color(190, 195, 240));
        centerInputPanel.add(inputField, BorderLayout.CENTER);
        centerInputPanel.add(typingLabel, BorderLayout.SOUTH);

        bottomPanel.add(centerInputPanel, BorderLayout.CENTER);

        JButton sendButton = new JButton("Send ✦");
        JButton fileButton = new JButton("Send File");
        styleButton(sendButton);
        styleButton(fileButton);

        bottomPanel.add(fileButton, BorderLayout.WEST);
        bottomPanel.add(sendButton, BorderLayout.EAST);

        // -------------------- RIGHT PANEL --------------------
        JPanel rightTopPanel = new JPanel(new BorderLayout());
        rightTopPanel.setBackground(new Color(12, 8, 25));

        toggleChatButton = new JButton("Hide Chat ▶");
        styleButton(toggleChatButton);
        toggleChatButton.addActionListener(e -> toggleChat());
        rightTopPanel.add(toggleChatButton, BorderLayout.NORTH);
        rightTopPanel.add(userList, BorderLayout.CENTER);

        JPanel rightPanel = new JPanel(new BorderLayout());
        rightPanel.setBackground(new Color(12, 8, 25));
        rightPanel.add(rightTopPanel, BorderLayout.NORTH);
        rightPanel.add(scrollPane, BorderLayout.CENTER);
        rightPanel.add(bottomPanel, BorderLayout.SOUTH);
        rightPanel.setPreferredSize(new Dimension(260, 0));

        // -------------------- SPLIT PANE --------------------
        splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, gamePanel, rightPanel);
        splitPane.setResizeWeight(1.0);
        splitPane.setDividerSize(6);
        add(splitPane, BorderLayout.CENTER);
        SwingUtilities.invokeLater(() -> splitPane.setDividerLocation(0.78));

        // -------------------- CONNECT TO SERVER --------------------
        connectToServer(ip);

        sendButton.addActionListener(e -> sendMessage());
        inputField.addActionListener(e -> sendMessage());
        fileButton.addActionListener(e -> chooseFile());

        setLocationRelativeTo(null);
        setVisible(true);
        new Thread(this::listenForMessages).start();
    }

    private void styleButton(JButton b) {
        b.setBackground(new Color(40, 30, 80));
        b.setForeground(new Color(220, 220, 255));
        b.setFocusPainted(false);
        b.setBorder(BorderFactory.createLineBorder(new Color(120, 120, 200)));
    }

    private void toggleChat() {
        if (chatVisible) {
            lastDividerLocation = splitPane.getDividerLocation();
            splitPane.setDividerLocation(1.0);
            splitPane.setDividerSize(0);
            chatVisible = false;
            toggleChatButton.setText("Show Chat ◀");
        } else {
            splitPane.setDividerSize(6);
            splitPane.setDividerLocation(lastDividerLocation);
            chatVisible = true;
            toggleChatButton.setText("Hide Chat ▶");
        }
    }

    private void connectToServer(String ip) {
        try {
            socket = new Socket(ip, 5555);

            serverIn = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            serverOut = new PrintWriter(socket.getOutputStream(), true);

            appendBubble("[SERVER] " + serverIn.readLine());
            serverOut.println(username);
            serverOut.flush();

        } catch (Exception e) {
            JOptionPane.showMessageDialog(this, "Connection failed: " + e.getMessage());
            System.exit(0);
        }
    }

    private void sendMessage() {
        String text = inputField.getText().trim();
        if (text.isEmpty() || serverOut == null) return;

        lastMessageSent = username + ": " + text;

        serverOut.println(text);
        serverOut.flush();

        inputField.setText("");
        typingSent = false;
    }

    private void listenForMessages() {
        try {
            InputStream rawIn = socket.getInputStream();
            String line;

            while ((line = serverIn.readLine()) != null) {

                if (line.startsWith("[GAMEBOARD]")) {
                    updateGameBoard(line);
                    continue;
                }

                if (line.startsWith("[GAME_OVER]")) {
                    gamePanel.showGameOver(line.replace("[GAME_OVER]", "").trim());
                    continue;
                }

                if (line.startsWith("[RETRY_STATUS]")) {
                    gamePanel.updateRetryStatus(Integer.parseInt(line.replace("[RETRY_STATUS]", "").trim()));
                    continue;
                }

                if (line.startsWith("FILE|")) {
                    handleIncomingFile(rawIn, line);
                    continue;
                }

                if (line.startsWith("[TYPING]")) {
                    showTypingIndicator(line);
                    continue;
                }

                if (line.startsWith("USERS|")) {
                    updateUserList(line);
                    continue;
                }

                appendBubble(line);
            }

        } catch (IOException e) {
            appendBubble("[ERROR] Connection closed.");
        }
    }

    private void appendBubble(String fullMessage) {

        if (fullMessage.equals(lastMessageSent) &&
                fullMessage.startsWith(username + ":")) {

            addBubble(username,
                    fullMessage.substring((username + ": ").length()),
                    new Color(255,215,130,180),   // Gold
                    true);

            lastMessageSent = "";
            return;
        }


        // Normalize
        if (fullMessage.startsWith("You: ")) {
            fullMessage = username + ": " + fullMessage.substring(5);
        }

        // Identify type
        boolean isServer = fullMessage.startsWith("[SERVER]");
        boolean isPM     = fullMessage.startsWith("[PM]");
        boolean isFile   = fullMessage.startsWith("[FILE]")
                || fullMessage.startsWith("[FILE RECEIVED]")
                || fullMessage.startsWith("[FILE SENT]")
                || fullMessage.startsWith("[SAVED]");
        boolean isError  = fullMessage.startsWith("[ERROR]");

        Color bg;
        if (isServer) bg = new Color(40,55,120,200);
        else if (isPM) bg = new Color(150,130,230,180);
        else if (isFile) bg = new Color(120,160,255,170);
        else if (isError) bg = new Color(255,110,150,170);
        else {
            bg = fullMessage.startsWith(username + ":")
                    ? new Color(255,215,130,180)
                    : new Color(130,110,230,170);
        }

        // Extract name + msg
        String sender;
        String content;

        if (fullMessage.contains(": ")) {
            int idx = fullMessage.indexOf(": ");
            sender = fullMessage.substring(0, idx);
            content = fullMessage.substring(idx + 2);
        } else {
            sender = "[SYSTEM]";
            content = fullMessage;
        }

        boolean rightAlign = sender.equals(username);
        addBubble(sender, content, bg, rightAlign);
    }

    private void addBubble(String sender, String message, Color bgColor, boolean rightAlign) {

        JPanel bubble = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                Graphics2D g2 = (Graphics2D) g;
                g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

                g2.setColor(new Color(bgColor.getRed(), bgColor.getGreen(), bgColor.getBlue(), 70));
                g2.fillRoundRect(4, 4, getWidth()-8, getHeight()-8, 26, 26);

                g2.setColor(bgColor);
                g2.fillRoundRect(0, 0, getWidth(), getHeight(), 24, 24);
            }
        };

        bubble.setOpaque(false);
        bubble.setLayout(new BoxLayout(bubble, BoxLayout.Y_AXIS));
        bubble.setBorder(BorderFactory.createEmptyBorder(10, 15, 10, 15));

        JLabel senderLabel = new JLabel(sender);
        senderLabel.setForeground(new Color(200,210,255));

        JTextArea msgLabel = new JTextArea(message);
        msgLabel.setOpaque(false);
        msgLabel.setEditable(false);
        msgLabel.setForeground(new Color(235,240,255));
        msgLabel.setLineWrap(true);
        msgLabel.setWrapStyleWord(true);

        JLabel timeLabel = new JLabel(java.time.LocalTime.now().format(
                java.time.format.DateTimeFormatter.ofPattern("hh:mm a")
        ));
        timeLabel.setForeground(new Color(170,175,220));

        bubble.add(senderLabel);
        bubble.add(msgLabel);
        bubble.add(timeLabel);

        JPanel align = new JPanel(new BorderLayout());
        align.setOpaque(false);
        align.add(bubble, rightAlign ? BorderLayout.EAST : BorderLayout.WEST);

        chatPanel.add(Box.createVerticalStrut(10));
        chatPanel.add(align);
        chatPanel.revalidate();

        SwingUtilities.invokeLater(() ->
                scrollPane.getVerticalScrollBar().setValue(
                        scrollPane.getVerticalScrollBar().getMaximum()
                )
        );
    }

    private void showTypingIndicator(String line) {
        String info = line.replace("[TYPING]", "").trim();
        if (info.startsWith(username + " ")) return;

        typingLabel.setText(info);

        if (typingDisplayTimer != null) typingDisplayTimer.stop();
        typingDisplayTimer = new Timer(1400, e -> typingLabel.setText(" "));
        typingDisplayTimer.setRepeats(false);
        typingDisplayTimer.start();
    }

    private void updateUserList(String line) {
        SwingUtilities.invokeLater(() -> {
            userListModel.clear();
            for (String u : line.substring(6).split(",")) {
                if (!u.isBlank()) userListModel.addElement(u);
            }
        });
    }

    private void chooseFile() {
        JFileChooser chooser = new JFileChooser();
        if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
            File f = chooser.getSelectedFile();
            String target = JOptionPane.showInputDialog(this, "Send to username:");
            if (target != null) sendFile(target, f);
        }
    }

    private void sendFile(String targetUser, File file) {
        try {
            long size = file.length();
            String name = file.getName();

            serverOut.println("FILE|" + username + "|" + targetUser + "|" + name + "|" + size);
            serverOut.flush();

            FileInputStream fis = new FileInputStream(file);
            OutputStream out = socket.getOutputStream();

            byte[] buf = new byte[4096];
            int r;
            while ((r = fis.read(buf)) != -1)
                out.write(buf, 0, r);

            out.flush();
            fis.close();

            appendBubble("[FILE SENT] " + name);

        } catch (Exception e) {
            appendBubble("[ERROR sending file] " + e.getMessage());
        }
    }

    private void handleIncomingFile(InputStream rawIn, String header) {
        try {
            String[] p = header.split("\\|");
            String sender = p[1];
            String name = p[3];
            long size = Long.parseLong(p[4]);

            appendBubble("[FILE RECEIVED] from " + sender + ": " + name);

            File dir = new File("downloads");
            if (!dir.exists()) dir.mkdir();

            File outFile = new File(dir, name);
            FileOutputStream fos = new FileOutputStream(outFile);

            byte[] buf = new byte[4096];
            long remaining = size;

            while (remaining > 0) {
                int read = rawIn.read(buf, 0, (int) Math.min(buf.length, remaining));
                if (read == -1) break;
                fos.write(buf, 0, read);
                remaining -= read;
            }

            fos.close();

            appendBubble("[SAVED] → downloads/" + name);

        } catch (Exception e) {
            appendBubble("[ERROR receiving file] " + e.getMessage());
        }
    }

    private void updateGameBoard(String msg) {
        msg = msg.replace("[GAMEBOARD]", "").trim();
        String[] parts = msg.split(";");

        for (String p : parts) {
            if (p.isBlank()) continue;
            String[] info = p.split(",");
            gamePanel.updateCell(
                    Integer.parseInt(info[0]),
                    Integer.parseInt(info[1]),
                    info[2].charAt(0)
            );
        }
    }
}

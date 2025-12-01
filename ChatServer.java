import java.io.*;
import java.net.*;
import java.util.concurrent.ConcurrentHashMap;

public class ChatServer {

    private static final int PORT = 5555;

    // Only allow 2 total connections (2 TicTacToe players, NO spectators)
    private static final int MAX_CONNECTIONS = 2;

    // Store username → handler
    public static ConcurrentHashMap<String, ClientHandler> clients = new ConcurrentHashMap<>();

    // Single Tic-Tac-Toe game instance
    public static TicTacToeGame game = new TicTacToeGame();

    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(PORT)) {
            System.out.println("[SERVER] Chat server running on port " + PORT);

            while (true) {
                Socket socket = serverSocket.accept();
                System.out.println("[SERVER] New incoming connection: " + socket);

                // HARD LIMIT: only 2 connected clients allowed at a time
                if (clients.size() >= MAX_CONNECTIONS) {
                    System.out.println("[SERVER] Connection rejected: server full.");
                    try {
                        PrintWriter tempOut = new PrintWriter(socket.getOutputStream(), true);
                        tempOut.println("[ERROR] Server full — only 2 TicTacToe players allowed.");
                        tempOut.flush();
                    } catch (IOException ignored) {}
                    socket.close();
                    continue;
                }

                // Accept connection and start handler thread
                ClientHandler handler = new ClientHandler(socket);
                handler.start();
            }

        } catch (IOException e) {
            System.out.println("[SERVER ERROR] " + e.getMessage());
        }
    }

    // Broadcast to all clients
    public static void broadcast(String message) {
        for (ClientHandler client : clients.values()) {
            client.send(message);
        }
    }

    // Broadcast to everyone except a given user
    public static void broadcastExcept(String sender, String message) {
        for (String user : clients.keySet()) {
            if (!user.equals(sender)) {
                clients.get(user).send(message);
            }
        }
    }

    // Send private message
    public static void sendPrivate(String target, String message) {
        ClientHandler c = clients.get(target);
        if (c != null) {
            c.send(message);
        }
    }

    // Update user list to all clients
    public static void updateUserList() {
        StringBuilder sb = new StringBuilder("USERS|");
        for (String user : clients.keySet()) {
            sb.append(user).append(",");
        }
        broadcast(sb.toString());
    }

    // Add client (after username is known)
    public static synchronized void addClient(String username, ClientHandler handler) {
        // Prevent duplicates
        if (clients.containsKey(username)) {
            handler.send("[ERROR] Username already in use. Please reconnect with a different name.");
            handler.close();
            return;
        }

        // Extra guard: should not exceed max connections
        if (clients.size() >= MAX_CONNECTIONS) {
            handler.send("[ERROR] Server full — only 2 TicTacToe players allowed.");
            handler.close();
            return;
        }

        clients.put(username, handler);
        System.out.println("[SERVER] Registered client: " + username);

        // Assign Tic-Tac-Toe role (X / O)
        game.assignPlayer(username);

        // Inform the player of their symbol if they are X or O
        if (game.isPlayer(username)) {
            char symbol = game.getSymbol(username);
            handler.send("[SERVER] You are player " + symbol + " in Tic-Tac-Toe.");
        } else {
            // With MAX_CONNECTIONS=2, this shouldn't normally happen,
            // but we keep it for safety.
            handler.send("[SERVER] You are connected as a spectator.");
        }

        updateUserList();

        // Send initial board state (in case game already started)
        game.sendBoardUpdate();
    }

    // Remove client
    public static synchronized void removeClient(String username) {
        if (username == null) return;

        ClientHandler removed = clients.remove(username);
        if (removed != null) {
            System.out.println("[SERVER] " + username + " disconnected.");
            broadcast("[SERVER] " + username + " has left the chat.");
            updateUserList();

            // If they were a TicTacToe player, update game state
            if (game.isPlayer(username)) {
                game.removePlayer(username);
            }
        }
    }
}


// ======================== CLIENT HANDLER ================================
class ClientHandler extends Thread {

    private final Socket socket;
    private BufferedReader in;
    private PrintWriter out;
    private InputStream rawIn;
    protected OutputStream rawOut;
    private String username;

    public ClientHandler(Socket socket) {
        this.socket = socket;
    }

    public void send(String msg) {
        if (out != null) {
            out.println(msg);
            out.flush();
        }
    }

    public void close() {
        try {
            socket.close();
        } catch (IOException ignored) {}
    }

    @Override
    public void run() {
        try {

            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            out = new PrintWriter(socket.getOutputStream(), true);
            rawIn = socket.getInputStream();
            rawOut = socket.getOutputStream();

            // Ask for username
            out.println("Enter username:");
            username = in.readLine();

            if (username == null || username.trim().isEmpty()) {
                send("[ERROR] Invalid username. Disconnecting.");
                close();
                return;
            }

            System.out.println("[SERVER] Username received: " + username);

            // Register the client
            ChatServer.addClient(username, this);
            // If addClient refused (duplicate or full), username might not have been added:
            if (!ChatServer.clients.containsKey(username)) {
                return;
            }

            ChatServer.broadcast("[SERVER] " + username + " has joined!");

            String message;

            while ((message = in.readLine()) != null) {

                // ---------------- FILE TRANSFER ----------------
                if (message.startsWith("FILE|")) {
                    handleFileTransfer(message);
                    continue;
                }

                // ---------------- TYPING INDICATOR -------------
                if (message.startsWith("/typing")) {
                    String[] p = message.split(" ");
                    if (p.length == 2) {
                        ChatServer.broadcastExcept(username, "[TYPING] " + username + " is typing...");
                    }
                    continue;
                }

                // ---------------- PRIVATE MESSAGE --------------
                if (message.startsWith("/pm")) {
                    String[] p = message.split(" ", 3);
                    if (p.length >= 3) {
                        String target = p[1];
                        String msg = p[2];
                        ChatServer.sendPrivate(target, "[PM] " + username + ": " + msg);
                    } else {
                        send("[ERROR] Usage: /pm <username> <message>");
                    }
                    continue;
                }

                // ---------------- TIC TAC TOE: MOVE ------------
                if (message.startsWith("/move")) {
                    try {
                        String[] p = message.split(" ");
                        int r = Integer.parseInt(p[1]) - 1;  // 1-3 -> 0-2
                        int c = Integer.parseInt(p[2]) - 1;
                        ChatServer.game.makeMove(username, r, c);
                    } catch (Exception e) {
                        send("[ERROR] Invalid move command. Use: /move row col");
                    }
                    continue;
                }

                // ---------------- TIC TAC TOE: RETRY -----------
                if (message.startsWith("/ttt retry")) {
                    ChatServer.game.handleRetry(username);
                    continue;
                }

                // ---------------- NORMAL BROADCAST -------------
                ChatServer.broadcast(username + ": " + message);
            }

        } catch (IOException e) {
            System.out.println("[SERVER] Client I/O error for " + username + ": " + e.getMessage());

        } finally {
            ChatServer.removeClient(username);
            try { socket.close(); } catch (IOException ignored) {}
        }
    }

    private void handleFileTransfer(String header) {
        try {
            // FILE|sender|target|filename|filesize
            String[] p = header.split("\\|");
            if (p.length < 5) {
                send("[ERROR] Invalid file header.");
                return;
            }

            String sender = p[1];
            String target = p[2];
            String filename = p[3];
            long size = Long.parseLong(p[4]);

            ClientHandler receiver = ChatServer.clients.get(target);
            if (receiver == null) {
                send("[ERROR] Target user not found for file transfer.");
                return;
            }

            // Forward header to receiver
            receiver.send("FILE|" + sender + "|" + target + "|" + filename + "|" + size);

            // Forward raw bytes
            byte[] buffer = new byte[4096];
            long remaining = size;
            int read;

            while (remaining > 0) {
                read = rawIn.read(buffer, 0, (int) Math.min(buffer.length, remaining));
                if (read == -1) break;

                receiver.rawOut.write(buffer, 0, read);
                receiver.rawOut.flush();

                remaining -= read;
            }

            System.out.println("[SERVER] File '" + filename + "' sent from " + sender + " to " + target);

        } catch (Exception e) {
            System.out.println("[SERVER ERROR] File transfer error: " + e.getMessage());
            send("[ERROR] File transfer failed: " + e.getMessage());
        }
    }
}


// ===============================================
//              TIC TAC TOE GAME SERVER
// ===============================================
class TicTacToeGame {

    private final char[][] board = new char[3][3];
    private String playerX = null;
    private String playerO = null;
    private String currentTurn = null;

    private int retryCount = 0;
    private boolean gameOver = false;

    public TicTacToeGame() {
        resetBoard();
    }

    private void resetBoard() {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                board[r][c] = ' ';

        gameOver = false;
        retryCount = 0;

        if (playerX != null && playerO != null) {
            currentTurn = playerX;  // X always starts
        } else {
            currentTurn = null;
        }
    }

    public synchronized boolean hasBothPlayers() {
        return (playerX != null && playerO != null);
    }

    // Assign first two connected players as X and O
    public synchronized void assignPlayer(String username) {
        if (playerX == null) {
            playerX = username;
            currentTurn = playerX;
            System.out.println("[GAME] " + username + " assigned as X");
        } else if (playerO == null) {
            playerO = username;
            System.out.println("[GAME] " + username + " assigned as O");

            // Both players ready → start fresh board
            resetBoard();
            sendBoardUpdate();
        } else {
            // With MAX_CONNECTIONS=2, this should not be reachable,
            // but we keep it logged for debugging.
            System.out.println("[GAME] " + username + " tried to join but both players already assigned.");
        }
    }

    public synchronized void removePlayer(String username) {
        boolean changed = false;

        if (username != null && username.equals(playerX)) {
            System.out.println("[GAME] Player X (" + playerX + ") removed.");
            playerX = null;
            changed = true;
        } else if (username != null && username.equals(playerO)) {
            System.out.println("[GAME] Player O (" + playerO + ") removed.");
            playerO = null;
            changed = true;
        }

        if (changed) {
            gameOver = true;
            retryCount = 0;
            currentTurn = null;
            ChatServer.broadcast("[SERVER] A Tic-Tac-Toe player left. Game reset.");
            resetBoard();
            sendBoardUpdate();
        }
    }

    public synchronized boolean isPlayer(String username) {
        return username != null && (username.equals(playerX) || username.equals(playerO));
    }

    public synchronized char getSymbol(String username) {
        if (username == null) return ' ';
        if (username.equals(playerX)) return 'X';
        if (username.equals(playerO)) return 'O';
        return ' ';
    }

    public synchronized boolean makeMove(String username, int r, int c) {
        if (!isPlayer(username)) {
            ChatServer.sendPrivate(username, "[ERROR] You are not a Tic-Tac-Toe player.");
            return false;
        }

        if (playerX == null || playerO == null) {
            ChatServer.sendPrivate(username, "[ERROR] Waiting for another player to join the game.");
            return false;
        }

        if (gameOver) {
            ChatServer.sendPrivate(username, "[ERROR] Game is over. Press Retry to start again.");
            return false;
        }

        if (r < 0 || r > 2 || c < 0 || c > 2) {
            ChatServer.sendPrivate(username, "[ERROR] Invalid move coordinates.");
            return false;
        }

        if (!username.equals(currentTurn)) {
            ChatServer.sendPrivate(username, "[ERROR] Not your turn.");
            return false;
        }

        if (board[r][c] != ' ') {
            ChatServer.sendPrivate(username, "[ERROR] That cell is already taken.");
            return false;
        }

        // Place mark
        char sym = getSymbol(username);
        board[r][c] = sym;

        // Send updated board to all
        sendBoardUpdate();

        // Check win
        if (checkWin(sym)) {
            gameOver = true;
            sendOutcome(username);
            return true;
        }

        // Check draw
        if (boardFull()) {
            gameOver = true;
            sendDraw();
            return true;
        }

        // Switch turn
        if (currentTurn != null && currentTurn.equals(playerX)) {
            currentTurn = playerO;
        } else {
            currentTurn = playerX;
        }

        return true;
    }

    private boolean checkWin(char p) {
        for (int i = 0; i < 3; i++) {
            if (board[i][0] == p && board[i][1] == p && board[i][2] == p) return true;
            if (board[0][i] == p && board[1][i] == p && board[2][i] == p) return true;
        }

        if (board[0][0] == p && board[1][1] == p && board[2][2] == p) return true;
        if (board[0][2] == p && board[1][1] == p && board[2][0] == p) return true;

        return false;
    }

    private boolean boardFull() {
        for (int r = 0; r < 3; r++)
            for (int c = 0; c < 3; c++)
                if (board[r][c] == ' ')
                    return false;
        return true;
    }

    private void sendOutcome(String winner) {
        if (winner.equals(playerX)) {
            ChatServer.sendPrivate(playerX, "[GAME_OVER] WIN");
            if (playerO != null) ChatServer.sendPrivate(playerO, "[GAME_OVER] LOSE");
        } else if (winner.equals(playerO)) {
            ChatServer.sendPrivate(playerO, "[GAME_OVER] WIN");
            if (playerX != null) ChatServer.sendPrivate(playerX, "[GAME_OVER] LOSE");
        }
    }

    private void sendDraw() {
        if (playerX != null) ChatServer.sendPrivate(playerX, "[GAME_OVER] DRAW");
        if (playerO != null) ChatServer.sendPrivate(playerO, "[GAME_OVER] DRAW");
    }

    // Send board state to everyone
    public synchronized void sendBoardUpdate() {
        StringBuilder sb = new StringBuilder("[GAMEBOARD] ");
        for (int r = 0; r < 3; r++) {
            for (int c = 0; c < 3; c++) {
                sb.append(r).append(",")
                        .append(c).append(",")
                        .append(board[r][c]).append(";");
            }
        }
        ChatServer.broadcast(sb.toString());
    }

    // Retry system: both players must press retry
    public synchronized void handleRetry(String username) {
        if (!isPlayer(username)) {
            ChatServer.sendPrivate(username, "[ERROR] Only Tic-Tac-Toe players can retry.");
            return;
        }

        if (!gameOver) {
            ChatServer.sendPrivate(username, "[ERROR] Game is not over yet.");
            return;
        }

        retryCount++;
        ChatServer.broadcast("[RETRY_STATUS] " + retryCount);

        if (retryCount >= 2) {
            // Both players agreed to retry
            resetBoard();
            sendBoardUpdate();
        }
    }
}

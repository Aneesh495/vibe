import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;

public class SocialClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public SocialClient(String address, int port) {
        try {
            socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Connected to the server.");
        } catch (IOException e) {
            System.out.println("Connection to server failed.");
        }
    }

    // Method to send a request to the server
    public String sendRequest(String action, String caller, String data) {
        try {
            // Format the request according to the server's handleRequest format
            String request = action + " ; " + caller + " ; " + (data != null ? data : "");
            out.println(request);
            return in.readLine();
        } catch (IOException e) {
            System.out.println("Error sending request to server.");
            return "Error: Could not send request.";
        }
    }

    // Method to close the client connection
    public void closeConnection() {
        try {
            in.close();
            out.close();
            socket.close();
        } catch (IOException e) {
            System.out.println("Error closing the connection.");
        }
    }

    public static void main(String[] args) {
        SocialClient client = new SocialClient("127.0.0.1", 4242);

        if (client.socket == null) {
            JOptionPane.showMessageDialog(null, "Failed to connect to the server.", "Error", JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Initial login dialog
        String username = JOptionPane.showInputDialog(null, "Enter username:", "Login", JOptionPane.QUESTION_MESSAGE);
        if (username == null) {
            return;
        }

        // Check if user exists
        String userExistsResponse = client.sendRequest("getUser", username, null);
        if ("User not found.".equals(userExistsResponse)) {
            int createNewUser = JOptionPane.showConfirmDialog(null,
                    "User not found. Create a new account?", "New User",
                    JOptionPane.YES_NO_OPTION);
            if (createNewUser == JOptionPane.YES_OPTION) {
                String password = JOptionPane.showInputDialog(null, "Enter new password:", "New User", JOptionPane.QUESTION_MESSAGE);
                if (password != null) {
                    // Send the formatted createUser request
                    String response = client.sendRequest("createUser", username, password + " | default_pfppath | default_bio");
                    JOptionPane.showMessageDialog(null, response, "User Created", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        } else {
            // Login existing user
            String password = JOptionPane.showInputDialog(null, "Enter password:", "Login", JOptionPane.QUESTION_MESSAGE);
            if (password != null) {
                // Verify password by sending the password along with the username
                String loginResponse = client.sendRequest("loginWithPassword", username, password);
                if ("Incorrect Password".equals(loginResponse)) {
                    JOptionPane.showMessageDialog(null, "Incorrect Password, try again.", "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null, "Login Successful!", "Welcome", JOptionPane.INFORMATION_MESSAGE);
                    showMainPanel(client, username);
                }
            }
        }
    }

    private static void showMainPanel(SocialClient client, String username) {
        JFrame frame = new JFrame("Social Client");
        frame.setSize(300, 250);  // Increased frame size to accommodate the new button
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(6, 1, 5, 5));  // Updated to 6 rows to fit the new button

        JButton blockButton = new JButton("Block User");
        JButton friendButton = new JButton("Friend User");
        JButton unfriendButton = new JButton("Unfriend User");
        JButton searchButton = new JButton("Search User");
        JButton messageButton = new JButton("Message User");
        JButton unblockButton = new JButton("Unblock User");  // New unblock button

        blockButton.addActionListener(e -> performAction(client, "blockUser", username));
        friendButton.addActionListener(e -> performAction(client, "friendUser", username));
        unfriendButton.addActionListener(e -> performAction(client, "unfriend", username));
        searchButton.addActionListener(e -> performAction(client, "getUser", username));
        messageButton.addActionListener(e -> JOptionPane.showMessageDialog(frame, "This feature is not yet available", "Message", JOptionPane.INFORMATION_MESSAGE));
        unblockButton.addActionListener(e -> performAction(client, "unblock", username));  // Unblock action

        panel.add(blockButton);
        panel.add(friendButton);
        panel.add(unfriendButton);
        panel.add(searchButton);
        panel.add(messageButton);
        panel.add(unblockButton);  // Add unblock button to the panel

        frame.add(panel);
        frame.setVisible(true);
    }

    private static void performAction(SocialClient client, String action, String username) {
        String targetUser = JOptionPane.showInputDialog(null, "Enter target username:", action, JOptionPane.QUESTION_MESSAGE);
        if (targetUser != null && !targetUser.trim().isEmpty()) {
            // Send the request with the format: action ; caller ; data (targetUser)
            String response = client.sendRequest(action, username, targetUser);
            JOptionPane.showMessageDialog(null, response, action, JOptionPane.INFORMATION_MESSAGE);
        }
    }
}

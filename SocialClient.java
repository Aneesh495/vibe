import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;

public class SocialClient implements Client{ // Start of SocialClient class
    private Socket socket; // Socket for client-server communication
    private PrintWriter writer; // Writer to send data to the server
    private BufferedReader reader; // Reader to receive data from the server

    // Constructor initializes the client and attempts to connect to the server.
    public SocialClient(String address, int port) { // Constructor
        try { // Attempt to initialize socket, writer, and reader
            socket = new Socket(address, port); // Initialize socket
            writer = new PrintWriter(socket.getOutputStream(), true); // Initialize PrintWriter
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); // Initialize BufferedReader
            System.out.println("Successfully connected to the server."); // Log successful connection
        } catch (IOException io) { // Handle exceptions during connection
            System.out.println("Failed to connect to the server: " + io.getMessage()); // Log failure
            socket = null; // Ensure socket is null if connection fails
        }
    } // End of constructor

    // Sends a request to the server with action, caller, and data.
    public String sendRequest(String action, String caller, String data) { // sendRequest method with three parameters
        try {
            // Format the request string as "action ; caller ; data"
            String requestString = action + " ; " + caller + " ; " + (data != null ? data : "");
            writer.println(requestString); // Send the request to the server
            return reader.readLine(); // Read and return the server's response
        } catch (IOException e) { // Handle IO exceptions
            System.out.println("Error encountered while sending request to the server: " + e.getMessage());
            return "Error: Could not send request.";
        }
    } // End of sendRequest method with three parameters

    // Overloaded sendRequest method for actions without a caller
    public String sendRequest(String action, String data) { // sendRequest method with two parameters
        try {
            // Format the request string as "action ; ; data" (empty caller)
            String requestString = action + " ; ; " + (data != null ? data : "");
            writer.println(requestString); // Send the request to the server
            return reader.readLine(); // Read and return the server's response
        } catch (IOException e) { // Handle IO exceptions
            System.out.println("Error encountered while sending request to the server: " + e.getMessage());
            return "Error: Could not send request.";
        }
    } // End of overloaded sendRequest method

    // Closes the client connection gracefully
    public void closeConnection() { // closeConnection method
        try { // Attempt to close resources
            if (reader != null)
                reader.close(); // Close BufferedReader
            if (writer != null)
                writer.close(); // Close PrintWriter
            if (socket != null)
                socket.close(); // Close Socket
            System.out.println("Connection closed gracefully."); // Log closure
        } catch (IOException e) { // Handle IO exceptions during closure
            System.out.println("Encountered error while closing connection: " + e.getMessage());
        }
    } // End of closeConnection method

    // Main method to run the client application
    public static void main(String[] args) {
        // Create a new SocialClient instance with server details.
        SocialClient client = new SocialClient("127.0.0.1", 4242); // Replace with actual server address and port

        // If connection to the server fails, show an error dialog and exit.
        if (client.socket == null) {
            JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            return; // Exit the application
        }

        // Initialize the main GUI
        SwingUtilities.invokeLater(() -> createInitialGUI(client));
    } // End of main method

    // Creates the initial GUI with Login and Create Account buttons
    private static void createInitialGUI(SocialClient client) {
        JFrame frame = new JFrame("Social Client");
        frame.setSize(300, 150);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // Center the window

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(2, 1, 10, 10));

        JButton loginButton = new JButton("Login");
        JButton createAccountButton = new JButton("Create Account");

        panel.add(loginButton);
        panel.add(createAccountButton);

        frame.add(panel);
        frame.setVisible(true);

        // Action Listener for Login Button
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose(); // Close the initial window
                performLogin(client);
            }
        });

        // Action Listener for Create Account Button
        createAccountButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose(); // Close the initial window
                performCreateAccount(client);
            }
        });
    }

    // Performs the login process
    private static void performLogin(SocialClient client) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Login",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Username and password cannot be empty.", "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                performLogin(client); // Retry login
                return;
            }

            // Send login request
            String loginResponse = client.sendRequest("loginWithPassword", username, password);
            System.out.println("loginWithPassword response: " + loginResponse); // Debugging statement

            if ("Login successful".equalsIgnoreCase(loginResponse.trim())) {
                JOptionPane.showMessageDialog(null, "Login Successful!", "Welcome",
                        JOptionPane.INFORMATION_MESSAGE);
                showMainPanel(client, username);
            } else if ("Input Error: Incorrect Password!".equalsIgnoreCase(loginResponse.trim()) ||
                    loginResponse.contains("Error")) {
                JOptionPane.showMessageDialog(null, "Incorrect Username or Password. Please try again.", "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
                createInitialGUI(client); // Return to initial GUI
            } else if ("User Not Found".equalsIgnoreCase(loginResponse.trim()) ||
                    loginResponse.contains("User Error")) {
                JOptionPane.showMessageDialog(null, "User not found. Please create an account.", "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
                createInitialGUI(client); // Return to initial GUI
            } else {
                JOptionPane.showMessageDialog(null, "Unexpected server response: " + loginResponse, "Server Error",
                        JOptionPane.ERROR_MESSAGE);
                createInitialGUI(client); // Return to initial GUI
            }
        } else {
            createInitialGUI(client); // Return to initial GUI if canceled
        }
    }

    // Performs the account creation process
    private static void performCreateAccount(SocialClient client) {
        JPanel panel = new JPanel(new GridLayout(2, 2, 5, 5));

        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        panel.add(userLabel);
        panel.add(userField);
        panel.add(passLabel);
        panel.add(passField);

        int result = JOptionPane.showConfirmDialog(null, panel, "Create Account",
                JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);

        if (result == JOptionPane.OK_OPTION) {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();

            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(null, "Username and password cannot be empty.", "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                performCreateAccount(client); // Retry account creation
                return;
            }

            if (password.length() < 6) {
                JOptionPane.showMessageDialog(null, "Password must be at least 6 characters long.", "Password Error",
                        JOptionPane.ERROR_MESSAGE);
                performCreateAccount(client); // Retry account creation
                return;
            }

            // Prepare data: username | password | default_pfp | default_bio
            String data = String.format("%s | %s | %s | %s", username, password, "Database/ProfilePicture/default.png",
                    "default bio");
            String createResponse = client.sendRequest("createUser", "", data);
            System.out.println("createUser response: " + createResponse); // Debugging statement

            if ("User created successfully".equalsIgnoreCase(createResponse.trim())) {
                JOptionPane.showMessageDialog(null, "Account created successfully! You can now log in.", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                createInitialGUI(client); // Return to initial GUI
            } else if (createResponse.contains("Error") || createResponse.contains("Input Error")) {
                JOptionPane.showMessageDialog(null, createResponse, "Creation Failed",
                        JOptionPane.ERROR_MESSAGE);
                performCreateAccount(client); // Retry account creation
            } else {
                JOptionPane.showMessageDialog(null, "Unexpected server response: " + createResponse, "Server Error",
                        JOptionPane.ERROR_MESSAGE);
                performCreateAccount(client); // Retry account creation
            }
        } else {
            createInitialGUI(client); // Return to initial GUI if canceled
        }
    }

    // Message GUI
    private static void sendMessage(SocialClient client, String username,JFrame frame){
        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(0,2));

        // initializes message input
        JTextField recipient = new JTextField();
        JTextField message = new JTextField();

        // sets up button
        JButton button = new JButton("Send Message");
        button.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                
                // gets text
                String rec = recipient.getText().trim();
                String mes = message.getText().trim();
                
                // checks to see if they are blank
                if (rec.isBlank()|| mes.isBlank()) {
                    JOptionPane.showMessageDialog(null,"All fields must be filled out",
                    "Field Error", JOptionPane.ERROR_MESSAGE);
                }
                String data = String.format("%s | %s", rec,mes);
                String createResponse = client.sendRequest("sendMessage", username,data);
                if (createResponse.toLowerCase().contains("error")) {
                    JOptionPane.showMessageDialog(null,createResponse,
                    "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(null,createResponse,
                    "Success", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });
        
        // sets up panel
        panel.add(new Label("Recipient:"));
        panel.add(recipient);
        panel.add(new Label("Message:"));
        panel.add(message);
        panel.add(button);
        
        JOptionPane.showConfirmDialog(frame, panel, "Send Message",
        JOptionPane.OK_OPTION, JOptionPane.PLAIN_MESSAGE);
    }
    // Displays the main application panel after successful login
    private static void showMainPanel(SocialClient client, String username) { // showMainPanel method
        JFrame frame = new JFrame("Social Client");
        frame.setSize(300, 300);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // Center the window

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(6, 1, 5, 5));

        // Create buttons for various actions    // CHANGE LAYOUT, ADD USER BIO AND PFP
        JButton blockButton = new JButton("Block User");
        JButton friendButton = new JButton("Friend User");
        JButton unfriendButton = new JButton("Unfriend User");
        JButton searchButton = new JButton("Search User");
        JButton messageButton = new JButton("Message User");
        JButton unblockButton = new JButton("Unblock User");
        JButton getMessage = new JButton("Retrieve Message");

        // Add action listeners to the buttons
        blockButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAction(client, "blockUser", username);
            }
        });

        friendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAction(client, "friendUser", username);
            }
        });

        unfriendButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAction(client, "unfriend", username);
            }
        });

        searchButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAction(client, "getUser", username);
            }
        });

        messageButton.addActionListener(new ActionListener() { // Currently not implemented
            @Override
            public void actionPerformed(ActionEvent e) {
                sendMessage(client,username, frame);
            }
        });

        getMessage.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e){
                performAction(client, "getMessage", username);
            }
        });
        unblockButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAction(client, "unblock", username);
            }
        });

        // Add buttons to the panel
        panel.add(blockButton);
        panel.add(friendButton);
        panel.add(unfriendButton);
        panel.add(searchButton);
        panel.add(messageButton);
        panel.add(unblockButton);
        panel.add(getMessage);
        frame.add(panel);
        frame.setVisible(true);
    } // End of showMainPanel method

    // Performs the requested action by sending it to the server
    private static void performAction(SocialClient client, String action, String username) { // performAction method
        // Prompt for the target username
        String targetUser = JOptionPane.showInputDialog(null, "Enter target username:",
                action, JOptionPane.QUESTION_MESSAGE);
        if (targetUser != null && !targetUser.trim().isEmpty()) { // Check if input is valid
            // Send the corresponding request to the server
            String response = client.sendRequest(action, username, targetUser.trim());
            System.out.println(action + " response: " + response); // Debugging statement
            // Show the server's response to the user
            JOptionPane.showMessageDialog(null, response, action, JOptionPane.INFORMATION_MESSAGE);
        } else {
            // If input is invalid, show an error message
            JOptionPane.showMessageDialog(null, "Target username cannot be empty.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    } // End of performAction method
} // End of SocialClient class

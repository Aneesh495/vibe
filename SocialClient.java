import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;

import javax.swing.*;
import javax.swing.border.Border;

import java.awt.*;

import javax.swing.filechooser.FileFilter;

public class SocialClient {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    // Constructor initializes the client and attempts to connect to the server.
    public SocialClient(String address, int port) {
        try {
            socket = new Socket(address, port);
            writer = new PrintWriter(socket.getOutputStream(), true);
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
            System.out.println("Successfully connected to the server.");
        } catch (IOException io) {
            System.out.println("Failed to connect to the server: " + io.getMessage());
            socket = null;
        }
    }

    // Sends a request to the server with action, caller, and data.
    public String sendRequest(String action, String caller, String data) {
        try {
            String requestString = action + " ; " + caller + " ; " + (data != null ? data : "");
            writer.println(requestString);
            return reader.readLine();
        } catch (IOException e) {
            System.out.println("Error encountered while sending request to the server: " + e.getMessage());
            return "Error: Could not send request.";
        }
    }

    // Overloaded sendRequest method for actions without a caller
    public String sendRequest(String action, String data) {
        try {
            String requestString = action + " ; ; " + (data != null ? data : "");
            writer.println(requestString);
            return reader.readLine();
        } catch (IOException e) {
            System.out.println("Error encountered while sending request to the server: " + e.getMessage());
            return "Error: Could not send request.";
        }
    }

    // Closes the client connection gracefully
    public void closeConnection() {
        try {
            if (reader != null)
                reader.close();
            if (writer != null)
                writer.close();
            if (socket != null)
                socket.close();
            System.out.println("Connection closed gracefully.");
        } catch (IOException e) {
            System.out.println("Encountered error while closing connection: " + e.getMessage());
        }
    }

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
    }

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

            if ("Login successful".equalsIgnoreCase(loginResponse.trim())) {
                JOptionPane.showMessageDialog(null, "Login Successful!", "Welcome",
                        JOptionPane.INFORMATION_MESSAGE);
                showChatList(client, username);
            } else if (loginResponse.contains("Incorrect Password") ||
                    loginResponse.contains("Input Error")) {
                JOptionPane.showMessageDialog(null, "Incorrect Username or Password. Please try again.", "Login Failed",
                        JOptionPane.ERROR_MESSAGE);
                createInitialGUI(client); // Return to initial GUI
            } else if (loginResponse.contains("User Not Found") ||
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

    private static void showEditProfileDialog(JPanel sidePanel, SocialClient client, String username, String password, String profilePicturePath, String bio) {
            // Create a new frame for editing profile
            JFrame editFrame = new JFrame("Edit Profile");
            editFrame.setSize(400, 500);
            editFrame.setLayout(new BorderLayout());
    
            // Main panel for editing
            JPanel editPanel = new JPanel();
            editPanel.setLayout(new GridLayout(5, 2, 10, 10));
            editPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    
                    // Components for editing
            JLabel usernameLabel = new JLabel("Username:");
            JTextField usernameField = new JTextField(username);
            JTextField profilePictureField = new JTextField();
            JLabel passwordLabel = new JLabel("Password:");
            JPasswordField passwordField = new JPasswordField(password);

            JLabel profilePictureLabel = new JLabel("Profile Picture:");
            JPanel profilePicturePanel = new JPanel(new BorderLayout());
            JLabel profilePictureDisplay = new JLabel(); // Label to display the profile picture
            profilePictureDisplay.setHorizontalAlignment(JLabel.CENTER);
            profilePictureDisplay.setBorder(BorderFactory.createLineBorder(Color.BLACK));

            // Load and scale the profile picture
            ImageIcon profileImage = new ImageIcon(profilePicturePath);
            Image scaledImage = profileImage.getImage().getScaledInstance(200, 300, Image.SCALE_SMOOTH);
            profilePictureDisplay.setIcon(new ImageIcon(scaledImage));

            JButton uploadButton = new JButton("Upload Picture");
            uploadButton.addActionListener(e -> {
                JFileChooser fileChooser = new JFileChooser();
                int result = fileChooser.showOpenDialog(editFrame);
                if (result == JFileChooser.APPROVE_OPTION) {
                    File selectedFile = fileChooser.getSelectedFile();

                    // Update the profile picture path field
                    profilePictureField.setText("Database/Files/" + username + selectedFile.getName());

                    File destination = new File("Database/Files/" + username + selectedFile.getName());
                    if (!destination.getParentFile().exists()) {
                        destination.getParentFile().mkdirs();
                    }
                    try {
                        Files.copy(selectedFile.toPath(), destination.toPath());
                    } catch (IOException e1) {
                        
                    }
                    
                }
            });

            profilePicturePanel.add(profilePictureDisplay, BorderLayout.CENTER);
            profilePicturePanel.add(uploadButton, BorderLayout.SOUTH);

            JLabel bioLabel = new JLabel("Bio:");
            JTextArea bioArea = new JTextArea(bio);
            bioArea.setLineWrap(true);
            bioArea.setWrapStyleWord(true);

            // Add components to panel
            editPanel.add(usernameLabel);
            editPanel.add(usernameField);
            editPanel.add(passwordLabel);
            editPanel.add(passwordField);
            editPanel.add(profilePictureLabel);
            editPanel.add(profilePicturePanel); // Add profile picture panel
            editPanel.add(bioLabel);
            JScrollPane bioScrollPane = new JScrollPane(bioArea);
            editPanel.add(bioScrollPane);
            // Buttons
            JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT));
            JButton saveButton = new JButton("Save");
            JButton cancelButton = new JButton("Cancel");
    
            // Add save and cancel buttons to panel
            buttonPanel.add(saveButton);
            buttonPanel.add(cancelButton);
    
            // Save button action listener
            saveButton.addActionListener((ActionEvent event) -> {
                String updatedUsername = usernameField.getText();
                String updatedPassword = new String(passwordField.getPassword());
                String updatedProfilePicturePath = profilePictureField.getText();
                String updatedBio = bioArea.getText();
    
                String data = String.format("%s | %s | %s | %s | %s ",username, updatedUsername,updatedPassword,updatedProfilePicturePath,updatedBio);
                System.out.println(data);
                String response = client.sendRequest("editUser", username, data);

                System.out.println(response);
                if (response.toLowerCase().contains("error")) {
                    JOptionPane.showMessageDialog(sidePanel, response, "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    editFrame.dispose();
                }
        });

        // Cancel button action listener
        cancelButton.addActionListener((ActionEvent event) -> editFrame.dispose());

        // Add components to frame
        editFrame.add(editPanel, BorderLayout.CENTER);
        editFrame.add(buttonPanel, BorderLayout.SOUTH);

        // Show the frame
        editFrame.setVisible(true);
    }

    // Displays the chat list after successful login
    private static void showChatList(SocialClient client, String username) {
        // Send request to get the list of chat users
        String response = client.sendRequest("getChatList", username, "");
        String[] chatUsers = response.trim().split(" \\| ");

        JFrame frame = new JFrame("Chats - " + username);
        frame.setSize(600, 600); // Increased width to accommodate side panel
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // Center the window

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Chat list
        DefaultListModel<String> listModel = new DefaultListModel<>();
        for (String user : chatUsers) {
            if (!user.trim().isEmpty()) {
                listModel.addElement(user.trim());
            }
        }

        JList<String> chatList = new JList<>(listModel);
        chatList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        chatList.setFont(new Font("Arial", Font.PLAIN, 18));

        JScrollPane scrollPane = new JScrollPane(chatList);

        // Side panel with buttons
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new GridLayout(0, 1, 5, 5));

        JButton friendButton = new JButton("Friend User");
        JButton unfriendButton = new JButton("Unfriend User");
        JButton blockButton = new JButton("Block User");
        JButton unblockButton = new JButton("Unblock User");
        JButton searchButton = new JButton("Search User");
        JButton profileButton = new JButton("Edit Profile");
        JButton logoutButton = new JButton("Logout");

        sidePanel.add(friendButton);
        sidePanel.add(unfriendButton);
        sidePanel.add(blockButton);
        sidePanel.add(unblockButton);
        sidePanel.add(searchButton);
        sidePanel.add(profileButton);
        sidePanel.add(logoutButton);

        // Add action listeners to buttons
        friendButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to friend:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                String response1 = client.sendRequest("friendUser", username, targetUser.trim());
                if (response1.toLowerCase().contains("successfully")) {
                    JOptionPane.showMessageDialog(frame, response1, "Friend User", JOptionPane.INFORMATION_MESSAGE);
                    // Refresh the chat list
                    frame.dispose();
                    showChatList(client, username);
                } else {
                    JOptionPane.showMessageDialog(frame, response1, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        unfriendButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to unfriend:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                String response1 = client.sendRequest("unfriend", username, targetUser.trim());
                if (response1.toLowerCase().contains("successfully")) {
                    JOptionPane.showMessageDialog(frame, response1, "Unfriend User", JOptionPane.INFORMATION_MESSAGE);
                    // Refresh the chat list
                    frame.dispose();
                    showChatList(client, username);
                } else {
                    JOptionPane.showMessageDialog(frame, response1, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        blockButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to block:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                String response1 = client.sendRequest("blockUser", username, targetUser.trim());
                if (response1.toLowerCase().contains("successfully")) {
                    JOptionPane.showMessageDialog(frame, response1, "Block User", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, response1, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        unblockButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to unblock:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                String response1 = client.sendRequest("unblock", username, targetUser.trim());
                if (response1.toLowerCase().contains("successfully")) {
                    JOptionPane.showMessageDialog(frame, response1, "Unblock User", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, response1, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        searchButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to search:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                String response1 = client.sendRequest("getUser", username, targetUser.trim());
                if (response1.toLowerCase().contains("User Not Found") || response1.toLowerCase().contains("error")) {
                    JOptionPane.showMessageDialog(frame, response1, "Search User", JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, response1, "Search User", JOptionPane.INFORMATION_MESSAGE);
                }
            }
        });

        profileButton.addActionListener(e->{
            String resp = client.sendRequest("getUser", username, username);
            String[] userInfo = resp.split(" \\| ");
            showEditProfileDialog(sidePanel, client, username,userInfo[1],userInfo[2],userInfo[3]);
        });

        logoutButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(frame, "Are you sure you want to logout?");
            if (result == JOptionPane.YES_OPTION) {
                frame.dispose();
                performLogin(client);
            }
        });
        // Split pane to hold chat list and side panel
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, scrollPane, sidePanel);
        splitPane.setDividerLocation(350); // Adjust as needed

        mainPanel.add(splitPane, BorderLayout.CENTER);

        JButton messageButton = new JButton("Message Someone");
        messageButton.setFont(new Font("Arial", Font.PLAIN, 16));
        messageButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to message:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                openChatWindow(client, username, targetUser.trim());
            }
        });

        mainPanel.add(messageButton, BorderLayout.SOUTH);

        chatList.addListSelectionListener(event -> {
            if (!event.getValueIsAdjusting()) {
                String selectedUser = chatList.getSelectedValue();
                if (selectedUser != null) {
                    openChatWindow(client, username, selectedUser);
                }
            }
        });

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // Opens the chat window with the selected user
    private static void openChatWindow(SocialClient client, String username, String targetUser) {
        JFrame chatFrame = new JFrame("Chat with " + targetUser);
        chatFrame.setSize(500, 600);
        chatFrame.setLocationRelativeTo(null); // Center the window
        String filename = "";
        JPanel panel = new JPanel(new BorderLayout());

         JTextArea chatArea = new JTextArea();

        JPanel chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));


        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));


        JScrollPane scrollPane = new JScrollPane(chatPanel);

        JTextField messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));

        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.PLAIN, 14));
        sendButton.addActionListener(e -> {
            String message = messageField.getText().trim();
            if(!filename.isEmpty()){
                String data = String.format("%s | %s",targetUser,filename);
                String response = client.sendRequest("sendMessage", username, data);
                if (response.toLowerCase().contains("error")) {
                    JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    // CHANGE THIS //
                    // Append the message to the chat area
                    chatArea.append("Me: " + message + "\n");
                    System.out.println("appended message");
                    messageField.setText("");
                }
            }
            if (!message.isEmpty()) {
                String data = String.format("%s | %s", targetUser, message);
                String response = client.sendRequest("sendMessage", username, data);
                if (response.toLowerCase().contains("error")) {
                    JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    // Append the message to the chat area
                    chatArea.append("Me: " + message + "\n");
                    messageField.setText("");
                }
            }
        });

        JButton uploadImg = new JButton("Files");
        JFileChooser fileChooser = new JFileChooser();
     
        fileChooser.setFileFilter(new FileFilter() {
            public boolean accept(File file){
                if (file.isDirectory()) {
                        return true; // Allow directories
                    }
                    String name = file.getName().toLowerCase();
                    return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
            }

            public String getDescription() {
                return "Image Files (*.jpg, *.jpeg, *.png, *.gif)";
            }
        });


        uploadImg.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e){

                int file = fileChooser.showOpenDialog(fileChooser);
                if(file == fileChooser.APPROVE_OPTION){
                    File selectedFile = fileChooser.getSelectedFile();
                    try {
                        File destination = new File("Database/Files/" + username+ targetUser + selectedFile.getName());
                        if (!destination.getParentFile().exists()) {
                            destination.getParentFile().mkdirs();
                        }
                        Files.copy(selectedFile.toPath(), destination.toPath());
        
                        String data = String.format("%s | #FILE#:%s", targetUser, "Database/Files/" + username+ targetUser + selectedFile.getName());
                        String response = client.sendRequest("sendMessage", username, data);
                        if (response.toLowerCase().contains("error")) {
                            JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                        } else {
                            // Append the message to the chat area
                            //CHANGE
                            // chatArea.append("Me: " + message + "\n");
                            // messageField.setText("");
                        }
                    
                    } catch (IOException ioException) {
                        JOptionPane.showMessageDialog(fileChooser, 
                            "Error saving file: " + ioException.getMessage(), 
                            "Error", 
                            JOptionPane.ERROR_MESSAGE);
                    }
                }
            }
        });

        chatPanel.setBackground(Color.decode("#F8F8FF"));
        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel uploadSend = new JPanel(new GridLayout(0,2));
        JPanel imageMessagePrev = new JPanel(new BorderLayout());
        uploadSend.add(sendButton);
        uploadSend.add(uploadImg);
        imageMessagePrev.add(inputPanel, BorderLayout.SOUTH);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(uploadSend, BorderLayout.EAST);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(imageMessagePrev, BorderLayout.SOUTH);
        
        chatFrame.add(panel);
        chatFrame.setVisible(true);

        // Load existing messages
        loadChatHistory(client, username, targetUser, chatPanel);



        // Start a timer to poll for new messages every 3 seconds
        Timer timer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadChatHistory(client, username, targetUser, chatPanel);
                chatFrame.validate();
                chatFrame.repaint();
            }
        });
        timer.start();

        // Stop the timer when the window is closed
        chatFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                timer.stop();
            }
        });
    }

    private static JPanel createMessageBubble(String sender, String messageText, JPanel chatPanel) {
        // Create a panel for the message bubble
        JPanel messageBubble = new JPanel();
        messageBubble.setLayout(new BorderLayout());
        messageBubble.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // Padding: Top, Left, Bottom, Right
        messageBubble.setOpaque(false); // Make the panel transparent
    
        // Create the editable text area
        JTextArea messageArea = new JTextArea(sender + ": " + messageText);
        messageArea.setFont(new Font("Arial", Font.PLAIN, 12)); // Smaller font for compact area
        messageArea.setEditable(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setLineWrap(true);
        messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding inside the bubble
        if (sender.equals("Me")) {
            messageArea.setBackground(new Color(220, 248, 198)); // Background color confined to content
        } else {
            messageArea.setBackground(new Color(220,220,220));
        }
      
        messageArea.setForeground(Color.BLACK);
        messageArea.setCaretColor(Color.BLACK);
    
        // Dynamically set the preferred size based on the text length
        int textLength = messageText.length();
        int bubbleWidth = Math.min(220, Math.max(50, textLength * 7)); // Adjust bubble width based on text length
        //int bubbleHeight = Math.min();
        messageArea.setPreferredSize(new Dimension(bubbleWidth, messageArea.getPreferredSize().height));
    
        // Add a rounded border to the message area itself
        messageArea.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(Color.GRAY, 1, true), // Rounded border
                BorderFactory.createEmptyBorder(5, 5, 5, 5) // Padding inside the border
         ));
    
  
        /*JButton deleteButton = new JButton("X"); // Compact delete button
        deleteButton.setFont(new Font("Arial", Font.BOLD, 10)); // Smaller font for delete button
        deleteButton.setMargin(new Insets(2, 2, 2, 2)); // Compact padding for the button
        deleteButton.addActionListener(e -> {
            int result = JOptionPane.showConfirmDialog(null, messageArea, "Delete Message?",
                    JOptionPane.OK_CANCEL_OPTION, JOptionPane.PLAIN_MESSAGE);
            if (result == JOptionPane.OK_OPTION) {
                chatPanel.remove(messageBubble);
            }
            // Remove the bubble from the panel
            chatPanel.revalidate();
            chatPanel.repaint();
        });*/
    
        // Add the text area and delete button to the bubble
        JPanel bubbleContent = new JPanel();
        bubbleContent.setLayout(new BorderLayout());
        bubbleContent.add(messageArea, BorderLayout.CENTER);
        //bubbleContent.add(deleteButton, BorderLayout.EAST);
        bubbleContent.setOpaque(false); // Transparent background for the content
    
        messageBubble.add(bubbleContent);
    
        return messageBubble;
    }

    

    // Loads the chat history between the user and target user
    private static void loadChatHistory(SocialClient client, String username, String targetUser, JPanel chatPanel) {
        String data = targetUser;
        String response = client.sendRequest("getMessage", username, data);
        if (response.toLowerCase().contains("error")) {
            // Do nothing or display an error message if needed
        } else {
            // Clear the chat area and display messages
            chatPanel.removeAll();
            String[] messages = response.split(" ; ");
            for (String msg : messages) {
                String[] parts = msg.split("-\\d+#");
                if (parts.length >= 2) {
                    String messageText = parts[0];
                    String messageType = parts[1].replace("#", "");
                    if (messageType.equals("S")) {
                        if(msg.contains("#FILE#:")){

                            String imgPath = msg.replace("#FILE#:", "").trim();
                            if (imgPath.matches(".*-\\d+#S#")) {
                                int index = imgPath.lastIndexOf('-');
                                if (index != -1) {
                                    imgPath = imgPath.substring(0, index);
                                }
                            }
                            //System.out.println("Img Path: "+ imgPath);
                            
                            ImageIcon tempImage= new ImageIcon(imgPath);
                            Image scaledImage = tempImage.getImage().getScaledInstance(tempImage.getIconWidth()/6, tempImage.getIconHeight()/6, Image.SCALE_SMOOTH);
                            ImageIcon scaledIcon = new ImageIcon(scaledImage);
                            JLabel imageLabel = new JLabel("Me:", JLabel.LEFT);
                            imageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            JPanel imgBubbleContent = new JPanel(new BorderLayout());
                            imgBubbleContent.add(imageLabel,BorderLayout.WEST);
                            imgBubbleContent.add(new JLabel(scaledIcon),BorderLayout.CENTER);
                            imgBubbleContent.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                            chatPanel.add(imgBubbleContent);   
                        } else {
                            chatPanel.add(createMessageBubble("Me", messageText, chatPanel));
                        }
                    } else if (messageType.equals("R")) {
                        if(msg.contains("#FILE#:")){

                            String imgPath = msg.replace("#FILE#:", "").trim();
                            ImageIcon tempImage= new ImageIcon(imgPath);
                            JLabel imageLabel = new JLabel(targetUser+":", JLabel.LEFT);
                            imageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            
                            chatPanel.add(imageLabel);
                            chatPanel.add(new JLabel(tempImage));   
                        } else {
                            chatPanel.add(createMessageBubble(targetUser, messageText, chatPanel));
                        }
                    }
                }
            }
        }
    }
}
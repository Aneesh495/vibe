import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import javax.swing.*;
import java.awt.*;
import javax.swing.filechooser.FileFilter;

public class SocialClientGUI {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    // Constructor initializes the client and attempts to connect to the server.
    public SocialClientGUI(String address, int port) {
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
        // Create a new SocialClientGUI instance with server details.
        SocialClientGUI client = new SocialClientGUI("127.0.0.1", 4242); // Replace with actual server address and port

        // If connection to the server fails, show an error dialog and exit.
        if (client.socket == null) {
            JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            return; // Exit the application
        }

        // Initialize the main GUI
        SwingUtilities.invokeLater(() -> createInitialGUI(client));
    }

    // Creates the initial GUI with Logo, Username, Password fields, and Create Account button
    private static void createInitialGUI(SocialClientGUI client) {
        JFrame frame = new JFrame("Social Client");
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // Center the window

        // Set background color
        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));

        // Main panel with BoxLayout to arrange components vertically
        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Add padding

        // Add Logo
        JLabel logoLabel = new JLabel();
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        // Load the logo image (ensure the path is correct)
        ImageIcon logoIcon = new ImageIcon("Database/logo.jpg"); // Replace with your logo path
        // Scale the image if necessary
        Image logoImage = logoIcon.getImage().getScaledInstance(200, 100, Image.SCALE_SMOOTH);
        logoLabel.setIcon(new ImageIcon(logoImage));
        mainPanel.add(logoLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Add space

        // Add Image Above Username Field
        JLabel userImageLabel = new JLabel();
        userImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon userIcon = new ImageIcon("path/to/user_image.png"); // Replace with your image path
        Image userImage = userIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
        userImageLabel.setIcon(new ImageIcon(userImage));
        mainPanel.add(userImageLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add space

        // Username and Password fields
        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setBackground(Color.decode("#F8F8FF"));
        fieldsPanel.setLayout(new GridLayout(2, 2, 10, 10));
        fieldsPanel.setMaximumSize(new Dimension(400, 60));
        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        fieldsPanel.add(userLabel);
        fieldsPanel.add(userField);
        fieldsPanel.add(passLabel);
        fieldsPanel.add(passField);

        mainPanel.add(fieldsPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Add space

        // Login button
        JButton loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(loginButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Add space

        // 'Don't have an account?' and 'Create Account' button
        JPanel accountPanel = new JPanel();
        accountPanel.setBackground(Color.decode("#F8F8FF"));
        accountPanel.setLayout(new BoxLayout(accountPanel, BoxLayout.Y_AXIS));
        accountPanel.setAlignmentX(Component.CENTER_ALIGNMENT);

        JLabel noAccountLabel = new JLabel("Don't have an account?");
        noAccountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton createAccountButton = new JButton("Create Account");
        createAccountButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        accountPanel.add(noAccountLabel);
        accountPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add space
        accountPanel.add(createAccountButton);

        mainPanel.add(accountPanel);

        frame.add(mainPanel);
        frame.setVisible(true);

        // Action Listener for Login Button
        loginButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userField.getText().trim();
                String password = new String(passField.getPassword()).trim();
                performLogin(client, frame, username, password);
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

        // Allow pressing Enter in password field to trigger login
        passField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loginButton.doClick();
            }
        });
    }

    // Performs the login process
    private static void performLogin(SocialClientGUI client, JFrame frame, String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Username and password cannot be empty.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        // Send login request
        String loginResponse = client.sendRequest("loginWithPassword", username, password);

        if ("Login successful".equalsIgnoreCase(loginResponse.trim())) {
            JOptionPane.showMessageDialog(frame, "Login Successful!", "Welcome",
                    JOptionPane.INFORMATION_MESSAGE);
            frame.dispose();
            showChatList(client, username);
        } else if (loginResponse.contains("Incorrect Password") ||
                loginResponse.contains("Input Error")) {
            JOptionPane.showMessageDialog(frame, "Incorrect Username or Password. Please try again.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        } else if (loginResponse.contains("User Not Found") ||
                loginResponse.contains("User Error")) {
            JOptionPane.showMessageDialog(frame, "User not found. Please create an account.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Unexpected server response: " + loginResponse, "Server Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Performs the account creation process
    private static void performCreateAccount(SocialClientGUI client) {
        JFrame frame = new JFrame("Create Account");
        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null);

        // Set background color
        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Add padding

        JLabel titleLabel = new JLabel("Create Account");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Add space

        // Image above username field
        JLabel userImageLabel = new JLabel();
        userImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon userIcon = new ImageIcon("Database/logo.jpg"); // Replace with your image path
        Image userImage = userIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
        userImageLabel.setIcon(new ImageIcon(userImage));
        mainPanel.add(userImageLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add space

        JPanel fieldsPanel = new JPanel();
        fieldsPanel.setBackground(Color.decode("#F8F8FF"));
        fieldsPanel.setLayout(new GridLayout(2, 2, 10, 10));
        fieldsPanel.setMaximumSize(new Dimension(300, 100));
        JLabel userLabel = new JLabel("Username:");
        JTextField userField = new JTextField();
        JLabel passLabel = new JLabel("Password:");
        JPasswordField passField = new JPasswordField();

        fieldsPanel.add(userLabel);
        fieldsPanel.add(userField);
        fieldsPanel.add(passLabel);
        fieldsPanel.add(passField);

        mainPanel.add(fieldsPanel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20))); // Add space

        // Buttons Panel
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBackground(Color.decode("#F8F8FF"));
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
        JButton createButton = new JButton("Create Account");
        JButton cancelButton = new JButton("Cancel");
        buttonsPanel.add(createButton);
        buttonsPanel.add(cancelButton);

        mainPanel.add(buttonsPanel);

        frame.add(mainPanel);
        frame.setVisible(true);

        // Action Listener for Create Account Button
        createButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                String username = userField.getText().trim();
                String password = new String(passField.getPassword()).trim();

                if (username.isEmpty() || password.isEmpty()) {
                    JOptionPane.showMessageDialog(frame, "Username and password cannot be empty.", "Input Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                if (password.length() < 6) {
                    JOptionPane.showMessageDialog(frame, "Password must be at least 6 characters long.", "Password Error",
                            JOptionPane.ERROR_MESSAGE);
                    return;
                }

                // Prepare data: username | password | default_pfp | default_bio
                String data = String.format("%s | %s | %s | %s", username, password, "Database/ProfilePicture/default.png",
                        "default bio");
                String createResponse = client.sendRequest("createUser", "", data);

                if ("User created successfully".equalsIgnoreCase(createResponse.trim())) {
                    JOptionPane.showMessageDialog(frame, "Account created successfully! You can now log in.", "Success",
                            JOptionPane.INFORMATION_MESSAGE);
                    frame.dispose();
                    createInitialGUI(client); // Return to initial GUI
                } else if (createResponse.contains("Error") || createResponse.contains("Input Error")) {
                    JOptionPane.showMessageDialog(frame, createResponse, "Creation Failed",
                            JOptionPane.ERROR_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(frame, "Unexpected server response: " + createResponse, "Server Error",
                            JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        // Action Listener for Cancel Button
        cancelButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                frame.dispose();
                createInitialGUI(client); // Return to initial GUI
            }
        });

        // Allow pressing Enter in password field to trigger account creation
        passField.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                createButton.doClick();
            }
        });
    }

    private static void showEditProfileDialog(JPanel sidePanel, SocialClientGUI client, String username, String password, String profilePicturePath, String bio) {
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
    // Displays the chat list after successful login
    private static void showChatList(SocialClientGUI client, String username) {
        // Send request to get the list of chat users
        String response = client.sendRequest("getChatList", username, "");
        String[] chatUsers = response.trim().split(" \\| ");

        JFrame frame = new JFrame("Chats - " + username);
        frame.setSize(600, 600); // Increased width to accommodate side panel
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null); // Center the window

        // Main panel
        JPanel mainPanel = new JPanel(new BorderLayout());

        // Chat list panel with BoxLayout
        JPanel chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));

        // Create chat items
        for (String user : chatUsers) {
            if (!user.trim().isEmpty()) {
                ChatItemPanel chatItem = new ChatItemPanel(user.trim(), username, client);
                chatListPanel.add(chatItem);
            }
        }

        // Make chat list scrollable
        JScrollPane scrollPane = new JScrollPane(chatListPanel);

        // Side panel with buttons (unchanged)
        JPanel sidePanel = new JPanel();
        sidePanel.setLayout(new GridLayout(0, 1, 5, 5));

        JButton friendButton = new JButton("Friend User");
        JButton unfriendButton = new JButton("Unfriend User");
        JButton blockButton = new JButton("Block User");
        JButton unblockButton = new JButton("Unblock User");
        JButton searchButton = new JButton("Search User");
        JButton profileButton = new JButton("Edit Profile");

        sidePanel.add(friendButton);
        sidePanel.add(unfriendButton);
        sidePanel.add(blockButton);
        sidePanel.add(unblockButton);
        sidePanel.add(searchButton);
        sidePanel.add(profileButton);

        // Add action listeners to buttons (unchanged)
        // ... (Your existing action listeners)

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

        frame.add(mainPanel);
        frame.setVisible(true);
    }

    // Custom panel for each chat item
    static class ChatItemPanel extends JPanel {
        private String targetUser;
        private String username;
        private SocialClientGUI clientGUI;

        public ChatItemPanel(String targetUser, String username, SocialClientGUI client) {
            this.targetUser = targetUser;
            this.username = username;
            // this.clientGUI = client;

            setLayout(new BorderLayout());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 80)); // Adjust height as needed
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY)); // Separator line

            // Load profile picture (placeholder for now)
            JLabel profilePicLabel = new JLabel();
            ImageIcon profilePicIcon = new ImageIcon("Database/ProfilePicture/default.png");
            Image profilePicImage = profilePicIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
            profilePicLabel.setIcon(new ImageIcon(profilePicImage));
            profilePicLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            // Username label
            JLabel usernameLabel = new JLabel(targetUser);
            usernameLabel.setFont(new Font("Arial", Font.BOLD, 18));

            // Add components
            add(profilePicLabel, BorderLayout.WEST);
            add(usernameLabel, BorderLayout.CENTER);

            // Add mouse listener to open chat on click
            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openChatWindow(client, username, targetUser);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(new Color(230, 230, 230)); // Light gray on hover
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(Color.WHITE); // Default background
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            // Set background color
            setBackground(Color.WHITE);
        }
    }

    // Opens the chat window with the selected user
    private static void openChatWindow(SocialClientGUI client, String username, String targetUser) {
        JFrame chatFrame = new JFrame("Chat with " + targetUser);
        chatFrame.setSize(400, 500);
        chatFrame.setLocationRelativeTo(null); // Center the window
        String filename = "";
        JPanel panel = new JPanel(new BorderLayout());

        JTextArea chatArea = new JTextArea();
        chatArea.setEditable(false);
        chatArea.setLineWrap(true);
        chatArea.setWrapStyleWord(true);
        chatArea.setFont(new Font("Arial", Font.PLAIN, 14));

        JScrollPane scrollPane = new JScrollPane(chatArea);

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

        JButton uploadImg = new JButton("{}");
        JFileChooser fileChooser = new JFileChooser();
        JLabel imagePreviewLabel = new JLabel("No Image selected");
        imagePreviewLabel.setPreferredSize(new Dimension(200, 150));
        imagePreviewLabel.setBorder(BorderFactory.createLineBorder(Color.BLACK));
        fileChooser.setFileFilter(new FileFilter() {
            public boolean accept(File file){
                if (file.isDirectory()) {
                    return true; // Allow directories
                }
                String name = file.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
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


        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel uploadSend = new JPanel(new GridLayout(0,2));
        JPanel imageMessagePrev = new JPanel(new BorderLayout());
        uploadSend.add(sendButton);
        uploadSend.add(uploadImg);
        imageMessagePrev.add(inputPanel, BorderLayout.SOUTH);
        imageMessagePrev.add(imagePreviewLabel, BorderLayout.NORTH);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(uploadSend, BorderLayout.EAST);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(imageMessagePrev, BorderLayout.SOUTH);

        chatFrame.add(panel);
        chatFrame.setVisible(true);

        // Load existing messages
        loadChatHistory(client, username, targetUser, chatArea);

        // Start a timer to poll for new messages every 3 seconds
        Timer timer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadChatHistory(client, username, targetUser, chatArea);
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

    // Loads the chat history between the user and target user
    private static void loadChatHistory(SocialClientGUI client, String username, String targetUser, JTextArea chatArea) {
        String data = targetUser;
        String response = client.sendRequest("getMessage", username, data);
        if (response.toLowerCase().contains("error")) {
            // Do nothing or display an error message if needed
        } else {
            // Clear the chat area and display messages
            chatArea.setText("");
            String[] messages = response.split(" ; ");
            for (String msg : messages) {
                String[] parts = msg.split("-\\d+#");
                if (parts.length >= 2) {
                    String messageText = parts[0];
                    String messageType = parts[1].replace("#", "");
                    if (messageType.equals("S")) {
                        chatArea.append("Me: " + messageText + "\n");
                    } else if (messageType.equals("R")) {
                        chatArea.append(targetUser + ": " + messageText + "\n");
                    }
                }
            }
        }
    }
}
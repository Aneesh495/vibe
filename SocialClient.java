import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import javax.swing.*;
import java.awt.*;
import javax.swing.filechooser.FileFilter;
import java.util.ArrayList;

public class SocialClient implements Client{

    // --- Socket and Communication Components ---
    protected Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    // --- User and UI Components ---
    protected String loggedInUser;
    private JFrame mainFrame;
    private JPanel chatListPanel;
    private JPanel cardPanel;
    private JPanel friendsPanel, blockedPanel, searchPanel, editProfilePanel;
    private DefaultListModel<String> friendModel, blockedModel;
    private JList<String> friendList, blockedList;
    private JButton unfriendButton, unblockButton;
    private JLabel noFriendsLabel, noBlockedLabel;
    private JTextField searchField;
    private JPanel searchResultPanel;

    // --- Edit Profile Components ---
    private JLabel editProfilePicLabel, editUsernameLabel, editBioLabel;
    private JButton editPfpButton, editBioButton, editPasswordButton;

    // --- Constructor ---
    // Connects to the server and initializes socket communication.
    public SocialClient(String address, int port) {
        try {
            // Conect to the server with given adress and port
            socket = new Socket(address, port);
    
            // Make a writer to send messges to the server
            writer = new PrintWriter(socket.getOutputStream(), true);
    
            // Make a reader to get messges from the server
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
    
            // Print mesage if connection is success
            System.out.println("Successfully connected to the server.");
        } catch (IOException io) {
            // Print error if connection fail
            System.out.println("Failed to connect to the server: " + io.getMessage());
    
            // Set socket to null so it show no connection
            socket = null;
        }
    }

    // --- Communication Methods ---
    // Sends a request to the server with caller and data.
    public String sendRequest(String action, String caller, String data) {
        try {
            // Make the request string using action, caller and data
            String requestString = action + " ; " + caller + " ; " + (data != null ? data : "");
    
            // Send the request to server
            writer.println(requestString);
    
            // Get and return the responce from server
            return reader.readLine();
        } catch (IOException e) {
            // Print error mesage if theres a problem with the request
            System.out.println("Error encountered while sending request to the server: " + e.getMessage());
    
            // Return error if request didnt work
            return "Error: Could not send request.";
        }
    }

    // Sends a request to the server without a caller.
    public String sendRequest(String action, String data) {
        try {
            // Build the request string with action and data only
            String requestString = action + " ; ; " + (data != null ? data : "");
    
            // Send the request to the server
            writer.println(requestString);
    
            // Read and return the server responce
            return reader.readLine();
        } catch (IOException e) {
            System.out.println("Error encountered while sending request to the server: " + e.getMessage());
            return "Error: Could not send request.";
        }
    }

    // Closes the connection to the server.
    public void closeConnection() {
        try {
            // closes connection if error
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

    // --- Main Method ---
    public static void main(String[] args) {
        // Create a new SocialClient with server address and port
        SocialClient client = new SocialClient("127.0.0.1", 4242);
    
        // Check if the client didnt connect succesfully
        if (client.socket == null) {
            JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
    
        // Start the GUI on the event dispatch thread
        SwingUtilities.invokeLater(() -> client.createInitialGUI());
    }
    

    // --- GUI: Initial Login/Registration ---
    // Sets up the initial login and register GUI.
    private void createInitialGUI() {
        // Create the main frame for the application
        JFrame frame = new JFrame("Social Client");
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));
    
        // Create the main panel to hold all components
        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    
        // Add a logo at the top
        JLabel logoLabel = new JLabel();
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon logoIcon = new ImageIcon("Database/logo.jpg");
        Image logoImage = logoIcon.getImage().getScaledInstance(200, 100, Image.SCALE_SMOOTH);
        logoLabel.setIcon(new ImageIcon(logoImage));
        mainPanel.add(logoLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Add username and password fields
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
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Add login button
        JButton loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(loginButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Add option to create account
        JPanel accountPanel = new JPanel();
        accountPanel.setBackground(Color.decode("#F8F8FF"));
        accountPanel.setLayout(new BoxLayout(accountPanel, BoxLayout.Y_AXIS));
        accountPanel.setAlignmentX(Component.CENTER_ALIGNMENT);
    
        JLabel noAccountLabel = new JLabel("Don't have an account?");
        noAccountLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        JButton createAccountButton = new JButton("Create Account");
        createAccountButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    
        accountPanel.add(noAccountLabel);
        accountPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        accountPanel.add(createAccountButton);
        mainPanel.add(accountPanel);
    
        // Set up the frame and make it visible
        frame.add(mainPanel);
        frame.setVisible(true);
    
        // Allow pressing "Enter" to submit login
        passField.addActionListener(e -> loginButton.doClick());
    
        // Handle login button action
        loginButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            performLogin(username, password, frame);
        });
    
        // Handle create account button action
        createAccountButton.addActionListener(e -> {
            frame.dispose();
            performCreateAccount();
        });
    }
    
    // Handles user login logic and server response.
    protected void performLogin(String username, String password, JFrame frame) {
        // Check if fields are empty and show error if they are
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Username and password cannot be empty.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
    
        // Send login request to server with provided data
        String loginResponse = sendRequest("loginWithPassword", username, password);
    
        // Handle the servers responce
        if ("Login successful".equalsIgnoreCase(loginResponse.trim())) {
            // If succesful, go to chat list and save username
            JOptionPane.showMessageDialog(frame, "Login Successful!", "Welcome",
                    JOptionPane.INFORMATION_MESSAGE);
            frame.dispose();
            this.loggedInUser = username;
            showChatList();
        } else if (loginResponse.contains("Incorrect Password") ||
                   loginResponse.contains("Input Error")) {
            // If user/pass are wrong, show error
            JOptionPane.showMessageDialog(frame, "Incorrect Username or Password. Please try again.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        } else if (loginResponse.contains("User not found") ||
                   loginResponse.contains("User Error")) {
            // Show error if user doesnt exist
            JOptionPane.showMessageDialog(frame, "User not found. Please create an account.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            // Show unexpected server response
            JOptionPane.showMessageDialog(frame, "Unexpected server response: " + loginResponse, "Server Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    // Displays the create account window and handles creation.
    private void performCreateAccount() {
        // Create a new frame for the Create Account screen
        JFrame frame = new JFrame("Create Account");
        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));
    
        // Main panel for all components
        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));
    
        // Add title label
        JLabel titleLabel = new JLabel("Create Account");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Add user image (logo or default)
        JLabel userImageLabel = new JLabel();
        userImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon userIcon = new ImageIcon("Database/logo.jpg");
        Image userImage = userIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
        userImageLabel.setIcon(new ImageIcon(userImage));
        mainPanel.add(userImageLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    
        // Add username and password input fields
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
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));
    
        // Add create and cancel buttons
        JPanel buttonsPanel = new JPanel();
        buttonsPanel.setBackground(Color.decode("#F8F8FF"));
        buttonsPanel.setLayout(new FlowLayout(FlowLayout.CENTER, 20, 0));
        JButton createButton = new JButton("Create Account");
        JButton cancelButton = new JButton("Cancel");
        buttonsPanel.add(createButton);
        buttonsPanel.add(cancelButton);
    
        mainPanel.add(buttonsPanel);
    
        // Add main panel to frame and show it
        frame.add(mainPanel);
        frame.setVisible(true);
    
        // Allow pressing Enter to trigger the Create Account button
        passField.addActionListener(e -> createButton.doClick());
    
        // Handle Create Account button click
        createButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
    
            // Check if fields are empty
            if (username.isEmpty() || password.isEmpty()) {
                JOptionPane.showMessageDialog(frame, "Username and password cannot be empty.", "Input Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
    
            // Ensure password is at least 6 chars
            if (password.length() < 6) {
                JOptionPane.showMessageDialog(frame, "Password must be at least 6 characters long.", "Password Error",
                        JOptionPane.ERROR_MESSAGE);
                return;
            }
    
            // Prepare data and send the account creation request
            String data = String.format("%s | %s | %s | %s", username, password, "Database/ProfilePicture/default.png",
                    "default bio");
            String createResponse = sendRequest("createUser", "", data);
    
            // Handle response from server
            if ("User created successfully".equalsIgnoreCase(createResponse.trim())) {
                JOptionPane.showMessageDialog(frame, "Account created successfully! You can now log in.", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                frame.dispose();
                createInitialGUI();
            } else if (createResponse.contains("Error") || createResponse.contains("Input Error")) {
                JOptionPane.showMessageDialog(frame, createResponse, "Creation Failed",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Unexpected server response: " + createResponse, "Server Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    
        // Handle Cancel button click
        cancelButton.addActionListener(e -> {
            frame.dispose();
            createInitialGUI();
        });
    }

    // -- MESSAGES--: Retrieve/Show/Send Messages
    // Shows the main chat list and user options.
    private void showChatList() {
        // Create the main frame for the chat list screen
        mainFrame = new JFrame("Chats - " + loggedInUser);
        mainFrame.setSize(1000, 700);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);
    
        // Main panel to organize components
        JPanel mainPanel = new JPanel(new BorderLayout());
    
        // Chat list panel to show the list of chats
        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        JScrollPane chatScrollPane = new JScrollPane(chatListPanel);
    
        // Update the chat list with current chats
        updateChatList();
    
        // Right panel for user info and options
        JPanel rightPanel = new JPanel(new BorderLayout());
    
        // Panel to show user information
        JPanel userInfoPanel = new JPanel();
        userInfoPanel.setLayout(new BoxLayout(userInfoPanel, BoxLayout.Y_AXIS));
        userInfoPanel.setBackground(Color.decode("#F8F8FF"));
        userInfoPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
    
        // Get user info like profile picture, username, and bio
        String resp = sendRequest("getUser", loggedInUser, loggedInUser);
        String[] userInfo = resp.split(" \\| ");
        String pfpPath = userInfo[2].trim();
        String currentUsername = userInfo[0].trim();
        String currentBio = userInfo[3].trim();
    
        // Add user profile picture and username
        JLabel userPfpLabel = new JLabel();
        userPfpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon userPfpIcon = new ImageIcon(pfpPath);
        Image userPfpImg = userPfpIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
        userPfpLabel.setIcon(new ImageIcon(userPfpImg));
    
        JLabel userNameLabel = new JLabel(currentUsername);
        userNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        userNameLabel.setFont(new Font("Arial", Font.BOLD, 16));
    
        // Button to edit user profile
        JButton editProfileButton = new JButton("Edit Profile");
        editProfileButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    
        // Add components to user info panel
        userInfoPanel.add(userPfpLabel);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        userInfoPanel.add(userNameLabel);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
        userInfoPanel.add(editProfileButton);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    
        // Separator and buttons for Friends, Blocked, and Search
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);
        userInfoPanel.add(sep);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0, 10)));
    
        JPanel topButtonsPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        JButton friendsButton = new JButton("Friends");
        JButton blockedButton = new JButton("Blocked");
        JButton searchButton = new JButton("Search");
        topButtonsPanel.add(friendsButton);
        topButtonsPanel.add(blockedButton);
        topButtonsPanel.add(searchButton);
    
        userInfoPanel.add(topButtonsPanel);
        rightPanel.add(userInfoPanel, BorderLayout.NORTH);
    
        // Card panel for switching between Friends, Blocked, Search, and Edit Profile
        cardPanel = new JPanel(new CardLayout());
    
        // Build individual panels for each feature
        buildFriendsPanel();
        buildBlockedPanel();
        buildSearchPanel();
        buildEditProfilePanel(pfpPath, currentUsername, currentBio);
    
        cardPanel.add(friendsPanel, "FRIENDS");
        cardPanel.add(blockedPanel, "BLOCKED");
        cardPanel.add(searchPanel, "SEARCH");
        cardPanel.add(editProfilePanel, "EDITPROFILE");
    
        rightPanel.add(cardPanel, BorderLayout.CENTER);
    
        // Add button actions for switching between panels
        friendsButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "FRIENDS");
            refreshFriendsPanel();
        });
        blockedButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "BLOCKED");
            refreshBlockedPanel();
        });
        searchButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "SEARCH");
            clearSearchPanel();
        });
    
        // Action for editing profile
        editProfileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String resp = sendRequest("getUser", loggedInUser, loggedInUser);
                String[] userInfo = resp.split(" \\| ");
                showEditProfile(mainPanel, loggedInUser, userInfo[1], userInfo[2], userInfo[3]);
            }
        });
    
        // Add components to main panel
        mainPanel.add(chatScrollPane, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);
    
        // Bottom panel for message and refresh buttons
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        bottomPanel.setBackground(Color.decode("#F8F8FF"));
        JButton messageButton = new JButton("Message Someone");
        messageButton.setFont(new Font("Arial", Font.PLAIN, 16));
        messageButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(mainFrame, "Enter username to message:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                openChatWindow(loggedInUser, targetUser.trim());
            }
        });
    
        JButton refreshButton = new JButton("Refresh List");
        refreshButton.setFont(new Font("Arial", Font.PLAIN, 16));
        refreshButton.addActionListener(e -> updateChatList());
    
        bottomPanel.add(messageButton);
        bottomPanel.add(refreshButton);
    
        mainPanel.add(bottomPanel, BorderLayout.SOUTH);
    
        // Finalize the frame and show it
        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);
    }

    // Updates the chat list panel with new chats.
    private void updateChatList() {
        // Get the chat list from the server for the logged in user
        String response = sendRequest("getChatList", loggedInUser, "");
        String[] chatUsers = response.trim().split(" \\| ");
    
        // Collect the current users already displayed in the chat list
        ArrayList<String> currentUsers = new ArrayList<>();
        for (Component comp : chatListPanel.getComponents()) {
            if (comp instanceof JPanel && ((JPanel) comp).getClientProperty("targetUser") != null) {
                currentUsers.add((String) ((JPanel) comp).getClientProperty("targetUser"));
            }
        }
    
        // Check for any new users not in the current list and add them
        boolean addedNew = false;
        for (String user : chatUsers) {
            if (!user.trim().isEmpty() && !currentUsers.contains(user.trim())) {
                JPanel chatItem = createChatItemPanel(user.trim());
                chatListPanel.add(chatItem);
                addedNew = true; // Mark that a new user was added
            }
        }
    
        // If new users were added, refresh the chat list panel
        if (addedNew) {
            chatListPanel.revalidate();
            chatListPanel.repaint();
        }
    }

    // Creates a panel for an individual chat item.
    private JPanel createChatItemPanel(String targetUser) {
        // Create a new panel for the chat item
        JPanel chatItem = new JPanel(new BorderLayout());
        chatItem.putClientProperty("targetUser", targetUser); // Store the user info in the panel property
        chatItem.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80)); // Set max height for panel
        chatItem.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY)); // Add a bottom border
    
        // Add the profile pic to the left side of the panel
        JLabel profilePicLabel = new JLabel();
        ImageIcon profilePicIcon = new ImageIcon("Database/ProfilePicture/default.png"); // Use default pic
        Image profilePicImage = profilePicIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH); // Resize image
        profilePicLabel.setIcon(new ImageIcon(profilePicImage));
        profilePicLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Padding around the pic
    
        // Add the username in the center of the panel
        JLabel usernameLabel = new JLabel(targetUser);
        usernameLabel.setFont(new Font("Arial", Font.BOLD, 18)); // Bold font for username
    
        chatItem.add(profilePicLabel, BorderLayout.WEST);
        chatItem.add(usernameLabel, BorderLayout.CENTER);
    
        // Add mouse interactions for the chat item
        chatItem.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openChatWindow(loggedInUser, targetUser); // Open chat when clicked
            }
    
            @Override
            public void mouseEntered(MouseEvent e) {
                chatItem.setBackground(new Color(230, 230, 230)); // Highlight when hovered
            }
    
            @Override
            public void mouseExited(MouseEvent e) {
                chatItem.setBackground(Color.WHITE); // Reset background when mouse leaves
            }
        });
    
        return chatItem; // Return the completed chat item panel
    }
    
    //-- SIDE PANEL--:

    //-- SIDE PANEL-- FRIENDS PANEL:
    // Builds the friends panel UI and logic.
    private void buildFriendsPanel() {
        // Create the main panel for the Friends section
        friendsPanel = new JPanel(new BorderLayout());
    
        // Set up the model and list to display friends
        friendModel = new DefaultListModel<>();
        friendList = new JList<>(friendModel);
        friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Allow only single selection
    
        // Add an "Unfriend" button with initialy disabled state
        unfriendButton = new JButton("Unfriend");
        unfriendButton.setEnabled(false);
    
        // Action for "Unfriend" button
        unfriendButton.addActionListener(e -> {
            String selected = friendList.getSelectedValue();
            if (selected != null) { // Check if a friend is selected
                String r = sendRequest("unfriend", loggedInUser, selected); // Send unfriend request
                if (r.toLowerCase().contains("success")) {
                    refreshFriendsPanel(); // Refresh the friends list
                } else {
                    // Show error if unfriend failed
                    JOptionPane.showMessageDialog(friendsPanel, r, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    
        // Enable/disable the "Unfriend" button based on selection
        friendList.addListSelectionListener(e -> unfriendButton.setEnabled(friendList.getSelectedIndex() != -1));
    
        // Message to display when no friends are present
        noFriendsLabel = new JLabel("Your friend list is empty. Use the search feature to look for someone to friend.");
        noFriendsLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center align the label
    }

    // Refreshes the friends list and updates the panel.
    private void refreshFriendsPanel() {
        // Reset the friends panel and clear the friend model
        friendsPanel.removeAll();
        friendModel.clear();
    
        // Fetch the list of friends from the server
        String friendsStr = sendRequest("getFriend", loggedInUser, "");
    
        // Handle the case where no friends are found
        if (friendsStr.toLowerCase().contains("no friends")) {
            friendsPanel.add(centeredPanel(noFriendsLabel), BorderLayout.CENTER);
        } else {
            // Extract and process the friend list from the server response
            String listPart = friendsStr.substring(friendsStr.indexOf(':') + 1).trim();
            if (listPart.startsWith("[") && listPart.endsWith("]")) {
                listPart = listPart.substring(1, listPart.length() - 1).trim();
            }
    
            // If the list is not empty, populate the model and update the panel
            if (!listPart.isEmpty()) {
                String[] frnds = listPart.split(",");
                for (String f : frnds) {
                    friendModel.addElement(f.trim());
                }
                friendsPanel.add(new JScrollPane(friendList), BorderLayout.CENTER);
                friendsPanel.add(unfriendButton, BorderLayout.SOUTH);
            } else {
                friendsPanel.add(centeredPanel(noFriendsLabel), BorderLayout.CENTER);
            }
        }
    
        // Refresh the friends panel to reflect changes
        friendsPanel.revalidate();
        friendsPanel.repaint();
    }

    // Checks if a user is already a friend.
    private boolean isFriendOf(String user) {
        // Send a request to get the list of friends for the loged in user
        String friendsStr = sendRequest("getFriend", loggedInUser, "");
    
        // Check if the given user is in the list and return true or false
        return friendsStr.contains(user);
    }

    //-- SIDE PANEL -- BLOCKED PANEL:
    // Builds the blocked panel UI and logic.
    private void buildBlockedPanel() {
        // Set up the main panel for the Blocked section
        blockedPanel = new JPanel(new BorderLayout());
    
        // Create a model and list to display blocked users
        blockedModel = new DefaultListModel<>();
        blockedList = new JList<>(blockedModel);
        blockedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION); // Only allow selecting one user at a time
    
        // Label to show when the blocked list is empty
        noBlockedLabel = new JLabel("Your blocked list is empty. Use the search feature to look for someone to block.");
        noBlockedLabel.setHorizontalAlignment(SwingConstants.CENTER); // Center align the label
    
        // Add the "Unblock" button and disable it initially
        unblockButton = new JButton("Unblock");
        unblockButton.setEnabled(false);
    
        // Action for the "Unblock" button
        unblockButton.addActionListener(e -> {
            String selected = blockedList.getSelectedValue(); // Get the selected user
            if (selected != null) {
                // Send an unblock request for the selected user
                String r = sendRequest("unblock", loggedInUser, selected);
                if (r.toLowerCase().contains("success")) {
                    refreshBlockedPanel(); // Refresh the blocked list if successful
                } else {
                    // Show an error message if the unblock failed
                    JOptionPane.showMessageDialog(blockedPanel, r, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    
        // Enable or disable the "Unblock" button based on list selection
        blockedList.addListSelectionListener(e -> unblockButton.setEnabled(blockedList.getSelectedIndex() != -1));
    }

    // Refreshes the blocked list and updates the panel.
    private void refreshBlockedPanel() {
        // Remove all components from the blocked panel to reset it
        blockedPanel.removeAll();
    
        // Clear the blocked model to refresh the list of blocked users
        blockedModel.clear();
    
        // Get the list of blocked users from the server
        String blockedStr = sendRequest("getBlocked", loggedInUser, "");
    
        // Check if there are no blocked users
        if (blockedStr.toLowerCase().contains("not blocked")) {
            // Add a label showing no blocked users if list is empty
            blockedPanel.add(centeredPanel(noBlockedLabel), BorderLayout.CENTER);
        } else {
            // Extract the relevant part of the server responce
            String listPart = blockedStr.substring(blockedStr.indexOf(':') + 1).trim();
    
            // Check if the list part starts and ends with brackets
            if (listPart.startsWith("[") && listPart.endsWith("]")) {
                // Remove the square brackets from the list
                listPart = listPart.substring(1, listPart.length() - 1).trim();
            }
    
            // If the list is not empty, process the blocked users
            if (!listPart.isEmpty()) {
                // Split the list into individual blocked users
                String[] blkd = listPart.split(",");
                for (String b : blkd) {
                    // Add each blocked user to the blocked model
                    blockedModel.addElement(b.trim());
                }
    
                // Add the blocked list to the panel
                blockedPanel.add(new JScrollPane(blockedList), BorderLayout.CENTER);
    
                // Add the "Unblock" button at the bottom of the panel
                blockedPanel.add(unblockButton, BorderLayout.SOUTH);
            } else {
                // Show a label when there are no blocked users
                blockedPanel.add(centeredPanel(noBlockedLabel), BorderLayout.CENTER);
            }
        }
    
        // Revalidate the panel to apply updates to the UI
        blockedPanel.revalidate();
    
        // Repaint the panel to show the changes
        blockedPanel.repaint();
    }

    // Checks if a user is blocked.
    private boolean isBlockedUser(String user) {
        // Send a request to get the list of blocked users for the loged-in user
        String blockedStr = sendRequest("getBlocked", loggedInUser, "");
    
        // Check if the given user is in the blocked list and return true if found
        return blockedStr.contains(user);
    }
    
    
    //-- SIDE PANEL -- EDIT PROFILE:
    // Styles the edit profile Panel
    private void showEditProfile(JPanel sidePanel, String username, String password, String profilePicturePath, String bio) {
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
            String response = sendRequest("editUser", username, data);

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
    
    // Builds the edit profile panel with user details.
    private void buildEditProfilePanel(String pfpPath, String username, String bio) {
        // Create a new panel for editing the profile
        editProfilePanel = new JPanel();
        editProfilePanel.setLayout(new BoxLayout(editProfilePanel, BoxLayout.Y_AXIS)); // Set layout for stacking elements vertically
        editProfilePanel.setBackground(Color.decode("#F8F8FF")); // Set background color
        editProfilePanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20)); // Add padding around the panel
    
        // Add profile picture display
        editProfilePicLabel = new JLabel();
        editProfilePicLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Center align the label
        ImageIcon pfpIcon = new ImageIcon(pfpPath); // Load the profile picture
        Image pfpImg = pfpIcon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH); // Resize the picture
        editProfilePicLabel.setIcon(new ImageIcon(pfpImg)); // Set the icon for the label
    
        // Add username label
        editUsernameLabel = new JLabel("Username: " + username);
        editUsernameLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Center align the username
    
        // Add bio label
        editBioLabel = new JLabel("Bio: " + bio);
        editBioLabel.setAlignmentX(Component.CENTER_ALIGNMENT); // Center align the bio
    
        // Add components to the panel
        editProfilePanel.add(editProfilePicLabel);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        editProfilePanel.add(editUsernameLabel);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        editProfilePanel.add(editBioLabel);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0, 20))); // Add spacing
    
        // Create buttons for editing profile picture, bio, and password
        editPfpButton = new JButton("Change Profile Picture");
        editBioButton = new JButton("Change Bio");
        editPasswordButton = new JButton("Change Password");
    
        // Center align the buttons
        editPfpButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        editBioButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        editPasswordButton.setAlignmentX(Component.CENTER_ALIGNMENT);
    
        // Action for "Change Profile Picture" button
        editPfpButton.addActionListener(e -> {
            String newPfp = chooseImageFile(); // Prompt the user to select a new picture
            if (newPfp != null) {
                String info = String.format("%s,%s,%s,%s", username, "NOPASSCHANGE", newPfp, "NOBIOCHANGE");
                String response = sendRequest("editUser", loggedInUser, info); // Send request to update picture
                if (!response.toLowerCase().contains("success")) {
                    JOptionPane.showMessageDialog(editProfilePanel, response, "Error", JOptionPane.ERROR_MESSAGE); // Show error if update fails
                } else {
                    refreshEditProfilePanel(); // Refresh the profile panel
                }
            }
        });
    
        // Action for "Change Bio" button
        editBioButton.addActionListener(e -> {
            String newBio = JOptionPane.showInputDialog(editProfilePanel, "Enter new bio:"); // Prompt for new bio
            if (newBio != null) {
                String info = String.format("%s,%s,%s,%s", username, "NOPASSCHANGE", "NOPFPCHANGE", newBio);
                String response = sendRequest("editUser", loggedInUser, info); // Send request to update bio
                if (!response.toLowerCase().contains("success")) {
                    JOptionPane.showMessageDialog(editProfilePanel, response, "Error", JOptionPane.ERROR_MESSAGE); // Show error if update fails
                } else {
                    refreshEditProfilePanel(); // Refresh the profile panel
                }
            }
        });
    
        // Action for "Change Password" button
        editPasswordButton.addActionListener(e -> {
            String newPass = JOptionPane.showInputDialog(editProfilePanel, "Enter new password:"); // Prompt for new password
            if (newPass != null && newPass.length() >= 6) {
                String info = String.format("%s,%s,%s,%s", username, newPass, "NOPFPCHANGE", "NOBIOCHANGE");
                String response = sendRequest("editUser", loggedInUser, info); // Send request to update password
                if (!response.toLowerCase().contains("success")) {
                    JOptionPane.showMessageDialog(editProfilePanel, response, "Error", JOptionPane.ERROR_MESSAGE); // Show error if update fails
                } else {
                    refreshEditProfilePanel(); // Refresh the profile panel
                }
            } else {
                JOptionPane.showMessageDialog(editProfilePanel, "Password must be at least 6 characters", "Error", JOptionPane.ERROR_MESSAGE); // Show error if password is too short
            }
        });
    
        // Add the buttons to the panel
        editProfilePanel.add(editPfpButton);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        editProfilePanel.add(editBioButton);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0, 10))); // Add spacing
        editProfilePanel.add(editPasswordButton);
    }
    
    // Refreshes the edit profile panel with updated data.
    private void refreshEditProfilePanel() {
        // Clear all components from the edit profile panel to reset it
        editProfilePanel.removeAll();
    
        // Send a request to get updated user info from the server
        String resp = sendRequest("getUser", loggedInUser, loggedInUser);
    
        // Split the server responce into different parts
        String[] userInfo = resp.split(" \\| ");
    
        // Extract the profile picture path, username, and bio from the response
        String pfpPath = userInfo[2].trim();
        String username = userInfo[0].trim();
        String bio = userInfo[3].trim();
    
        // Rebuild the edit profile panel with the updated information
        buildEditProfilePanel(pfpPath, username, bio);
    
        // Revalidate the panel to apply changes
        editProfilePanel.revalidate();
    
        // Repaint the panel to reflect the updates visually
        editProfilePanel.repaint();
    }

    //-- SIDE PANEL -- SEARCH PROFILE:
    // Builds the search panel for user lookup. 
    private void buildSearchPanel() {
        // Create main search panel with a border layout
        searchPanel = new JPanel(new BorderLayout());
    
        // Top panel with search field and button
        JPanel topPanel = new JPanel(new FlowLayout());
        searchField = new JTextField(15); // Input field for search
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e -> doSearch()); // Action to perform search
        topPanel.add(searchField);
        topPanel.add(searchBtn);
    
        // Panel for displaying search results
        searchResultPanel = new JPanel(new BorderLayout());
        searchResultPanel.setBackground(Color.decode("#F8F8FF")); // Set background color
    
        // Add components to the main search panel
        searchPanel.add(topPanel, BorderLayout.NORTH);
        searchPanel.add(searchResultPanel, BorderLayout.CENTER);
    }    

    // Clears the search panel and results.
    private void clearSearchPanel() {
        // Clear the search field text
        searchField.setText("");
    
        // Remove all components from the results panel
        searchResultPanel.removeAll();
    
        // Update the results panel to apply changes
        searchResultPanel.revalidate();
    
        // Repaint the panel to refresh the display
        searchResultPanel.repaint();
    }

    // Executes a search for a specific username.
    private void doSearch() {
        // Get the username to search from the search field
        String targetUser = searchField.getText().trim();
    
        // Check if the search field is empty
        if (targetUser.isEmpty()) {
            JOptionPane.showMessageDialog(searchPanel, "Enter a username to search.", "Input Error", JOptionPane.ERROR_MESSAGE);
            return;
        }
    
        // Send a request to fetch user information
        String resp = sendRequest("getUser", loggedInUser, targetUser);
    
        // Clear any existing search results
        searchResultPanel.removeAll();
    
        // Handle cases where the user is not found or there is an error
        if (resp.toLowerCase().contains("not found") || resp.toLowerCase().contains("error")) {
            searchResultPanel.add(centeredPanel(new JLabel("User Not Found")), BorderLayout.CENTER);
        } else {
            // Parse the response to extract user information
            String[] userInfo = resp.split(" \\| ");
            String foundUser = userInfo[0].trim();
            String pfpPath = userInfo[2].trim();
            String bio = userInfo[3].trim();
    
            // Check if the user is already a friend or blocked
            boolean isFriend = isFriendOf(foundUser);
            boolean isBlocked = isBlockedUser(foundUser);
    
            // Create a panel to display user details
            JPanel userPanel = new JPanel();
            userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));
            userPanel.setBackground(Color.decode("#F8F8FF"));
    
            // Add the user's profile picture
            JLabel pfpLabel = new JLabel();
            pfpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            ImageIcon pfpIcon = new ImageIcon(pfpPath);
            Image pfpImg = pfpIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
            pfpLabel.setIcon(new ImageIcon(pfpImg));
    
            // Add the user's username and bio
            JLabel unameLabel = new JLabel("Username: " + foundUser);
            unameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            unameLabel.setFont(new Font("Arial", Font.BOLD, 16));
            JLabel bioLabel = new JLabel("Bio: " + bio);
            bioLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    
            // Add components to the user panel
            userPanel.add(pfpLabel);
            userPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            userPanel.add(unameLabel);
            userPanel.add(Box.createRigidArea(new Dimension(0, 10)));
            userPanel.add(bioLabel);
            userPanel.add(Box.createRigidArea(new Dimension(0, 20)));
    
            // Create buttons for friend/unfriend and block/unblock actions
            JPanel btnPanel = new JPanel(new FlowLayout());
            btnPanel.setBackground(Color.decode("#F8F8FF"));
            JButton friendUnfriendBtn = new JButton(isFriend ? "Unfriend" : "Friend");
            friendUnfriendBtn.addActionListener(e -> {
                String action = isFriend ? "unfriend" : "friendUser";
                String r = sendRequest(action, loggedInUser, foundUser);
                if (r.toLowerCase().contains("success")) {
                    JOptionPane.showMessageDialog(searchPanel, (isFriend ? "Unfriended" : "Friended") + " successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(searchPanel, r, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
    
            JButton blockUnblockBtn = new JButton(isBlocked ? "Unblock" : "Block");
            blockUnblockBtn.addActionListener(e -> {
                String action = isBlocked ? "unblock" : "blockUser";
                String r = sendRequest(action, loggedInUser, foundUser);
                if (r.toLowerCase().contains("success")) {
                    JOptionPane.showMessageDialog(searchPanel, (isBlocked ? "Unblocked" : "Blocked") + " successfully!", "Success", JOptionPane.INFORMATION_MESSAGE);
                } else {
                    JOptionPane.showMessageDialog(searchPanel, r, "Error", JOptionPane.ERROR_MESSAGE);
                }
            });
    
            // Add buttons to the button panel and user panel
            btnPanel.add(friendUnfriendBtn);
            btnPanel.add(blockUnblockBtn);
            userPanel.add(btnPanel);
    
            // Display the user panel in the search results
            searchResultPanel.add(userPanel, BorderLayout.CENTER);
        }
    
        // Refresh the search results panel to show updates
        searchResultPanel.revalidate();
        searchResultPanel.repaint();
    }

    //-- IMAGE FILE CHOOSER --
    // Opens a file chooser to select an image.
    private String chooseImageFile() {
        // Open a file chooser dialog
        JFileChooser fileChooser = new JFileChooser();
    
        // Set a filter to only accept image files
        fileChooser.setFileFilter(new FileFilter() {
            public boolean accept(File file) {
                // Allow directories and image files with certain extensions
                if (file.isDirectory()) {
                    return true;
                }
                String name = file.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
            }
            public String getDescription() {
                return "Image Files (*.jpg, *.jpeg, *.png, *.gif)"; // Description for the file filter
            }
        });
    
        // Show the file dialog and get user selection
        int result = fileChooser.showOpenDialog(mainFrame);
        if (result == JFileChooser.APPROVE_OPTION) {
            File selectedFile = fileChooser.getSelectedFile(); // Get the selected file
            try {
                // Copy the file to the destination folder
                File destination = new File("Database/Files/" + loggedInUser + selectedFile.getName());
                if (!destination.getParentFile().exists()) {
                    destination.getParentFile().mkdirs(); // Create directories if needed
                }
                Files.copy(selectedFile.toPath(), destination.toPath());
                return "Database/Files/" + loggedInUser + selectedFile.getName(); // Return the path to the copied file
            } catch (IOException ioException) {
                // Show an error if file copying fails
                JOptionPane.showMessageDialog(mainFrame,
                        "Error saving file: " + ioException.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        return null; 
    }
    
    //-- MAIN CENTRAL PANEL --
    // Centers a component inside a panel.
    private JPanel centeredPanel(JComponent comp) {
        // Create a new panel with GridBagLayout to center its content
        JPanel p = new JPanel(new GridBagLayout());
    
        // Set the background color for the panel
        p.setBackground(Color.decode("#F8F8FF"));
    
        // Add the provided component to the center of the panel
        p.add(comp);
    
        return p;
    }
    

    //-- CHAT WINDOW--
    // Opens a chat window for a specific user.
    private void openChatWindow(String username, String targetUser) {
        // Create a new frame for the chat window
        JFrame chatFrame = new JFrame("Chat with " + targetUser);
        chatFrame.setSize(500, 600); // Set the size of the frame
        chatFrame.setLocationRelativeTo(null); // Center the window on the screen
    
        JPanel panel = new JPanel(new BorderLayout()); // Main panel with a border layout
    
        // Panel for displaying chat messages
        JPanel chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS)); // Stack messages vertically
    
        JScrollPane scrollPane = new JScrollPane(chatPanel); // Add scrolling for the chat panel
    
        // Input field for typing messages
        JTextField messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14)); // Set font for the text field
    
        // Button for sending messages
        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.PLAIN, 14)); // Set font for the button
        sendButton.addActionListener(e -> {
            String message = messageField.getText().trim(); // Get the typed message
            if (!message.isEmpty()) {
                String data = String.format("%s | %s", targetUser, message); // Format the message data
                String response = sendRequest("sendMessage", username, data); // Send the message to the server
    
                if (response.toLowerCase().contains("error")) {
                    // Show an error if the message fails to send
                    JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    // Add the sent message to the chat panel
                    chatPanel.add(createMessageBubble("Me", message, chatPanel, Integer.parseInt(response), targetUser, username));
                    chatPanel.revalidate(); // Refresh the chat panel
    
                    // Scroll to the bottom of the chat
                    JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
                    SwingUtilities.invokeLater(() -> verticalScrollBar.setValue(verticalScrollBar.getMaximum()));
    
                    // Clear the input field after sending
                    messageField.setText("");
                }
            }
        });
    
        // Button for uploading images
        JButton uploadImg = new JButton("IMG");
        JFileChooser fileChooser = new JFileChooser(); // File chooser for selecting images
        fileChooser.setFileFilter(new FileFilter() {
            public boolean accept(File file) {
                // Allow directories and specific image file formats
                if (file.isDirectory()) {
                    return true;
                }
                String name = file.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png");
            }
            public String getDescription() {
                return "Image Files (*.jpg, *.jpeg, *.png)";
            }
        });
    
        // Action for uploading an image
        uploadImg.addActionListener(e -> {
            int file = fileChooser.showOpenDialog(fileChooser); // Open file chooser dialog
            if (file == fileChooser.APPROVE_OPTION) {
                File selectedFile = fileChooser.getSelectedFile(); // Get selected file
                try {
                    // Copy the file to the destination folder
                    File destination = new File("Database/Files/" + username + targetUser + selectedFile.getName());
                    if (!destination.getParentFile().exists()) {
                        destination.getParentFile().mkdirs();
                    }
                    Files.copy(selectedFile.toPath(), destination.toPath());
    
                    // Send the file path as a message
                    String data = String.format("%s | #FILE#:%s", targetUser, "Database/Files/" + username + targetUser + selectedFile.getName());
                    String response = sendRequest("sendMessage", username, data);
    
                    if (response.toLowerCase().contains("error")) {
                        JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                    } else {
                        // Display the uploaded image in the chat
                        ImageIcon tempImage = new ImageIcon("Database/Files/" + username + targetUser + selectedFile.getName());
                        Image scaledImage = tempImage.getImage().getScaledInstance(tempImage.getIconWidth() / 6, tempImage.getIconHeight() / 6, Image.SCALE_SMOOTH);
                        JLabel imageLabel = new JLabel(new ImageIcon(scaledImage));
                        chatPanel.add(imageLabel);
                        chatPanel.revalidate();
                    }
                } catch (IOException ioException) {
                    // Show an error if the file cannot be saved
                    JOptionPane.showMessageDialog(fileChooser, "Error saving file: " + ioException.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    
        // Setup the input and upload panel
        chatPanel.setBackground(Color.decode("#F8F8FF"));
        JPanel inputPanel = new JPanel(new BorderLayout());
        JPanel uploadSend = new JPanel(new GridLayout(0, 2));
        uploadSend.add(sendButton);
        uploadSend.add(uploadImg);
        inputPanel.add(messageField, BorderLayout.CENTER);
        inputPanel.add(uploadSend, BorderLayout.EAST);
        panel.add(scrollPane, BorderLayout.CENTER);
        panel.add(inputPanel, BorderLayout.SOUTH);
    
        // Add the panel to the frame and make it visible
        chatFrame.add(panel);
        chatFrame.setVisible(true);
    
        // Load existing chat messages
        loadChatHistory(username, targetUser, chatPanel);
    
        // Timer to fetch new messages every 3 seconds
        Timer timer = new Timer(3000, e -> loadChatHistory(username, targetUser, chatPanel));
        timer.start();
    
        // Stop the timer when the chat window is closed
        chatFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                timer.stop();
            }
        });
    }
    
    // Creates a message bubble for displaying a message.
    private JPanel createMessageBubble(String sender, String messageText, JPanel chatPanel, int msgId, String receiver, String sendDel) {
        // Create a panel for the message bubble
        JPanel messageBubble = new JPanel();
        messageBubble.setLayout(new BorderLayout());
        messageBubble.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10)); // Add padding to the bubble
        messageBubble.setOpaque(false); // Make the panel transparent
    
        // Set up the text area for the message content
        JTextArea messageArea = new JTextArea(sender + ": " + messageText);
        messageArea.setFont(new Font("Arial", Font.PLAIN, 12)); // Use a compact font size
        messageArea.setEditable(true);
        messageArea.setWrapStyleWord(true);
        messageArea.setLineWrap(true);
        messageArea.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10)); // Add padding inside the bubble
    
        // Set background color based on the sender
        if (sender.equals("Me")) {
            messageArea.setBackground(new Color(220, 248, 198)); // Light green for the user
        } else {
            messageArea.setBackground(new Color(220, 220, 220)); // Light gray for others
        }
    
        // Set text and caret color
        messageArea.setForeground(Color.BLACK);
        messageArea.setCaretColor(Color.BLACK);
    
        // Adjust the bubble's width dynamically based on message length
        int textLength = messageText.length();
        int bubbleWidth = Math.min(220, Math.max(50, textLength * 7));
        messageArea.setPreferredSize(new Dimension(bubbleWidth, messageArea.getPreferredSize().height));
    
        // Create the "Delete" button for the message
        JButton deleteButton = new JButton("X");
        deleteButton.setFont(new Font("Arial", Font.BOLD, 10));
        deleteButton.setMargin(new Insets(1, 1, 1, 1));
        deleteButton.addActionListener(e -> {
            // Send a delete request to the server and remove the bubble if successful
            String data = String.format("%s | %s", receiver, msgId);
            String delResponse = sendRequest("deleteMessage", sendDel, data);
            if (delResponse.contains("succesfully")) {
                chatPanel.remove(messageBubble);
                chatPanel.revalidate();
            } else {
                JOptionPane.showMessageDialog(chatPanel, "Error, try again", "Error", JOptionPane.ERROR_MESSAGE);
            }
    
        });
    
        // Create the "Edit" button for the message
        JButton editButton = new JButton("Edit");
        editButton.setFont(new Font("Arial", Font.BOLD, 10));
        editButton.setMargin(new Insets(1, 1, 1, 1));
        editButton.addActionListener(e -> {
            // Prompt the user to edit their message and send the updated message to the server
            String newMessage = JOptionPane.showInputDialog("Edit your message:", messageText);
            if (newMessage != null && !newMessage.trim().isEmpty()) {
                String data = String.format("%s | %s | %s", receiver, msgId, newMessage.trim());
                String reqString = sendRequest("editMessage", sendDel, data);
    
                // Update the UI if the edit was successful
                if (reqString.equals("Message Edited")) {
                    messageArea.setText("Me" + ": " + newMessage.trim());
                } else {
                    JOptionPane.showMessageDialog(chatPanel, "Error, try again", "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
    
        // Create a panel to hold the edit and delete buttons vertically
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setOpaque(false);
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
    
        // Add the message text and button panel to the bubble
        JPanel bubbleContent = new JPanel();
        bubbleContent.setLayout(new BorderLayout());
        bubbleContent.add(messageArea, BorderLayout.CENTER);
    
        // Add the button panel to the right for messages sent by the user
        if (sender.toLowerCase().equals("me")) {
            bubbleContent.add(buttonPanel, BorderLayout.EAST);
        }
        bubbleContent.setOpaque(false);
    
        messageBubble.add(bubbleContent);
    
        return messageBubble;
    }
    
    // Loads chat history and displays messages in the chat panel.
    private void loadChatHistory(String username, String targetUser, JPanel chatPanel) {
        // Prepare data to fetch messages for the target user
        String data = targetUser;
    
        // Send a request to get the chat history
        String response = sendRequest("getMessage", username, data);
    
        // Handle cases where there is an error in the response
        if (response.toLowerCase().contains("error")) {
            // Do nothing or display an error message if needed
        } else {
            // Clear the chat panel to load the latest messages
            chatPanel.removeAll();
    
            // Split the response into individual messages
            String[] messages = response.split(" ; ");
            for (String msg : messages) {
                // Parse each message to extract the text and its type
                String[] parts = msg.split("-\\d+#");
                int lastDashIndex = msg.lastIndexOf('-'); // Find the last occurrence of '-'
                int firstHashIndex = msg.indexOf('#', lastDashIndex); // Find the first occurrence of '#' after the last '-'
                
                // Extract the message ID between the last '-' and the first '#'
                String number = msg.substring(lastDashIndex + 1, firstHashIndex);
                if (parts.length >= 2) {
                    String messageText = parts[0];
                    String messageType = parts[1].replace("#", ""); // Determine the type of message (sent/received)
    
                    // Handle messages sent by the user
                    if (messageType.equals("S")) {
                        if (msg.contains("#FILE#:")) {
                            // Process file messages (e.g., images) sent by the user
                            String imgPath = msg.replace("#FILE#:", "").trim();
                            if (imgPath.matches(".*-\\d+#S#")) {
                                int index = imgPath.lastIndexOf('-');
                                if (index != -1) {
                                    imgPath = imgPath.substring(0, index);
                                }
                            }
                            
                            // Load and display the image in the chat
                            ImageIcon tempImage = new ImageIcon(imgPath);
                            Image scaledImage = tempImage.getImage().getScaledInstance(tempImage.getIconWidth() / 6, tempImage.getIconHeight() / 6, Image.SCALE_SMOOTH);
                            JLabel imageLabel = new JLabel("Me:", JLabel.LEFT);
                            imageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            JPanel imgBubbleContent = new JPanel(new BorderLayout());
                            imgBubbleContent.add(imageLabel, BorderLayout.WEST);
                            imgBubbleContent.add(new JLabel(new ImageIcon(scaledImage)), BorderLayout.CENTER);
                            imgBubbleContent.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                            chatPanel.add(imgBubbleContent);
                        } else {
                            // Add regular text messages sent by the user
                            chatPanel.add(createMessageBubble("Me", messageText, chatPanel, Integer.parseInt(number), targetUser, username));
                            chatPanel.revalidate();
                        }
                    } else if (messageType.equals("R")) {
                        // Handle messages received from the target user
                        if (msg.contains("#FILE#:")) {
                            // Process file messages received from the user
                            String imgPath = msg.replace("#FILE#:", "").trim();
                            if (imgPath.matches(".*-\\d+#R#")) {
                                int index = imgPath.lastIndexOf('-');
                                if (index != -1) {
                                    imgPath = imgPath.substring(0, index);
                                }
                            }
                            
                            // Load and display the image in the chat
                            ImageIcon tempImage = new ImageIcon(imgPath);
                            Image scaledImage = tempImage.getImage().getScaledInstance(tempImage.getIconWidth() / 6, tempImage.getIconHeight() / 6, Image.SCALE_SMOOTH);
                            JLabel imageLabel = new JLabel(targetUser, JLabel.LEFT);
                            imageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            JPanel imgBubbleContent = new JPanel(new BorderLayout());
                            imgBubbleContent.add(imageLabel, BorderLayout.WEST);
                            imgBubbleContent.add(new JLabel(new ImageIcon(scaledImage)), BorderLayout.CENTER);
                            imgBubbleContent.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                            chatPanel.add(imgBubbleContent);
                        } else {
                            // Add regular text messages received from the user
                            chatPanel.add(createMessageBubble(targetUser, messageText, chatPanel, Integer.parseInt(number), targetUser, username));
                        }
                    }
                }
            }
        }
    }
}

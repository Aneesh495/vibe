import java.awt.event.*;
import java.io.*;
import java.net.*;
import java.nio.file.Files;
import javax.swing.*;
import java.awt.*;
import javax.swing.filechooser.FileFilter;
import java.util.ArrayList;

public class SocialClientGUI {
    private Socket socket;
    private PrintWriter writer;
    private BufferedReader reader;

    private String loggedInUser;

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

    private JLabel editProfilePicLabel, editUsernameLabel, editBioLabel;
    private JButton editPfpButton, editBioButton, editPasswordButton;

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

    public static void main(String[] args) {
        SocialClientGUI client = new SocialClientGUI("127.0.0.1", 4242);
        if (client.socket == null) {
            JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Connection Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }
        SwingUtilities.invokeLater(() -> client.createInitialGUI());
    }

    private void createInitialGUI() {
        JFrame frame = new JFrame("Social Client");
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel logoLabel = new JLabel();
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon logoIcon = new ImageIcon("Database/logo.jpg");
        Image logoImage = logoIcon.getImage().getScaledInstance(200, 100, Image.SCALE_SMOOTH);
        logoLabel.setIcon(new ImageIcon(logoImage));
        mainPanel.add(logoLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

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

        JButton loginButton = new JButton("Login");
        loginButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(loginButton);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

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

        frame.add(mainPanel);
        frame.setVisible(true);

        passField.addActionListener(e -> loginButton.doClick());

        loginButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            performLogin(username, password, frame);
        });

        createAccountButton.addActionListener(e -> {
            frame.dispose();
            performCreateAccount();
        });
    }

    private void performLogin(String username, String password, JFrame frame) {
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Username and password cannot be empty.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

        String loginResponse = sendRequest("loginWithPassword", username, password);

        if ("Login successful".equalsIgnoreCase(loginResponse.trim())) {
            JOptionPane.showMessageDialog(frame, "Login Successful!", "Welcome",
                    JOptionPane.INFORMATION_MESSAGE);
            frame.dispose();
            this.loggedInUser = username;
            showChatList();
        } else if (loginResponse.contains("Incorrect Password") ||
                loginResponse.contains("Input Error")) {
            JOptionPane.showMessageDialog(frame, "Incorrect Username or Password. Please try again.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        } else if (loginResponse.contains("User not found") ||
                loginResponse.contains("User Error")) {
            JOptionPane.showMessageDialog(frame, "User not found. Please create an account.", "Login Failed",
                    JOptionPane.ERROR_MESSAGE);
        } else {
            JOptionPane.showMessageDialog(frame, "Unexpected server response: " + loginResponse, "Server Error",
                    JOptionPane.ERROR_MESSAGE);
        }
    }

    private void performCreateAccount() {
        JFrame frame = new JFrame("Create Account");
        frame.setSize(400, 500);
        frame.setLocationRelativeTo(null);
        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        JLabel titleLabel = new JLabel("Create Account");
        titleLabel.setFont(new Font("Arial", Font.BOLD, 20));
        titleLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        mainPanel.add(titleLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

        JLabel userImageLabel = new JLabel();
        userImageLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon userIcon = new ImageIcon("Database/logo.jpg");
        Image userImage = userIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
        userImageLabel.setIcon(new ImageIcon(userImage));
        mainPanel.add(userImageLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 10)));

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

        passField.addActionListener(e -> createButton.doClick());

        createButton.addActionListener(e -> {
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

            String data = String.format("%s | %s | %s | %s", username, password, "Database/ProfilePicture/default.png",
                    "default bio");
            String createResponse = sendRequest("createUser", "", data);

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

        cancelButton.addActionListener(e -> {
            frame.dispose();
            createInitialGUI();
        });
    }

    private void showChatList() {
        mainFrame = new JFrame("Chats - " + loggedInUser);
        mainFrame.setSize(1000, 700);
        mainFrame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        mainFrame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());

        chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        JScrollPane chatScrollPane = new JScrollPane(chatListPanel);

        updateChatList();

        JPanel rightPanel = new JPanel(new BorderLayout());

        JPanel userInfoPanel = new JPanel();
        userInfoPanel.setLayout(new BoxLayout(userInfoPanel, BoxLayout.Y_AXIS));
        userInfoPanel.setBackground(Color.decode("#F8F8FF"));
        userInfoPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        String resp = sendRequest("getUser", loggedInUser, loggedInUser);
        String[] userInfo = resp.split(" \\| ");
        String pfpPath = userInfo[2].trim();
        String currentUsername = userInfo[0].trim();
        String currentBio = userInfo[3].trim();

        JLabel userPfpLabel = new JLabel();
        userPfpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon userPfpIcon = new ImageIcon(pfpPath);
        Image userPfpImg = userPfpIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
        userPfpLabel.setIcon(new ImageIcon(userPfpImg));

        JLabel userNameLabel = new JLabel(currentUsername);
        userNameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        userNameLabel.setFont(new Font("Arial", Font.BOLD, 16));

        JButton editProfileButton = new JButton("Edit Profile");
        editProfileButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        userInfoPanel.add(userPfpLabel);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0,10)));
        userInfoPanel.add(userNameLabel);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0,10)));
        userInfoPanel.add(editProfileButton);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0,10)));

        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);
        userInfoPanel.add(sep);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0,10)));

        JPanel topButtonsPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        JButton friendsButton = new JButton("Friends");
        JButton blockedButton = new JButton("Blocked");
        JButton searchButton = new JButton("Search");
        topButtonsPanel.add(friendsButton);
        topButtonsPanel.add(blockedButton);
        topButtonsPanel.add(searchButton);

        userInfoPanel.add(topButtonsPanel);
        rightPanel.add(userInfoPanel, BorderLayout.NORTH);

        cardPanel = new JPanel(new CardLayout());

        buildFriendsPanel();
        buildBlockedPanel();
        buildSearchPanel();
        buildEditProfilePanel(pfpPath, currentUsername, currentBio);

        cardPanel.add(friendsPanel, "FRIENDS");
        cardPanel.add(blockedPanel, "BLOCKED");
        cardPanel.add(searchPanel, "SEARCH");
        cardPanel.add(editProfilePanel, "EDITPROFILE");

        rightPanel.add(cardPanel, BorderLayout.CENTER);

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

        editProfileButton.addActionListener(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                String resp = sendRequest("getUser", loggedInUser, loggedInUser);
                String[] userInfo = resp.split(" \\| ");
                showEditProfile(mainPanel, loggedInUser,userInfo[1],userInfo[2],userInfo[3]);
            }
        });

        mainPanel.add(chatScrollPane, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

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

        mainFrame.add(mainPanel);
        mainFrame.setVisible(true);
    }

    private void updateChatList() {
        String response = sendRequest("getChatList", loggedInUser, "");
        String[] chatUsers = response.trim().split(" \\| ");

        ArrayList<String> currentUsers = new ArrayList<>();
        for (Component comp : chatListPanel.getComponents()) {
            if (comp instanceof JPanel && ((JPanel)comp).getClientProperty("targetUser")!=null) {
                currentUsers.add((String)((JPanel)comp).getClientProperty("targetUser"));
            }
        }

        boolean addedNew = false;
        for (String user : chatUsers) {
            if (!user.trim().isEmpty() && !currentUsers.contains(user.trim())) {
                JPanel chatItem = createChatItemPanel(user.trim());
                chatListPanel.add(chatItem);
                addedNew = true;
            }
        }

        if (addedNew) {
            chatListPanel.revalidate();
            chatListPanel.repaint();
        }
    }

    private JPanel createChatItemPanel(String targetUser) {
        JPanel chatItem = new JPanel(new BorderLayout());
        chatItem.putClientProperty("targetUser", targetUser);
        chatItem.setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
        chatItem.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

        JLabel profilePicLabel = new JLabel();
        ImageIcon profilePicIcon = new ImageIcon("Database/ProfilePicture/default.png");
        Image profilePicImage = profilePicIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
        profilePicLabel.setIcon(new ImageIcon(profilePicImage));
        profilePicLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

        JLabel usernameLabel = new JLabel(targetUser);
        usernameLabel.setFont(new Font("Arial", Font.BOLD, 18));

        chatItem.add(profilePicLabel, BorderLayout.WEST);
        chatItem.add(usernameLabel, BorderLayout.CENTER);

        chatItem.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                openChatWindow(loggedInUser, targetUser);
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                chatItem.setBackground(new Color(230,230,230));
            }

            @Override
            public void mouseExited(MouseEvent e) {
                chatItem.setBackground(Color.WHITE);
            }
        });
        return chatItem;
    }

    private void buildFriendsPanel() {
        friendsPanel = new JPanel(new BorderLayout());
        friendModel = new DefaultListModel<>();
        friendList = new JList<>(friendModel);
        friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        unfriendButton = new JButton("Unfriend");
        unfriendButton.setEnabled(false);
        unfriendButton.addActionListener(e-> {
            String selected = friendList.getSelectedValue();
            if(selected!=null){
                String r = sendRequest("unfriend", loggedInUser, selected);
                if(r.toLowerCase().contains("success")){
                    refreshFriendsPanel();
                }else{
                    JOptionPane.showMessageDialog(friendsPanel, r, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });
        friendList.addListSelectionListener(e -> unfriendButton.setEnabled(friendList.getSelectedIndex() != -1));
        noFriendsLabel = new JLabel("Your friend list is empty. Use the search feature to look for someone to friend.");
        noFriendsLabel.setHorizontalAlignment(SwingConstants.CENTER);
    }

    private void refreshFriendsPanel() {
        friendsPanel.removeAll();
        friendModel.clear();

        String friendsStr = sendRequest("getFriend", loggedInUser, "");
        if (friendsStr.toLowerCase().contains("no friends")) {
            friendsPanel.add(centeredPanel(noFriendsLabel), BorderLayout.CENTER);
        } else {
            String listPart = friendsStr.substring(friendsStr.indexOf(':')+1).trim();
            if (listPart.startsWith("[") && listPart.endsWith("]")) {
                listPart = listPart.substring(1, listPart.length()-1).trim();
            }
            if(!listPart.isEmpty()){
                String[] frnds = listPart.split(",");
                for(String f: frnds){
                    friendModel.addElement(f.trim());
                }
                friendsPanel.add(new JScrollPane(friendList), BorderLayout.CENTER);
                friendsPanel.add(unfriendButton, BorderLayout.SOUTH);
            } else {
                friendsPanel.add(centeredPanel(noFriendsLabel), BorderLayout.CENTER);
            }
        }

        friendsPanel.revalidate();
        friendsPanel.repaint();
    }

    private void buildBlockedPanel() {
        blockedPanel = new JPanel(new BorderLayout());
        blockedModel = new DefaultListModel<>();
        blockedList = new JList<>(blockedModel);
        blockedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        noBlockedLabel = new JLabel("Your blocked list is empty. Use the search feature to look for someone to block.");
        noBlockedLabel.setHorizontalAlignment(SwingConstants.CENTER);

        unblockButton = new JButton("Unblock");
        unblockButton.setEnabled(false);
        unblockButton.addActionListener(e-> {
            String selected = blockedList.getSelectedValue();
            if(selected!=null){
                String r = sendRequest("unblock", loggedInUser, selected);
                if(r.toLowerCase().contains("success")){
                    refreshBlockedPanel();
                }else{
                    JOptionPane.showMessageDialog(blockedPanel, r, "Error", JOptionPane.ERROR_MESSAGE);
                }
            }
        });

        blockedList.addListSelectionListener(e -> unblockButton.setEnabled(blockedList.getSelectedIndex()!= -1));
    }

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

    private void refreshBlockedPanel() {
        blockedPanel.removeAll();
        blockedModel.clear();

        String blockedStr = sendRequest("getBlocked", loggedInUser, "");
        if(blockedStr.toLowerCase().contains("not blocked")){
            blockedPanel.add(centeredPanel(noBlockedLabel), BorderLayout.CENTER);
        }else{
            String listPart = blockedStr.substring(blockedStr.indexOf(':')+1).trim();
            if (listPart.startsWith("[") && listPart.endsWith("]")) {
                listPart = listPart.substring(1, listPart.length()-1).trim();
            }
            if(!listPart.isEmpty()){
                String[] blkd = listPart.split(",");
                for(String b : blkd){
                    blockedModel.addElement(b.trim());
                }
                blockedPanel.add(new JScrollPane(blockedList), BorderLayout.CENTER);
                blockedPanel.add(unblockButton, BorderLayout.SOUTH);
            } else {
                blockedPanel.add(centeredPanel(noBlockedLabel), BorderLayout.CENTER);
            }
        }

        blockedPanel.revalidate();
        blockedPanel.repaint();
    }

    private void buildSearchPanel() {
        searchPanel = new JPanel(new BorderLayout());
        JPanel topPanel = new JPanel(new FlowLayout());
        searchField = new JTextField(15);
        JButton searchBtn = new JButton("Search");
        searchBtn.addActionListener(e->doSearch());
        topPanel.add(searchField);
        topPanel.add(searchBtn);

        searchResultPanel = new JPanel(new BorderLayout());
        searchResultPanel.setBackground(Color.decode("#F8F8FF"));

        searchPanel.add(topPanel, BorderLayout.NORTH);
        searchPanel.add(searchResultPanel, BorderLayout.CENTER);
    }

    private void clearSearchPanel() {
        searchField.setText("");
        searchResultPanel.removeAll();
        searchResultPanel.revalidate();
        searchResultPanel.repaint();
    }

    private void doSearch() {
        String targetUser = searchField.getText().trim();
        if(targetUser.isEmpty()){
            JOptionPane.showMessageDialog(searchPanel,"Enter a username to search.","Input Error",JOptionPane.ERROR_MESSAGE);
            return;
        }
        String resp = sendRequest("getUser", loggedInUser, targetUser);
        searchResultPanel.removeAll();
        if(resp.toLowerCase().contains("not found")||resp.toLowerCase().contains("error")){
            searchResultPanel.add(centeredPanel(new JLabel("User Not Found")), BorderLayout.CENTER);
        }else {
            String[] userInfo = resp.split(" \\| ");
            String foundUser = userInfo[0].trim();
            String pfpPath = userInfo[2].trim();
            String bio = userInfo[3].trim();

            boolean isFriend = isFriendOf(foundUser);
            boolean isBlocked = isBlockedUser(foundUser);

            JPanel userPanel = new JPanel();
            userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));
            userPanel.setBackground(Color.decode("#F8F8FF"));

            JLabel pfpLabel = new JLabel();
            pfpLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            ImageIcon pfpIcon = new ImageIcon(pfpPath);
            Image pfpImg = pfpIcon.getImage().getScaledInstance(50,50,Image.SCALE_SMOOTH);
            pfpLabel.setIcon(new ImageIcon(pfpImg));

            JLabel unameLabel = new JLabel("Username: " + foundUser);
            unameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
            unameLabel.setFont(new Font("Arial", Font.BOLD,16));

            JLabel bioLabel = new JLabel("Bio: " + bio);
            bioLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

            userPanel.add(pfpLabel);
            userPanel.add(Box.createRigidArea(new Dimension(0,10)));
            userPanel.add(unameLabel);
            userPanel.add(Box.createRigidArea(new Dimension(0,10)));
            userPanel.add(bioLabel);
            userPanel.add(Box.createRigidArea(new Dimension(0,20)));

            JPanel btnPanel = new JPanel(new FlowLayout());
            btnPanel.setBackground(Color.decode("#F8F8FF"));
            JButton friendUnfriendBtn = new JButton(isFriend?"Unfriend":"Friend");
            friendUnfriendBtn.addActionListener(e->{
                String action = isFriend?"unfriend":"friendUser";
                String r = sendRequest(action, loggedInUser, foundUser);
                if(r.toLowerCase().contains("success")){
                    JOptionPane.showMessageDialog(searchPanel,(isFriend?"Unfriended":"Friended")+" successfully!","Success",JOptionPane.INFORMATION_MESSAGE);
                }else{
                    JOptionPane.showMessageDialog(searchPanel,r,"Error",JOptionPane.ERROR_MESSAGE);
                }
            });

            JButton blockUnblockBtn = new JButton(isBlocked?"Unblock":"Block");
            blockUnblockBtn.addActionListener(e->{
                String action = isBlocked?"unblock":"blockUser";
                String r = sendRequest(action, loggedInUser, foundUser);
                if(r.toLowerCase().contains("success")){
                    JOptionPane.showMessageDialog(searchPanel,(isBlocked?"Unblocked":"Blocked")+" successfully!","Success",JOptionPane.INFORMATION_MESSAGE);
                }else{
                    JOptionPane.showMessageDialog(searchPanel,r,"Error",JOptionPane.ERROR_MESSAGE);
                }
            });

            btnPanel.add(friendUnfriendBtn);
            btnPanel.add(blockUnblockBtn);
            userPanel.add(btnPanel);

            searchResultPanel.add(userPanel, BorderLayout.CENTER);
        }
        searchResultPanel.revalidate();
        searchResultPanel.repaint();
    }

    private boolean isFriendOf(String user) {
        String friendsStr = sendRequest("getFriend", loggedInUser, "");
        return friendsStr.contains(user);
    }

    private boolean isBlockedUser(String user) {
        String blockedStr = sendRequest("getBlocked", loggedInUser, "");
        return blockedStr.contains(user);
    }

    private void buildEditProfilePanel(String pfpPath, String username, String bio) {
        editProfilePanel = new JPanel();
        editProfilePanel.setLayout(new BoxLayout(editProfilePanel, BoxLayout.Y_AXIS));
        editProfilePanel.setBackground(Color.decode("#F8F8FF"));
        editProfilePanel.setBorder(BorderFactory.createEmptyBorder(20,20,20,20));

        editProfilePicLabel = new JLabel();
        editProfilePicLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon pfpIcon = new ImageIcon(pfpPath);
        Image pfpImg = pfpIcon.getImage().getScaledInstance(100,100, Image.SCALE_SMOOTH);
        editProfilePicLabel.setIcon(new ImageIcon(pfpImg));

        editUsernameLabel = new JLabel("Username: " + username);
        editUsernameLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        editBioLabel = new JLabel("Bio: " + bio);
        editBioLabel.setAlignmentX(Component.CENTER_ALIGNMENT);

        editProfilePanel.add(editProfilePicLabel);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0,10)));
        editProfilePanel.add(editUsernameLabel);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0,10)));
        editProfilePanel.add(editBioLabel);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0,20)));

        editPfpButton = new JButton("Change Profile Picture");
        editBioButton = new JButton("Change Bio");
        editPasswordButton = new JButton("Change Password");

        editPfpButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        editBioButton.setAlignmentX(Component.CENTER_ALIGNMENT);
        editPasswordButton.setAlignmentX(Component.CENTER_ALIGNMENT);

        editPfpButton.addActionListener(e->{
            String newPfp = chooseImageFile();
            if(newPfp!=null){
                String info = String.format("%s,%s,%s,%s", username, "NOPASSCHANGE", newPfp, "NOBIOCHANGE");
                String response = sendRequest("editUser", loggedInUser, info);
                if(!response.toLowerCase().contains("success")){
                    JOptionPane.showMessageDialog(editProfilePanel,response,"Error",JOptionPane.ERROR_MESSAGE);
                } else {
                    refreshEditProfilePanel();
                }
            }
        });

        editBioButton.addActionListener(e->{
            String newBio = JOptionPane.showInputDialog(editProfilePanel,"Enter new bio:");
            if(newBio!=null){
                String info = String.format("%s,%s,%s,%s", username, "NOPASSCHANGE", "NOPFPCHANGE", newBio);
                String response = sendRequest("editUser", loggedInUser, info);
                if(!response.toLowerCase().contains("success")){
                    JOptionPane.showMessageDialog(editProfilePanel,response,"Error",JOptionPane.ERROR_MESSAGE);
                } else {
                    refreshEditProfilePanel();
                }
            }
        });

        editPasswordButton.addActionListener(e->{
            String newPass = JOptionPane.showInputDialog(editProfilePanel,"Enter new password:");
            if(newPass!=null && newPass.length()>=6){
                String info = String.format("%s,%s,%s,%s", username, newPass, "NOPFPCHANGE","NOBIOCHANGE");
                String response = sendRequest("editUser", loggedInUser, info);
                if(!response.toLowerCase().contains("success")){
                    JOptionPane.showMessageDialog(editProfilePanel,response,"Error",JOptionPane.ERROR_MESSAGE);
                } else {
                    refreshEditProfilePanel();
                }
            }else{
                JOptionPane.showMessageDialog(editProfilePanel,"Password must be at least 6 characters","Error",JOptionPane.ERROR_MESSAGE);
            }
        });

        editProfilePanel.add(editPfpButton);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0,10)));
        editProfilePanel.add(editBioButton);
        editProfilePanel.add(Box.createRigidArea(new Dimension(0,10)));
        editProfilePanel.add(editPasswordButton);
    }

    private void refreshEditProfilePanel() {
        editProfilePanel.removeAll();
        String resp = sendRequest("getUser", loggedInUser, loggedInUser);
        String[] userInfo = resp.split(" \\| ");
        String pfpPath = userInfo[2].trim();
        String username = userInfo[0].trim();
        String bio = userInfo[3].trim();
        buildEditProfilePanel(pfpPath, username, bio);
        editProfilePanel.revalidate();
        editProfilePanel.repaint();
    }

    private String chooseImageFile() {
        JFileChooser fileChooser = new JFileChooser();
        fileChooser.setFileFilter(new FileFilter() {
            public boolean accept(File file){
                if (file.isDirectory()) {
                    return true;
                }
                String name = file.getName().toLowerCase();
                return name.endsWith(".jpg") || name.endsWith(".jpeg") || name.endsWith(".png") || name.endsWith(".gif");
            }
            public String getDescription() {
                return "Image Files (*.jpg, *.jpeg, *.png, *.gif)";
            }
        });
        int result = fileChooser.showOpenDialog(mainFrame);
        if(result == JFileChooser.APPROVE_OPTION){
            File selectedFile = fileChooser.getSelectedFile();
            try {
                File destination = new File("Database/Files/" + loggedInUser + selectedFile.getName());
                if (!destination.getParentFile().exists()) {
                    destination.getParentFile().mkdirs();
                }
                Files.copy(selectedFile.toPath(), destination.toPath());
                return "Database/Files/" + loggedInUser + selectedFile.getName();
            } catch (IOException ioException) {
                JOptionPane.showMessageDialog(mainFrame,
                        "Error saving file: " + ioException.getMessage(),
                        "Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        }
        return null;
    }

    private JPanel centeredPanel(JComponent comp) {
        JPanel p = new JPanel(new GridBagLayout());
        p.setBackground(Color.decode("#F8F8FF"));
        p.add(comp);
        return p;
    }

    private void openChatWindow(String username, String targetUser) {
        JFrame chatFrame = new JFrame("Chat with " + targetUser);
        chatFrame.setSize(500, 600);
        chatFrame.setLocationRelativeTo(null); // Center the window
        String filename = "";
        JPanel panel = new JPanel(new BorderLayout());

        // JTextArea chatArea = new JTextArea();

        JPanel chatPanel = new JPanel();
        chatPanel.setLayout(new BoxLayout(chatPanel, BoxLayout.Y_AXIS));




        JScrollPane scrollPane = new JScrollPane(chatPanel);

        JTextField messageField = new JTextField();
        messageField.setFont(new Font("Arial", Font.PLAIN, 14));

        JButton sendButton = new JButton("Send");
        sendButton.setFont(new Font("Arial", Font.PLAIN, 14));
        sendButton.addActionListener(e -> {
            String message = messageField.getText().trim();
            if (!message.isEmpty()) {
                // Send the message to the server or handle it
                
                String data = String.format("%s | %s", targetUser, message);
                String response = sendRequest("sendMessage", username, data);
        
                if (response.toLowerCase().contains("error")) {
                    JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                } else {
                    // Add the message bubble to the chat panel
                    chatPanel.add(createMessageBubble("Me", message, chatPanel,Integer.parseInt(response),targetUser,username));
                    // Update the panel
                    chatPanel.revalidate();
        
                    // Scroll to the bottom
                    JScrollBar verticalScrollBar = scrollPane.getVerticalScrollBar();
                    SwingUtilities.invokeLater(() -> {
                        verticalScrollBar.setValue(verticalScrollBar.getMaximum());
                    });
        
                    // Clear the input field
                    messageField.setText("");
                }
            }
        });

        JButton uploadImg = new JButton("IMG");
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
                        String response = sendRequest("sendMessage", username, data);
                        if (response.toLowerCase().contains("error")) {
                            JOptionPane.showMessageDialog(chatFrame, response, "Error", JOptionPane.ERROR_MESSAGE);
                        } else {
                            ImageIcon tempImage= new ImageIcon("Database/Files/" + username+ targetUser + selectedFile.getName());
                            Image scaledImage = tempImage.getImage().getScaledInstance(tempImage.getIconWidth()/6, tempImage.getIconHeight()/6, Image.SCALE_SMOOTH);
                            ImageIcon scaledIcon = new ImageIcon(scaledImage);
                            JLabel imageLabel = new JLabel("Me:", JLabel.LEFT);
                            imageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            JPanel imgBubbleContent = new JPanel(new BorderLayout());
                            imgBubbleContent.add(imageLabel,BorderLayout.WEST);
                            imgBubbleContent.add(new JLabel(scaledIcon),BorderLayout.CENTER);
                            imgBubbleContent.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                            chatPanel.add(imgBubbleContent); 

                            chatPanel.revalidate();
                            chatPanel.repaint();
                            chatFrame.revalidate();
                            chatFrame.repaint();
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
        scrollPane.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED);
        scrollPane.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER); // Horizontal scrolling can be avoided for cha
        chatFrame.add(panel);
        chatFrame.setVisible(true);

        // Load existing messages
        loadChatHistory(username, targetUser, chatPanel);

        // Start a timer to poll for new messages every 3 seconds
        Timer timer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadChatHistory(username, targetUser, chatPanel);
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

    private JPanel createMessageBubble(String sender, String messageText, JPanel chatPanel, int msgId, String reciever, String sendDel) {
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
        if(sender.equals("Me")){
            messageArea.setBackground(new Color(220, 248, 198)); // Background color confined to content
        }else{
            messageArea.setBackground(new Color(220,220,220));
        }
      
        messageArea.setForeground(Color.BLACK);
        messageArea.setCaretColor(Color.BLACK);
    
        // Dynamically set the preferred size based on the text length
        int textLength = messageText.length();
        int bubbleWidth = Math.min(220, Math.max(50, textLength * 7)); // Adjust bubble width based on text length
        messageArea.setPreferredSize(new Dimension(bubbleWidth, messageArea.getPreferredSize().height));
    
        // Add a rounded border to the message area itself
        // messageArea.setBorder(BorderFactory.createCompoundBorder(
        //         BorderFactory.createLineBorder(Color.GRAY, 1, true) // Rounded border
        //         //BorderFactory.createEmptyBorder(5, 5, 5, 5) // Padding inside the border
        // ));
    
  
        JButton deleteButton = new JButton("X"); // Compact delete button
        deleteButton.setFont(new Font("Arial", Font.BOLD, 10)); // Smaller font for delete button
        deleteButton.setMargin(new Insets(1, 1, 1, 1)); // Compact padding for the button
        deleteButton.addActionListener(e -> {
            String data = String.format("%s | %s", reciever, msgId);
            String delResponse = sendRequest("deleteMessage", sendDel, data);
            if(delResponse.equals("Message deleted succesfully")){
                chatPanel.remove(messageBubble); // Remove the bubble from the panel
            }
            else{
                JOptionPane.showMessageDialog(chatPanel, "Error, try again", "Error", JOptionPane.ERROR_MESSAGE);
            }

            chatPanel.revalidate();
        });
        
        JButton editButton = new JButton("Edit");
        editButton.setFont(new Font("Arial", Font.BOLD, 10)); // Same font style
        editButton.setMargin(new Insets(1, 1, 1, 1)); // Compact padding
        editButton.addActionListener(e -> {
            // Handle edit action here
            String newMessage = JOptionPane.showInputDialog("Edit your message:", messageText);
            if (newMessage != null && !newMessage.trim().isEmpty()) {
                // Update the message text and send update to the server
                String data = String.format("%s | %s | %s", reciever, msgId,newMessage.trim());
                System.out.println(data);
                String reqString = sendRequest("editMessage", sendDel, data);
                messageBubble.revalidate();
                messageBubble.repaint();
                chatPanel.revalidate();
                if(reqString.equals("Message Edited")){
                    messageArea.setText("Me" + ": " + newMessage.trim());
                }else{
                    JOptionPane.showMessageDialog(chatPanel, "Error, try again", "Error", JOptionPane.ERROR_MESSAGE);
                }

            }
        });
        
        // Create a panel to stack the buttons vertically
        JPanel buttonPanel = new JPanel();
        buttonPanel.setLayout(new BoxLayout(buttonPanel, BoxLayout.Y_AXIS));
        buttonPanel.setOpaque(false); // Transparent background for the button panel
        buttonPanel.add(editButton);
        buttonPanel.add(deleteButton);
        
        // Add the text area and button panel to the bubble
        JPanel bubbleContent = new JPanel();
        bubbleContent.setLayout(new BorderLayout());
        bubbleContent.add(messageArea, BorderLayout.CENTER);
        
        if (sender.toLowerCase().equals("me")) {
            bubbleContent.add(buttonPanel, BorderLayout.EAST); // Add stacked buttons to the right
        }
        bubbleContent.setOpaque(false); // Transparent background for the content
        
        messageBubble.add(bubbleContent);
    
        return messageBubble;
    }

    private void loadChatHistory( String username, String targetUser, JPanel chatPanel) {
        String data = targetUser;
        String response = sendRequest("getMessage", username, data);
        if (response.toLowerCase().contains("error")) {
            // Do nothing or display an error message if needed
        } else {
            // Clear the chat area and display messages
            chatPanel.removeAll();
            String[] messages = response.split(" ; ");
            for (String msg : messages) {
                String[] parts = msg.split("-\\d+#");
                String number = msg.substring(msg.length() - 4, msg.length() - 3);
                if (parts.length >= 2) {
                    String messageText = parts[0];
                    String messageType = parts[1].replace("#", "");
                    if (messageType.equals("S")) {
                        if(msg.contains("#FILE#:")){
                            String imgPath = msg.replace("#FILE#:", "").trim();
                            if (imgPath.matches(".*-\\d+#S#")) {
                                int index = imgPath.lastIndexOf('-');
                                System.out.println(imgPath);
                                if (index != -1) {
                                    imgPath = imgPath.substring(0, index);
                                }
                            }
                            System.out.println(number);
                            
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
                        }else{
                            chatPanel.add(createMessageBubble("Me", messageText, chatPanel,Integer.parseInt(number),targetUser,username));
                        }
                    } else if (messageType.equals("R")) {
                        if(msg.contains("#FILE#:")){
                            String imgPath = msg.replace("#FILE#:", "").trim();
                            if (imgPath.matches(".*-\\d+#R#")) {
                                int index = imgPath.lastIndexOf('-');
                                System.out.println(imgPath);
                                if (index != -1) {
                                    imgPath = imgPath.substring(0, index);
                                }
                            }
                            
                            ImageIcon tempImage= new ImageIcon(imgPath);
                            Image scaledImage = tempImage.getImage().getScaledInstance(tempImage.getIconWidth()/6, tempImage.getIconHeight()/6, Image.SCALE_SMOOTH);
                            ImageIcon scaledIcon = new ImageIcon(scaledImage);
                            JLabel imageLabel = new JLabel(targetUser, JLabel.LEFT);
                            imageLabel.setFont(new Font("Arial", Font.PLAIN, 14));
                            JPanel imgBubbleContent = new JPanel(new BorderLayout());
                            imgBubbleContent.add(imageLabel,BorderLayout.WEST);
                            imgBubbleContent.add(new JLabel(scaledIcon),BorderLayout.CENTER);
                            imgBubbleContent.setBorder(BorderFactory.createEmptyBorder(5, 10, 5, 10));
                            chatPanel.add(imgBubbleContent);   
                        }else{
                            chatPanel.add(createMessageBubble(targetUser, messageText, chatPanel,Integer.parseInt(number),targetUser,username));
                        }
                    }
                }
            }
        }
    }
}

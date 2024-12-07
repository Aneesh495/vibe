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

    // Timer for refreshing chat list - now commented out
    // private static Timer chatListRefreshTimer;

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

        SwingUtilities.invokeLater(() -> createInitialGUI(client));
    }

    private static void createInitialGUI(SocialClientGUI client) {
        JFrame frame = new JFrame("Social Client");
        frame.setSize(400, 400);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        frame.getContentPane().setBackground(Color.decode("#F8F8FF"));

        JPanel mainPanel = new JPanel();
        mainPanel.setBackground(Color.decode("#F8F8FF"));
        mainPanel.setLayout(new BoxLayout(mainPanel, BoxLayout.Y_AXIS));
        mainPanel.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

        // Add Logo
        JLabel logoLabel = new JLabel();
        logoLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
        ImageIcon logoIcon = new ImageIcon("Database/logo.jpg");
        Image logoImage = logoIcon.getImage().getScaledInstance(200, 100, Image.SCALE_SMOOTH);
        logoLabel.setIcon(new ImageIcon(logoImage));
        mainPanel.add(logoLabel);
        mainPanel.add(Box.createRigidArea(new Dimension(0, 20)));

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

        loginButton.addActionListener(e -> {
            String username = userField.getText().trim();
            String password = new String(passField.getPassword()).trim();
            performLogin(client, frame, username, password);
        });

        createAccountButton.addActionListener(e -> {
            frame.dispose();
            performCreateAccount(client);
        });

        passField.addActionListener(e -> loginButton.doClick());
    }

    private static void performLogin(SocialClientGUI client, JFrame frame, String username, String password) {
        if (username.isEmpty() || password.isEmpty()) {
            JOptionPane.showMessageDialog(frame, "Username and password cannot be empty.", "Input Error",
                    JOptionPane.ERROR_MESSAGE);
            return;
        }

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

    private static void performCreateAccount(SocialClientGUI client) {
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

        createButton.addActionListener((ActionEvent e) -> {
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
            String createResponse = client.sendRequest("createUser", "", data);

            if ("User created successfully".equalsIgnoreCase(createResponse.trim())) {
                JOptionPane.showMessageDialog(frame, "Account created successfully! You can now log in.", "Success",
                        JOptionPane.INFORMATION_MESSAGE);
                frame.dispose();
                createInitialGUI(client);
            } else if (createResponse.contains("Error") || createResponse.contains("Input Error")) {
                JOptionPane.showMessageDialog(frame, createResponse, "Creation Failed",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                JOptionPane.showMessageDialog(frame, "Unexpected server response: " + createResponse, "Server Error",
                        JOptionPane.ERROR_MESSAGE);
            }
        });

        cancelButton.addActionListener((ActionEvent e) -> {
            frame.dispose();
            createInitialGUI(client);
        });

        passField.addActionListener(e -> createButton.doClick());
    }

    // New panel for editing profile
    static class EditProfilePanel extends JPanel {
        private SocialClientGUI client;
        private String username;

        private JLabel profilePicLabel;
        private JLabel usernameLabel;
        private JLabel bioLabel;
        private JLabel messageLabel;

        private JButton changePfpButton;
        private JButton changeUsernameButton;
        private JButton changeBioButton;
        private JButton changeMessageButton;

        public EditProfilePanel(SocialClientGUI client, String username) {
           // this.client = client;
            this.username = username;
            setLayout(new BoxLayout(this, BoxLayout.Y_AXIS));
            setBackground(Color.decode("#F8F8FF"));
            setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

            // Fetch user info to display profile
            String resp = client.sendRequest("getUser", username, username);
            // resp: username | password | pfp | bio
            String[] userInfo = resp.split(" \\| ");
            String pfpPath = userInfo[2].trim();
            String bio = userInfo[3].trim();
            String currentUsername = userInfo[0].trim();

            // We don't have a "message" field defined, if you meant status message or something,
            // we can assume "message" can be part of bio or add a placeholder
            // For now, treat bio as user message or just display bio
            // If we need a separate "message", we'd need server support.

            profilePicLabel = new JLabel();
            profilePicLabel.setAlignmentX(CENTER_ALIGNMENT);
            ImageIcon pfpIcon = new ImageIcon(pfpPath);
            Image pfpImg = pfpIcon.getImage().getScaledInstance(100, 100, Image.SCALE_SMOOTH);
            profilePicLabel.setIcon(new ImageIcon(pfpImg));

            usernameLabel = new JLabel("Username: " + currentUsername);
            usernameLabel.setAlignmentX(CENTER_ALIGNMENT);
            bioLabel = new JLabel("Bio: " + bio);
            bioLabel.setAlignmentX(CENTER_ALIGNMENT);
            // If there's a message separate from bio, handle similarly
            messageLabel = new JLabel("Message: Not Implemented");
            messageLabel.setAlignmentX(CENTER_ALIGNMENT);

            add(profilePicLabel);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(usernameLabel);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(bioLabel);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(messageLabel);
            add(Box.createRigidArea(new Dimension(0, 20)));

            changePfpButton = new JButton("Change Profile Picture");
            changeUsernameButton = new JButton("Change Username");
            changeBioButton = new JButton("Change Bio");
            changeMessageButton = new JButton("Change Message");

            changePfpButton.setAlignmentX(CENTER_ALIGNMENT);
            changeUsernameButton.setAlignmentX(CENTER_ALIGNMENT);
            changeBioButton.setAlignmentX(CENTER_ALIGNMENT);
            changeMessageButton.setAlignmentX(CENTER_ALIGNMENT);

            add(changePfpButton);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(changeUsernameButton);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(changeBioButton);
            add(Box.createRigidArea(new Dimension(0, 10)));
            add(changeMessageButton);

            // Add action listeners to actually change these fields as needed
            // For now, placeholders:
            changePfpButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this, "Change PFP functionality not implemented", "Info", JOptionPane.INFORMATION_MESSAGE);
            });
            changeUsernameButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this, "Change Username functionality not implemented", "Info", JOptionPane.INFORMATION_MESSAGE);
            });
            changeBioButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this, "Change Bio functionality not implemented", "Info", JOptionPane.INFORMATION_MESSAGE);
            });
            changeMessageButton.addActionListener(e -> {
                JOptionPane.showMessageDialog(this, "Change Message functionality not implemented", "Info", JOptionPane.INFORMATION_MESSAGE);
            });
        }
    }

    private static void showEditProfilePanel(SocialClientGUI client, String username, JPanel cardPanel) {
        EditProfilePanel editProfilePanel = new EditProfilePanel(client, username);
        cardPanel.add(editProfilePanel, "EDITPROFILE");
        ((CardLayout) cardPanel.getLayout()).show(cardPanel, "EDITPROFILE");
    }

    private static void showChatList(SocialClientGUI client, String username) {
        JFrame frame = new JFrame("Chats - " + username);
        frame.setSize(800, 600);
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        frame.setLocationRelativeTo(null);

        JPanel mainPanel = new JPanel(new BorderLayout());

        JPanel chatListPanel = new JPanel();
        chatListPanel.setLayout(new BoxLayout(chatListPanel, BoxLayout.Y_AXIS));
        JScrollPane chatScrollPane = new JScrollPane(chatListPanel);

        updateChatList(client, username, chatListPanel);

        // Right panel layout
        JPanel rightPanel = new JPanel(new BorderLayout());

        // Top user info panel
        JPanel userInfoPanel = new JPanel();
        userInfoPanel.setLayout(new BoxLayout(userInfoPanel, BoxLayout.Y_AXIS));
        userInfoPanel.setBackground(Color.decode("#F8F8FF"));
        userInfoPanel.setBorder(BorderFactory.createEmptyBorder(10,10,10,10));

        // Display user pfp and username
        String resp = client.sendRequest("getUser", username, username);
        String[] userInfo = resp.split(" \\| ");
        String pfpPath = userInfo[2].trim();
        String currentUsername = userInfo[0].trim();

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

        // Separator line
        JSeparator sep = new JSeparator();
        sep.setAlignmentX(Component.CENTER_ALIGNMENT);
        userInfoPanel.add(sep);
        userInfoPanel.add(Box.createRigidArea(new Dimension(0,10)));

        // Now the 3 buttons (Friends, Blocked, Search)
        JPanel topButtonsPanel = new JPanel(new GridLayout(1, 3, 5, 5));
        JButton friendsButton = new JButton("Friends");
        JButton blockedButton = new JButton("Blocked");
        JButton searchButton = new JButton("Search");
        topButtonsPanel.add(friendsButton);
        topButtonsPanel.add(blockedButton);
        topButtonsPanel.add(searchButton);

        userInfoPanel.add(topButtonsPanel);

        rightPanel.add(userInfoPanel, BorderLayout.NORTH);

        // CardLayout panel for Friends, Blocked, Search, EditProfile
        JPanel cardPanel = new JPanel(new CardLayout());
        FriendsPanel friendsPanel = new FriendsPanel(client, username);
        BlockedPanel blockedPanel = new BlockedPanel(client, username);
        SearchPanel searchPanel = new SearchPanel(client, username);
        EditProfilePanel editProfilePanel = new EditProfilePanel(client, username);

        cardPanel.add(friendsPanel, "FRIENDS");
        cardPanel.add(blockedPanel, "BLOCKED");
        cardPanel.add(searchPanel, "SEARCH");
        cardPanel.add(editProfilePanel, "EDITPROFILE"); // We can show this on edit click

        rightPanel.add(cardPanel, BorderLayout.CENTER);

        friendsButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "FRIENDS");
            friendsPanel.refresh();
        });
        blockedButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "BLOCKED");
            blockedPanel.refresh();
        });
        searchButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "SEARCH");
            searchPanel.clear();
        });

        editProfileButton.addActionListener(e -> {
            ((CardLayout) cardPanel.getLayout()).show(cardPanel, "EDITPROFILE");
        });

        mainPanel.add(chatScrollPane, BorderLayout.CENTER);
        mainPanel.add(rightPanel, BorderLayout.EAST);

        // Bottom panel with "Message Someone" and "Refresh List"
        JPanel bottomPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 10, 10));
        JButton messageButton = new JButton("Message Someone");
        messageButton.setFont(new Font("Arial", Font.PLAIN, 16));
        messageButton.addActionListener(e -> {
            String targetUser = JOptionPane.showInputDialog(frame, "Enter username to message:");
            if (targetUser != null && !targetUser.trim().isEmpty()) {
                openChatWindow(client, username, targetUser.trim());
            }
        });

        // Refresh List button (instead of auto-refresh timer)
        JButton refreshButton = new JButton("Refresh List");
        refreshButton.setFont(new Font("Arial", Font.PLAIN, 16));
        refreshButton.addActionListener(e -> {
            updateChatList(client, username, chatListPanel);
        });

        bottomPanel.setBackground(Color.decode("#F8F8FF"));
        bottomPanel.add(messageButton);
        bottomPanel.add(refreshButton);

        mainPanel.add(bottomPanel, BorderLayout.SOUTH);

        frame.add(mainPanel);
        frame.setVisible(true);

        // Commented out auto-refresh timer code:
        // chatListRefreshTimer = new Timer(10000, evt -> {
        //     updateChatList(client, username, chatListPanel);
        // });
        // chatListRefreshTimer.start();
    }

    private static void updateChatList(SocialClientGUI client, String username, JPanel chatListPanel) {
        String response = client.sendRequest("getChatList", username, "");
        String[] chatUsers = response.trim().split(" \\| ");

        ArrayList<String> currentUsers = new ArrayList<>();
        for (Component comp : chatListPanel.getComponents()) {
            if (comp instanceof ChatItemPanel) {
                currentUsers.add(((ChatItemPanel) comp).getTargetUser());
            }
        }

        boolean addedNew = false;
        for (String user : chatUsers) {
            if (!user.trim().isEmpty() && !currentUsers.contains(user.trim())) {
                ChatItemPanel chatItem = new ChatItemPanel(user.trim(), username, client);
                chatListPanel.add(chatItem);
                addedNew = true;
            }
        }

        if (addedNew) {
            chatListPanel.revalidate();
            chatListPanel.repaint();
        }
    }

    static class ChatItemPanel extends JPanel {
        private String targetUser;
        private String username;

        public ChatItemPanel(String targetUser, String username, SocialClientGUI client) {
            this.targetUser = targetUser;
            this.username = username;

            setLayout(new BorderLayout());
            setMaximumSize(new Dimension(Integer.MAX_VALUE, 80));
            setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, Color.LIGHT_GRAY));

            JLabel profilePicLabel = new JLabel();
            ImageIcon profilePicIcon = new ImageIcon("Database/ProfilePicture/default.png");
            Image profilePicImage = profilePicIcon.getImage().getScaledInstance(60, 60, Image.SCALE_SMOOTH);
            profilePicLabel.setIcon(new ImageIcon(profilePicImage));
            profilePicLabel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));

            JLabel usernameLabel = new JLabel(targetUser);
            usernameLabel.setFont(new Font("Arial", Font.BOLD, 18));

            add(profilePicLabel, BorderLayout.WEST);
            add(usernameLabel, BorderLayout.CENTER);

            addMouseListener(new MouseAdapter() {
                @Override
                public void mouseClicked(MouseEvent e) {
                    openChatWindow(client, username, targetUser);
                }

                @Override
                public void mouseEntered(MouseEvent e) {
                    setBackground(new Color(230, 230, 230));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    setBackground(Color.WHITE);
                }
            });
        }

        @Override
        protected void paintComponent(Graphics g) {
            super.paintComponent(g);
            setBackground(Color.WHITE);
        }

        public String getTargetUser() {
            return targetUser;
        }
    }

    static class FriendsPanel extends JPanel {
        private SocialClientGUI client;
        private String username;
        private DefaultListModel<String> friendModel;
        private JList<String> friendList;
        private JButton unfriendButton;

        public FriendsPanel(SocialClientGUI client, String username) {
            // this.client = client;
            this.username = username;
            setLayout(new BorderLayout());

            friendModel = new DefaultListModel<>();
            friendList = new JList<>(friendModel);
            friendList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            friendList.addListSelectionListener(e -> updateUnfriendButtonState());

            unfriendButton = new JButton("Unfriend");
            unfriendButton.setEnabled(false);
            unfriendButton.addActionListener(e -> {
                String selected = friendList.getSelectedValue();
                if (selected != null) {
                    String response = client.sendRequest("unfriend", username, selected);
                    if (response.toLowerCase().contains("success")) {
                        refresh();
                    } else {
                        JOptionPane.showMessageDialog(this, response, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }

        public void refresh() {
            removeAll();
            friendModel.clear();

            String friendsStr = client.sendRequest("getFriend", username, "");
            if (friendsStr.toLowerCase().contains("no friends")) {
                JLabel noFriendsLabel = new JLabel("Your friend list is empty. Use the search feature to look for someone to friend.");
                add(centeredPanel(noFriendsLabel), BorderLayout.CENTER);
            } else {
                String listPart = friendsStr.substring(friendsStr.indexOf(':') + 1).trim();
                if (listPart.startsWith("[") && listPart.endsWith("]")) {
                    listPart = listPart.substring(1, listPart.length() - 1).trim();
                }
                if (!listPart.isEmpty()) {
                    String[] frnds = listPart.split(",");
                    for (String f : frnds) {
                        friendModel.addElement(f.trim());
                    }
                    add(new JScrollPane(friendList), BorderLayout.CENTER);
                    add(unfriendButton, BorderLayout.SOUTH);
                } else {
                    JLabel noFriendsLabel = new JLabel("Your friend list is empty. Use the search feature to look for someone to friend.");
                    add(centeredPanel(noFriendsLabel), BorderLayout.CENTER);
                }
            }

            revalidate();
            repaint();
        }

        private void updateUnfriendButtonState() {
            unfriendButton.setEnabled(friendList.getSelectedIndex() != -1);
        }

        private JPanel centeredPanel(JComponent comp) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(Color.decode("#F8F8FF"));
            p.add(comp);
            return p;
        }
    }

    static class BlockedPanel extends JPanel {
        private SocialClientGUI client;
        private String username;
        private DefaultListModel<String> blockedModel;
        private JList<String> blockedList;
        private JButton unblockButton;

        public BlockedPanel(SocialClientGUI client, String username) {
            // this.client = client;
            this.username = username;
            setLayout(new BorderLayout());

            blockedModel = new DefaultListModel<>();
            blockedList = new JList<>(blockedModel);
            blockedList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
            blockedList.addListSelectionListener(e -> updateUnblockButtonState());

            unblockButton = new JButton("Unblock");
            unblockButton.setEnabled(false);
            unblockButton.addActionListener(e -> {
                String selected = blockedList.getSelectedValue();
                if (selected != null) {
                    String response = client.sendRequest("unblock", username, selected);
                    if (response.toLowerCase().contains("success")) {
                        refresh();
                    } else {
                        JOptionPane.showMessageDialog(this, response, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                }
            });
        }

        public void refresh() {
            removeAll();
            blockedModel.clear();

            String blockedStr = client.sendRequest("getBlocked", username, "");
            if (blockedStr.toLowerCase().contains("not blocked")) {
                JLabel noBlockedLabel = new JLabel("Your blocked list is empty. Use the search feature to look for someone to block.");
                add(centeredPanel(noBlockedLabel), BorderLayout.CENTER);
            } else {
                String listPart = blockedStr.substring(blockedStr.indexOf(':') + 1).trim();
                if (listPart.startsWith("[") && listPart.endsWith("]")) {
                    listPart = listPart.substring(1, listPart.length() - 1).trim();
                }
                if (!listPart.isEmpty()) {
                    String[] blkd = listPart.split(",");
                    for (String b : blkd) {
                        blockedModel.addElement(b.trim());
                    }
                    add(new JScrollPane(blockedList), BorderLayout.CENTER);
                    add(unblockButton, BorderLayout.SOUTH);
                } else {
                    JLabel noBlockedLabel = new JLabel("Your blocked list is empty. Use the search feature to look for someone to block.");
                    add(centeredPanel(noBlockedLabel), BorderLayout.CENTER);
                }
            }

            revalidate();
            repaint();
        }

        private void updateUnblockButtonState() {
            unblockButton.setEnabled(blockedList.getSelectedIndex() != -1);
        }

        private JPanel centeredPanel(JComponent comp) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(Color.decode("#F8F8FF"));
            p.add(comp);
            return p;
        }
    }

    static class SearchPanel extends JPanel {
        private SocialClientGUI client;
        private String username;
        private JTextField searchField;
        private JPanel resultPanel;

        public SearchPanel(SocialClientGUI client, String username) {
           // this.client = client;
            this.username = username;
            setLayout(new BorderLayout());

            JPanel topPanel = new JPanel(new FlowLayout());
            searchField = new JTextField(15);
            JButton searchBtn = new JButton("Search");
            searchBtn.addActionListener(e -> doSearch());

            topPanel.add(searchField);
            topPanel.add(searchBtn);

            resultPanel = new JPanel(new BorderLayout());
            resultPanel.setBackground(Color.decode("#F8F8FF"));

            add(topPanel, BorderLayout.NORTH);
            add(resultPanel, BorderLayout.CENTER);
        }

        public void clear() {
            searchField.setText("");
            resultPanel.removeAll();
            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private void doSearch() {
            String targetUser = searchField.getText().trim();
            if (targetUser.isEmpty()) {
                JOptionPane.showMessageDialog(this, "Enter a username to search.", "Input Error", JOptionPane.ERROR_MESSAGE);
                return;
            }

            String resp = client.sendRequest("getUser", username, targetUser);
            resultPanel.removeAll();
            if (resp.toLowerCase().contains("not found") || resp.toLowerCase().contains("error")) {
                resultPanel.add(centeredPanel(new JLabel("User Not Found")), BorderLayout.CENTER);
            } else {
                // userInfo: username | password | pfp | bio
                String[] userInfo = resp.split(" \\| ");
                String foundUser = userInfo[0].trim();
                String pfpPath = userInfo[2].trim();
                String bio = userInfo[3].trim();

                boolean isFriend = isFriendOf(foundUser);
                boolean isBlocked = isBlockedUser(foundUser);

                JPanel userPanel = new JPanel();
                userPanel.setLayout(new BoxLayout(userPanel, BoxLayout.Y_AXIS));
                userPanel.setBackground(Color.decode("#F8F8FF"));

                // Show pfp, username, bio
                JLabel pfpLabel = new JLabel();
                pfpLabel.setAlignmentX(CENTER_ALIGNMENT);
                ImageIcon pfpIcon = new ImageIcon(pfpPath);
                Image pfpImg = pfpIcon.getImage().getScaledInstance(50, 50, Image.SCALE_SMOOTH);
                pfpLabel.setIcon(new ImageIcon(pfpImg));

                JLabel unameLabel = new JLabel("Username: " + foundUser);
                unameLabel.setAlignmentX(CENTER_ALIGNMENT);
                unameLabel.setFont(new Font("Arial", Font.BOLD, 16));

                JLabel bioLabel = new JLabel("Bio: " + bio);
                bioLabel.setAlignmentX(CENTER_ALIGNMENT);

                userPanel.add(pfpLabel);
                userPanel.add(Box.createRigidArea(new Dimension(0,10)));
                userPanel.add(unameLabel);
                userPanel.add(Box.createRigidArea(new Dimension(0,10)));
                userPanel.add(bioLabel);
                userPanel.add(Box.createRigidArea(new Dimension(0,20)));

                // Friend/Unfriend and Block/Unblock
                JPanel btnPanel = new JPanel(new FlowLayout());
                btnPanel.setBackground(Color.decode("#F8F8FF"));
                JButton friendUnfriendBtn = new JButton(isFriend ? "Unfriend" : "Friend");
                friendUnfriendBtn.addActionListener(e -> {
                    String action = isFriend ? "unfriend" : "friendUser";
                    String r = client.sendRequest(action, username, foundUser);
                    if (r.toLowerCase().contains("success")) {
                        JOptionPane.showMessageDialog(this, (isFriend ? "Unfriended" : "Friended") + " successfully!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, r, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });

                JButton blockUnblockBtn = new JButton(isBlocked ? "Unblock" : "Block");
                blockUnblockBtn.addActionListener(e -> {
                    String action = isBlocked ? "unblock" : "blockUser";
                    String r = client.sendRequest(action, username, foundUser);
                    if (r.toLowerCase().contains("success")) {
                        JOptionPane.showMessageDialog(this, (isBlocked ? "Unblocked" : "Blocked") + " successfully!",
                                "Success", JOptionPane.INFORMATION_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(this, r, "Error", JOptionPane.ERROR_MESSAGE);
                    }
                });

                btnPanel.add(friendUnfriendBtn);
                btnPanel.add(blockUnblockBtn);
                userPanel.add(btnPanel);

                resultPanel.add(userPanel, BorderLayout.CENTER);
            }
            resultPanel.revalidate();
            resultPanel.repaint();
        }

        private boolean isFriendOf(String user) {
            String friendsStr = client.sendRequest("getFriend", username, "");
            return friendsStr.contains(user);
        }

        private boolean isBlockedUser(String user) {
            String blockedStr = client.sendRequest("getBlocked", username, "");
            return blockedStr.contains(user);
        }

        private JPanel centeredPanel(JComponent comp) {
            JPanel p = new JPanel(new GridBagLayout());
            p.setBackground(Color.decode("#F8F8FF"));
            p.add(comp);
            return p;
        }
    }

    private static void openChatWindow(SocialClientGUI client, String username, String targetUser) {
        JFrame chatFrame = new JFrame("Chat with " + targetUser);
        chatFrame.setSize(400, 500);
        chatFrame.setLocationRelativeTo(null);
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
                    return true;
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

        loadChatHistory(client, username, targetUser, chatArea);

        Timer timer = new Timer(3000, new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                loadChatHistory(client, username, targetUser, chatArea);
            }
        });
        timer.start();

        chatFrame.addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent windowEvent) {
                timer.stop();
            }
        });
    }

    private static void loadChatHistory(SocialClientGUI client, String username, String targetUser, JTextArea chatArea) {
        String data = targetUser;
        String response = client.sendRequest("getMessage", username, data);
        if (response.toLowerCase().contains("error")) {
            // do nothing
        } else {
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

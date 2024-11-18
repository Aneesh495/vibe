import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.io.*;
import java.net.*;
import javax.swing.*;
import java.awt.*;
//import statements
public class SocialClient { //start of socialClient.
    private Socket socket; //initialize socket part 1.
    private PrintWriter writer; //initialize print writer part 1.
    private BufferedReader reader; //initialize buffered reader part 1.

    // Constructor initialises the client and attempts to connect to the server.
    public SocialClient(String address, int port) { //constructor socialClient.
        try { //try initializing socket, print writer and buffered reader (part 2)
            socket = new Socket(address, port); //initialize socket part 2
            writer = new PrintWriter(socket.getOutputStream(), true); //initialize print writer part 2.
            reader = new BufferedReader(new InputStreamReader(socket.getInputStream())); //initialize reader part 2.
            System.out.println("Successfully connected to the server."); //successful connection.
        } catch (IOException io) { //catch any IO exception.
            System.out.println("Failed to connect to the server."); //failed connection.
        } //end catch.
    } //end of constructor.

    // Sends a request to server with the specified parameters.
    public String sendRequest(String action, String caller, String data) { //start of sendRequest method.
        try { //start try
            // Format the request for server
            String requestString = action + " ; " + caller + " ; ";
            if (data != null) { //if data isn't empty
                requestString += data; //adds data to request string.
            } //end if.
            writer.println(requestString);
            return reader.readLine(); //reads & returns read line.
        } catch (IOException e) { //catch any IO exception
            // Print an error message if the sending fails
            System.out.println("Error encountered while sending request to the server.");
            return "Error: Could not send request.";
        } //end of catch.
    } //end of sendRequest method.

    // Closes the client connection gracefully
    public void closeConnection() {
        try { //start try
            // Closes input, output streams and socket
            reader.close(); //closes buffered reader.
            writer.close(); //closes print writer.
            socket.close(); //closes the socket.
        } catch (IOException e) { //catches any IO Exception & end try.
            // Displays error if there are any problem(s) while closing
            System.out.println("Encountered error while closing connection.");
        } //end catch.
    } //end close connection.

    // Main method to run the client application
    public static void main(String[] args) { //start main.
        // Creates a new SocialClient instance with server details.
        SocialClient client = new SocialClient("127.0.0.1", 4242); //initializes client.

        // If connection to the server fails, shows an error dialog
        if (client.socket == null) { //if the socket is null.
            JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Error",
                    JOptionPane.ERROR_MESSAGE);
            return; //ends method.
        } //end if

        // Asks the user for their username
        String username = JOptionPane.showInputDialog(null, "Enter username:", "Login",
                JOptionPane.QUESTION_MESSAGE);
        if (username == null) {
            return;
        }

        // Checks if user exists by sending a getUser request
        String userExistsResponse = client.sendRequest("getUser", username, null);
        if ("User not found.".equals(userExistsResponse)) {
            // If user not found, prompts user to create new account.
            int createNewUser = JOptionPane.showConfirmDialog(null,
                    "User not found. Create a new account?", "New User",
                    JOptionPane.YES_NO_OPTION);
            if (createNewUser == JOptionPane.YES_OPTION) {
                String password = JOptionPane.showInputDialog(null, "Enter new password:",
                        "New User", JOptionPane.QUESTION_MESSAGE);
                if (password != null) { //if password isn't null.
                    // Sends createUser request with password and default user infomation
                    String response = client.sendRequest("createUser", username, password + " | default_pfppath | default_bio");
                    JOptionPane.showMessageDialog(null, response, "User Created", JOptionPane.INFORMATION_MESSAGE);
                } //end if 1.
            } //end if 2.
        } else { //end if 3 & start else.
            // Logs in existing user if account exist
            String password = JOptionPane.showInputDialog(null, "Enter password:", "Login",
                    JOptionPane.QUESTION_MESSAGE);
            if (password != null) { //if password isn't null
                // Sends loginWithPassword request to verify credentials
                String loginResponse = client.sendRequest("loginWithPassword", username, password);
                System.out.println(loginResponse);
                if ("Input Error: Incorrect Password!".equals(loginResponse)) {
                    JOptionPane.showMessageDialog(null, "Incorrect Password, try again.",
                            "Error", JOptionPane.ERROR_MESSAGE);
                } else { //end if & start else.
                    JOptionPane.showMessageDialog(null, "Login Successful!", "Welcome",
                            JOptionPane.INFORMATION_MESSAGE);
                    showMainPanel(client, username);
                } //end else
            } //end if.
        } //end else.
    } //end main.

    // Displays the main application panel after login
    private static void showMainPanel(SocialClient client, String username) { //start showMainPanel class.
        JFrame frame = new JFrame("Social Client"); //social Client JFrame.
        frame.setSize(300, 250); // Sets the window sizes
        frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

        JPanel panel = new JPanel();
        panel.setLayout(new GridLayout(6, 1, 5, 5)); // Layout for buttons

        // Creates the buttons for various actions
        JButton blockButton = new JButton("Block User"); //block button.
        JButton friendButton = new JButton("Friend User"); //friend button.
        JButton unfriendButton = new JButton("Unfriend User"); //unfriend button.
        JButton searchButton = new JButton("Search User"); //search for user button.
        JButton messageButton = new JButton("Message User"); //message a user button.
        JButton unblockButton = new JButton("Unblock User"); // Adds new unblock button

        // Adds action listeners to the buttons
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
        messageButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                JOptionPane.showMessageDialog(frame, "This feature is not yet available",
                        "Message", JOptionPane.INFORMATION_MESSAGE);
            }
        });
        unblockButton.addActionListener(new ActionListener() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performAction(client, "unblock", username);
            }
        });

        // Adds buttons to the panel
        panel.add(blockButton); //adds block button to the panel.
        panel.add(friendButton); //adds friend button to the panel.
        panel.add(unfriendButton); //adds unfriend button to the panel.
        panel.add(searchButton); //adds search button to the panel.
        panel.add(messageButton); //adds message button to the panel.
        panel.add(unblockButton); //adds unblock button to the panel.

        frame.add(panel); //adds a frame to the panel.
        frame.setVisible(true); // Makes the window visible to user.
    } //end showMainPanel method.

    // Performs the requested action by sending it to the server
    private static void performAction(SocialClient client, String action, String username) { //start of performAction
        String targetUser = JOptionPane.showInputDialog(null, "Enter target username:",
                action, JOptionPane.QUESTION_MESSAGE);
        if (targetUser != null && !targetUser.trim().isEmpty()) { //start if.
            // Formats and sends the requests to the server
            String response = client.sendRequest(action, username, targetUser);
            JOptionPane.showMessageDialog(null, response, action, JOptionPane.INFORMATION_MESSAGE);
        } //end if.
    } //end performAction class.
} //end of social client.

 import java.io.*;
 import java.net.*;
 import javax.swing.*;
 import java.awt.*;
 
 public class SocialClient {
     private Socket socket;
     private PrintWriter out;
     private BufferedReader in;
 
     // Constructor initialises the client and attempts to connect to the server
     public SocialClient(String address, int port) {
         try {
             socket = new Socket(address, port);
             out = new PrintWriter(socket.getOutputStream(), true);
             in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             System.out.println("Connected to the servar.");
         } catch (IOException e) {
             System.out.println("Connection to server fail.");
         }
     }
 
     // Sends a request to server with specified parameters
     public String sendRequest(String action, String caller, String data) {
         try {
             // Format the request for server
             String request = action + " ; " + caller + " ; " + (data != null ? data : "");
             out.println(request);
             return in.readLine();
         } catch (IOException e) {
             // Print an error message if sending fail
             System.out.println("Error sending request to server.");
             return "Error: Could not sended request.";
         }
     }
 
     // Closes the client connection gracefull
     public void closeConnection() {
         try {
             // Closes input, output streams and soket
             in.close();
             out.close();
             socket.close();
         } catch (IOException e) {
             // Displays error if there is problem while closing
             System.out.println("Error closing connection.");
         }
     }
 
     // Main method to run the client aplication
     public static void main(String[] args) {
         // Creates a new SocialClient instance with server detals
         SocialClient client = new SocialClient("127.0.0.1", 4242);
 
         // If connection to server fail, shows an error dialog
         if (client.socket == null) {
             JOptionPane.showMessageDialog(null, "Failed to connect to server.", "Error", JOptionPane.ERROR_MESSAGE);
             return;
         }
 
         // Asks the user for they username
         String username = JOptionPane.showInputDialog(null, "Enter username:", "Login", JOptionPane.QUESTION_MESSAGE);
         if (username == null) {
             return;
         }
 
         // Checks if user exists by sending getUser request
         String userExistsResponse = client.sendRequest("getUser", username, null);
         if ("User not found.".equals(userExistsResponse)) {
             // If user not found, prompts user to create new acount
             int createNewUser = JOptionPane.showConfirmDialog(null,
                     "User not found. Create a new acount?", "New User",
                     JOptionPane.YES_NO_OPTION);
             if (createNewUser == JOptionPane.YES_OPTION) {
                 String password = JOptionPane.showInputDialog(null, "Enter new password:", "New User", JOptionPane.QUESTION_MESSAGE);
                 if (password != null) {
                     // Sends createUser request with password and default user infomation
                     String response = client.sendRequest("createUser", username, password + " | default_pfppath | default_bio");
                     JOptionPane.showMessageDialog(null, response, "User Created", JOptionPane.INFORMATION_MESSAGE);
                 }
             }
         } else {
             // Logs in existing user if acount exist
             String password = JOptionPane.showInputDialog(null, "Enter password:", "Login", JOptionPane.QUESTION_MESSAGE);
             if (password != null) {
                 // Sends loginWithPassword request to verify credencials
                 String loginResponse = client.sendRequest("loginWithPassword", username, password);
                 System.out.println(loginResponse);
                 if ("Input Error: Incorrect Password!".equals(loginResponse)) {
                     JOptionPane.showMessageDialog(null, "Incorrect Password, try again.", "Error", JOptionPane.ERROR_MESSAGE);
                 } else {
                     JOptionPane.showMessageDialog(null, "Login Successful!", "Welcome", JOptionPane.INFORMATION_MESSAGE);
                     showMainPanel(client, username);
                 }
             }
         }
     }
 
     // Displays the main apllication panel after login
     private static void showMainPanel(SocialClient client, String username) {
         JFrame frame = new JFrame("Social Client");
         frame.setSize(300, 250); // Sets the window sizes
         frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
 
         JPanel panel = new JPanel();
         panel.setLayout(new GridLayout(6, 1, 5, 5)); // Layout for bottons
 
         // Creates the bottons for various actions
         JButton blockButton = new JButton("Block User");
         JButton friendButton = new JButton("Friend User");
         JButton unfriendButton = new JButton("Unfriend User");
         JButton searchButton = new JButton("Search User");
         JButton messageButton = new JButton("Message User");
         JButton unblockButton = new JButton("Unblock User"); // Adds new unblock botton
 
         // Adds action listeners to the bottons
         blockButton.addActionListener(e -> performAction(client, "blockUser", username));
         friendButton.addActionListener(e -> performAction(client, "friendUser", username));
         unfriendButton.addActionListener(e -> performAction(client, "unfriend", username));
         searchButton.addActionListener(e -> performAction(client, "getUser", username));
         messageButton.addActionListener(e -> JOptionPane.showMessageDialog(frame, "This featur is not yet available", "Message", JOptionPane.INFORMATION_MESSAGE));
         unblockButton.addActionListener(e -> performAction(client, "unblock", username)); // Unblock action
 
         // Adds bottons to the panel
         panel.add(blockButton);
         panel.add(friendButton);
         panel.add(unfriendButton);
         panel.add(searchButton);
         panel.add(messageButton);
         panel.add(unblockButton); // Adds unblock botton to the panel
 
         frame.add(panel);
         frame.setVisible(true); // Makes the window visible
     }
 
     // Performs the requested action by sending it to the server
     private static void performAction(SocialClient client, String action, String username) {
         String targetUser = JOptionPane.showInputDialog(null, "Enter target username:", action, JOptionPane.QUESTION_MESSAGE);
         if (targetUser != null && !targetUser.trim().isEmpty()) {
             // Formats and sends the requests to the server
             String response = client.sendRequest(action, username, targetUser);
             JOptionPane.showMessageDialog(null, response, action, JOptionPane.INFORMATION_MESSAGE);
         }
     }
 }
 
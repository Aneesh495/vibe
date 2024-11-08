import java.io.*;
import java.net.*;
import java.util.*;
import javax.swing.JOptionPane;

public class SocialClient {
    public static void main(String[] args) {
        boolean running = true;

        try (Socket socket = new Socket("localhost", 8080);
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream()));
                PrintWriter writer = new PrintWriter(socket.getOutputStream(), true)) {

            while (running) {
                String userName = JOptionPane.showInputDialog(null, "Enter username:", "Login",
                        JOptionPane.QUESTION_MESSAGE);

                // Check if the user pressed cancel
                if (userName == null) {
                    running = false;
                    continue;
                }

                // Send username to server to check existence
                writer.println("userExists | " + userName);

                String response = reader.readLine();
                if (response == null) {
                    continue;
                }

                boolean userExists = !(response.equals("User not found"));

                if (!userExists) {
                    int newUser = JOptionPane.showConfirmDialog(null,
                            "User not found. Do you want to create a new account with that username?", "Continue",
                            JOptionPane.YES_NO_OPTION);

                    if (newUser == JOptionPane.YES_OPTION) {
                        String userPassword = JOptionPane.showInputDialog(null, "Enter new password", "New User",
                                JOptionPane.QUESTION_MESSAGE);
                        if (userPassword == null) {
                            continue; // Handle cancel button press when entering password
                        }
                        writer.println("New User | " + userName + " | " + userPassword);
                    } else {
                        continue; // User chooses not to create an account
                    }
                } else {
                    String password = JOptionPane.showInputDialog(null, "Enter password:", "Login",
                            JOptionPane.QUESTION_MESSAGE);

                    if (password == null) {
                        continue; // Handle cancel button press when entering password
                    }

                    writer.println("loginWithPassword | " + password);
                    String loginResponse = reader.readLine(); // Expecting login success or incorrect password message
                    if ("Incorrect Password".equals(loginResponse)) {
                        JOptionPane.showMessageDialog(null, "Incorrect Password, try again.", "Error",
                                JOptionPane.ERROR_MESSAGE);
                    } else {
                        JOptionPane.showMessageDialog(null, "Login Successful!", "Welcome",
                                JOptionPane.INFORMATION_MESSAGE);
                        running = false; // Exit the loop after successful login
                    }
                }
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}

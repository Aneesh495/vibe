import java.io.*;
import java.net.*;
import java.util.Scanner;

public class TestClient {
    private Socket socket;
    private PrintWriter out;
    private BufferedReader in;

    public TestClient(String address, int port) {
        try {
            socket = new Socket(address, port);
            out = new PrintWriter(socket.getOutputStream(), true);
            in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
        } catch (IOException e) {
            System.out.println("Connection to server failed.");
        }
    }

    // Method to send a request to the server
    public void sendRequest(String action, String data) {
        try {
            String request = action + " ; " + data;
            out.println(request);
            String response = in.readLine();
            System.out.println("Server Response: " + response);
        } catch (IOException e) {
            System.out.println("Error sending request to server.");
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
        Scanner scanner = new Scanner(System.in);
        TestClient client = new TestClient("127.0.0.1", 4242);

        // Menu for testing different functionalities
        while (true) {
            System.out.println("\nChoose an action:");
            System.out.println("1. Create User");
            System.out.println("2. Get User Info");
            System.out.println("3. Change User Info");
            System.out.println("4. Block User");
            System.out.println("5. Get Blocked Users");
            System.out.println("6. Exit");
            System.out.print("Enter choice: ");
            int choice = scanner.nextInt();
            scanner.nextLine(); // Consume the newline character

            switch (choice) {
                case 1:
                    // Create User
                    System.out.print("Enter username: ");
                    String username = scanner.nextLine();
                    System.out.print("Enter password: ");
                    String password = scanner.nextLine();
                    client.sendRequest("createUser", username + " | " + password);
                    break;

                case 2:
                    // Get User Info
                    System.out.print("Enter username to get info: ");
                    username = scanner.nextLine();
                    client.sendRequest("getUser", username);
                    break;

                case 3:
                    // Change User Info
                    System.out.print("Enter current username: ");
                    username = scanner.nextLine();
                    System.out.print("Enter new username: ");
                    String newUsername = scanner.nextLine();
                    System.out.print("Enter new password: ");
                    String newPassword = scanner.nextLine();
                    System.out.print("Enter new profile picture path: ");
                    String profilePicture = scanner.nextLine();
                    System.out.print("Enter new bio: ");
                    String bio = scanner.nextLine();
                    client.sendRequest("changeUserInfo", username + " | " + newUsername + " | " + newPassword + " | " + profilePicture + " | " + bio);
                    break;

                case 4:
                    // Block User
                    System.out.print("Enter your username: ");
                    String blocker = scanner.nextLine();
                    System.out.print("Enter username to block: ");
                    String blockee = scanner.nextLine();
                    client.sendRequest("blockUser", blocker + " | " + blockee);
                    break;

                case 5:
                    // Get Blocked Users
                    System.out.print("Enter your username to get blocked list: ");
                    username = scanner.nextLine();
                    client.sendRequest("getBlocked", username);
                    break;

                case 6:
                    // Exit
                    client.closeConnection();
                    System.out.println("Client disconnected.");
                    return;

                default:
                    System.out.println("Invalid choice, please try again.");
                    break;
            }
        }
    }
}

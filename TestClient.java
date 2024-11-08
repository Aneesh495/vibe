import java.io.*;
import java.net.*;

public class TestClient {

    public static void main(String[] args) {
        String host = "localhost";
        int port = 4242;

        // Predefined commands to test the server
        String[] testCommands = {
            "createUser ; jonny | jonny123",
            "createUser ; alice | alicepass | /path/to/pic | Hello, I am Alice!",
            "getUser ; jonny",
            "changeUserInfo ; jonny | johnnyNew | newpass123 | /path/to/newpic | Updated bio for Johnny",
            "getUser ; johnnyNew"
        };
        
        try (Socket socket = new Socket(host, port);
             BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream()));
             PrintWriter out = new PrintWriter(socket.getOutputStream(), true)) {

            System.out.println("Connected to SocialServer on port " + port);

            // Send each command in the testCommands array to the server
            for (String command : testCommands) {
                System.out.println("Sending command: " + command);
                out.println(command);

                // Read the server's response
                String response = in.readLine();
                System.out.println("Server response: " + response);
            }

            System.out.println("All commands sent. Test complete.");

        } catch (IOException e) {
            System.out.println("Unable to connect to server: " + e.getMessage());
        }
    }
}

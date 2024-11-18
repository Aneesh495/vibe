import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import java.io.*;
import java.net.*;
import javax.swing.*;

class SocialClientTest {

    private ServerSocket mockServerSocket;
    private Socket mockClientSocket;
    private SocialClient socialClient;

    @BeforeEach
    void setUp() throws IOException {
        // Set up a mock server to test the client
        mockServerSocket = new ServerSocket(4242);

        // Create the SocialClient instance
        socialClient = new SocialClient("127.0.0.1", 4242);

        // Accept the connection on the mock server
        mockClientSocket = mockServerSocket.accept();
    }

    @AfterEach
    void tearDown() throws IOException {
        // Close all sockets
        if (socialClient != null) {
            socialClient.closeConnection();
        }
        if (mockClientSocket != null) {
            mockClientSocket.close();
        }
        if (mockServerSocket != null) {
            mockServerSocket.close();
        }
    }

    @Test
    void testSendRequestSuccess() throws IOException {
        BufferedWriter mockServerWriter = new BufferedWriter(new OutputStreamWriter(mockClientSocket.getOutputStream()));

        // Mock server sends a response
        new Thread(() -> {
            try {
                mockServerWriter.write("Login successful\n");
                mockServerWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Send a request and verify the response
        String response = socialClient.sendRequest("loginWithPassword", "testUser", "password123");
        assertEquals("Login successful", response);

        // Ensure the server received the correct request
        BufferedReader mockServerReader = new BufferedReader(new InputStreamReader(mockClientSocket.getInputStream()));
        assertEquals("loginWithPassword ; testUser ; password123", mockServerReader.readLine());
    }

    @Test
    void testSendRequestOverloaded() throws IOException {
        BufferedWriter mockServerWriter = new BufferedWriter(new OutputStreamWriter(mockClientSocket.getOutputStream()));

        // Mock server sends a response
        new Thread(() -> {
            try {
                mockServerWriter.write("User created successfully\n");
                mockServerWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Test the overloaded method
        String response = socialClient.sendRequest("createUser", "newUser | password123");
        assertEquals("User created successfully", response);

        // Ensure the server received the correct request format
        BufferedReader mockServerReader = new BufferedReader(new InputStreamReader(mockClientSocket.getInputStream()));
        assertEquals("createUser ; ; newUser | password123", mockServerReader.readLine());
    }

    @Test
    void testLoginButtonAction() throws IOException {
        BufferedWriter mockServerWriter = new BufferedWriter(new OutputStreamWriter(mockClientSocket.getOutputStream()));

        // Mock server sends a response for login
        new Thread(() -> {
            try {
                mockServerWriter.write("Login successful\n");
                mockServerWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Simulate GUI inputs
        JTextField userField = new JTextField("testUser");
        JPasswordField passField = new JPasswordField("password123");

        // Simulate button action
        String username = userField.getText().trim();
        String password = new String(passField.getPassword()).trim();
        String response = socialClient.sendRequest("loginWithPassword", username, password);

        assertEquals("Login successful", response);
    }

    @Test
    void testCreateAccountButtonAction() throws IOException {
        BufferedWriter mockServerWriter = new BufferedWriter(new OutputStreamWriter(mockClientSocket.getOutputStream()));

        // Mock server sends a response for account creation
        new Thread(() -> {
            try {
                mockServerWriter.write("Account created successfully\n");
                mockServerWriter.flush();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }).start();

        // Simulate GUI inputs
        JTextField userField = new JTextField("newUser");
        JPasswordField passField = new JPasswordField("securePass");

        // Simulate button action
        String username = userField.getText().trim();
        String password = new String(passField.getPassword()).trim();
        String response = socialClient.sendRequest("createUser", username + " | " + password);

        assertEquals("Account created successfully", response);
    }

    @Test
    void testCloseConnection() {
        assertDoesNotThrow(() -> socialClient.closeConnection());
    }
}

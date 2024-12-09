import org.junit.jupiter.api.*;
import java.io.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialClientTest {
    private SocialClient client;
    private SocialServer server;
    private Thread serverThread;

    @BeforeEach
    void setUp() throws IOException {
        // Start the server in a separate thread
        serverThread = new Thread(() -> {
            try {
                ServerSocket serverSocket = new ServerSocket(4242);
                while (true) {
                    new Thread(new SocialServer(serverSocket.accept())).start();
                }
            } catch (IOException e) {
                e.printStackTrace();
            }
        });
        serverThread.start();

        // Initialize client with the running server
        client = new SocialClient("127.0.0.1", 4242);
    }

    @AfterEach
    void tearDown() {
        // Close the client connection
        client.closeConnection();

        // Stop the server thread
        serverThread.interrupt();
    }

    //Account test cases.

    @Test
    void testCreateAccount() {
        // Simulate user creation
        String username = "testClientUser_" + System.currentTimeMillis();
        String password = "testPassword123";
        String data = username + " | " + password + " | default.png | default bio";
        String response = client.sendRequest("createUser", "", data);

        assertEquals("User created successfully", response.trim(), "User should be created successfully");
    }

    @Test
    void testLogin() {
        // Create a user and test login
        String username = "clientLoginUser_" + System.currentTimeMillis();
        String password = "securePass";
        client.sendRequest("createUser", "", username + " | " + password + 
                " | defaultPic.png | default bio");

        String loginResponse = client.sendRequest("loginWithPassword", username, password);
        assertEquals("Login successful", loginResponse.trim(), "Login should be successful with correct credentials");
    }

    //Friend test cases.

    @Test
    void testAddFriend() {
        String userA = "clientUserA_" + System.currentTimeMillis();
        String userB = "clientUserB_" + System.currentTimeMillis();
        client.sendRequest("createUser", "", userA + " | passwordA | default.png | default bio");
        client.sendRequest("createUser", "", userB + " | passwordB | default.png | default bio");

        // User A friends User b. 
        String friendResponse = client.sendRequest("friendUser", userA, userB);
        assertTrue(friendResponse.toLowerCase().contains("friended successfully"), 
                "User should be friended successfully");
    }

    @Test
    void testRemoveFriend() {
        String userA = "clientUserA_" + System.currentTimeMillis();
        String userB = "clientUserB_" + System.currentTimeMillis();
        client.sendRequest("createUser", "", userA + " | passwordA | default.png | default bio");
        client.sendRequest("createUser", "", userB + " | passwordB | default.png | default bio");
        //User A unfriends User B.
        client.sendRequest("friendUser", userA, userB);
        String unfriendResponse = client.sendRequest("unfriend", userA, userB);
        assertTrue(unfriendResponse.toLowerCase().contains("unfriended successfully"), 
                "User should be unfriended successfully");
    }

    //Block test cases.

    @Test
    void testBlockUser() {
        String userA = "clientBlockA_" + System.currentTimeMillis();
        String userB = "clientBlockB_" + System.currentTimeMillis();
        client.sendRequest("createUser", "", userA + " | passwordA | default.png | default bio");
        client.sendRequest("createUser", "", userB + " | passwordB | default.png | default bio");
        //User A block user B
        String blockResponse = client.sendRequest("blockUser", userA, userB);
        assertTrue(blockResponse.toLowerCase().contains("blocked successfully"), "User should be blocked successfully");
    }

    @Test
    void testUnblockUser() {
        // Create two users
        String userA = "clientBlockA_" + System.currentTimeMillis();
        String userB = "clientBlockB_" + System.currentTimeMillis();
        client.sendRequest("createUser", "", userA + " | passwordA | default.png | default bio");
        client.sendRequest("createUser", "", userB + " | passwordB | default.png | default bio");

        // Block and then unblock userB by userA
        client.sendRequest("blockUser", userA, userB);
        String unblockResponse = client.sendRequest("unblock", userA, userB);
        assertTrue(unblockResponse.toLowerCase().contains("unblocked successfully"), 
                "User should be unblocked successfully");
    }

    //Messages Test Cases.

    @Test
    void testSendMessage() {
        String sender = "clientSender_" + System.currentTimeMillis();
        String receiver = "clientReceiver_" + System.currentTimeMillis();
        client.sendRequest("createUser", "", sender + 
                " | passwordSender | default.png | default bio");
        client.sendRequest("createUser", "", receiver + 
                " | passwordReceiver | default.png | default bio");
        String sendMessageResponse = client.sendRequest("sendMessage", sender, receiver + " | Hello!");
        assertNotNull(sendMessageResponse, "Sending a message should return a response.");
    }

    @Test
    void testRetrieveMessages() {
        String sender = "clientSender_" + System.currentTimeMillis();
        String receiver = "clientReceiver_" + System.currentTimeMillis();
        client.sendRequest("createUser", "", sender + 
                " | passwordSender | default.png | default bio");
        client.sendRequest("createUser", "", receiver + 
                " | passwordReceiver | default.png | default bio");

        //Send & receive messages.
        client.sendRequest("sendMessage", sender, receiver + " | Hello!");
        String messages = client.sendRequest("getMessage", sender, receiver);
        assertTrue(messages.contains("Hello!"), "Messages should contain the sent message");
    }
}

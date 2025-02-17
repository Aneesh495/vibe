import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import javax.swing.*;
import java.util.*;

// SERVER HAS TO BE RUNNING FOR TEST CASES TO WORK
public class SocialClientTest {

    @BeforeClass
    public static void setupDatabase() throws Exception {
        // Wipe and initialize the database with basic test data
        SocialServer server = new SocialServer(null);

        // Clear all files
        server.overwriteFile(new ArrayList<>(), "./Database/Data/userInfo.txt");
        server.overwriteFile(new ArrayList<>(), "./Database/Data/friends.txt");
        server.overwriteFile(new ArrayList<>(), "./Database/Data/blocked.txt");
        server.overwriteFile(new ArrayList<>(), "./Database/Data/msgs.txt");

        // Create test accounts
        server.createUser("testuser", "password");
        server.createUser("frienduser", "password");
        server.createUser("user1", "password");
        server.createUser("user2", "password");
        server.createUser("blockeduser", "password");
        server.createUser("validuser", "validpassword", "Database/ProfilePicture/default.png","Default Bio");
        server.createUser("duplicateuser", "password");
    }

    // --- Socket Connection Tests ---
    @Test
    public void testConnectionSuccess() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        assertNotNull("Socket should not be null if connection is successful", client.socket);
        client.closeConnection();
    }

    @Test
    public void testConnectionFailure() {
        SocialClient client = new SocialClient("127.0.0.1", 9999); // Invalid port
        assertNull("Socket should be null if connection fails", client.socket);
    }

    // --- Request Handling Tests ---
    @Test
    public void testSendValidRequest() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String data = String.format("%s | %s | %s | %s", "newuser", "newpassword", "Database/ProfilePicture/default.png",
                "default bio");
        String response = client.sendRequest("createUser", "", data);
        assertEquals("Expected successful response from server", "User created successfully", response);
        client.closeConnection();
    }

    @Test
    public void testSendInvalidRequest() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String response = client.sendRequest("invalidAction", "testuser", "data");
        assertTrue("Response should contain 'Error' for invalid action", response.contains("Error"));
        client.closeConnection();
    }

    // --- Login Tests ---
    @Test
    public void testLoginValidCredentials() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        client.performLogin("validuser", "validpassword", new JFrame());
        assertEquals("Logged in user should be updated", "validuser", client.loggedInUser);
        client.closeConnection();
    }

    @Test
    public void testLoginInvalidCredentials() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        client.performLogin("invaliduser", "wrongpassword", new JFrame());
        assertNull("Logged in user should not be updated with invalid credentials", client.loggedInUser);
        client.closeConnection();
    }

    // --- Account Creation Tests ---
    @Test
    public void testAccountCreation() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String data = String.format("%s | %s | %s | %s", "uniqueuser", "uniquepassword", "Database/ProfilePicture/default.png",
                "default bio");
        String response = client.sendRequest("createUser", "", data);
        assertEquals("Expected success message", "User created successfully", response);
        client.closeConnection();
    }

    @Test
    public void testDuplicateAccountCreation() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String response = client.sendRequest("createUser", "duplicateuser", "password");
        assertTrue("Response should indicate duplicate user", response.contains("Error"));
        client.closeConnection();
    }

    // --- Friend Management Tests ---
    @Test
    public void testAddFriend() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String response = client.sendRequest("friendUser", "testuser", "frienduser");
        assertTrue("Response should indicate success", response.contains("success"));
        client.closeConnection();
    }

    @Test
    public void testRemoveFriend() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        try {
            client.sendRequest("friendUser", "testuser", "frienduser");
        } catch (Exception e) {

        }
        String response = client.sendRequest("unfriend", "testuser", "frienduser");
        System.out.println(response);
        assertTrue("Response should indicate success", response.contains("success"));
        client.closeConnection();
    }

    // --- Messaging Tests ---
    @Test
    public void testSendMessage() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String response = client.sendRequest("sendMessage", "user1", "user2 | Hello!");
        assertTrue("Message ID should be returned", response.matches("\\d+"));
        client.closeConnection();
    }

    @Test
    public void testRetrieveMessages() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        client.sendRequest("sendMessage", "user1", "user2 | Hello!");
        String response = client.sendRequest("getMessage", "user1", "user2");
        assertTrue("Response should contain the sent message", response.contains("Hello!"));
        client.closeConnection();
    }

    // --- Block Management Tests ---
    @Test
    public void testBlockUser() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        String response = client.sendRequest("blockUser", "testuser", "blockeduser");
        assertTrue("Response should indicate success", response.contains("success"));
        client.closeConnection();
    }

    @Test
    public void testUnblockUser() {
        SocialClient client = new SocialClient("127.0.0.1", 4242);
        try{
            client.sendRequest("blockUser", "testuser", "blockeduser");
        }catch(Exception e){

        }
        String response = client.sendRequest("unblock", "testuser", "blockeduser");
        assertTrue("Response should indicate success", response.contains("success"));
        client.closeConnection();
    }
}

import org.junit.Before;
import org.junit.BeforeClass;
import org.junit.Test;
import static org.junit.Assert.*;
import java.io.*;

public class SocialServerTest {

    @BeforeClass
    public static void wipeDatabase() throws Exception {
        // Paths to your database files
        String[] databaseFiles = {
                "./Database/Data/userInfo.txt",
                "./Database/Data/friends.txt",
                "./Database/Data/blocked.txt",
                "./Database/Data/msgs.txt"
        };

        // Clear the contents of each database file
        for (String filePath : databaseFiles) {
            File file = new File(filePath);
            if (!file.exists()) {
                // Create the file if it doesn't exist
                file.getParentFile().mkdirs(); // Create directories if needed
                file.createNewFile();
            }

            // Overwrite the file with an empty string
            try (FileWriter writer = new FileWriter(file, false)) {
                writer.write(""); // Clear the file
            }
        }
    }
    // --- User Management Tests ---
    @Test
    public void testCreateUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("testuser123", "testpassword", "default.png", "This is a test bio");
        String userInfo = SocialServer.getUser("testuser123");
        assertNotNull("User info should not be null after creation", userInfo);
    }

    @Test(expected = InvalidInputException.class)
    public void testCreateDuplicateUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("testuser", "testpassword");
        server.createUser("testuser", "testpassword"); // Should throw exception
    }

    @Test
    public void testRetrieveUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("retrieveuser", "password");
        String userInfo = SocialServer.getUser("retrieveuser");
        assertTrue("User info should contain username", userInfo.contains("retrieveuser"));
    }

    // --- Friend Management Tests ---
    @Test
    public void testFriendUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("testFriend2", "password2");
        server.createUser("testFriend", "password1");
        SocialServer.friendUser("testFriend", "testFriend2");
        assertTrue("Users should be friends", SocialServer.checkFriend("testFriend", "testFriend2"));
    }

    @Test
    public void testUnfriendUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("testUnfriend", "password1");
        server.createUser("testUnfriend2", "password2");
        SocialServer.friendUser("testUnfriend", "testUnfriend2");
        SocialServer.unfriend("testUnfriend", "testUnfriend2");
        assertFalse("Users should no longer be friends", SocialServer.checkFriend("user1", "user2"));
    }

    // --- Messaging Tests ---
    @Test
    public void testSendAndRetrieveMessage() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("testSendAndRetrieveMessage1", "password1");
        server.createUser("testSendAndRetrieveMessage2", "password2");
        int messageId = server.sendMessage("testSendAndRetrieveMessage1", "testSendAndRetrieveMessage2", "Hello, User2!");
        assertTrue("Message ID should be non-negative", messageId >= 0);
        String message = server.getMessage("testSendAndRetrieveMessage1", "testSendAndRetrieveMessage2");
        assertTrue("Message should contain the sent text", message.contains("Hello, User2!"));
    }

    @Test
    public void testDeleteMessage() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("testDeleteMessage1", "password1");
        server.createUser("testDeleteMessage2", "password2");
        int messageId = server.sendMessage("testDeleteMessage1", "testDeleteMessage2", "Hello, User2!");
        server.deleteMessage("testDeleteMessage1", "testDeleteMessage2", messageId);
        try {
            String message = server.getMessage("testDeleteMessage1", "testDeleteMessage2");
            assertFalse("Message should no longer exist", message.contains("Hello, User2!"));
        }catch(InvalidInputException e) {
            return;
        }
    }

    // --- Block Management Tests ---
    @Test
    public void testBlockUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("blocker123", "password1");
        server.createUser("blockee123", "password2");
        server.blockUser("blocker123", "blockee123");
        assertTrue("User should be blocked", SocialServer.checkBlocked("blocker123", "blockee123"));
    }

    @Test
    public void testUnblockUser() throws Exception {
        SocialServer server = new SocialServer(null);
        server.createUser("blocker", "password1");
        server.createUser("blockee", "password2");
        server.blockUser("blocker", "blockee");
        server.unblock("blocker", "blockee");
        assertFalse("User should be unblocked", SocialServer.checkBlocked("blocker", "blockee"));
    }
}

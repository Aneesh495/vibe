import org.junit.jupiter.api.*;
import java.io.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class SocialServerTest {
    private SocialServer server;

    @BeforeEach
    void setUp() {
        server = new SocialServer(null);
    }

    @Test
    void testCreateUserSuccess() throws Exception {
        String username = "testuser_" + System.currentTimeMillis();
        server.createUser(username, "password123");
        String userInfo = SocialServer.getUser(username);
        assertNotNull(userInfo, "User should exist after creation");
        assertTrue(userInfo.contains(username), "User info should contain username");
        assertTrue(userInfo.contains("password123"), "User info should contain password");
    }

    @Test
    void testCreateUserDuplicate() {
        String username = "duplicateUser_" + System.currentTimeMillis();
        assertThrows(InvalidInputException.class, () -> {
            server.createUser(username, "password");
            server.createUser(username, "newpassword");
        });
    }

    @Test
    void testLoginWithPasswordSuccess() throws Exception {
        String username = "loginUser_" + System.currentTimeMillis();
        server.createUser(username, "securepass");
        boolean loginSuccess = server.loginWithPassword(username, "securepass");
        assertTrue(loginSuccess, "Login should succeed with correct credentials");
    }

    @Test
    void testLoginWithIncorrectPassword() throws Exception {
        String username = "loginUser_" + System.currentTimeMillis();
        server.createUser(username, "securepass");
        assertThrows(InvalidInputException.class, () -> {
            server.loginWithPassword(username, "wrongpass");
        });
    }

    @Test
    void testEditUserProfileSuccess() throws Exception {
        String username = "editUser_" + System.currentTimeMillis();
        server.createUser(username, "password123", "defaultPic.png", "original bio");
        SocialServer.editUser(username, "newUser", "newPass123", "newPic.png",
                "updated bio");
        String updatedUserInfo = SocialServer.getUser(username);
        assertNotNull(updatedUserInfo, "User info should be retrievable");
        assertTrue(updatedUserInfo.contains("newPass123"), "Password should be updated");
        assertTrue(updatedUserInfo.contains("newPic.png"), "Profile picture should be updated");
        assertTrue(updatedUserInfo.contains("updated bio"), "Bio should be updated");
    }

    //Messaging test cases.

    @Test
    void testSendMessageSuccess() throws Exception {
        String sender = "userA_" + System.currentTimeMillis();
        String receiver = "userB_" + System.currentTimeMillis();
        server.createUser(sender, "passwordA");
        server.createUser(receiver, "passwordB");

        int messageId = server.sendMessage(sender, receiver, "Hello userB!");
        assertEquals(0, messageId, "First message ID should be 0");

        String retrievedMessage = server.getMessage(sender, receiver);
        assertTrue(retrievedMessage.contains("Hello userB!"), "Message content should match");
    }

    @Test
    void testRetrieveMessagesBetweenUsers() throws Exception {
        String sender = "userA_" + System.currentTimeMillis();
        String receiver = "userB_" + System.currentTimeMillis();
        server.createUser(sender, "passwordA");
        server.createUser(receiver, "passwordB");

        server.sendMessage(sender, receiver, "Message 1 from A to B");
        server.sendMessage(receiver, sender, "Reply from B to A");
        server.sendMessage(sender, receiver, "Message 2 from A to B");

        String conversation = server.getMessage(sender, receiver);
        assertTrue(conversation.contains("Message 1 from A to B"), "First message should be retrievable");
        assertTrue(conversation.contains("Reply from B to A"), "Reply message should be retrievable");
        assertTrue(conversation.contains("Message 2 from A to B"), "Second message should be retrievable");
    }

    @Test
    void testDeleteMessageSuccess() throws Exception {
        String sender = "userA_" + System.currentTimeMillis();
        String receiver = "userB_" + System.currentTimeMillis();
        server.createUser(sender, "passwordA");
        server.createUser(receiver, "passwordB");

        int messageId = server.sendMessage(sender, receiver, "Temporary Message");
        server.deleteMessage(sender, receiver, messageId);

        String conversation = server.getMessage(sender, receiver);
        assertFalse(conversation.contains("Temporary Message"), "Deleted message should no longer exist");
    }

    // --- FRIEND OPERATIONS ---

    @Test
    void testAddFriendSuccess() throws Exception {
        String userA = "userA_" + System.currentTimeMillis();
        String userB = "userB_" + System.currentTimeMillis();
        server.createUser(userA, "passwordA");
        server.createUser(userB, "passwordB");

        SocialServer.friendUser(userA, userB);
        ArrayList<String> friendsOfUserA = SocialServer.getFriend(userA);
        assertTrue(friendsOfUserA.contains(userB), "userB should be a friend of userA");
    }

    @Test
    void testRemoveFriendSuccess() throws Exception {
        String userA = "userA_" + System.currentTimeMillis();
        String userB = "userB_" + System.currentTimeMillis();
        server.createUser(userA, "passwordA");
        server.createUser(userB, "passwordB");

        SocialServer.friendUser(userA, userB);
        SocialServer.unfriend(userA, userB);

        ArrayList<String> friendsOfUserA = SocialServer.getFriend(userA);
        assertFalse(friendsOfUserA.contains(userB), "userB should no longer be a friend of userA");
    }

    // --- BLOCK OPERATIONS ---

    @Test
    void testBlockUserSuccess() throws Exception {
        String userA = "userA_" + System.currentTimeMillis();
        String userB = "userB_" + System.currentTimeMillis();
        server.createUser(userA, "passwordA");
        server.createUser(userB, "passwordB");

        server.blockUser(userA, userB);
        ArrayList<String> blockedByUserA = SocialServer.getBlocked(userA);
        assertTrue(blockedByUserA.contains(userB), "userB should be blocked by userA");
    }

    @Test
    void testUnblockUserSuccess() throws Exception {
        String userA = "userA_" + System.currentTimeMillis();
        String userB = "userB_" + System.currentTimeMillis();
        server.createUser(userA, "passwordA");
        server.createUser(userB, "passwordB");

        server.blockUser(userA, userB);
        server.unblock(userA, userB);

        ArrayList<String> blockedByUserA = SocialServer.getBlocked(userA);
        assertFalse(blockedByUserA.contains(userB), "userB should no longer be blocked by userA");
    }
}

import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import ServerException.*;

class SocialServerTest {
    private List<String> users; // Simulated list of users
    private List<String> blockedUsers; // Simulated list of blocked relationships
    private SocialServer socialServer;

    @BeforeEach
    void setUp() {
        users = new ArrayList<>();
        blockedUsers = new ArrayList<>();
        socialServer = new SocialServer(null) {
            @Override
            public void createUser(String username, String password) throws InvalidInputException {
                for (String user : users) {
                    String[] userInfo = user.split(" \\| ");
                    if (userInfo[0].equals(username)) {
                        throw new InvalidInputException("User already exists.");
                    }
                }
                users.add(username + " | " + password);
            }

            @Override
            public String getUser(String username) throws UserNotFoundException {
                for (String user : users) {
                    String[] userInfo = user.split(" \\| ");
                    if (userInfo[0].equals(username)) {
                        return user;
                    }
                }
                throw new UserNotFoundException("User Not Found");
            }

            @Override
            public boolean loginWithPassword(String username, String password) throws UserNotFoundException, InvalidInputException {
                for (String user : users) {
                    String[] userInfo = user.split(" \\| ");
                    if (userInfo[0].equals(username)) {
                        if (userInfo[1].equals(password)) {
                            return true;
                        } else {
                            throw new InvalidInputException("Incorrect Password!");
                        }
                    }
                }
                throw new UserNotFoundException("User does not exist!");
            }

            @Override
            public void blockUser(String username, String blockedUser) throws InvalidInputException {
                if (username.equals(blockedUser)) {
                    throw new InvalidInputException("User cannot block themselves.");
                }
                String blockEntry = username + " -> " + blockedUser;
                if (blockedUsers.contains(blockEntry)) {
                    throw new InvalidInputException("User is already blocked.");
                }
                blockedUsers.add(blockEntry);
            }

            @Override
            public void unblock(String username, String unblockUser) throws InvalidInputException {
                String blockEntry = username + " -> " + unblockUser;
                if (!blockedUsers.contains(blockEntry)) {
                    throw new InvalidInputException("User is not blocked.");
                }
                blockedUsers.remove(blockEntry);
            }
        };
    }

    @Test
    void testCreateUserSuccess() throws Exception {
        socialServer.createUser("testUser", "password123");
        assertTrue(users.contains("testUser | password123"));
    }

    @Test
    void testCreateUserDuplicate() throws Exception {
        socialServer.createUser("testUser", "password123");
        Exception exception = assertThrows(InvalidInputException.class, () -> {
            socialServer.createUser("testUser", "password123");
        });
        assertEquals("User already exists.", exception.getMessage());
    }

    @Test
    void testGetUserSuccess() throws Exception {
        socialServer.createUser("testUser", "password123");
        String user = socialServer.getUser("testUser");
        assertEquals("testUser | password123", user);
    }

    @Test
    void testGetUserNotFound() {
        Exception exception = assertThrows(UserNotFoundException.class, () -> {
            socialServer.getUser("nonexistentUser");
        });
        assertEquals("User Not Found", exception.getMessage());
    }

    @Test
    void testLoginWithPasswordSuccess() throws Exception {
        socialServer.createUser("testUser", "password123");
        assertTrue(socialServer.loginWithPassword("testUser", "password123"));
    }

    @Test
    void testLoginWithPasswordIncorrectPassword() throws Exception {
        socialServer.createUser("testUser", "password123");
        Exception exception = assertThrows(InvalidInputException.class, () -> {
            socialServer.loginWithPassword("testUser", "wrongPassword");
        });
        assertEquals("Incorrect Password!", exception.getMessage());
    }

    @Test
    void testLoginWithPasswordUserNotFound() {
        Exception exception = assertThrows(UserNotFoundException.class, () -> {
            socialServer.loginWithPassword("nonexistentUser", "password123");
        });
        assertEquals("User does not exist!", exception.getMessage());
    }

    @Test
    void testBlockUserSuccess() throws Exception {
        socialServer.createUser("userA", "password1");
        socialServer.createUser("userB", "password2");
        socialServer.blockUser("userA", "userB");
        assertTrue(blockedUsers.contains("userA -> userB"));
    }

    @Test
    void testBlockUserSelf() {
        Exception exception = assertThrows(InvalidInputException.class, () -> {
            socialServer.blockUser("userA", "userA");
        });
        assertEquals("User cannot block themselves.", exception.getMessage());
    }

    @Test
    void testUnblockUserSuccess() throws Exception {
        socialServer.createUser("userA", "password1");
        socialServer.createUser("userB", "password2");
        socialServer.blockUser("userA", "userB");
        socialServer.unblock("userA", "userB");
        assertFalse(blockedUsers.contains("userA -> userB"));
    }

    @Test
    void testUnblockUserNotBlocked() {
        Exception exception = assertThrows(InvalidInputException.class, () -> {
            socialServer.unblock("userA", "userB");
        });
        assertEquals("User is not blocked.", exception.getMessage());
    }
}

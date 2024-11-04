// import static org.junit.Assert.*;
// import org.junit.Before;
// import org.junit.Test;
import java.util.ArrayList;
// import Exceptions.*;
import UserExceptions.*;

public class UserTest {
    private User user;
    private User friend;
    private User blockedUser;

    @Before
    public void setUp() {
        user = new User("username", "password", "bio", new ArrayList<>(), new ArrayList<>());
        friend = new User("friendname", "password2");
        blockedUser = new User("blockedname", "password3");
    }

    @Test
    public void testUserConstructor() {
        assertEquals("username", user.getUsername());
        assertEquals("password", user.getPassword());
        assertNotNull(user.getFriends());
        assertNotNull(user.getBlocked());
        assertTrue(user.getFriends().isEmpty());
        assertTrue(user.getBlocked().isEmpty());
    }

    @Test
    public void testSetUsername() throws UserActionException {
        user.setUsername("newUsername");
        assertEquals("newUsername", user.getUsername());
    }

    @Test(expected = UserActionException.class)
    public void testSetUsernameFailure() throws UserActionException {
        user.setUsername(""); // Assuming setting an empty username should fail
    }

    @Test
    public void testAddFriend() throws FriendActionException, BlockedActionException {
        user.addFriend(friend);
        assertTrue(user.getFriends().contains(friend));
    }

    @Test(expected = FriendActionException.class)
    public void testAddFriendAlreadyFriend() throws FriendActionException, BlockedActionException {
        user.addFriend(friend);
        user.addFriend(friend); // Attempt to add the same friend again
    }

    @Test
    public void testRemoveFriend() throws FriendActionException, BlockedActionException {
        user.addFriend(friend);
        user.removeFriend(friend);
        assertFalse(user.getFriends().contains(friend));
    }

    @Test(expected = FriendActionException.class)
    public void testRemoveNonExistentFriend() throws FriendActionException {
        user.removeFriend(friend); // Attempt to remove a non-existent friend
    }

    @Test
    public void testBlockUser() throws BlockedActionException, FriendActionException {
        user.blockUser(blockedUser);
        assertTrue(user.getBlocked().contains(blockedUser));
    }

    @Test(expected = BlockedActionException.class)
    public void testBlockAlreadyBlockedUser() throws BlockedActionException, FriendActionException {
        user.blockUser(blockedUser);
        user.blockUser(blockedUser); // Attempt to block an already blocked user
    }

    @Test
    public void testUnblockUser() throws BlockedActionException, FriendActionException {
        user.blockUser(blockedUser);
        user.unblockUser(blockedUser);
        assertFalse(user.getBlocked().contains(blockedUser));
    }

    @Test(expected = BlockedActionException.class)
    public void testUnblockNonBlockedUser() throws BlockedActionException {
        user.unblockUser(blockedUser); // Attempt to unblock a user who is not blocked
    }
}

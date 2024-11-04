import java.util.ArrayList;
import UserExceptions.*;


public interface UserInterface {
    public void setUsername(String newUsername) throws UserActionException;

    public void setPassword(String newPassword) throws UserActionException;

    public void setProfilePicture(String imgPath) throws UserActionException;

    public void setBio(String bio);

    public void addFriend(User friend) throws FriendActionException, BlockedActionException;

    public void removeFriend(User friend) throws FriendActionException;

    public void blockUser(User user) throws BlockedActionException, FriendActionException;

    public void unblockUser(User user) throws BlockedActionException;

    public String getUsername();

    public String getPassword();

    public String getProfilePicture();

    public String getBio();

    public ArrayList<User> getFriends() throws FriendActionException, BlockedActionException;

    public ArrayList<User> getBlocked() throws BlockedActionException,FriendActionException;
}

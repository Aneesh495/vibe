import java.util.ArrayList;
// import Exceptions.*;
import UserExceptions.*;


public interface UserInterface {
    public void setUsername(String newUsername) throws UserActionException;

    public void setPassword(String newPassword) throws UserActionException;

    public void setProfilePicture(String imgPath) throws UserActionException;

    public void setBio(String bio);

    public void addFriend(String friend) throws FriendActionException, BlockedActionException;

    public void removeFriend(String friend) throws FriendActionException;

    public void blockUser(String user) throws BlockedActionException, FriendActionException;

    public void unblockUser(String user) throws BlockedActionException;

    public String getUsername();

    public String getPassword();

    public String getProfilePicture();

    public String getBio();

    public ArrayList<String> getFriends() throws FriendActionException, BlockedActionException;

    public ArrayList<String> getBlocked() throws BlockedActionException,FriendActionException;
}

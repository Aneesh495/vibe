import java.util.ArrayList;

public interface UserInterface {
    public void setUsername(String newUsername);

    public void setPassword(String newPassword);

    public void setProfilePicture(String imgPath);

    public void setBio(String bio);

    public void addFriend(User friend);

    public void removeFriend(User friend);

    public void blockUser(User user);

    public void unblockUser(User user);

    public String getUsername();
    public String getPassword();
    public String getProfilePicture();
    public String getBio();
    public ArrayList<User> getFriends();
    public ArrayList<User> getBlocked();
}

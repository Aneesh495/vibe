import java.util.ArrayList;
import java.io.*;
import java.util.*;

public interface UserInterface {
    public void setUsername();

    public void setPassword();

    public void setPFP();

    public void setBio();

    public void addFriend();

    public void removeFriend();

    public void blockUser();

    public void unblockUser();

    public String getUsername();

    public String getPassword();

    public String getPFP();

    public String getBio();

    public ArrayList<String> getFriends();

    public ArrayList<String> getBlocked();
}

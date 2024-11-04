// import Exceptions.*;
import Exceptions.UserExceptions.*;

import java.io.File;
import java.util.ArrayList;
import java.util.Scanner;

class User implements UserInterface {
    private static int totalUsers = 0;
    private String userName;
    private String userPassword;
    private File profilePicture;
    private String bio;
    private ArrayList<User> friends;
    private ArrayList<User> blocked;

    // User initializers
    public User(String username, String password, String profilePicture, String bio, ArrayList<User> friends, ArrayList<User> blocked) {
        this.userName = username;
        this.userPassword = password;
        this.profilePicture = new File(profilePicture);
        this.bio = bio;
        this.friends = friends;
        this.blocked = blocked;
        totalUsers++;
    }

    public User(String username, String password) {
        this.userName = username;
        this.userPassword = password;
        this.profilePicture = new File("Phase 1/Database/ProfilePictures/default.png");
        this.bio = "";
        this.friends = new ArrayList<>();
        this.blocked = new ArrayList<>();
        totalUsers++;
    }

    public User(String username, String password, String bio, ArrayList<User> friends, ArrayList<User> blocked) {
        this.userName = username;
        this.userPassword = password;
        this.profilePicture = new File("Phase 1/Database/ProfilePictures/default.png");
        this.bio = bio;
        this.friends = friends;
        this.blocked = blocked;
        totalUsers++;
    }

    // User setters
    public void setUsername(String newUsername) throws UserActionException {
        System.out.println("Enter current password to confirm username change:");
        try (Scanner sc = new Scanner(System.in)) {
            String check = sc.nextLine();
            if (check.equals(userPassword)) {
                this.userName = newUsername;
                System.out.println("Username changed successfully.");
            } else {
                throw new UserActionException("Incorrect password. Unable to change username.");
            }
        }
    }

    public void setPassword(String newPassword) throws UserActionException {
        System.out.println("Enter current password to change to a new password:");
        try (Scanner sc = new Scanner(System.in)) {
            String check = sc.nextLine();
            if (check.equals(userPassword)) {
                this.userPassword = newPassword;
                System.out.println("Password changed successfully.");
            } else {
                throw new UserActionException("Incorrect password. Unable to change password.");
            }
        }
    }

    public void setProfilePicture(String imgPath) throws UserActionException {
        File f = new File(imgPath);
        if (!f.exists()) {
            throw new UserActionException("Image path does not exist.");
        }
        this.profilePicture = f;
        System.out.println("Profile picture updated.");
    }

    public void setBio(String bio) {
        this.bio = bio;
        System.out.println("Bio updated.");
    }

    public void addFriend(User friend) throws FriendActionException, BlockedActionException {
        if (friends.contains(friend)) {
            throw new FriendActionException("User is already a friend!");
        }
        if (blocked.contains(friend)) {
            throw new BlockedActionException("User has been blocked!");
        }
        friends.add(friend);
        System.out.println("Friend added successfully.");
    }

    public void removeFriend(User friend) throws FriendActionException {
        if (!friends.contains(friend)) {
            throw new FriendActionException("User isn't a friend!");
        }
        friends.remove(friend);
        System.out.println("Friend removed successfully.");
    }

    public void blockUser(User user) throws BlockedActionException, FriendActionException {
        if (friends.contains(user)) {
            throw new FriendActionException("Cannot block a friend! Unfriend them first.");
        }
        if (blocked.contains(user)) {
            throw new BlockedActionException("User is already blocked.");
        }
        blocked.add(user);
        System.out.println("User blocked successfully.");
    }

    public void unblockUser(User user) throws BlockedActionException {
        if (!blocked.contains(user)) {
            throw new BlockedActionException("User isn't blocked.");
        }
        blocked.remove(user);
        System.out.println("User unblocked successfully.");
    }


    // User getters
    public String getUsername() {
        return userName;
    }

    public String getPassword() {
        return userPassword;
    }

    public String getProfilePicture() {
        return profilePicture.getPath();
    }

    public String getBio() {
        return bio;
    }

    public ArrayList<User> getFriends() {
        return friends;
    }

    public ArrayList<User> getBlocked() {
        return blocked;
    }

    public String toString() {
        return String.format("%s | %s | %s | %s", userName, userPassword, profilePicture.getPath(), bio);
    }
}

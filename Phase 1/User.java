import java.io.IOException;
import java.util.ArrayList;
import java.util.Scanner;

class User implements UserInterface {
    private static int totalUsers = 0;
    private String userName;
    private String userPassword;
    private String profilePicture;
    private String bio;
    private ArrayList<User> friends;
    private ArrayList<User> blocked;

    public User(String username, String password, String profilePicture, String bio,ArrayList<User> friends, ArrayList<User> blocked) {
        this.userName = username;
        this.userPassword = password;
        this.profilePicture = "";
        this.bio = "";
        this.friends = friends; 
        this.blocked = blocked;
        totalUsers++;
    }

    // Setter methods
   
    public void setUsername(String newUsername) throws UsernameNotValidException {
        System.out.println("Enter current password to confirm username change:");
        try (Scanner sc = new Scanner(System.in)) {
            String check = sc.nextLine();
            if (check.equals(userPassword)) {
                this.userName = newUsername;
                System.out.println("Username changed successfully.");
            } else {
                System.out.println("Incorrect password, try again.");
            }
        }
    }

   
    public void setPassword(String newPassword) throws PasswordNotValidException {
        System.out.println("Enter current password to change to a new password:");
        try (Scanner sc = new Scanner(System.in)) {
            String check = sc.nextLine();
            if (check.equals(userPassword)) {
                this.userPassword = newPassword;
                System.out.println("Password changed successfully.");
            } else {
                System.out.println("Incorrect password, try again.");
            }
        }
    }

   
    public void setProfilePicture(String imgPath) {
        this.profilePicture = imgPath;
        System.out.println("Profile picture updated.");
    }

   
    public void setBio(String bio) throws UserBlockedException, UserNotFoundException {
        this.bio = bio;
        System.out.println("Bio updated.");
    }

   
    public void addFriend(User friend) throws UserBlockedException {
        if (!friends.contains(friend)) {
            friends.add(friend);
        }
    }

   
    public void removeFriend(User friend) {
        if (friends.contains(friend)) {
            friends.remove(friend);
        }
    }

   
    public void blockUser(User user) throws UserNotFriendException, UserNotBlockedException {
        if (!blocked.contains(user)) {
            blocked.add(user);
        }
    }

   
    public void unblockUser(User user) throws UserNotFriendException{
        if (blocked.contains(user)) {
            blocked.remove(user);
        }
    }

   
    public String getUsername() {
        return userName;
    }

   
    public String getPassword() {
        return userPassword;
    }

   
    public String getProfilePicture() {
        return profilePicture;
    }

   
    public String getBio() {
        return bio;
    }

   
    public ArrayList<User> getFriends() throws UserNotFriendException {
        return friends;
    }

   
    public ArrayList<User> getBlocked() throws UserBlockedException{
        return blocked;
    }
}

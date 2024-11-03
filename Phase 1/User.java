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
        } catch (UsernameNotValidException unve) {
            System.out.println ("Username taken!");
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
        } catch (PasswordNotValidException pnve) {
            System.out.println ("Password does not meet criteria!");
        }
    }


    public void setProfilePicture(String imgPath) {
        try {
            this.profilePicture = imgPath;
            System.out.println("Profile picture updated.");
        } catch (Exception e) {
            System.out.println("Invalid image");
        }
    }


    public void setBio(String bio) {
        try {
            this.bio = bio;
            System.out.println("Bio updated.");
        } catch (Exception e) {
            System.out.println("Invalid bio");
        }
    }


    public void addFriend(User friend) throws UserBlockedException, UserAlreadyFriendException {
        try {
            if (!friends.contains(friend)) {
                friends.add(friend);
            }
        } catch (UserBlockedException ube) {
            System.out.println("User has been blocked!");
        } catch (UserAlreadyFriendException uafe) {
            System.out.println("User is already a friend!");
        }
    }


    public void removeFriend(User friend) throws UserNotFriendException {
        try {
            if (friends.contains(friend)) {
                friends.remove(friend);
            }
        } catch (UserNotFriendException unfe) {
            System.out.println("User isn't a friend!");
        }
    }


    public void blockUser(User user) throws UserAlreadyFriendException, UserAlreadyBlockedException {
        try {
            if (!blocked.contains(user)) {
                blocked.add(user);
            }
        } catch (UserAlreadyFriendException unfe) {
            System.out.println("Can't block a friend! Unblock them first");
        } catch (UserAlreadyBlockedException uabe) {
            System.out.println("User is already blocked!");
        }
    }


    public void unblockUser(User user) throws UserNotBlockedException {
        try {
            if (blocked.contains(user)) {
                blocked.remove(user);
            }
        } catch (UserNotBlockedException unbe) {
            System.out.println("User isn't blocked!");
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
        try {
            return friends;
        } catch (UserNotFriendException unfe) {
            System.out.println("User does not have any friends!");
        }
    }


    public ArrayList<User> getBlocked() throws UserBlockedException{
        try {
            return blocked;
        } catch (UserBlockedException ube) {
            System.out.println("User has not blocked anyone!");
        }
    }
}

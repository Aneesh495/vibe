import java.io.*;
import java.util.*;

class User {
    private static int totalUsers;
    private String userName;
    // private String displayName // Add if we decide to add after discussion
    private String userPassword;
    private ArrayList < User > userList;

    public User(String username, String password) {
        this.userName = username;
        this.userPassword = password;
        userList.add(this);
    }

    // Setter methods for an instance should be only accessible to that particular
    // instance of User
    // Am using `private` right now for that purpose, can modify it once a solution
    // is verified.
    // Pretty sure it will be needed as a public class but for now
    
    private void setUsername() {
        System.out.println("Enter new username:");
        try (Scanner sc = new Scanner(System.in)) {
            String newName = sc.nextLine();
            System.out.println("Enter current password to confirm username change from " + userName + " to " + newName);
            String check = sc.nextLine();

            if (check.equals(userPassword)) {
                this.userName = newName;
            } else {
                System.out.println("Incorrect password, please try again");
            }
        }
    }

    private void setPassword(String password) {
        try (Scanner sc = new Scanner(System.in)) {
            System.out.println("Enter current password");
            String check = sc.nextLine();
            if (check.equals(userPassword)) {
                    this.userPassword = password;
                } 
            else {
                System.out.println("Incorrect password, please try again.");
                return;
            }
        }
    }
}

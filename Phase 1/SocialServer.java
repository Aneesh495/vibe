import java.io.*;
import java.util.*;

public class SocialServer implements Server {
    // private BufferedReader br;
    // private PrintWriter pw;
    private final String Users = "./Database/userPassword.txt";
    private final String UserInfo = "./Database/userInfo.txt";
    private final String FriendList = "./Database/friends.txt";
    private final String BlockedList = "./Database/blocked.txt";
    private final String Reported = "fileName";

    public SocialServer() {
        // this.br=br;
        // this.pw=pw;
        super();
    }

    public void createUser(String username, String password) { // Create user with username/password/default pfp
        try (PrintWriter pw = new PrintWriter(new FileWriter(Users))) {
            pw.write(username + " | " + password + " | " + "Default PFP");
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void createUser(String username, String password, String profilePicture) { // Creater user with
                                                                                      // username/password/pfp
        try (PrintWriter pw = new PrintWriter(new FileWriter(Users))) {
            pw.write(username + " | " + password + " | " + profilePicture);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void editUsername(User user, String newUsername) throws IOException {
        /*
         * LINES 46 TO 62 CAN BE REUSED EVERYWHERE
         * ArrayList userinfo = first read from UserInfo file
         * ArrayList userinfodetailed = read form UserInfo file and broken down into a
         * String for access to data
         * 
         * Make changes to username.
         * ArrayList updatedUserinfo = array after making changes
         */

        BufferedReader bfr = new BufferedReader(new FileReader(UserInfo));
        PrintWriter pw = new PrintWriter(new FileWriter(UserInfo));

        ArrayList<String> userinfo = new ArrayList<>();
        String line = bfr.readLine();
        while (line != null) {
            userinfo.add(line);
            bfr.readLine();
        }

        ArrayList<String[]> userinfodetailed = new ArrayList<>();
        for (int i = 0; i < userinfo.size(); i++) {
            String[] data = userinfo.get(i).split(" | ");
            userinfodetailed.get(i)[0] = data[0]; // username
            userinfodetailed.get(i)[1] = data[1]; // user Password
            userinfodetailed.get(i)[2] = data[2]; // user pfp
        }
        // Example format of userinfodetailed
        /*
         * < {garvt | garvpassword | Default PFP},
         * {aneeshk | aneeshpassword | aneeshkpfp.jpg}>
         */

        ArrayList<String> updatedUserinfo = new ArrayList<>();
        for (String[] details : userinfodetailed) { // 63-69 makes changes to arraylist
            if (details[0] == user.getUsername() && confirmWithPassword(user)) {
                details[0] = newUsername;
            }
            updatedUserinfo.add(details[0] + " | " + details[1] + " | " + details[2]);
        }
        for (String updatedLine : updatedUserinfo) {
            pw.write(updatedLine);
        }
        bfr.close();
        pw.close();
    }

    public void editUserPassword(User user, String newPassword) throws IOException {
        BufferedReader bfr = new BufferedReader(new FileReader(UserInfo));
        PrintWriter pw = new PrintWriter(new FileWriter(UserInfo));

        ArrayList<String> userinfo = new ArrayList<>();
        String line = bfr.readLine();
        while (line != null) {
            userinfo.add(line);
            bfr.readLine();
        }

        ArrayList<String[]> userinfodetailed = new ArrayList<>();
        for (int i = 0; i < userinfo.size(); i++) {
            String[] data = userinfo.get(i).split(" | ");
            userinfodetailed.get(i)[0] = data[0]; // username
            userinfodetailed.get(i)[1] = data[1]; // user Password
            userinfodetailed.get(i)[2] = data[2]; // user pfp
        }
        // Example format of userinfodetailed
        /*
         * < {garvt | garvpassword | Default PFP},
         * {aneeshk | aneeshpassword | aneeshkpfp.jpg}>
         */

        ArrayList<String> updatedUserinfo = new ArrayList<>();
        for (String[] details : userinfodetailed) { // 63-69 makes changes to arraylist
            if (details[1] == user.getPassword() && confirmWithPassword(user)) {
                details[1] = newPassword;
            }
            updatedUserinfo.add(details[0] + " | " + details[1] + " | " + details[2]);
        }
        for (String updatedLine : updatedUserinfo) {
            pw.write(updatedLine);
        }
        bfr.close();
        pw.close();
    }

    public void editUserPFP(User user, String newPFP) throws IOException { // will have to add overloaded methods

        BufferedReader bfr = new BufferedReader(new FileReader(UserInfo));
        PrintWriter pw = new PrintWriter(new FileWriter(UserInfo));

        ArrayList<String> userinfo = new ArrayList<>();
        String line = bfr.readLine();
        while (line != null) {
            userinfo.add(line);
            bfr.readLine();
        }

        ArrayList<String[]> userinfodetailed = new ArrayList<>();
        for (int i = 0; i < userinfo.size(); i++) {
            String[] data = userinfo.get(i).split(" | ");
            userinfodetailed.get(i)[0] = data[0]; // username
            userinfodetailed.get(i)[1] = data[1]; // user Password
            userinfodetailed.get(i)[2] = data[2]; // user pfp
        }
        // Example format of userinfodetailed
        /*
         * < {garvt | garvpassword | Default PFP},
         * {aneeshk | aneeshpassword | aneeshkpfp.jpg}>
         */

        ArrayList<String> updatedUserinfo = new ArrayList<>();
        for (String[] details : userinfodetailed) { // 63-69 makes changes to arraylist
            if (details[2] == user.getUsername() && confirmWithPassword(user)) {
                details[2] = newPFP;
            }
            updatedUserinfo.add(details[0] + " | " + details[1] + " | " + details[2]);
        }
        for (String updatedLine : updatedUserinfo) {
            pw.write(updatedLine);
        }
        bfr.close();
        pw.close();
    }

    public void blockUser(String userId) {

    }

    public void getMessage(String userId) {

    }

    public static boolean confirmWithPassword(User user) {
        Scanner sc = new Scanner(System.in);
        System.out.print("Enter your password to confirm action: ");
        String inputPw = sc.nextLine();
        sc.close();
        if (inputPw.equals(user.getPassword())) {
            return true;
        } else {
            System.out.println("Incorrect Password");
            return false;
        }
    }
}

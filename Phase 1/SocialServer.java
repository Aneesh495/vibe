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

    public static boolean confirmWithPassword(User user) {
        try {
            Scanner sc = new Scanner(System.in);
            System.out.print("Enter your password to confirm action: ");

            try {
                String inputPw = sc.nextLine();
                sc.close();
                if (inputPw.equals(user.getPassword())) {
                    return true;
                } else {
                    System.out.println("Incorrect Password");
                    return false;
                }
            } catch (NoSuchElementException e) {
                System.err.println("Error reading password input: " + e.getMessage());
                return false;
            } finally {
                sc.close(); // Ensure the scanner is closed in all cases
            }
        } catch (IOException e) {
            System.out.println("Invalid user");
        }
    }

    public void createUser(String username, String password) throws UsernameNotValidException,
            PasswordNotValidException { // Create user with username/password/default pfp
        try (PrintWriter pw = new PrintWriter(new FileWriter(Users))) {
            pw.write(username + " | " + password + " | " + "Default PFP");
        } catch (UsernameNotValidException unve) {
            System.out.println("Invalid username");
        } catch (PasswordNotValidException pnve) {
            System.out.println("Invalid password");
        }
    }

    @Override
    public void createUser(String username, String password, String profilePicture)
            throws UsernameNotValidException, PasswordNotValidException { // Creater user with // username/password/pfp
        try (PrintWriter pw = new PrintWriter(new FileWriter(Users))) {
            pw.write(username + " | " + password + " | " + profilePicture);
        } catch (UsernameNotValidException unve) {
            System.out.println("Invalid username");
        } catch (PasswordNotValidException pnve) {
            System.out.println("Invalid password");
        }
    }

    public void editUsername(User user, String newUsername) throws UsernameNotValidException {
        /*
         * LINES 46 TO 62 CAN BE REUSED EVERYWHERE
         * ArrayList userinfo = first read from UserInfo file
         * ArrayList userinfodetailed = read form UserInfo file and broken down into a
         * String for access to data
         *
         * Make changes to username.
         * ArrayList updatedUserinfo = array after making changes
         */
        try {
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
        } catch (UsernameNotValidException unve) {
            System.out.println("Username taken");
        }
    }

    public void editUserPassword(User user, String newPassword) throws PasswordNotValidException {
        try {
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
                    details[1] = newPassword;
                }
                updatedUserinfo.add(details[0] + " | " + details[1] + " | " + details[2]);
            }
            for (String updatedLine : updatedUserinfo) {
                pw.write(updatedLine);
            }
            bfr.close();
            pw.close();
        } catch (PasswordNotValidException pnve) {
            System.out.println("Invalid password");
        }
    }

    public void editUserPFP(User user, String newPFP) throws IOException {// will have to add overloaded methods
        try {
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
                    details[2] = newPFP;
                }
                updatedUserinfo.add(details[0] + " | " + details[1] + " | " + details[2]);
            }
            for (String updatedLine : updatedUserinfo) {
                pw.write(updatedLine);
            }
            bfr.close();
            pw.close();
        } catch (IOException io) {
            System.out.println("Invalid image");
        }
    }

    public void blockUser(String userId) throws UserBlockedException {
        try {

        } catch (UserBlockedException ube) {
            System.out.println("User already blocked!");
        }
    }

    public void getMessage(String userId) throws IOException {
        try {

        } catch (IOException io) {
            System.out.println("Invalid message");
        }
    }
}

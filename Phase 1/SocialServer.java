import java.io.*;
import java.util.*;
import Exceptions.ServerException.*;

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
                System.out.println("Error reading password input: " + e.getMessage());
                return false;
            } finally {
                sc.close(); 
            }
        }

    public void createUser(User user) throws InvalidCredentialsException, IOException {
        if (user.getUsername().length() < 3) {
            throw new InvalidCredentialsException("Invalid username: must be at least 3 characters.");
        }
        if (user.getUsername().length() < 6) {
            throw new InvalidCredentialsException("Invalid password: must be at least 6 characters.");
        }
        try (PrintWriter pw = new PrintWriter(new FileWriter("Users.txt"))) {
            pw.write(user.toString());
        }
    }
        

    public void createUser(String username, String password) throws InvalidCredentialsException, IOException {
    if (username.length() < 3) {
        
    }
    if (password.length() < 6) {
        throw new InvalidCredentialsException("Invalid password: must be at least 6 characters.");
    }

    try (PrintWriter pw = new PrintWriter(new FileWriter("Users.txt"))) {
        pw.write(username + " | " + password + " | Default PFP" + " | ");
    }
}


    public void editUsername(User user, String newUsername) throws InvalidCredentialsException, IOException, UserNotFoundException  {
        /*
         * LINES 46 TO 62 CAN BE REUSED EVERYWHERE
         * ArrayList userinfo = first read from UserInfo file
         * ArrayList userinfodetailed = read form UserInfo file and broken down into a
         * String for access to data
         *
         * Make changes to username.
         * ArrayList updatedUserinfo = array after making changes
         */
        if(newUsername.length()<3){
            throw new InvalidCredentialsException("Invalid username: must be at least 3 characters.");
        }
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

            if (updatedUserinfo.isEmpty()) {
                throw new UserNotFoundException("User not found in the system.");
            }

            for (String updatedLine : updatedUserinfo) {
                pw.write(updatedLine);
            }
            bfr.close();
            pw.close();
        } catch (IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void editUserPassword(User user, String newPassword) throws PasswordNotValidException {
        
        if (newPassword.length() < 6) {
            throw new InvalidCredentialsException("Invalid password: must be at least 6 characters.");
        }
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

            if (updatedUserinfo.isEmpty()) {
                throw new UserNotFoundException("User not found in the system.");
            }

            for (String updatedLine : updatedUserinfo) {
                pw.write(updatedLine);
            }
            bfr.close();
            pw.close();
        } catch (IOException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void editUserPFP(User user, String newPFP) throws IOException, UserNotFoundException {// will have to add overloaded methods
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

            if (updatedUserinfo.isEmpty()) {
                throw new UserNotFoundException("User not found in the system.");
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

// garvt | garvtpassword | garvtpfp | bio

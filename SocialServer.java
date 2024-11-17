import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;
import ServerException.CustomException;
import ServerException.InvalidInputException;
import ServerException.UserNotFoundException;
import java.util.ArrayList;

//TODO: Add asynchronous for race theory 
public class  SocialServer implements Runnable {
    private final String Users = "./Database/userPassword.txt";
    private final static String UserInfo = "./Database/Data/userInfo.txt";
    private final static String FriendList = "./Database/Data/friends.txt";
    private final static String BlockedList = "./Database/Data/blocked.txt";
    private final static String MessageList = "./Database/Data/msgs.txt";
    private final String Reported = "fileName";
    private Socket clientSocket;
    public static AtomicInteger messageID = new AtomicInteger(0);

    // User file Routes
    public static ArrayList<String[]> getMessage(String sender, String receiver) {
        ArrayList<String[]> messagesWithID = new ArrayList<>();
        try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) {
            String line = messageBr.readLine();
            while (line != null) {
                String[] data = line.split(" \\| ");
                if (data[0].equals(sender) && data[1].equals(receiver)) {
                    for (int i = 2; i < data.length; i++) {
                        String[] messageAndID = data[i].split(",");
                        messagesWithID.add(messageAndID);
                    }
                }
                line = messageBr.readLine(); // Move to the next line
            }
        } catch (IOException io) {
            System.out.println("Error reading file: " + io.getMessage());
        }
        return messagesWithID;
    }

    public static ArrayList<String[]> orderMessage (String user1, String user2) {

        ArrayList<String[]> messageUser1ToUser2 = getMessage(user1, user2);
        ArrayList<String[]> messageUser2ToUser1 = getMessage(user2, user1);

        ArrayList<String[]> combinedMessages = new ArrayList<>();
        combinedMessages.addAll(messagesUser1ToUser2);
        combinedMessages.addAll(messagesUser2ToUser1);

        for (int i = 0; i < combinedMessages.size() - 1; i++) {
            for (int j = 0; j < combinedMessages.size() - 1 - i; j++) {
                String[] message1 = combinedMessages.get(j);
                String[] message2 = combinedMessages.get(j + 1);
                int msgID1 = Integer.parseInt(message1[1]);
                int msgID2 = Integer.parseInt(message2[1]);

                if (msgID1 > msgID2) {
                    combinedMessages.set(j, message2);
                    combinedMessages.set(j + 1, message1);
                }
            }
        }
        return combinedMessages;
    }

    private static final ReentrantLock lock = new ReentrantLock();

    public  SocialServer(Socket socket) {
        this.clientSocket = socket;
    }

    // Returns a new user
    public static String getUser(String username) throws UserNotFoundException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");

                if (userInfo.length > 0 && userInfo[0].trim().equals(username.trim())) {
                    return line; //changing this line
                }
                line = userBr.readLine();
            }
            throw new UserNotFoundException("User Not Found");
        }
    }

    // verifies user exists in database
    public static boolean checkUser(String username) {
        try {
            getUser(username);
            return true;
        } catch (Exception e) { //changed the exceptions here
            return false;
        }
    }

    private static boolean loginWithPassword(String username, String password) throws UserNotFoundException, InvalidInputException, IOException{
        
        if(checkUser(username)== false){
            throw new UserNotFoundException("User does not exist!");
        }

        String[] userInfo = getUser(username).split(" | ");

        if(userInfo[1].equals(password)){
            return true;
        }
        throw new InvalidInputException("Incorrect Password!");
    }

    // Creates a new user
    private static void createUser(String username, String password, String profilePicture, String bio) throws InvalidInputException, IOException {
            if (checkUser(username) == false) {
                writeToFile(String.format("%s \\| %s \\| %s \\| %s", username, password, profilePicture, bio), UserInfo);
            } else {
                throw new InvalidInputException("User already exists.");
            }
    }

    // Creates a new user
    private static void createUser(String username, String password) throws InvalidInputException, IOException {
        try {
            if (checkUser(username) == false) {
                writeToFile(String.format("%s \\| %s \\| %s \\| %s", username, password, "Database/ProfilePicture/default.png", ""), UserInfo);
            } else {
                throw new InvalidInputException("User already exists.");
            }
        }
    }

// Change UserInfo Method is for now redundant
    /*
    // Change user information
    public static void changeUserInfo(String username, String newUsername, String password, String profilePicture,
                                      String bio) throws CustomException {
        ArrayList <String> userLines = new ArrayList <>(); // Array to store lines to rewrite

        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) {

            // loops through each line and rewrites each line to array and changes line that needs to be changed
            if (checkUser(username) == true) {
                String line = userBr.readLine();
                while (line != null) {
                    String[] userInfo = line.split("  \\|  ");
                    /*if (!userInfo[0].equals(username)) {
                        userLines.add(line);
                    } else {
                        userLines.add(String.format("%s \\| %s \\| %s \\| %s", newUsername, password, profilePicture, bio));
                    }
                    if (userInfo[0].equals(username)) {
                        userLines.add(String.format("%s \\| %s \\| %s \\| %s", newUsername, password, profilePicture, bio));
                    }
                    line = userBr.readLine();
                }
                // TODO: throw an error if  nothing found
                overwriteFile(userLines, UserInfo);
            } else {
                throw new UserNotFoundException("User doesn't Exist");
            }
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }
*/
    // User Blocked route

    // verifies user exists in blocked file
    public static boolean checkBlocked(String username) {
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userBlockedInfo = line.split(" \\| ");
                if (userBlockedInfo.length > 0 && userBlockedInfo[0].trim().equals(username.trim())) {
                    return true;  // The user exists as a blocker
                }
                line = userBr.readLine();
            }
            return false;  // The user does not exist as a blocker
        } catch (IOException e) {
            return false;
        }
    }

    public static ArrayList<String> getBlocked(String username) throws CustomException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userBlockedInfo = line.split(" \\| ");

                if (userBlockedInfo.length > 0 && userBlockedInfo[0].trim().equals(username.trim())) {
                    return new ArrayList<>(Arrays.asList(userBlockedInfo).subList(1, userBlockedInfo.length));
                }
                line = userBr.readLine();
            }
            throw new CustomException("User has not blocked anyone!");
        }
    }

    public static void blockUser(String username, String blockedUser) throws CustomException {
        if (username.trim().equals(blockedUser.trim())) throw new CustomException("User can not block themself");

        ArrayList<String> userLines = new ArrayList<>(); // Array to store lines to rewrite

        // Check if both users exist in the general user database
        boolean blockerExists = checkUser(username);
        boolean blockedExists = checkUser(blockedUser);

        if (!blockerExists) {
            throw new CustomException("Blocker does not exist in userInfo file.");
        }
        if (!blockedExists) {
            throw new CustomException("Cannot block " + blockedUser + " as user does not exist in userInfo file.");
        }

        // If both users exist, proceed with blocking logic
        boolean userFound = false;  // To track if the blocker already has an entry in BlockedList

        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();

            // Read through the file and build the new list of lines
            while (line != null) {
                String[] userInfo = line.split(" \\| ");

                if (userInfo[0].equals(username)) {
                    userFound = true;  // User already has a blocking entry
                    ArrayList<String> blockedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));

                    // Check if the user is already blocked to avoid duplicates
                    if (blockedUsers.contains(blockedUser)) {
                        throw new CustomException("User " + blockedUser + " is already blocked by " + username + ".");
                    } else {
                        // Add the new blocked user
                        blockedUsers.add(blockedUser);
                        userLines.add(username + " | " + String.join(" | ", blockedUsers));
                    }
                } else {
                    // Retain other lines as they are
                    userLines.add(line);
                }
                line = userBr.readLine();
            }

            // If the blocker has no entry in BlockedList, create a new line for them
            if (!userFound) {
                userLines.add(String.format("%s | %s", username, blockedUser));
            }

            // Overwrite the BlockedList file with the updated content
            overwriteFile(userLines, BlockedList);

        } catch (IOException e) {
            throw new CustomException("IO Exception occurred: " + e.getMessage());
        }
    }

    public static void unblock(String username, String unblockUser) throws CustomException, IOException {
        ArrayList<String> blockedLines = new ArrayList<>();

        // Check if both users exist
        if (!checkUser(username) || !checkUser(unblockUser)) {
            throw new CustomException("User not found");
        }

        // Check if the user has the specified user blocked
        if (!getBlocked(username).contains(unblockUser)) {
            throw new CustomException("User is not blocked");
        }

        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();

            while (line != null) {
                String[] userInfo = line.split(" \\| ");

                // If this line belongs to the blocker
                if (userInfo[0].equals(username)) {
                    // Get the current list of blocked users and remove the specified user
                    ArrayList<String> blockedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
                    blockedUsers.remove(unblockUser);

                    // If there are still blocked users, update the line
                    if (!blockedUsers.isEmpty()) {
                        blockedLines.add(username + " \\| " + String.join(" \\| ", blockedUsers));
                    }
                    // If no users remain blocked, skip adding this line (effectively removing it)
                } else {
                    // Keep other lines as they are
                    blockedLines.add(line);
                }

                line = userBr.readLine();
            }

            // Overwrite the file with the updated content
            overwriteFile(blockedLines, BlockedList);
            
            // unfriend User


        } catch (IOException e) {
            throw new CustomException("IO Exception occurred: " + e.getMessage());
        }
        
    }

    // Checks if a user has friended another user
    public static boolean checkFriend(String username) {
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userFriendInfo = line.split(" \\| ");
                if (userFriendInfo.length > 0 && userFriendInfo[0].trim().equals(username.trim())) {
                    return true;  // The user exists as a friender
                }
                line = userBr.readLine();
            }
            return false;  // The user does not exist as a friender
        } catch (IOException e) {
            System.out.println("IO Exception occurred: " + e.getMessage());
            return false;
        }
    }

    // Retrieves a list of friends for a given user
    public static ArrayList<String> getFriend(String username) throws CustomException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userFriendInfo = line.split(" \\| ");

                if (userFriendInfo.length > 0 && userFriendInfo[0].trim().equals(username.trim())) {
                    return new ArrayList<>(Arrays.asList(userFriendInfo).subList(1, userFriendInfo.length));
                }
                line = userBr.readLine();
            }
            throw new CustomException("User has not friended anyone!");
        } catch (IOException e) {
            throw new CustomException("IO Exception Occurred: " + e.getMessage());
        }
    }

    // Adds a user to another user's friend list
    public static void friendUser(String username, String friendedUser) throws CustomException {
        if (username.trim().equals(friendedUser.trim())) throw new CustomException("User cannot friend themself");

        ArrayList<String> userLines = new ArrayList<>(); // Array to store lines to rewrite

        // Check if both users exist in the general user database
        boolean frienderExists = checkUser(username);
        boolean friendedExists = checkUser(friendedUser);

        if (!frienderExists) {
            throw new CustomException("Friender does not exist in userInfo file.");
        }
        if (!friendedExists) {
            throw new CustomException("Cannot friend " + friendedUser + " as user does not exist in userInfo file.");
        }

        // If both users exist, proceed with friending logic
        boolean userFound = false;  // To track if the friender already has an entry in FriendList

        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();

            // Read through the file and build the new list of lines
            while (line != null) {
                String[] userInfo = line.split(" \\| ");

                if (userInfo[0].equals(username)) {
                    userFound = true;  // User already has a friending entry
                    ArrayList<String> friendedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));

                    // Check if the user is already friended to avoid duplicates
                    if (friendedUsers.contains(friendedUser)) {
                        throw new CustomException("User " + friendedUser + " is already friended by " + username + ".");
                    } else {
                        // Add the new friended user
                        friendedUsers.add(friendedUser);
                        userLines.add(username + " | " + String.join(" | ", friendedUsers));
                    }
                } else {
                    // Retain other lines as they are
                    userLines.add(line);
                }
                line = userBr.readLine();
            }

            // If the friender has no entry in FriendList, create a new line for them
            if (!userFound) {
                userLines.add(String.format("%s | %s", username, friendedUser));
            }

            // Overwrite the FriendList file with the updated content
            overwriteFile(userLines, FriendList);
            unfriend(username,friendedUser);
        } catch (IOException e) {
            throw new CustomException("IO Exception occurred: " + e.getMessage());
        }
    }

    // Removes a friend from a user's friend list
    public static void unfriend(String username, String unfriendUser) throws CustomException {
        ArrayList<String> friendLines = new ArrayList<>();
    
        // Check if both users exist
        if (!checkUser(username) || !checkUser(unfriendUser)) {
            throw new CustomException("User not found");
        }
    
        // Check if the user has the specified user friended
        if (!getFriend(username).contains(unfriendUser)) {
            throw new CustomException("User is not friended");
        }
    
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
    
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
    
                // If this line belongs to the friender
                if (userInfo[0].equals(username)) {
                    // Get the current list of friended users and remove the specified user
                    ArrayList<String> friendedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
                    friendedUsers.remove(unfriendUser);
    
                    // If there are still friended users, update the line
                    if (!friendedUsers.isEmpty()) {
                        friendLines.add(username + " | " + String.join(" | ", friendedUsers));
                    }
                    // If no users remain friended, skip adding this line (effectively removing it)
                } else {
                    // Keep other lines as they are
                    friendLines.add(line);
                }
    
                line = userBr.readLine();
            }
    
            // Overwrite the FriendList file with the updated content
            overwriteFile(friendLines, FriendList);
    
        } catch (IOException e) {
            throw new CustomException("IO Exception occurred: " + e.getMessage());
        }
    }
    
    // Messages routes

    // gets messages between two users
    /*
    public static String getMessage(String sender, String reciever) throws OperationFailedException,
            UserNotFoundException, MessagesNotFoundException{
        ArrayList<String> messages = new ArrayList<>();

        // checks to see if users exist
        if(!(checkUser(reciever) && checkUser(sender))){
            throw new UserNotFoundException("User not found");
        }

        try(BufferedReader br = new BufferedReader(new FileReader(MessageList))){
            String line = br.readLine();
            while(line !=null){
                if(line.contains(String.format("%s \\| %s",sender,reciever))){
                    String newMessage= "s"+ line.substring(line.indexOf(" : ")+3);
                    messages.add(newMessage);
                }else if(line.contains(String.format("%s \\| %s",reciever,sender))){

                    String newMessage= "r"+ line.substring(line.indexOf(" : ")+3);
                    messages.add(newMessage);
                }
                line = br.readLine();
            }
            if(messages.isEmpty()){
                throw new MessagesNotFoundException("No messages Found.");
            }
            String retMessage="";
            for(int i =0; i< messages.size();i++){
                retMessage+= messages.get(i);
                if(i!=messages.size()-1){
                    retMessage+=" \\| ";
                }
            }
            return retMessage;
        }catch(IOException e){
            throw new OperationFailedException(e.getMessage());
        }
    } */

    // messages between two users
    /*
    public static void message(String sender, String reciever, String message) throws OperationFailedException, UserNotFoundException{
        // checks to see if users exist
        if(!(checkUser(reciever) && checkUser(sender))){
            throw new UserNotFoundException("User not found");
        }
        try {
            writeToFile(String.format("%s \\| %s : %s",sender,reciever,message), MessageList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }
    */

    // Writes to user File(appends Information)
    public static void writeToFile(String text, String filePath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true), true)) {
            pw.println(text);
        }
    }

    // Writes to user File(overwrite Information)
    public static void overwriteFile(ArrayList < String > userLines, String filePath) throws IOException, CustomException {
        try (PrintWriter pwTemp = new PrintWriter(filePath)) { // creates temporary print writer to overwrite
            // rewrites all lines to file
            for (String newLine: userLines) {
                pwTemp.println(newLine);
            }
        }
    }
    // File routes

    // Handles Queries

    public static String handleRequest(String action, String caller, String data) {
        try {
            System.out.println("Action: " + action);
            System.out.println("Caller: " + caller);
            System.out.println("Data: " + data);

            String[] userInformation = data.split(" \\| ");

            switch (action) {
                case "createUser":
                    if (userInformation.length == 2) {
                        createUser(userInformation[0], userInformation[1]);
                        return "User created successfully";
                    } else if (userInformation.length == 4) {
                        createUser(userInformation[0], userInformation[1], userInformation[2], userInformation[3]);
                        return "User created successfully";
                    } else {
                        throw new CustomException("Invalid user creation data format.");
                    }

                case "getUser":
                    return getUser(data);  // Retrieve user information

                case "blockUser":
                    if (userInformation.length == 1) {
                        blockUser(caller, userInformation[0]);
                        return "User " + userInformation[0] + " blocked successfully by " + caller;
                    } else {
                        throw new CustomException("Invalid block data format. Expected target user.");
                    }

                case "getBlocked":
                    return "Blocked users by " + caller + ": " + getBlocked(caller);

                case "unblock":
                    if (userInformation.length == 1) {
                        unblock(caller, userInformation[0]);
                        return "User " + userInformation[0] + " unblocked successfully by " + caller;
                    } else {
                        throw new CustomException("Invalid unblock data format. Expected target user.");
                    }

                case "friendUser":
                    if (userInformation.length == 1) {
                        friendUser(caller, userInformation[0]);
                        return "User " + userInformation[0] + " friended successfully by " + caller;
                    } else {
                        throw new CustomException("Invalid friend data format. Expected target user.");
                    }

                case "getFriend":
                    return "Friends of " + caller + ": " + getFriend(caller);

                case "unfriend":
                    if (userInformation.length == 1) {
                        unfriend(caller, userInformation[0]);
                        return "User " + userInformation[0] + " unfriended successfully by " + caller;
                    } else {
                        throw new CustomException("Invalid unfriend data format. Expected target user.");
                    }

                /*case "message":
                    if (userInformation.length == 2) {
                        message(caller, userInformation[0], userInformation[1]);
                        return "Message sent from " + caller + " to " + userInformation[0];
                    } else {
                        throw new CustomException("Invalid message data format. Expected recipient and message.");
                    }

                case "getMessage":
                    if (userInformation.length == 1) {
                        return "Messages between " + caller + " and " + userInformation[0] + ": " + getMessage(caller, userInformation[0]);
                    } else {
                        throw new CustomException("Invalid getMessage data format. Expected target user.");
                    }*/

                default:
                    throw new CustomException("Invalid action: " + action);
            }
        } catch (CustomException e) {
            return "Error: " + e.getMessage();
        }
    }


    // Thread-safe implementation of run method
    @Override
    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true)) {

            String line;
            while ((line = br.readLine()) != null) {
                try {
                    String[] command = line.split(" ; ");
                    if (command.length < 3) {
                        pw.println("Invalid request format. Expected format: <action> ; <caller> ; <data>");
                        continue;
                    }

                    String action = command[0];
                    String caller = command[1];
                    String data = command[2];

                    // Locking the access to shared data resources
                    lock.lock();
                    try {
                        String response = handleRequest(action, caller, data);
                        pw.println(response);
                    } finally {
                        lock.unlock();
                    }
                } catch (Exception e) {
                    pw.println("Error processing request: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        } catch (IOException e) {
            System.out.println("Error with client connection: " + e.getMessage());
        } finally {
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Failed to close client socket.");
            }
        }
    }

    // Main server method to listen for connections and spawn new threads
    public static void main(String[] args) {
        try (ServerSocket serverSocket = new ServerSocket(4242)) {
            System.out.println("Server running on port 4242...");

            while (true) {
                Socket clientSocket = serverSocket.accept();
                System.out.println("Client Connected");

                 SocialServer server = new  SocialServer(clientSocket);
                Thread clientThread = new Thread(server);
                clientThread.start();
            }
        } catch (IOException e) {
            System.out.println("Server failed: " + e.getMessage());
        }
    }
}

    /*
    public static void main(String[] args) {
        try {
            // Test creating a user
            System.out.println("Creating User...");
            createUser("testuser", "password123", "profile.png", "This is a test bio");

            // Test checking if a user exists
            System.out.println("Checking if user exists...");
            System.out.println("User exists: " + checkUser("testuser"));

            // Test getting user information
            System.out.println("Getting User Information...");
            System.out.println(getUser("testuser"));

            // Test checking if user is blocked

            System.out.println("Checking if user is blocked...");
            System.out.println(checkBlocked("testuser"));

            // Test blocking a user
            System.out.println("Blocking user...");
            blockUser("testuser", "alice");

            // Test getting blocked users
            System.out.println("Getting blocked users...");
            System.out.println(getBlocked("lakshaym"));

            // Test unblocking a user
            System.out.println("Unblocking user...");
            unblock("testuser", "alice");

            // Test friending a user
            System.out.println("Friending user...");
            friendUser("testuser", "lakshaym");

            // Test getting friend list
            System.out.println("Getting friends...");
            System.out.println(getFriend("testuser"));

            // Test unfriending a user
            System.out.println("Unfriending user...");
            unfriend("testuser", "lakshaym");

            // Test writing and overwriting files
            System.out.println("Testing file write and overwrite...");
            writeToFile("test data", "./testFile.txt");

            // Test overwriting a file
            ArrayList<String> lines = new ArrayList<>();
            lines.add("new line");
            overwriteFile(lines, "./testFile.txt");

        } catch (CustomException | IOException e) {
            e.printStackTrace();
        }
    }
     */ // Test for each method. DONT DELETE.



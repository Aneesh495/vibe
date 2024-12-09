import java.io.*;
import java.util.*;
import java.net.*;
import java.nio.Buffer;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

// Assuming you have custom exception classes in ServerException package
// import ServerException.*;

public class SocialServer implements Runnable {
    private final static String UserInfo = "./Database/Data/userInfo.txt";
    private final static String FriendList = "./Database/Data/friends.txt";
    private final static String BlockedList = "./Database/Data/blocked.txt";
    private final static String MessageList = "./Database/Data/msgs.txt";
    private static final ReentrantLock lock = new ReentrantLock();
    //public static AtomicInteger messageID = new AtomicInteger(0);
    public static String messageID = "";
    private final String Users = "./Database/userPassword.txt";
    private final String Reported = "fileName";
    private Socket clientSocket;

    public SocialServer(Socket socket) {
        this.clientSocket = socket;
    }

    // USER ROUTES

    // Create a new user
    private void createUser(String username, String password, String profilePicture, String bio)
            throws InvalidInputException, IOException {
        if (checkUser(username) == false) {
            writeToFile(String.format("%s | %s | %s | %s", username, password, profilePicture, bio), UserInfo);
        } else {
            throw new InvalidInputException("User already exists.");
        }
    }

    // creates a new user with less parameters
    public void createUser(String username, String password) throws InvalidInputException, IOException {
        if (checkUser(username) == false) {
            writeToFile(String.format("%s | %s | %s | %s", username, password,
                    "Database/ProfilePicture/default.png", ""), UserInfo);
        } else {
            throw new InvalidInputException("User already exists.");
        }
    }

    // Get a specific user's information
    public static String getUser(String username) throws UserNotFoundException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo.length > 0 && userInfo[0].trim().equals(username.trim())) {
                    return line;
                }
                line = userBr.readLine();
            }
            throw new UserNotFoundException("User Not Found");
        }
    }
    public static void editUser(String username, String info) throws UserNotFoundException, IOException {
        String[] data = info.split(",");
        editUser(username, data[0], data[1], data[2], data[3]);
    }

    public static void editUser(String username,String newUsername, String newPassword, String newProfilePicture, String newBio) throws UserNotFoundException, IOException {
        ArrayList<String> userInfo = new ArrayList<>();
        try(BufferedReader br = new BufferedReader(new FileReader(UserInfo))){
            String line = br.readLine();
            System.out.println(String.format("%s | %s | %s | %s", username, newPassword,
            newProfilePicture, newBio));
            while(line!=null){
                if(line.contains(username)){
                    userInfo.add(String.format("%s | %s | %s | %s", username, newPassword,
                    newProfilePicture, newBio));
                }else{
                    userInfo.add(line);
                }
                line=br.readLine();
            }
        }
        overwriteFile(userInfo, UserInfo);
    }

    // Check if user exists
    public static boolean checkUser(String username) {
        try {
            getUser(username);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // USER AUTH ROUTE
    public boolean loginWithPassword(String username, String password)
            throws UserNotFoundException, InvalidInputException, IOException {
        if (checkUser(username) == false) {
            throw new UserNotFoundException("User does not exist!");
        }
        String[] userInfo = getUser(username).split(" \\| ");
        if (userInfo[1].equals(password)) {
            return true;
        }
        throw new InvalidInputException("Incorrect Password!");
    }

    // BLOCK ROUTES

    // Check if a user is blocked
    public static boolean checkBlocked(String username, String blockedUser) {
        try {
            if (getBlocked(username).contains(blockedUser)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Get blocked users for a specific user
    public static ArrayList<String> getBlocked(String username) throws InvalidInputException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userBlockedInfo = line.split(" \\| ");
                if (userBlockedInfo.length > 0 && userBlockedInfo[0].trim().equals(username.trim())) {
                    return new ArrayList<>(Arrays.asList(userBlockedInfo).subList(1, userBlockedInfo.length));
                }
                line = userBr.readLine();
            }
            throw new InvalidInputException("User is not blocked!");
        }
    }

    // Block a user
    public void blockUser(String username, String blockedUser)
            throws InvalidInputException, UserNotFoundException, IOException {
        if (username.trim().equals(blockedUser.trim())) {
            throw new InvalidInputException("User cannot block themselves!");
        }
        ArrayList<String> userLines = new ArrayList<>();
        boolean blockerExists = checkUser(username);
        boolean blockedExists = checkUser(blockedUser);

        if (!blockerExists || !blockedExists) {
            throw new UserNotFoundException("One or both users do not exist.");
        }

        if (checkBlocked(username, blockedUser)) {
            throw new InvalidInputException("User is already blocked");
        }

        boolean userFound = false;
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo[0].equals(username)) {
                    ArrayList<String> blockedUsers = new ArrayList<>(
                            Arrays.asList(userInfo).subList(1, userInfo.length));
                    if (!blockedUsers.contains(blockedUser)) {
                        blockedUsers.add(blockedUser);
                    }
                    userLines.add(username + " | " + String.join(" | ", blockedUsers));
                    userFound = true;
                } else {
                    userLines.add(line);
                }
                line = userBr.readLine();
            }
        }
        if (!userFound) {
            userLines.add(username + " | " + blockedUser);
        }
        overwriteFile(userLines, BlockedList);
    }

    // Unblock a user
    public void unblock(String username, String unblockUser)
            throws UserNotFoundException, IOException, InvalidInputException {
        ArrayList<String> blockedLines = new ArrayList<>();

        if (!checkUser(username) || !checkUser(unblockUser)) {
            throw new UserNotFoundException("User not found");
        }

        if (!checkBlocked(username, unblockUser)) {
            throw new InvalidInputException("User is already unblocked");
        }

        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo[0].equals(username)) {
                    ArrayList<String> blockedUsers = new ArrayList<>(
                            Arrays.asList(userInfo).subList(1, userInfo.length));
                    blockedUsers.remove(unblockUser);
                    if (!blockedUsers.isEmpty()) {
                        blockedLines.add(username + " | " + String.join(" | ", blockedUsers));
                    }
                } else {
                    blockedLines.add(line);
                }
                line = userBr.readLine();
            }
            overwriteFile(blockedLines, BlockedList);
        }
    }

    // FRIEND USER ROUTES

    // Check if a user has friended another
    public static boolean checkFriend(String username, String friendCheck) {
        try {
            if (getFriend(username).contains(friendCheck)) {
                return true;
            }
            return false;
        } catch (Exception e) {
            return false;
        }
    }

    // Get friends of a user
    public static ArrayList<String> getFriend(String username) throws InvalidInputException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userFriendInfo = line.split(" \\| ");
                if (userFriendInfo.length > 0 && userFriendInfo[0].trim().equals(username.trim())) {
                    return new ArrayList<>(Arrays.asList(userFriendInfo).subList(1, userFriendInfo.length));
                }
                line = userBr.readLine();
            }
            throw new InvalidInputException("User has no friends!");
        }
    }

    // Friend a user
    public static void friendUser(String username, String friendedUser) throws InvalidInputException, IOException {
        // checks to see if user friended themselves
        if (username.trim().equals(friendedUser.trim())) {
            throw new InvalidInputException("User cannot friend themselves");
        }

        ArrayList<String> userLines = new ArrayList<>();

        // checks to see if users exist
        boolean frienderExists = checkUser(username);
        boolean friendedExists = checkUser(friendedUser);
        if (!frienderExists || !friendedExists) {
            throw new InvalidInputException("One or both users do not exist.");
        }

        // checks to see if users are already friends
        if (checkFriend(username, friendedUser)) {
            throw new InvalidInputException("Users are already friends");
        }

        boolean userFound = false;
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo[0].equals(username)) {
                    ArrayList<String> friendedUsers = new ArrayList<>(
                            Arrays.asList(userInfo).subList(1, userInfo.length));
                    if (!friendedUsers.contains(friendedUser)) {
                        friendedUsers.add(friendedUser);
                    }
                    userLines.add(username + " | " + String.join(" | ", friendedUsers));
                    userFound = true;
                } else {
                    userLines.add(line);
                }
                line = userBr.readLine();
            }
        }
        if (!userFound) {
            userLines.add(username + " | " + friendedUser);
        }
        overwriteFile(userLines, FriendList);
    }

    // Unfriend a user
    public static void unfriend(String username, String unfriendUser)
            throws UserNotFoundException, IOException, InvalidInputException {
        ArrayList<String> friendLines = new ArrayList<>();
        if (!checkUser(username) || !checkUser(unfriendUser)) {
            throw new UserNotFoundException("User not found");
        }

        // checks to see if users are already friends
        if (!checkFriend(username, unfriendUser)) {
            throw new InvalidInputException("Users are not friends");
        }

        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo[0].equals(username)) {
                    ArrayList<String> friendedUsers = new ArrayList<>(
                            Arrays.asList(userInfo).subList(1, userInfo.length));
                    friendedUsers.remove(unfriendUser);
                    if (!friendedUsers.isEmpty()) {
                        friendLines.add(username + " | " + String.join(" | ", friendedUsers));
                    }
                } else {
                    friendLines.add(line);
                }
                line = userBr.readLine();
            }
            overwriteFile(friendLines, FriendList);
        }
    }

    // MESSAGING ROUTES

    public boolean checkUserMessage(String user1, String user2) {
        try {
            this.getMessage(user1, user2);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // Retrieve messages between two users
    public String getMessage(String sender, String receiver) throws IOException, InvalidInputException {
        try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) {
            String line = messageBr.readLine();
            while (line != null) {
                String[] data = line.split(" \\| ");
                if (data.length >= 3 && ((data[0].equals(sender) && data[1].equals(receiver))
                        || (data[0].equals(receiver) && data[1].equals(sender)))) {
                    if (data[0].equals(sender)) {
                        return data[2];
                    } else {
                        // Swap #S# and #R# to correct the message direction
                        return data[2].replaceAll("#R#", "TEMP_PLACEHOLDER")
                                .replaceAll("#S#", "#R#")
                                .replaceAll("TEMP_PLACEHOLDER", "#S#");
                    }
                }
                line = messageBr.readLine();
            }
            throw new InvalidInputException("No messages found!");
        }
    }

    public int sendMessage(String sender, String receiver, String message)
            throws IOException, UserNotFoundException, InvalidInputException {
        ArrayList<String> messageLines = new ArrayList<>();
        int msgId = -1;
        if (!checkUser(sender) || !checkUser(receiver)) {
            throw new UserNotFoundException("One or both users do not exist.");
        }
        if (sender.equals(receiver)) {
            throw new InvalidInputException("Users cannot send a message to themselves.");
        }

        boolean conversationExists = false;
        try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) {
            String line = messageBr.readLine();
            while (line != null) {
                String[] data = line.split(" \\| ");
                if (data.length >= 3 && ((data[0].equals(sender) && data[1].equals(receiver))
                        || (data[0].equals(receiver) && data[1].equals(sender)))) {
                    conversationExists = true;
                    String messages = data[2];
                    int num = messages.split(";").length;
                    msgId = num;
                    String type = data[0].equals(sender) ? "S" : "R";
                    messages += " ; " + message + "-" + num + "#" + type + "#";
                    messageLines.add(data[0] + " | " + data[1] + " | " + messages);
                } else {
                    messageLines.add(line);
                }
                line = messageBr.readLine();
            }
        }
        if (!conversationExists) {
            String initialMessage = message + "-0#S#";
            messageLines.add(sender + " | " + receiver + " | " + initialMessage);
        }
        overwriteFile(messageLines, MessageList);
        return msgId;
    }

    public void deleteMessage(String sender, String reciever, int messageId) throws IOException, UserNotFoundException{
        ArrayList<String> messageLine = new ArrayList<>();

        System.out.println("works");
        // check if users exist
        if (!checkUser(sender) || !checkUser(reciever)) {
            throw new UserNotFoundException("User not found");
        }

        try (BufferedReader userBr = new BufferedReader(new FileReader(MessageList))) {
            // loop through file
            String line = userBr.readLine();
            while (line != null) {
                // check user info
                String[] userInfo = line.split(" \\| ");
                if ((userInfo[0].equals(sender) || userInfo[0].equals(reciever)) &&
                (userInfo[1].equals(reciever) || userInfo[1].equals(sender))) {
                    System.out.println(userInfo[2]);
                    String[] messages = userInfo[2].split("\\s*;\\s*");
                    String newLine= "";
                    for(int i=0;i<messages.length;i++){
                        if(messages[i].contains("-"+messageId+"#S#")){
                            messages[i]= "";
                        }else if(messages[i].contains("-"+messageId+"#R#")){
                            messages[i]= "";
                        }
                        newLine += messages[i];
                        if(i!=messages.length-1 && !messages[i].isEmpty()){
                            newLine+=" ; ";
                        }
                    }
                    if(!newLine.isEmpty()){
                        messageLine.add(String.format("%s | %s | %s", userInfo[0], userInfo[1],newLine));
                    }
                } else {
                    messageLine.add(line);
                }
                line = userBr.readLine();
            }
            overwriteFile(messageLine, MessageList);
        }
    }


    // FILE OPERATIONS

    public static void writeToFile(String text, String filePath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true), true)) {
            pw.println(text);
        }
    }

    public static void overwriteFile(ArrayList<String> userLines, String filePath) throws IOException {
        try (PrintWriter pwTemp = new PrintWriter(filePath)) {
            for (String newLine : userLines) {
                pwTemp.println(newLine);
            }
        }
    }

    // NEW METHOD: Get the list of users with whom the caller has a chat history
    public ArrayList<String> getChatList(String caller) throws IOException {
        ArrayList<String> chatUsers = new ArrayList<>();
        try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) {
            String line = messageBr.readLine();
            while (line != null) {
                String[] data = line.split(" \\| ");
                if (data.length >= 2) {
                    String user1 = data[0].trim();
                    String user2 = data[1].trim();
                    if (user1.equals(caller) && !chatUsers.contains(user2)) {
                        chatUsers.add(user2);
                    } else if (user2.equals(caller) && !chatUsers.contains(user1)) {
                        chatUsers.add(user1);
                    }
                }
                line = messageBr.readLine();
            }
        }
        return chatUsers;
    }

    public static void editMessage(String sender, String reciever, int messageId, String newMessage) throws IOException, UserNotFoundException{
            ArrayList<String> messageLine = new ArrayList<>();
    
            System.out.println("works");
            // check if users exist
            if (!checkUser(sender) || !checkUser(reciever)) {
                throw new UserNotFoundException("User not found");
            }
    
            try (BufferedReader userBr = new BufferedReader(new FileReader(MessageList))) {
                // loop through file
                String line = userBr.readLine();
                while (line != null) {
                    // check user info
                    String[] userInfo = line.split(" \\| ");
                    if ((userInfo[0].equals(sender) && userInfo[1].equals(reciever)) ||
                    (userInfo[0].equals(reciever) && userInfo[1].equals(sender))) {
                        System.out.println(userInfo[2]);
                        System.out.println("userFound");
                        String[] messages = userInfo[2].split("\s*;\s*");
                        String newLine= "";
                        for(int i=0;i<messages.length;i++){
                            if(messages[i].contains("-"+messageId+"#S#")){
                                messages[i]= String.format("%s-%d#S#",newMessage,messageId);
                            }else if(messages[i].contains("-"+messageId+"#R#")){
                                messages[i]= String.format("%s-%d#R#",newMessage,messageId);
                            }
                            newLine += messages[i];
                            if(i!=messages.length-1){
                                newLine+=" ; ";
                            }
                        }
                        messageLine.add(String.format("%s | %s | %s", userInfo[0], userInfo[1],newLine));
                    } else {
                        messageLine.add(line);
                    }
                    line = userBr.readLine();
                }
                overwriteFile(messageLine, MessageList);
            }
        }
    
        // REQUEST HANDLING
    
        public String handleRequest(String action, String caller, String data) {
            try {
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
                            throw new ClientDataException("Invalid user creation data format.");
                        }
                    case "editUser":
                        System.out.println(data);
                        if(userInformation.length>4){
                            editUser(caller, userInformation[1], userInformation[2], userInformation[3], userInformation[4]);
                            return "User edited succesfully";
                        }else{
                            throw new ClientDataException("Invalid edit user data format");
                        }
                    case "loginWithPassword":
                        loginWithPassword(caller, userInformation[0]);
                        return "Login successful";
                    case "getUser":
                        return getUser(data);
                    case "blockUser":
                        if (userInformation.length == 1) {
                            blockUser(caller, userInformation[0]);
                            return "User " + userInformation[0] + " blocked successfully by " + caller;
                        } else {
                            throw new ClientDataException("Invalid block data format. Expected target user.");
                        }
                    case "getBlocked":
                        return "Blocked users by " + caller + ": " + getBlocked(caller);
                    case "unblock":
                        if (userInformation.length == 1) {
                            unblock(caller, userInformation[0]);
                            return "User " + userInformation[0] + " unblocked successfully by " + caller;
                        } else {
                            throw new ClientDataException("Invalid unblock data format. Expected target user.");
                        }
                    case "friendUser":
                        if (userInformation.length == 1) {
                            friendUser(caller, userInformation[0]);
                            return "User " + userInformation[0] + " friended successfully by " + caller;
                        } else {
                            throw new ClientDataException("Invalid friend data format. Expected target user.");
                        }
                    case "getFriend":
                        return "Friends of " + caller + ": " + getFriend(caller);
                    case "unfriend":
                        if (userInformation.length == 1) {
                            unfriend(caller, userInformation[0]);
                            return "User " + userInformation[0] + " unfriended successfully by " + caller;
                        } else {
                            throw new ClientDataException("Invalid unfriend data format. Expected target user.");
                        }
                    case "sendMessage":
                        if (userInformation.length >= 2) {
                            return Integer.toString(sendMessage(caller, userInformation[0], userInformation[1]));
                        } else {
                            throw new ClientDataException("Invalid message data format.");
                        }
                    case "getMessage":
                        return getMessage(caller, userInformation[0]);
                    case "getChatList":
                        return String.join(" | ", getChatList(caller));
                    case "deleteMessage":
                        deleteMessage(caller, userInformation[0],Integer.parseInt(userInformation[1]));
                        return "Message deleted succesfully";
                    case "editMessage":
                        editMessage(caller, userInformation[0], Integer.parseInt(userInformation[1]), userInformation[2]);
                        return "Message Edited";
                    default:
                        throw new ClientDataException("Invalid action: " + action);
                }
            } catch (UserNotFoundException e) {
                return "User Error: " + e.getMessage();
            } catch (InvalidInputException e) {
                return "Input Error: " + e.getMessage();
            } catch (ClientDataException e) {
                return "Client Data Format Error: " + e.getMessage();
            } catch (IOException e) {
                return "Server Error: " + e.getMessage();
            }
        }
    
        // Main method
        public static void main(String[] args) throws UserNotFoundException, IOException {
            try (ServerSocket serverSocket = new ServerSocket(4242)) {
                System.out.println("Server running on port 4242...");
                while (true) {
                    Socket clientSocket = serverSocket.accept();
                    System.out.println("Client Connected");
                    SocialServer server = new SocialServer(clientSocket);
                    Thread clientThread = new Thread(server);
                    clientThread.start();
                }
            } catch (IOException e) {
                System.out.println("Server failed: " + e.getMessage());
            }
    }

    // THREAD MANAGEMENT
    public void run() {
        try (BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    // Use split with limit -1 to include trailing empty strings
                    String[] command = line.split(" ; ", -1);
                    if (command.length < 3) {
                        pw.println("Invalid request format. Expected format: <action> ; <caller> ; <data>");
                        continue;
                    }
                    String action = command[0];
                    String caller = command[1];
                    String data = command[2];
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
}

// Exception classes (place these in separate files if needed)
class InvalidInputException extends Exception {
    public InvalidInputException(String message) {
        super(message);
    }
}

class ClientDataException extends Exception {
    public ClientDataException(String message) {
        super(message);
    }
}

class UserNotFoundException extends Exception {
    public UserNotFoundException(String message) {
        super(message);
    }
}
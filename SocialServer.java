import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

import ServerException.*;

public class SocialServer implements Runnable {
    private final static String UserInfo = "./Database/Data/userInfo.txt";
    private final static String FriendList = "./Database/Data/friends.txt";
    private final static String BlockedList = "./Database/Data/blocked.txt";
    private final static String MessageList = "./Database/Data/msgs.txt";
    private static final ReentrantLock lock = new ReentrantLock();
    public static AtomicInteger messageID = new AtomicInteger(0);
    private final String Users = "./Database/userPassword.txt";
    private final String Reported = "fileName";
    private Socket clientSocket;

    public SocialServer(Socket socket) {
        this.clientSocket = socket;
    }

    // USER ROUTES


    // Create a new user
    private static void createUser(String username, String password, String profilePicture, String bio) throws InvalidInputException, IOException {
        if (checkUser(username) == false) {
            writeToFile(String.format("%s \\| %s \\| %s \\| %s", username, password, profilePicture, bio), UserInfo);
        } else {
            throw new InvalidInputException("User already exists.");
        }
    }

    // creates a new user with less parameters
    private static void createUser(String username, String password) throws InvalidInputException, IOException {
        if (checkUser(username) == false) {
            writeToFile(String.format("%s \\| %s \\| %s \\| %s", username, password, "Database/ProfilePicture/default.png", ""), UserInfo);
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
    private static boolean loginWithPassword(String username, String password) throws UserNotFoundException, InvalidInputException, IOException {
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
    public static void blockUser(String username, String blockedUser) throws InvalidInputException, UserNotFoundException, IOException {
        if (username.trim().equals(blockedUser.trim())) {
            throw new InvalidInputException("User can not block themself!");
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

        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo[0].equals(username)) {
                    ArrayList<String> blockedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
                    if (!blockedUsers.contains(blockedUser)) {
                        blockedUsers.add(blockedUser);
                    }
                    userLines.add(username + " | " + String.join(" | ", blockedUsers));
                } else {
                    userLines.add(line);
                }
                line = userBr.readLine();
            }
            overwriteFile(userLines, BlockedList);
        }
    }

    // Unblock a user
    public static void unblock(String username, String unblockUser) throws UserNotFoundException, IOException, InvalidInputException {
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
                    ArrayList<String> blockedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
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

    //FRIEND USER ROUTES

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
        // checks to see if user friended themself
        if (username.trim().equals(friendedUser.trim())) {
            throw new InvalidInputException("User cannot friend themself");
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
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split(" \\| ");
                if (userInfo[0].equals(username)) {
                    ArrayList<String> friendedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
                    if (!friendedUsers.contains(friendedUser)) {
                        friendedUsers.add(friendedUser);
                    }
                    userLines.add(username + " | " + String.join(" | ", friendedUsers));
                } else {
                    userLines.add(line);
                }
                line = userBr.readLine();
            }
            overwriteFile(userLines, FriendList);
        }
    }

    // Unfriend a user
    public static void unfriend(String username, String unfriendUser) throws UserNotFoundException, IOException, InvalidInputException {
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
                    ArrayList<String> friendedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
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

    //MESSAGING ROUTES

    public static boolean checkUserMessage(String user1, String user2){
        try{
            getMessage(user1, user2);
            return true;
        }catch(Exception e){
            return false;
        }
    }

    // Retrieve a message
    public static String getMessage(String sender, String receiver) throws IOException, InvalidInputException {
        try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) {
            String line = messageBr.readLine();
            while (line != null) {
                
                if(line.contains(sender)&& line.contains(receiver)){
                    String[] data = line.split(" \\| ");
                    if(data[0].equals(sender)){
                        return data[2];
                    }else{
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

    public static void sendMessage(String sender, String receiver, String message)throws IOException, UserNotFoundException, InvalidInputException {
        ArrayList<String> messageLine = new ArrayList<>();
        if (!checkUser(sender) || !checkUser(receiver)) {
            throw new UserNotFoundException("One of (or both) user(s) does not exist.");
        }
        if (sender.equals(receiver)) {
            throw new InvalidInputException("Users can not send a message to themselves.");
        }

        if(!checkUserMessage(sender,receiver)){
            writeToFile(String.format("%s | %s ; %s-%d#S#", sender,receiver, message, 0), MessageList);
        }else{
            System.out.println("run");
            try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) {
                String line = messageBr.readLine();
                while (line != null) {
                    if(line.contains(sender)&& line.contains(receiver)){
                        String[] data = line.split(" | ");
                        String type;
                        if(data[0].equals(sender)){
                            type = "S";
                        }else{
                            type = "R";
                        }
                        int num = Integer.parseInt(data[data.length-1].substring(data[data.length-1].indexOf('-') + 1, data[data.length-1].indexOf('#', data[data.length-1].indexOf('-'))));
                        num+=1;
                        messageLine.add(String.format("%s ; %s-%d#%s#", line, message,num,type));
                    }else{
                        messageLine.add(line);
                    }
                    line = messageBr.readLine();
                }
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

    // REQUEST HANDLING

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
                        throw new ClientDataException("Invalid user creation data format.");
                    }
                case "loginWithPassword":
                    if (userInformation.length == 1) {
                        System.out.println(userInformation);
                        loginWithPassword(caller, userInformation[0]);
                    }
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
                default:
                    throw new ClientDataException("Invalid action: " + action);
            }
        } catch (UserNotFoundException e) {
            return "User Error: " + e.getMessage();
        } catch (InvalidInputException e) {
            return "Input Error: " + e.getMessage();
        } catch (ClientDataException e) {
            return "Client Data Format Error" + e.getMessage();
        } catch (IOException e) {
            return "Server Error: " + e.getMessage();
        }
    }

    // Main method
    public static void main(String[] args) throws IOException, UserNotFoundException, InvalidInputException {
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

    // THREAD MANAGMENT
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

import java.io.*;
import java.util.*;
import java.net.*;
import java.util.concurrent.locks.ReentrantLock;
import java.util.concurrent.atomic.AtomicInteger;

import ServerException.*;
//import statements

public class SocialServer implements Runnable { //start runnable
    private final static String UserInfo = "./Database/Data/userInfo.txt";
    private final static String FriendList = "./Database/Data/friends.txt";
    private final static String BlockedList = "./Database/Data/blocked.txt";
    private final static String MessageList = "./Database/Data/msgs.txt";
    private static final ReentrantLock lock = new ReentrantLock();
    public static AtomicInteger messageID = new AtomicInteger(0);
    private final String Users = "./Database/userPassword.txt";
    private final String Reported = "fileName";
    private Socket clientSocket;

    public SocialServer(Socket socket) { //social Server constructor.
        this.clientSocket = socket;
    }

    // USER ROUTES


    // Create a new user
    private static void createUser(String username, String password, String profilePicture, String bio)
            throws InvalidInputException, IOException { //start createUser method.
        if (checkUser(username) == false) { //if user doesn't exist.
            writeToFile(String.format("%s \\| %s \\| %s \\| %s", username, password, profilePicture, bio), UserInfo);
        } else { //if user with given username already exists
            throw new InvalidInputException("User already exists.");
        }
    } //end createUser method.

    // creates a new user with fewer parameters
    private static void createUser(String username, String password) throws InvalidInputException, IOException {
        //creates a user without profile picture and bio parameters.
        if (checkUser(username) == false) { //if user doesn't exist
            writeToFile(String.format("%s \\| %s \\| %s \\| %s", username, password,
                    "Database/ProfilePicture/default.png", ""), UserInfo);
        } else {
            throw new InvalidInputException("User already exists.");
        }
    }

    // Get a specific user's information
    public static String getUser(String username) throws UserNotFoundException, IOException { //start getUser method.
        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userInfo = line.split(" \\| "); //splits array by "|"
                if (userInfo.length > 0 && userInfo[0].trim().equals(username.trim())) {
                    return line;
                }
                line = userBr.readLine();
            }
            throw new UserNotFoundException("User Not Found");
        }
    } //end getUser method.

    // Check if user exists
    public static boolean checkUser(String username) { //start checkUser method.
        try {
            getUser(username);
            return true;
        } catch (Exception e) { //if user doesn't exist.
            return false;
        }
    } //end checkUser method.


    // USER AUTH ROUTE
    private static boolean loginWithPassword(String username, String password) throws UserNotFoundException,
            InvalidInputException, IOException { //start loginWithPassword method.
        if (checkUser(username) == false) { //if user doesn't exist.
            throw new UserNotFoundException("User does not exist!");
        }
        String[] userInfo = getUser(username).split(" \\| "); //split array by "|"
        if (userInfo[1].equals(password)) { //if correct password is entered.
            return true;
        }
        throw new InvalidInputException("Incorrect Password!"); //if incorrect password is entered.
    } //end loginWithPassword method.


    // BLOCK ROUTES

    // Check if a user is blocked
    public static boolean checkBlocked(String username, String blockedUser) { //start checkBlocked method.
        try {
            if (getBlocked(username).contains(blockedUser)) { //if user 2 is blocked by user 1.
                return true; //blocked.
            }
            return false; //if user 2 isn't blocked by user 1.
        } catch (Exception e) {
            return false; //not blocked.
        }
    } //end checkBlocked method.

    // Get blocked users for a specific user
    public static ArrayList<String> getBlocked(String username) throws InvalidInputException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userBlockedInfo = line.split(" \\| "); //splits array by "|"
                if (userBlockedInfo.length > 0 && userBlockedInfo[0].trim().equals(username.trim())) {
                    return new ArrayList<>(Arrays.asList(userBlockedInfo).subList(1, userBlockedInfo.length));
                }
                line = userBr.readLine(); //continue reading from file.
            } //end while.
            throw new InvalidInputException("User is not blocked!");
        } //end try.
    } //end getBlocked method.

    // Block a user
    public static void blockUser(String username, String blockedUser) throws InvalidInputException,
            UserNotFoundException, IOException { //start blockUser method.
        if (username.trim().equals(blockedUser.trim())) { //if user tries blocking themselves.
            throw new InvalidInputException("User can not block themself!");
        } //end if.
        ArrayList<String> userLines = new ArrayList<>();
        boolean blockerExists = checkUser(username); //boolean - does user 1 exist.
        boolean blockedExists = checkUser(blockedUser); //boolean - does user 2 exist.

        if (!blockerExists || !blockedExists) { //if either user 1 or user 2 doesn't exist.
            throw new UserNotFoundException("One or both users do not exist.");
        } //end if.

        if (checkBlocked(username, blockedUser)) { //if user 1 already blocked user 2.
            throw new InvalidInputException("User is already blocked");
        } //end if.

        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userInfo = line.split(" \\| "); //splits array by "|"
                if (userInfo[0].equals(username)) {
                    ArrayList<String> blockedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1,
                            userInfo.length));
                    if (!blockedUsers.contains(blockedUser)) {
                        blockedUsers.add(blockedUser);
                    }
                    userLines.add(username + " | " + String.join(" | ", blockedUsers));
                } else {
                    userLines.add(line);
                }
                line = userBr.readLine();
            } //end while.
            overwriteFile(userLines, BlockedList);
        } //end try.
    } //end blockUser method.

    // Unblock a user
    public static void unblock(String username, String unblockUser) throws UserNotFoundException, IOException,
            InvalidInputException { //start unblock method.
        ArrayList<String> blockedLines = new ArrayList<>();

        if (!checkUser(username) || !checkUser(unblockUser)) { //if either user doesn't exist.
            throw new UserNotFoundException("User not found");
        } //end if.

        if (!checkBlocked(username, unblockUser)) { //if user 1 doesn't have user 2 blocked.
            throw new InvalidInputException("User is already unblocked");
        } //end if.

        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userInfo = line.split(" \\| "); //splits array by "|"
                if (userInfo[0].equals(username)) {
                    ArrayList<String> blockedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1,
                            userInfo.length));
                    blockedUsers.remove(unblockUser);
                    if (!blockedUsers.isEmpty()) {
                        blockedLines.add(username + " | " + String.join(" | ", blockedUsers));
                    }
                } else {
                    blockedLines.add(line);
                }
                line = userBr.readLine();
            } //end while.
            overwriteFile(blockedLines, BlockedList);
        } //end try.
    } //end unblock method.

    //FRIEND USER ROUTES

    // Check if a user has friended another
    public static boolean checkFriend(String username, String friendCheck) { //start checkFriend method.
        try {
            if (getFriend(username).contains(friendCheck)) { //if user 1 is friends with user 2.
                return true;
            }
            return false;
        } catch (Exception e) { //if user 1 isn't on user 2's friendlist.
            return false;
        }
    } //end checkFriend method.

    // Get friends of a user
    public static ArrayList<String> getFriend(String username) throws InvalidInputException, IOException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userFriendInfo = line.split(" \\| "); //splits array by "|"
                if (userFriendInfo.length > 0 && userFriendInfo[0].trim().equals(username.trim())) {
                    return new ArrayList<>(Arrays.asList(userFriendInfo).subList(1, userFriendInfo.length));
                }
                line = userBr.readLine();
            } //end while.
            throw new InvalidInputException("User has no friends!");
        } //end try.
    } //end getFriend method.

    // Friend a user
    public static void friendUser(String username, String friendedUser) throws InvalidInputException, IOException {
        // checks to see if user friended themself
        if (username.trim().equals(friendedUser.trim())) { //if user tries friending themself.
            throw new InvalidInputException("User cannot friend themself");
        } //end if.

        ArrayList<String> userLines = new ArrayList<>();

        // checks to see if users exist
        boolean frienderExists = checkUser(username); //boolean = user 1 exists.
        boolean friendedExists = checkUser(friendedUser); //boolean = user 2 exists.
        if (!frienderExists || !friendedExists) { //if either user doesn't exist.
            throw new InvalidInputException("One or both users do not exist.");
        } //end if.

        // checks to see if users are already friends
        if (checkFriend(username, friendedUser)) { //if users are already friends with each other.
            throw new InvalidInputException("Users are already friends");
        } //end if.
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userInfo = line.split(" \\| "); //splits array by "|"
                if (userInfo[0].equals(username)) {
                    ArrayList<String> friendedUsers = new ArrayList<>(Arrays.asList(userInfo).subList(1,
                            userInfo.length));
                    if (!friendedUsers.contains(friendedUser)) {
                        friendedUsers.add(friendedUser);
                    }
                    userLines.add(username + " | " + String.join(" | ", friendedUsers));
                } else {
                    userLines.add(line);
                }
                line = userBr.readLine();
            } //end while.
            overwriteFile(userLines, FriendList);
        } //end try.
    } //end friendUser method.

    // Unfriend a user
    public static void unfriend(String username, String unfriendUser) throws UserNotFoundException,
            IOException, InvalidInputException { //start unfriend method.
        ArrayList<String> friendLines = new ArrayList<>();
        if (!checkUser(username) || !checkUser(unfriendUser)) { //if either user doesn't exist
            throw new UserNotFoundException("User not found");
        } //end if.

        // checks to see if users are already friends
        if (!checkFriend(username, unfriendUser)) { //if users aren't friends with each other in the first place.
            throw new InvalidInputException("Users are not friends");
        } //end if.

        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) { //initialize buffered reader.
            String line = userBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                String[] userInfo = line.split(" \\| "); //splits array by "|"
                if (userInfo[0].equals(username)) {
                    ArrayList<String> friendedUsers =
                            new ArrayList<>(Arrays.asList(userInfo).subList(1, userInfo.length));
                    friendedUsers.remove(unfriendUser);
                    if (!friendedUsers.isEmpty()) {
                        friendLines.add(username + " | " + String.join(" | ", friendedUsers));
                    }
                } else {
                    friendLines.add(line);
                }
                line = userBr.readLine();
            } //end while.
            overwriteFile(friendLines, FriendList);
        } //end try.
    } //end unfriend method.

    //MESSAGING ROUTES

    public static boolean checkUserMessage(String user1, String user2) { //start checkUserMessage method.
        try {
            getMessage(user1, user2);
            return true;
        } catch (Exception e) {
            return false;
        }
    } //end checkUserMessage method.

    // Retrieve a message
    public static String getMessage(String sender, String receiver) throws IOException, InvalidInputException {
        try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) { //initialize buffered reader.
            String line = messageBr.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.

                if (line.contains(sender) && line.contains(receiver)) {
                    String[] data = line.split(" \\| "); //splits array by "|"
                    if (data[0].equals(sender)) {
                        return data[2];
                    } else {
                        return data[2].replaceAll("#R#", "TEMP_PLACEHOLDER")
                                .replaceAll("#S#", "#R#")
                                .replaceAll("TEMP_PLACEHOLDER", "#S#");
                    }
                }
                line = messageBr.readLine();
            }
            throw new InvalidInputException("No messages found!"); //if there is no message history between users.
        } //end try.
    } //end getMessage method.

    public static void sendMessage(String sender, String receiver, String message)
            throws IOException, UserNotFoundException, InvalidInputException { //start sendMessage method.
        ArrayList<String> messageLine = new ArrayList<>();
        if (!checkUser(sender) || !checkUser(receiver)) { //if either user doesn't exist.
            throw new UserNotFoundException("One of (or both) user(s) does not exist.");
        } //end if.
        if (sender.equals(receiver)) { //if user tries messaging themselves.
            throw new InvalidInputException("Users can not send a message to themselves.");
        } //end if.

        if (!checkUserMessage(sender, receiver)) {
            writeToFile(String.format("%s | %s ; %s-%d#S#", sender, receiver, message, 0), MessageList);
        } else {
            System.out.println("run");
            try (BufferedReader messageBr = new BufferedReader(new FileReader(MessageList))) { //initialize buffered reader.
                String line = messageBr.readLine(); //initialize string line to read from buffered reader.
                while (line != null) { //keeps looping as long as there is info to read.
                    if (line.contains(sender) && line.contains(receiver)) {
                        String[] data = line.split(" | "); //splits array by "|"
                        String type;
                        if (data[0].equals(sender)) {
                            type = "S";
                        } else {
                            type = "R";
                        }
                        int num = Integer.parseInt(data[data.length - 1].substring(data[data.length - 1].indexOf('-')
                                        + 1,
                                data[data.length - 1].indexOf('#', data[data.length - 1].indexOf('-'))));
                        num += 1;
                        messageLine.add(String.format("%s ; %s-%d#%s#", line, message, num, type));
                    } else {
                        messageLine.add(line);
                    }
                    line = messageBr.readLine();
                } //end while.
            } //end try.
            overwriteFile(messageLine, MessageList);
        } //end else.
    } //end sendMessage method.

    public static void editMessage(String sender, String receiver, int messageID, String newMessage)
            throws IOException, UserNotFoundException, InvalidInputException { //start editMessage method.
        ArrayList<String> messageLine = new ArrayList<>();
        boolean messageExists = false; //boolean to check is given message exists.
        if (checkUser(sender) == false || checkUser(receiver) == false) { //if either user doesn't exist.
            throw new UserNotFoundException("One (or both) user(s) specified were not found.");
        } //end if.

        try (BufferedReader br = new BufferedReader(new FileReader(MessageList))) { //initialize buffered reader.
            String line = br.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                if (line.contains(sender) && line.contains(receiver)) {
                    String[] parts = line.split("\\|"); //splits array by "|"
                    if (parts.length > 1) {
                        String[] messageHistory = parts[2].split(";");
                        StringBuilder editedConversation = new StringBuilder();
                        for (String c : messageHistory) {
                            if (c.contains("-" + messageID + "#S#") || c.contains("-" + messageID + "#R#")) {
                                messageExists = true;
                                c = c.substring(0, c.indexOf('-') + 1) + messageID + "#S#" + newMessage;
                            }
                            editedConversation.append(c).append(";");
                        }
                        line = parts[0] + " | " + parts[1] + " | " + editedConversation.toString();
                    }
                }
                messageLine.add(line);
                line = br.readLine();
            } //end while
        } //end try.
        if (messageExists == false) { //if message isn't found.
            throw new InvalidInputException("Message ID not found in files.");
        } //end if.
        overwriteFile(messageLine, MessageList);
    } //end edit message method.

    public static void deleteMessage(String sender, String receiver, int messageID) throws IOException,
            UserNotFoundException, InvalidInputException { //start deleteMessage method.
        ArrayList<String> messageLine = new ArrayList<>();
        boolean messageExists = false; //boolean to check is given message exists.
        if (checkUser(sender) == false || checkUser(receiver) == false) { //start if.
            //if either the specified user or receiver user doesn't exist.
            throw new UserNotFoundException("One (or both) user(s) specified were not found.");
        } //end if.
        try (BufferedReader br = new BufferedReader(new FileReader(MessageList))) { //initialize buffered reader
            String line = br.readLine(); //initialize string line to read from buffered reader.
            while (line != null) { //keeps looping as long as there is info to read.
                if (line.contains(sender) && line.contains(receiver)) {
                    String[] parts = line.split("\\|"); //splits array by "|"
                    if (parts.length > 1) {
                        String[] messageHistory = parts[2].split(";");
                        StringBuilder updatedConversation = new StringBuilder();
                        for (String c : messageHistory) {
                            if (c.contains("-" + messageID + "#S#") || c.contains("-" + messageID + "#R#")) {
                                messageExists = true;
                                continue;
                            }
                            updatedConversation.append(c).append(";");
                        }
                        line = parts[0] + " | " + parts[1] + " | " + updatedConversation.toString();
                    }
                }
                messageLine.add(line);
                line = br.readLine();
            } //end while.
        } //end try
        if (messageExists == false) { //if specified message doesn't exist.
            throw new InvalidInputException("Given Message not found with corresponding Message ID.");
        } //end if.
    } //end deleteMessage method.

    // FILE OPERATIONS

    public static void writeToFile(String text, String filePath) throws IOException { //start writeToFile method.
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true), true)) {
            pw.println(text);
        }
    } //end writeToFile method.

    public static void overwriteFile(ArrayList<String> userLines, String filePath) throws IOException {
        try (PrintWriter pwTemp = new PrintWriter(filePath)) {
            for (String newLine : userLines) {
                pwTemp.println(newLine);
            }
        } //end try.
    } //end overwriteFile method.

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
    } //end main.

    // THREAD MANAGMENT
    public void run() { //start run.
        try (BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
             //initialize buffered reader.
             PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true)) {
            String line;
            while ((line = br.readLine()) != null) {
                try {
                    String[] command = line.split(" ; "); //splits array by "|"
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
        } finally { //start finally.
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Failed to close client socket.");
            }
        } //end finally.
    } //end run.
} //end runnable

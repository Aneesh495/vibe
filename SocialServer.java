import java.io.*;
import java.util.*;

import ServerException.*;

import java.net.*;
//TODO: Add asynchronous for race theory 
public class SocialServer implements Runnable {
    private final String Users = "./Database/userPassword.txt";
    private final static String UserInfo = "./Database/Data/userInfo.txt";
    private final static String FriendList = "./Database/Data/friends.txt";
    private final static String BlockedList = "./Database/Data/blocked.txt";
    private final static String MessageList = "./Database/Data/msgs.txt";
    private final String Reported = "fileName";
    private Socket clientSocket;
    // private static BufferedReader userBr;
    // private static PrintWriter userPw;

    public SocialServer(Socket socket) {
        this.clientSocket = socket;
    }

    // User file Routes

    // Creates a new user
    private static void createUser(String username, String password, String profilePicture, String bio) throws OperationFailedException, UserAlreadyExistsException {
        try {
            if (!checkUser(username)) {
                writeToFile(String.format("%s | %s | %s | %s", username, password, profilePicture, bio), UserInfo);
            } else {
                throw new UserAlreadyExistsException("User already exists.");
            }
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }

    // Creates a new user
    private static void createUser(String username, String password) throws OperationFailedException, UserAlreadyExistsException {
        try {
            if (!checkUser(username)) {
                writeToFile(String.format("%s | %s | %s | %s", username, password, "Database/ProfilePicture/default.png", ""), UserInfo);
            } else {
                throw new UserAlreadyExistsException("User already exists.");
            }
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }

    // Returns a new user
    public static String getUser(String username) throws UserNotFoundException, OperationFailedException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userInfo = line.split("\\|");

                if (userInfo.length > 0 && userInfo[0].trim().equals(username.trim())) {
                    return line;
                }
                line = userBr.readLine();
            }
            throw new UserNotFoundException("User not found.");
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }


    // Change user information
    public static void changeUserInfo(String username, String newUsername, String password, String profilePicture, String bio) throws OperationFailedException, UserNotFoundException {
        ArrayList < String > userLines = new ArrayList < String > (); // Array to store lines to rewtire

        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) {

            // loops through each line and rewrites each line to array and changes line that needs to be changed
            if (checkUser(username)) {
                String line = userBr.readLine();
                while (line != null) {
                    String[] userInfo = line.split(" \\| ");
                    if (!userInfo[0].equals(username)) {
                        userLines.add(line);
                    } else {
                        userLines.add(String.format("%s | %s | %s | %s", newUsername, password, profilePicture, bio));
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

    // User Blocked route

    public static String getBlocked(String username) throws UserNotFoundException, OperationFailedException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userBlockedInfo = line.split(" \\| ");

                if (userBlockedInfo.length > 0 && userBlockedInfo[0].trim().equals(username.trim())) {
                    return line.substring(line.indexOf(" | ") + 3);
                }
                line = userBr.readLine();
            }
            throw new UserNotFoundException("User not Found!");
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }

    public static void blockUser(String username, String blockedUser) throws OperationFailedException, UserNotFoundException, UserAlreadyBlockedException {
        ArrayList < String > userLines = new ArrayList < String > (); // Array to store lines to rewtire

        // checks to see if both users exist
        if (checkUser(username) && checkUser(blockedUser)) {
            try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {

                // checks to see if user is already blocked
                if (getBlocked(username).contains(blockedUser)) {
                    throw new UserAlreadyBlockedException("User is already blocked.");
                }
                // checks to see if User exists within blocked File
                if (checkBlocked(username)) {
                    // If user exists adds to the users list of blocked
                    String line = userBr.readLine();
                    while (line != null) {
                        String[] userInfo = line.split(" \\| ");
                        if (!userInfo[0].equals(username)) {
                            userLines.add(line);
                        } else {
                            userLines.add(String.format("%s | %s", line, blockedUser));
                        }
                        line = userBr.readLine();
                    }
                    overwriteFile(userLines, BlockedList);
                } else {
                    // if user doesnt exists appends blocked information
                    writeToFile(String.format("%s | %s", username, blockedUser), BlockedList);
                }

            } catch (IOException e) {
                throw new OperationFailedException(e.getMessage());
            }
        } else {
            throw new UserNotFoundException("User does not exist");
        }
    }
    public static void unblock(String username, String unblockUser) throws UserNotBlockedException, UserNotFoundException, OperationFailedException {
        ArrayList < String > blockedLines = new ArrayList < > ();
        if (!getBlocked(username).contains(unblockUser)) {

            throw new UserNotBlockedException("User is not blocked");
        }
        if (!(checkUser(username) && checkUser(unblockUser))) {

            throw new UserNotBlockedException("User not found");
        }
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) {

            String line = userBr.readLine();
            while (line != null) {

                String[] userInfo = line.split(" \\| ");
                if (!userInfo[0].equals(username)) {
                    blockedLines.add(line);
                } else {
                    ArrayList < String > blockedList = new ArrayList < > (Arrays.asList(line.substring(line.indexOf(" | ") + 3).split(" \\| ")));
                    blockedList.remove(unblockUser);
                    String newLine = username;
                    for (String names: blockedList) {
                        newLine += " | ";
                        newLine += names;
                    }
                    blockedLines.add(newLine);
                }
                line = userBr.readLine();
            }
            overwriteFile(blockedLines, BlockedList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }

    // friend routes
    public static String getFriend(String username) throws UserNotFoundException, OperationFailedException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {
            String line = userBr.readLine();
            while (line != null) {
                String[] userFriendInfo = line.split(" \\| ");

                if (userFriendInfo.length > 0 && userFriendInfo[0].trim().equals(username.trim())) {
                    return line.substring(line.indexOf(" | ") + 3);
                }
                line = userBr.readLine();
            }
            throw new UserNotFoundException("User not Found!");
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }
    public static void friendUser(String username, String friendUser) throws OperationFailedException, UserNotFoundException, UserAlreadFriendException {
        ArrayList < String > userLines = new ArrayList < String > (); // Array to store lines to rewtire

        // checks to see if both users exist
        if (checkUser(username) && checkUser(friendUser)) {
            try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {

  
                // checks to see if User exists within friend File
                if (checkFriend(username)) {
                    // checks to see if user is already friend
                    if (getFriend(username).contains(friendUser)) {
                        throw new UserAlreadFriendException("User is already friend.");
                    }
                    // If user exists adds to the users list of blocked
                    String line = userBr.readLine();
                    while (line != null) {
                        String[] userInfo = line.split(" \\| ");
                        if (!userInfo[0].equals(username)) {
                            userLines.add(line);
                        } else {
                            userLines.add(String.format("%s | %s", line, friendUser));
                        }
                        line = userBr.readLine();
                    }
                    overwriteFile(userLines, FriendList);
                } else {
                    // if user doesnt exists appends blocked information
                    writeToFile(String.format("%s | %s", username, friendUser), FriendList);
                }

            } catch (IOException e) {
                throw new OperationFailedException(e.getMessage());
            }
        } else {
            throw new UserNotFoundException("User does not exist");
        }
    }



    public static void unfriend(String username, String unfriend) throws UserNotFriendException, UserNotFoundException, OperationFailedException {
        ArrayList < String > blockedLines = new ArrayList < > ();
        if (!getBlocked(username).contains(unfriend)) {

            throw new UserNotFriendException("User is not blocked");
        }
        if (!(checkUser(username) && checkUser(unfriend))) {

            throw new UserNotFriendException("User not found");
        }
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) {

            String line = userBr.readLine();
            while (line != null) {

                String[] userInfo = line.split(" \\| ");
                if (!userInfo[0].equals(username)) {
                    blockedLines.add(line);
                } else {
                    ArrayList < String > blockedList = new ArrayList < > (Arrays.asList(line.substring(line.indexOf(" | ") + 3).split(" \\| ")));
                    blockedList.remove(unfriend);
                    String newLine = username;
                    for (String names: blockedList) {
                        newLine += " | ";
                        newLine += names;
                    }
                    blockedLines.add(newLine);
                }
                line = userBr.readLine();
            }
            overwriteFile(blockedLines, FriendList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }

    // Messages routes

    // gets messages between two users
    public static String getMessage(String sender, String reciever) throws OperationFailedException, UserNotFoundException, MessagesNotFoundException{
        ArrayList<String> messages = new ArrayList<>();

        // checks to see if users exist
        if(!(checkUser(reciever) && checkUser(sender))){
            throw new UserNotFoundException("User not found");
        }
        
        try(BufferedReader br = new BufferedReader(new FileReader(MessageList))){
            String line = br.readLine();
            while(line !=null){
                if(line.contains(String.format("%s | %s",sender,reciever))){
                    String newMessage= "s"+ line.substring(line.indexOf(" : ")+3);
                    messages.add(newMessage);
                }else if(line.contains(String.format("%s | %s",reciever,sender))){

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
                    retMessage+=" | ";
                }
            }
            return retMessage;
        }catch(IOException e){
            throw new OperationFailedException(e.getMessage()); 
        }
    }

    // messages between two users
    public static void message(String sender, String reciever, String message) throws OperationFailedException, UserNotFoundException{
        // checks to see if users exist
        if(!(checkUser(reciever) && checkUser(sender))){
            throw new UserNotFoundException("User not found");
        }
        try {
            writeToFile(String.format("%s | %s : %s",sender,reciever,message), MessageList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage());
        }
    }
    

    // verifies user exists in database
    public static boolean checkUser(String username) throws OperationFailedException {
        try {
            getUser(username);
            return true;
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    // verifies user exists in friend Database
    public static boolean checkFriend(String username) throws OperationFailedException {
        try {
            getFriend(username);
            return true;
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    // verifies user exists in blocked file
    public static boolean checkBlocked(String username) throws OperationFailedException {
        try {
            getBlocked(username);
            return true;
        } catch (UserNotFoundException e) {
            return false;
        }
    }

    // File routes

    // Writes to user File(appends Information)
    public static void writeToFile(String text, String filePath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true), true)) {
            pw.println(text);
        }
    }

    // Writes to user File(overwrite Information)
    public static void overwriteFile(ArrayList < String > userLines, String filePath) throws IOException, OperationFailedException {
        try (PrintWriter pwTemp = new PrintWriter(filePath)) { // creates temporary print writer to overwrite
            // rewrites all lines to file
            for (String newLine: userLines) {
                pwTemp.println(newLine);
            }
        }
    }

    // Handles Queries
    public static String handleRequest(String action, String data) {
        try {
            System.out.println("action: "+ action);
            System.out.println("Data: "+ data);
            switch (action) {
                case "createUser":
                    String[] userInformation = data.split(" \\| ");
                    if (userInformation.length == 2) {
                        createUser(userInformation[0], userInformation[1]);
                        return "User created succesfully";
                    } else {
                        createUser(userInformation[0], userInformation[1], userInformation[2], userInformation[3]);
                        return "User created succesfully";
                    }
                case "getUser":
                    return getUser(data);
                case "changeUserInfo":
                    userInformation = data.split(" \\| ");
                    System.out.println("userInfo : " + userInformation[0]);
                    changeUserInfo(userInformation[0], userInformation[1], userInformation[2], userInformation[3], userInformation[4]);
                    return "User information changed succesfully";
                case "blockUser":
                    userInformation = data.split(" \\| ");
                    blockUser(userInformation[0], userInformation[1]);
                    return "User blocked Succesfully";
                case "getBlocked":
                    return getBlocked(data);
                case "unblock":
                    userInformation = data.split(" \\| ");
                    unblock(userInformation[0], userInformation[1]);
                case "friendUser":
                    userInformation = data.split(" \\| ");
                    friendUser(userInformation[0], userInformation[1]);
                case "getFriend":
                    return getFriend(data);
                case "unfriend":
                    userInformation = data.split(" \\| ");
                    unfriend(userInformation[0], userInformation[1]);
                case "message":
                    userInformation = data.split(" \\| ");
                    message(userInformation[0], userInformation[1], userInformation[2]);
                case "getMessage":
                    userInformation=data.split(" \\| ");
                    return getMessage(userInformation[0], userInformation[1]);

            }
            return "Invalid Query";
        } catch (UserAlreadyExistsException e) {
            return "User already exist.";
        } catch (UserNotFoundException e) {
            return "User does not exist.";
        } catch (UserAlreadyBlockedException e) {
            return "User already blocked.";
        } catch (UserNotBlockedException e) {
            return "User is not blocked";
        } catch (UserAlreadFriendException e) {
            return "User is already friend";
        } catch (UserNotFriendException e) {
            return "User is not friend.";
        } catch(MessagesNotFoundException e){
            return "Users have no messages.";
        } catch (OperationFailedException e) {
            return "500:Internal Server Error - Try again.";
        }
    }

    public void run() {
        try {

            // br and pw for reading client messages
            BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
            PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(), true);

            // reads what client seds and send it to handle Request
            while (true) {
                String line;
                try {
                    // retrievers response
                    line = br.readLine();
                    if (line == null) break;

                    // formats response
                    String[] command = line.split(" ; ");

                    // Queries response and sends back results
                    String response = handleRequest(command[0], command[1]);
                    pw.println(response);
                } catch (Exception e) {
                    // catch error from .split
                    // TODO: add better error handeling 
                    e.printStackTrace();
                    pw.println("Error processing request.");
                }
            }
            // catches server staring errors
        } catch (IOException e) {
            System.out.println("Server failed Error.");
        } finally {
            // closes server socket when finsihed
            try {
                clientSocket.close();
            } catch (IOException e) {
                System.out.println("Failed to close client socket.");
            }
        }
    }

    public static void main(String args[]) throws UserNotFoundException, OperationFailedException, UserNotBlockedException, UserAlreadFriendException, UserNotFriendException, MessagesNotFoundException {
        try{
            // creates a new server
            ServerSocket serverSocket = new ServerSocket(4242);
            System.out.println("Server running on port 4242...");

            // intiializes new thread for each client
            while(true){
                Socket socket = serverSocket.accept();
                System.out.println("Client Connected");
                SocialServer server = new SocialServer(socket);
                new Thread(server).start();

            }
        }catch(IOException e){
            System.out.println("Server Crashed");
        }
    }
}
import java.io.*;
import java.util.*;

import ServerException.*;

import java.net.*;
//TODO: Add asynchronous for race theory 
public class SocialServer implements Runnable {
    private final String Users = "./Database/userPassword.txt"; //assigns usernames and passwords to "userPassword.txt" textfile.
    private final static String UserInfo = "./Database/Data/userInfo.txt"; //assings unser information to "userInfo.txt" textfile.
    private final static String FriendList = "./Database/Data/friends.txt"; //assigns user's list of friends to "friends.txt" textfile.
    private final static String BlockedList = "./Database/Data/blocked.txt"; //asigns user's list of blocked users to "blocked.txt" textfile.
    private final static String MessageList = "./Database/Data/msgs.txt"; //asgins list of messages sent by each user to "msgs.txt" textfile.
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
                writeToFile(String.format("%s | %s | %s | %s", username, password, profilePicture, bio), UserInfo); //adds the newly created user's information to textfile.
            } else {
                throw new UserAlreadyExistsException("User already exists."); //if a user tries making an account with a username that already exists in the databse.
            }
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //to handle any irregular input by user.
        }
    }

    // Creates a new user
    private static void createUser(String username, String password) throws OperationFailedException, UserAlreadyExistsException { //creates new user with just username and password.
        try {
            if (!checkUser(username)) {
                writeToFile(String.format("%s | %s | %s | %s", username, password, "Database/ProfilePicture/default.png", ""), UserInfo); //adds the newly created user's information to textfile.
            } else {
                throw new UserAlreadyExistsException("User already exists."); //if a user tries making an account with a username that already exists in the databse.
            }
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //to handle any irregular input by user.
        }
    }

    // Returns a new user
    public static String getUser(String username) throws UserNotFoundException, OperationFailedException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) { //initalize buffered reader.
            String line = userBr.readLine(); //temporary variable to store current user being parsed through.
            while (line != null) {
                String[] userInfo = line.split("\\|"); //parses through the usernames in UserInfo.

                if (userInfo.length > 0 && userInfo[0].trim().equals(username.trim())) { //if match found
                    return line; //return user
                }
                line = userBr.readLine(); //use buffered reader to read from UserInfo textfile.
            }
            throw new UserNotFoundException("User not found."); //for when specified user doesn't exist in database.
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }


    // Change user information
    public static void changeUserInfo(String username, String newUsername, String password, String profilePicture, String bio) throws OperationFailedException, UserNotFoundException {
        ArrayList < String > userLines = new ArrayList < String > (); // Array to store lines to rewtire

        try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) { //initalize buffered reader. 

            // loops through each line and rewrites each line to array and changes line that needs to be changed
            if (checkUser(username)) {
                String line = userBr.readLine();
                while (line != null) {
                    String[] userInfo = line.split(" \\| "); //parses through users in UserInfo.
                    if (!userInfo[0].equals(username)) { //if user found
                        userLines.add(line); //adds user to an array.
                    } else {
                        userLines.add(String.format("%s | %s | %s | %s", newUsername, password, profilePicture, bio));
                    }
                    line = userBr.readLine(); //stores info to temporary variable.
                }
                // TODO: throw an error if  nothing found
                overwriteFile(userLines, UserInfo);
            } else {
                throw new UserNotFoundException("User doesn't Exist"); //if specified user isn't found.
            }
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }

    // User Blocked route

    public static String getBlocked(String username) throws UserNotFoundException, OperationFailedException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) { //initalize buffered reader.
            String line = userBr.readLine(); //temporary variable.
            while (line != null) {
                String[] userBlockedInfo = line.split(" \\| "); //parses through BlockList textfile.

                if (userBlockedInfo.length > 0 && userBlockedInfo[0].trim().equals(username.trim())) {
                    return line.substring(line.indexOf(" | ") + 3); //returns blocked user.
                }
                line = userBr.readLine(); //stores blockekd info to temporary variable.
            }
            throw new UserNotFoundException("User not Found!"); //if specified user isn't found.
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }

    public static void blockUser(String username, String blockedUser) throws OperationFailedException, UserNotFoundException, UserAlreadyBlockedException {
        ArrayList < String > userLines = new ArrayList < String > (); // Array to store lines to rewtire

        // checks to see if both users exist
        if (checkUser(username) && checkUser(blockedUser)) {
            try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) { //initialize buffered reader.

                // checks to see if user is already blocked
                if (getBlocked(username).contains(blockedUser)) {
                    throw new UserAlreadyBlockedException("User is already blocked."); 
                }
                // checks to see if User exists within blocked File
                if (checkBlocked(username)) {
                    // If user exists adds to the users list of blocked
                    String line = userBr.readLine(); //temporary variable.
                    while (line != null) {
                        String[] userInfo = line.split(" \\| ");
                        if (!userInfo[0].equals(username)) {
                            userLines.add(line);
                        } else {
                            userLines.add(String.format("%s | %s", line, blockedUser));
                        }
                        line = userBr.readLine(); //stores info to temporary variable.
                    }
                    overwriteFile(userLines, BlockedList);
                } else {
                    // if user doesnt exists appends blocked information
                    writeToFile(String.format("%s | %s", username, blockedUser), BlockedList);
                }

            } catch (IOException e) {
                throw new OperationFailedException(e.getMessage()); //for general input error.
            }
        } else {
            throw new UserNotFoundException("User does not exist"); //for when specified user doesn't exist.
        }
    }
    public static void unblock(String username, String unblockUser) throws UserNotBlockedException, UserNotFoundException, OperationFailedException {
        ArrayList < String > blockedLines = new ArrayList < > ();
        if (!getBlocked(username).contains(unblockUser)) { //checks to see if user isn't blocked.

            throw new UserNotBlockedException("User is not blocked");
        }
        if (!(checkUser(username) && checkUser(unblockUser))) { //checks to see if use can be blocked.

            throw new UserNotBlockedException("User not found");
        }
        try (BufferedReader userBr = new BufferedReader(new FileReader(BlockedList))) { //initalize buffered reader.

            String line = userBr.readLine(); //temporary variable.
            while (line != null) {

                String[] userInfo = line.split(" \\| "); //parses through useInfo.
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
                    blockedLines.add(newLine); //stores info to temporary variable.
                }
                line = userBr.readLine(); //stores info to temporary variable.
            }
            overwriteFile(blockedLines, BlockedList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }

    // friend routes
    public static String getFriend(String username) throws UserNotFoundException, OperationFailedException {
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) { //initalize buffered reader.
            String line = userBr.readLine(); //temporary variable.
            while (line != null) {
                String[] userFriendInfo = line.split(" \\| ");

                if (userFriendInfo.length > 0 && userFriendInfo[0].trim().equals(username.trim())) {
                    return line.substring(line.indexOf(" | ") + 3);
                }
                line = userBr.readLine(); //stores info to temporary variable.
            }
            throw new UserNotFoundException("User not Found!"); //if specified user isn't found.
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }
    public static void friendUser(String username, String friendUser) throws OperationFailedException, UserNotFoundException, UserAlreadFriendException {
        ArrayList < String > userLines = new ArrayList < String > (); // Array to store lines to rewtire

        // checks to see if both users exist
        if (checkUser(username) && checkUser(friendUser)) {
            try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) { //initalize buffered reader.

  
                // checks to see if User exists within friend File
                if (checkFriend(username)) {
                    // checks to see if user is already friend
                    if (getFriend(username).contains(friendUser)) {
                        throw new UserAlreadFriendException("User is already friend.");
                    }
                    // If user exists adds to the users list of blocked
                    String line = userBr.readLine(); //temporary variable.
                    while (line != null) {
                        String[] userInfo = line.split(" \\| ");
                        if (!userInfo[0].equals(username)) {
                            userLines.add(line);
                        } else {
                            userLines.add(String.format("%s | %s", line, friendUser));
                        }
                        line = userBr.readLine(); //stores info to temporary variable.
                    }
                    overwriteFile(userLines, FriendList);
                } else {
                    // if user doesnt exists appends blocked information
                    writeToFile(String.format("%s | %s", username, friendUser), FriendList);
                }

            } catch (IOException e) {
                throw new OperationFailedException(e.getMessage()); //for general input error.
            }
        } else {
            throw new UserNotFoundException("User does not exist"); //if user doesn't exist.
        }
    }



    public static void unfriend(String username, String unfriend) throws UserNotFriendException, UserNotFoundException, OperationFailedException {
        ArrayList < String > blockedLines = new ArrayList < > ();
        if (!getBlocked(username).contains(unfriend)) { //checks to see if user is a friend in the first place.

            throw new UserNotFriendException("User is not a friend"); 
        }
        if (!(checkUser(username) && checkUser(unfriend))) { //checks to see if user can be friended.

            throw new UserNotFriendException("User not found");
        }
        try (BufferedReader userBr = new BufferedReader(new FileReader(FriendList))) { //initalize buffered reader.

            String line = userBr.readLine(); //temporary variable.
            while (line != null) {

                String[] userInfo = line.split(" \\| "); //parses through textfile.
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
                line = userBr.readLine(); //stores info in temporary variable.
            }
            overwriteFile(blockedLines, FriendList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }

    // Messages routes

    // gets messages between two users
    public static String getMessage(String sender, String reciever) throws OperationFailedException, UserNotFoundException, MessagesNotFoundException{
        ArrayList<String> messages = new ArrayList<>();

        // checks to see if users exist
        if(!(checkUser(reciever) && checkUser(sender))){
            throw new UserNotFoundException("User not found"); //if specified user isn't found.
        }
        
        try(BufferedReader br = new BufferedReader(new FileReader(MessageList))) { //initalize buffered reader.
            String line = br.readLine(); //temporary variable.
            while(line !=null) {
                if(line.contains(String.format("%s | %s",sender,reciever))){
                    String newMessage= "s"+ line.substring(line.indexOf(" : ")+3);
                    messages.add(newMessage);
                } else if(line.contains(String.format("%s | %s",reciever,sender))){

                    String newMessage= "r"+ line.substring(line.indexOf(" : ")+3);
                    messages.add(newMessage);
                }
                line = br.readLine();
            }
            if(messages.isEmpty()){
                throw new MessagesNotFoundException("No messages Found."); //if there is no message history.
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
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }

    // messages between two users
    public static void message(String sender, String reciever, String message) throws OperationFailedException, UserNotFoundException{
        // checks to see if users exist
        if(!(checkUser(reciever) && checkUser(sender))){
            throw new UserNotFoundException("User not found"); //if specified user isn't found
        }
        try {
            writeToFile(String.format("%s | %s : %s",sender,reciever,message), MessageList);
        } catch (IOException e) {
            throw new OperationFailedException(e.getMessage()); //for general input error.
        }
    }
    

    // verifies user exists in database
    public static boolean checkUser(String username) throws OperationFailedException {
        try {
            getUser(username);
            return true; //function complete if user is found.
        } catch (UserNotFoundException e) {
            return false; //function incomplete if user isn't found.
        }
    }

    // verifies user exists in friend Database
    public static boolean checkFriend(String username) throws OperationFailedException {
        try {
            getFriend(username);
            return true; //function complete if friend user is found.
        } catch (UserNotFoundException e) {
            return false; //function incomplete if friend user isn't found.
        }
    }

    // verifies user exists in blocked file
    public static boolean checkBlocked(String username) throws OperationFailedException {
        try {
            getBlocked(username);
            return true; //function complete if blocked user is found.
        } catch (UserNotFoundException e) {
            return false; //function incomplete if blocked user isn't found.
        }
    }

    // File routes

    // Writes to user File(appends Information)
    public static void writeToFile(String text, String filePath) throws IOException {
        try (PrintWriter pw = new PrintWriter(new FileWriter(filePath, true), true)) { //initialize print writer.
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
                    String[] userInformation = data.split(" \\| "); //parses through data
                    if (userInformation.length == 2) {
                        createUser(userInformation[0], userInformation[1]);
                        return "User created succesfully"; //function complete.
                    } else {
                        createUser(userInformation[0], userInformation[1], userInformation[2], userInformation[3]);
                        return "User created succesfully"; //function complete.
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
            //error handling.
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
                String line; //temporary variable.
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
        try {
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
        } catch(IOException e){
            System.out.println("Server Crashed"); //if function doesn't go through.
        }
    }
}

import java.io.*;
import java.util.*;
import java.net.*;
//TODO: Add asynchronous for race theory
public class SocialServer implements Runnable{
        private final String Users = "./Database/userPassword.txt";
        private final static String UserInfo = "./Database/Data/userInfo.txt";
        private final String FriendList = "./Database/friends.txt";
        private final String BlockedList = "./Database/blocked.txt";
        private final String Reported = "fileName";
        private Socket clientSocket;
        // private static BufferedReader userBr;
        // private static PrintWriter userPw;
    
        public SocialServer(Socket socket) {
            this.clientSocket = socket;
        }
    
        // User file Routes
    
        // Creates a new user
        private static void createUser(String username, String password, String profilePicture, String bio) {
            try{
                // TODO: create a boolean funcion verify username
                if(getUser(username).equals("User not found")){
                    writeToUserFile(String.format("%s | %s | %s | %s", username, password, profilePicture, bio),UserInfo);
                }else{
                    // TODO: throw an error
                }
            }catch(IOException e){
                // TODO: throw an error
            }
        }
        
        // Creates a new user
        private static void createUser(String username, String password){
            try{
                // TODO: create a boolean funcion verify username
                if(getUser(username).equals("User not found")){
                    writeToUserFile(String.format("%s | %s | %s | %s", username, password,"Database/ProfilePicture/default.png" ,""),UserInfo);
                }else{
                    // TODO: throw an error
                }
            }catch(IOException e){
                // TODO: throw an error
            }
        }
    
        // Returns a new user
        public static String getUser(String username) {
            try (BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))) {
                // TODO: create a boolean funcion verify username and verify username
                String line= userBr.readLine();
                while (line != null) {
                    String[] userInfo = line.split("\\|");

                    if (userInfo.length > 0 && userInfo[0].trim().equals(username.trim())) {
                        return line;
                    }
                    line = userBr.readLine();
                }
                return "User not found";
                // TODO: throw an error instead
            } catch (IOException e) {
                // TODO: throw an error
                return "User not found due to error";
            }
        }
        
        
        // Change user information
        public static void changeUserInfo(String username,String newUsername, String password, String profilePicture, String bio){
           ArrayList<String> userLines= new ArrayList<String>(); // Array to store lines to rewtire
           System.out.println("run");
    
           try(BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))){

            // loops through each line and rewrites each line to array and changes line that needs to be changed
            // TODO: Check to see if user Exists
            String line= userBr.readLine();
            while(line!= null){
                String[] userInfo= line.split(" \\| ");
                if(!userInfo[0].equals(username)){
                    userLines.add(line);
                }else{
                    userLines.add(String.format("%s | %s | %s | %s", newUsername, password, profilePicture, bio));
                }
                line= userBr.readLine();
                }
                // TODO: throw an error if  nothing found
                overwriteUserFile(userLines, UserInfo);
           }catch(IOException e){
                // TODO: throw an error
           }
        }



        // File routes 

        // Writes to user File(appends Information)
        public static void writeToUserFile(String text, String filePath) throws IOException{
            try(PrintWriter pw= new PrintWriter(new FileWriter(filePath, true),true)){
                pw.println(text);
            }
        }

        // Writes to user File(overwrite Information)
        public static void overwriteUserFile(ArrayList<String> userLines, String filePath) throws IOException{
            try(PrintWriter pwTemp = new PrintWriter(filePath)){// creates temporary print writer to overwrite
                // rewrites all lines to file
                for(String newLine: userLines){
                    pwTemp.println(newLine);
                }
            }
        }


        // Handles Queries

        public static String handleRequest(String action, String data){
            switch(action){
                case "createUser":
                    String[] userInformation= data.split(" \\| ");
                    if(userInformation.length==2){
                        createUser(userInformation[0], userInformation[1]);
                        return "User created succesfully";
                    }else{
                        createUser(userInformation[0], userInformation[1],userInformation[2],userInformation[3]);
                        return "User created succesfully";
                    }
                case "getUser":
                    return getUser(data);
                case "changeUserInfo":
                    userInformation= data.split(" \\| ");
                    System.out.println("userInfo : "+userInformation[0]);
                    changeUserInfo(userInformation[0],userInformation[1],userInformation[2],userInformation[3],userInformation[4]);
                    return "User information changed succesfully";
            }
            // TODO: throw error instead
            // TODO: catch all errors on the handle Request
            return "Invalid Query";
        }

        public void run(){
            try{

                // br and pw for reading client messages
                BufferedReader br = new BufferedReader(new InputStreamReader(clientSocket.getInputStream()));
                PrintWriter pw = new PrintWriter(clientSocket.getOutputStream(),true);
        
                // reads what client seds and send it to handle Request
                while(true){
                    String line;
                    try{
                        // retrievers response
                        line = br.readLine();
                        if (line == null) break;

                        // formats response
                        String[] command = line.split(" ; ");
                        System.out.println(line);

                        // Queries response and sends back results
                        String response = handleRequest(command[0], command[1]);
                        pw.println(response);
                    } catch(Exception e){
                        // catch error from .split
                        // TODO: add better error handeling 
                        e.printStackTrace();
                        pw.println("Error processing request.");
                    }
                }
            // catches server staring errors
            } catch(IOException e){
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
        
        public static void main(String args[]){
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
import java.io.*;
import java.util.*;
import java.net.*;

public class SocialServer{
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
                writeToUserFile(String.format("%s | %s | %s | %s", username, password, profilePicture, bio),UserInfo);
            }catch(IOException e){
                System.out.println("Failed to write to file.");
            }
        }
        
        // Creates a new user
        private static void createUser(String username, String password){
            try{
                writeToUserFile(String.format("%s | %s | %s | %s", username, password,"Database/ProfilePicture/default.png" ,""),UserInfo);
            }catch(IOException e){
                System.out.println("Failed to write to file.");
            }
        }
    
        // Returns a new user
        public static String getUser(String username){
    
            try(BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))){
                String line = userBr.readLine();
                while(line!= null){
                    String[] userInfo= line.split(" | ");
                    if(userInfo.equals(username)){
                        return line;
                    }
                }
                return "User not found";
            }catch(IOException e){
                 return "User not found";
            }
        }
        
        // Change user information
        public static void changeUserInfo(String username,String newUsername, String password, String profilePicture, String bio){
           ArrayList<String> userLines= new ArrayList<String>(); // Array to store lines to rewtire
           System.out.println("run");
    
           try(BufferedReader userBr = new BufferedReader(new FileReader(UserInfo))){

            // loops through each line and rewrites each line to array and changes line that needs to be changed
            String line= userBr.readLine();
            while(line!= null){
                String[] userInfo= line.split(" | ");
                if(!userInfo[0].equals(username)){
                    userLines.add(line);
                }else{
                    userLines.add(String.format("%s | %s | %s | %s", newUsername, password, profilePicture, bio));
                    System.out.println("line");
                }
                line= userBr.readLine();
                }
            
                overwriteUserFile(userLines, UserInfo);
           }catch(IOException e){
            
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
        
        public static void main(String args[]) throws IOException{

    }
}
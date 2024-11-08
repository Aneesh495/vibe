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
        private static BufferedReader userBr;
        private static PrintWriter userPw;
    
        public SocialServer(Socket socket) {
            this.clientSocket = socket;
        }
    
        // User file
    
        private static void createUser(String username, String password, String profilePicture, String bio) {
            userPw.println(String.format("%s | %s | %s | %s", username, password, profilePicture, bio));
        }
        
    
        private static void createUser(String username, String password){
            userPw.println(String.format("%s | %s | %s | %s", username, password,"Database/ProfilePicture/default.png" ,""));
        }
    
    
        public static String getUser(String username) throws IOException{
            String line = userBr.readLine();
            while(line!= null){
                String[] userInfo = line.split(" | ");
                if(userInfo[0].equals(username)){
                    return line;
                }
            }
            return "User not Found";
        }
    
        
        public static void main(String args[]) throws IOException{
            userPw= new PrintWriter(new FileWriter(UserInfo));
            userBr =new BufferedReader(new FileReader(UserInfo));
            createUser("testUser","testpassword");
            userPw.flush();
            System.out.println(getUser("testUser"));
    }
    

}
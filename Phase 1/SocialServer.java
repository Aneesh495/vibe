import java.io.BufferedReader;
import java.io.PrintWriter;

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
        super()
    }
    public void createUser(String username, String password /* String profilePicture*/ ) { 
        // We should make another overloaded method with pfp
        try(PrintWriter pw = new PrintWriter(new FileWriter(Users))) {
            pw.write(username + " | " + password);
        }
        catch (Exception e) {
            e.printStackTrace();
        }
    
    }
    public void editUserInfo(User user) {
        try(PrintWriter pw = new PrintWriter(new FileWriter(UserInfo));
           BufferedReader bfr = new BufferedReader(new FileReader(UserInfo))) {
            
            
    
    }
    public void blockUser(String userId) {
    
    }
    public void getMessage(String userId) {
    
    }
}

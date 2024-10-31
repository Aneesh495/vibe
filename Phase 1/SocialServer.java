import java.io.BufferedReader;
import java.io.PrintWriter;

public class SocialServer implements Server {
    private BufferedReader br;
    private PrintWriter pw;
    private final String Users = "./Database/userPassword.txt";
    private final String UserInfo = "./Database/userInfo.txt";
    private final String FriendList = "./Database/friends.txt";
    private final String BlockedList = "fileName";
    private final String Reported = "fileName";

    public SocialServer(BufferedReader br, PrintWriter pw) {
        this.br=br;
        this.pw=pw;
    }
    public void createUser(String username, String password, String profilePicture) {
    
    }
    public void editUserInfo(User user) {
    
    }
    public void blockUser(String userId) {
    
    }
    public void getMessage(String userId) {
    
    }
}
import java.io.IOException;
import ServerException.*;

public interface Server {
    void createUser(String username, String password) throws IOException, InvalidInputException;
    boolean loginWithPassword(String username, String password) 
            throws IOException, UserNotFoundException, InvalidInputException;
    String handleRequest(String action, String caller, String data)
            throws IOException, UserNotFoundException, InvalidInputException, ClientDataException;
    int sendMessage(String sender, String receiver, String message)
            throws IOException, UserNotFoundException, InvalidInputException;
    String getMessage(String sender, String receiver)
            throws IOException, UserNotFoundException, InvalidInputException;
    void blockUser(String username, String blockedUser)
            throws IOException, UserNotFoundException, InvalidInputException;
    void unblock(String username, String unblockUser)
            throws IOException, UserNotFoundException, InvalidInputException;
}
import Exceptions.ServerException.*;

import java.io.*;
public interface Server {

    public void createUser(User user) throws InvalidCredentialsException, IOException;

    public void createUser(String username, String password) throws InvalidCredentialsException, IOException;

    // public void editUserInfo(User user) throws UserNotFoundException, UserInputException, IOException;

}

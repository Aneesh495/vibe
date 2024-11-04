import InvalidBioException;
import PasswordNotValidException;
import UserAlreadyBlockedException;
import UsernameNotValidException;
import UserExceptions.UserNotFoundException;

 
public interface Server {
    public void createUser(User user) 
           throws UsernameNotValidException, PasswordNotValidException, IOException;

    public void editUserInfo(User user) 
           throws UserNotFoundException, InvalidBioException, IOException;

    public void blockUser(String UserId) 
           throws UserNotFoundException, UserAlreadyBlockedException;

    public void getMessage(String UserId1) 
           throws UserNotFoundException, UserBlockedException;
}

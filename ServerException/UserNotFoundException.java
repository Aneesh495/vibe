package ServerException;

public class UserNotFoundException extends Exception {
    public UserNotFoundException(String text){
        super(text);
    }
}

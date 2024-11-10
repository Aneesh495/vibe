package ServerException;

public class UserNotFriendException extends Exception{
    public UserNotFriendException(String message){
        super(message);
    }
}

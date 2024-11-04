package UserExceptions;

public class BlockedActionException extends UserActionException {
    public BlockedActionException(String message) {
        super(message);
    }
}
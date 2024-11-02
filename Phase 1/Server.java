public interface Server {
    public void createUser(String username, String password, String profilePicture) 
           throws UsernameNotValidException, PasswordNotValidException, IOException;

    public void editUserInfo(User user) 
           throws UserNotFoundException, InvalidBioException, IOException;

    public void blockUser(String UserId) 
           throws UserNotFoundException, UserAlreadyBlockedException;

    public void getMessage(String UserId1) 
           throws UserNotFoundException, UserBlockedException;
}

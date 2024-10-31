public interface Server {
    public void createUser(String username, String password, String profilePicture);
    public void editUserInfo(User user);
    public void blockUser(String UserId);
    public void getMessage(String UserId1);
}

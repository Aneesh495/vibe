class User {
  private static int totalUsers;
  private String userName;
  // private String displayName // Add if we decide to add after discussion
  private String userPassword;
  private ArrayList<User> userList;

  public User(String username, String password ) {    
    this.userName = username;
    this.userPassword = password;
    userList.add(this);
  }
}
  
  
  

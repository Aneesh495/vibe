README.file Phase 1 Team Project - 
by: Aneesh, Lakshay, Garv, Tasha, and Amelia

ALERT: The currentcode is not thread-safe yet as we are not spawning multilple threads or user inputs yet. The code will be made thread safe when client/server network functions are introduced. 

Database:
We opt to store the data for our social media app using files. We decided it would be feasible to implement this as we could make use of buffered reader & writer to access user information conviniently.

Files:
1. blocked.txt - Consists of a list of every user and who they have blocked.
2. friends.txt - Consists of a list of every user and their list of friends.
3. msgs.txt - Consists of a list of every direct message chat history.
4. userInfo.txt - Conists of every user's profile information such as username as user bio.
5. userPasswords.txt - Consists of every user's current password used for login.

Exceptions:
Albeit not all exceptions are finalized yet, these are the ones we decided to include based on our phase 1 code:
1. BlockedActionException.java - Displays a custom error message when a user tries interacting with a user they blocked, a user that blocked them, or tries blocking a user they've already blocked.
2. FriendActionException.java - Displays a custom error message when a user tries frieding a user that's already a friend or tries unfriending a user that's not a friend of theirs in the first place.
3. UserActionException.java - Displays a custom error message when a user tries modifying their username or password to an invalid/already existing username password.
4. UserNotFoundException.java - Displays a custom error message when a user tries messaging/friending/blocking a user that doesn't exist.
5. InvalidBioException.java - Displays a custom error message when a user tries updating their bio to an invalid string that is either too long, too short, or contains unsupported characters.
6. UserCreationException.java - Displays a custom error message when a user tries signing up with an invalid username or password.
7. InvalidCredentialsException - Displays a custom error message when a user tries logging in with invalid username/password.

Test Cases: 
  UserTest.java - JUnit Testcases
   1. setUp - Creates 3 different users: user, friend, and blockedUser to run the test cases.
   2. testUserConstructor - Verifies that setUp properly initalizes the user information.
   3. testSetUsername - Tests the setUsername method
   4. testSetUsernameFailure - Ensures that setting a empty string as username throws the userAction exception.
   5. testAddFriend - Tests the add friend function.
   6. testFriendAlreadyFriend - Ensures that a friendAction error is thrown when a friend already friended is attempted to be added as a friend again
   7. testRemoveNonExistentFriend - Ensures that a friendAction error is thrown when a user who isn't a friend is attempted to be unfriended.
   8. testBlockUser - Verifies that the blockUser method succesfully adds a user to the blocked list.
   9. testUnblockUser - Verifies that the unblockUser method succesfully removes a user from teh blocked list.
   10. testUnblockedNonBlockedUser - Ensures that a blockedActionException is thrown when a user tries unblocking a user who isn't in the blocked list.

   SocialServerTest.java - JUnit Testcases
     1. setUp - Initalizes a new instance of social server.
     2. testConfirmWithPasswordCorrect - Tests that the confirm with password method works as expected. 
     3. testConfirmWithPasswordIncorrect - Ensures that when an incorrect password is entered, the InvalidCredentialsException is thrown.
     4. restoreStreams - Flushes all buffers (reader & writers).
     
MessageInterface.java: 
  Contains the methods for Messages.java - getSender, getReceiver, getContent, setContent, getTimeStamp, setReported, setTimeStamp, toString.
  
Message.java: 
  1. getSender - Returns the sender of a message as a string
  2. getReceiver - Returns the receiver of a message as a string.
  3. getContent - Returns the content of a message.
  4. setContent - Sets the content of a message to the specified string.
  5. getTimeStamp - Returns the time when the message was sent.
  6. setReported - Flags a message as reported
  7. toString - returns a string representation of a message, including the sender, receiver, content and the timestamp of the message.
  8. setTimeStamp - Sets the timestamp of a message. 

Server.java: 
  Provides the interface for social server - consisting of the createUser method.

SocialServer.java:
  1. createUser - Creates a new user account with the specified username and password, throws InvalidCredentialsException when a user tries signing up with a username that is already taken or an invalid password.
  2. socialServer - default constructor for the SocialServer class.
  3. confirmWithPassword - prompts the user to confirm sensitive action with user password.
  4. editUsername - Modifies the username of the specified user, throws InvalidCredentialsException, IO Exception and UserNotFoundException. 
  5. editUserPassword - Modifies the passowrd of the specified user, throws InvalidCredentialsException, UserNotFoundException, and IOException.
  6. editUserPFP - Updates the profile picture of the specified user, throws IOException, and UserNotFoundException.
  
  UserInterface.java: 
  Provides the interface for User.java - setUsername, setPassword, setProfilePicture, setBio, addFriend, removeFriend, getUsername, getPassword, getProfilePicture, getBio, getFriends, and getBlocked

  User.java: 
  1. User - Default user constructor that initalizes the username, password, profile, bio, friendlist and blocklist to the given parameters.
  2. User(2) - Initalizes User with specified username and password
  3. User(3) - Initalizes user with provided username, password, bio, friendlist and blocklist, alongside a default profile picture.
  4. setUsername - Sets the user's username to given String, throws UserActionException when an invalid or taken username is given as a String. 
  5. setPassword - Sets the user's password to given String, throws UserActionException when a password that is pre-existing or doesn't meet the criterias is provided as the parameter.
  6. setProfilePicture - Sets the user's profile picture to given file, throws UserActionException when an invalid filename is given as parameter.
  7. setBio - Sets the user's bio to given String
  8. addFriend - adds the specified user to user's friendlist, throws FriendActionException if the user is already friended, and BlockedActionException if the user is blocked.
  9. removeFriend - Removes a friend from user's friendlist, throws a FriendActionException is the specified user is not a friend in the first place. 
  10. blockUser - Adds the specified user to user's blocklist, throws FriendActionException if the user is a friend, and BlockedActionException if user is already blocked.
  11. unblockUser - Removes a friend from user's blocklist, throws BlockedActionException if the specified user is not blocked in the first place.
  12. getUsername - Returns the username of the user.
  13. getPassword - Returns the password of the user.
  14. getProfilePicture - Displays the profile picture of the user, shows default PFP if no PFP is set by user.
  15. getFriends - Returns the friendlist of a user.
  16. getBlocked - Returns the blocklist of a user.
  17. toString - Returns a string representation of user's username, password, and bio of user. 

Groupmember Contributions - Phase 1:
1. Aneesh: Authored readme file, custom exception classes, and threw exceptions in existing classes.
2. Garv: Authored testcases, SocialServer.java, user.java
3. Lakshay: Authored user.java, messages.java, SocialServer.java
4. Tasha: Co-authored readme file and exceptions
5. Amelia: Oversaw operations 


Citations:
1. Stack overflow - https://docs.oracle.com/javase/6/docs/api/java/lang/StackOverflowError.html
2. JPass - https://docs.oracle.com/javase/tutorial/uiswing/components/passwordfield.html

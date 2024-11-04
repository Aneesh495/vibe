README.file Phase 1 Team Project - 
by: Aneesh, Lakshay, Garv, Tasha, and Amelia

ALERT: The currentcode is not thread-safe yet as we are not spwanign multilple threads or user inputs yet. The code will be made thread safe when client/server network functions are introduced. 

Database:
We opt to store the data for our social media app using files. We decided it would be feasible to implement this as we could make use of buffered reader & writer to access user information conviniently.

Files:
1. blocked.txt - Consists of a list of every user and who they have blocked.
2. friends.txt - Consists of a list of every user and their list of friends.
3. msgs.txt - Consists of a list of every direct message chat history.
4. userInfo.txt - Conists of a every user's profile information such as username as user bio.
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
   a. setUp - Creates 3 different users: user, friend, and blockedUser to run the test cases.
   b. testUserConstructor - Verifies that setUp properly initalizes the user information.
   c. testSetUsername - Tests the setUsername method
   d. testSetUsernameFailure - Ensures that setting a empty string as username throws the userAction exception.
   e. testAddFriend - Tests the add friend function.
   f. testFriendAlreadyFriend - Ensures that a friendAction error is thrown when a friend already friended is attempted to be added as a friend again
   g. testRemoveNonExistentFriend - Ensures that a friendAction error is thrown when a user who isn't a friend is attempted to be unfriended.
   h. testBlockUser - Verifies that the blockUser method succesfully adds a user to the blocked list.
   i. testUnblockUser - Verifies that the unblockUser method succesfully removes a user from teh blocked list.
   j. testUnblockedNonBlockedUser - Ensures that a blockedActionException is thrown when a user tries unblocking a user who isn't in the blocked list.

   SocialServerTest.java - JUnit Testcases
     a. setUp - Initalizes a new instance of social server.
     b. testConfirmWithPasswordCorrect - Tests that the confirm with password method works as expected. 
     c. testConfirmWithPasswordIncorrect - Ensures that when an incorrect password is entered, the InvalidCredentialsException is thrown.
     d. restoreStreams - Flushes all buffers (reader & writers).
     
MessageInterface.java: 
  Contains the methods for Messages.java - getSender, getReceiver, getContent, setContent, getTimeStamp, setReported, setTimeStamp, toString.
  
Message.java: 
  a. getSender - Returns the sender of a message as a string
  b. getReceiver - Returns the receiver of a message as a string.
  c. getContent - Returns the content of a message.
  d. setContent - Sets the content of a message to the specified string.
  e. getTimeStamp - Returns the time when the message was sent.
  f. setReported - Flags a message as reported
  g. toString - returns a string representation of a message, including the sender, receiver, content and the timestamp of the message.
  h. setTimeStamp - Sets the timestamp of a message. 

ServerInterface.java: 
  Provides the interface for social server - consisting of the createUser method.

SocialServer.java:
  a. createUser - Creates a new user account with the specified username and password, throws InvalidCredentialsException when a user tries signing up with a username that is already taken or an invalid password.
  b. socialServer - default constructor for the SocialServer class.
  c. confirmWithPassword - prompts the user to confirm sensitive action with user password.
  d. editUsername - Modifies the username of the specified user, throws InvalidCredentialsException, IO Exception and UserNotFoundException. 
  e. editUserPassword - Modifies the passowrd of the specified user, throws InvalidCredentialsException, UserNotFoundException, and IOException.
  f. editUserPFP - Updates the profile picture of the specified user, throws IOException, and UserNotFoundException.
  g. blockUser - Blocks the specified user by the userID, throws UserNotFoundException, and UserBlockedException.
  h. getMessage - Retrieves all the messages a user sent. 
  
  UserInterface.java: 
  Provides the interface for User.java - setUsername, setPassword, setProfilePicture, setBio, addFriend, removeFriend, blockUser, unblockUser, getUsername, getPassword, getProfilePicture, getBio, getFriends, and getBlocked

  User.java: 
    a. User - Default user constructor that initalizes the username, password, profile, bio, friendlist and blocklist to the given parameters.
    b. User(2) - Initalizes User with specified username and password
    c. User(3) - Initalizes user with provided username, password, bio, friendlist and blocklist, alongside a default profile picture.
    d. setUsername - Sets the user's username to given String, throws UserActionException when an invalid or taken username is given as a String. 
    e. setPassword - Sets the user's password to given String, throws UserActionException when a password that is pre-existing or doesn't meet the criterias is provided as the parameter.
    f. setProfilePicture - Sets the user's profile picture to given file, throws UserActionException when an invalid filename is given as parameter.
    g. setBio - Sets the user's bio to given String
    h. addFriend - adds the specified user to user's friendlist, throws FriendActionException if the user is already friended, and BlockedActionException if the user is blocked.
    i. removeFriend - Removes a friend from user's friendlist, throws a FriendActionException is the specified user is not a friend in the first place. 
    j. blockUser - Adds the specified user to user's blocklist, throws FriendActionException if the user is a friend, and BlockedActionException if user is already blocked.
    k. unblockUser - Removes a friend from user's blocklist, throws BlockedActionException if the specified user is not blocked in the first place.
    l. getUsername - Returns the username of the user.
    m. getPassword - Returns the password of the user.
    n. getProfilePicture - Displays the profile picture of the user, shows default PFP if no PFP is set by user.
    o. getFriends - Returns the friendlist of a user.
    p. getBlocked - Returns the blocklist of a user.
    q. toString - Returns a string representation of user's username, password, and bio of user. 

Groupmember Contributions - Phase 1:
Aneesh: Authored readme file, custom exception classes, and threw exceptions in existing classes.
Garv: Authored testcases, SocialServer.java, user.java
Lakshay: Authored user.java, messages.java, SocialServer.java
Tasha: Co-authored readme file and oversaw exceptions
Amelia: Co-authored message.java

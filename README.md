README.file Phase 3 Team Project - by: Aneesh, Lakshay, Garv, Tasha, and Amelia

ALERT: We extensively worked on the GUI of the Social Meida App on this phase, and a lot of GUI has been completely reworked. 

Database: We opt to store the data for our social media app using files. We decided it would be feasible to implement this as we could make use of buffered reader & writer to access user information conviniently.

GUI: 
In phase 3, we focused on implementing complex GUI for our social media app.
1. For messaging, we took inspiration from WhatsApp to implement dark-grey and light-green bubbles to differentiate messages sent between user 1 and 2.
2. For edit and delete messages, we took inspiration from Discord to impleent a 'pencil' icon to model the edit message function and a 'trash' icon to model the delete message function.
3. We decided to design the home screen on our own to keep it original and give our social media an app a flavor of our own. 

Files:
1. blocked.txt - Consists of a list of every user and who they have blocked.
2. friends.txt - Consists of a list of every user and their list of friends.
3. msgs.txt - Consists of a list of every direct message chat history.
4. userInfo.txt - Conists of every user's profile information such as username as user bio.
5. userPasswords.txt - Consists of every user's current password used for login.
6. deafult.png - Consists of the default PFP if user doesn't provide a PFP image. 

Exceptions:
1. ClientDataException.java - Throws an exception when a user tries accessing invalid data.
2. CustomException.java - Used for throwing custom exceptions for rare cases not covered by other exceptions.
3. InvalidInputException.java - Throws an exception when a user inputs contains errors.
4. UserNotFound.java - Throws an exception when a function is called with an invalid username as paramater.

Social Client:
Initializes a "localhost" on socket 8080. Shows GUI to Login. If entered username isn't found, prompts user to create new account. If a correct username is entered, prompts user to confirm login with password.

Social Server:
Stores user info and user's list of messages, friends and blocked users in respective text files by providing the path for it. Creates a new user with all user info, and provides seperate method to create new user with just username and password. 
1. getUser - Searcher for user based on username and returns user's information.
2. changeUserInfo - Edits user's username, password, PFP, and bio information based on input.
3. getBlocked - Returns user1's list of blocked users.
4. blockUser - If user2 isn't already blocked by user1, blocks user 2. 
5. unblockUser - If user2 is blocked by user1, unblocks user2. 
6. getFriend - Returns user1's list of blocked users.
7. friendUser - Adds user2 to user1's friend list if not already there.
8. unfriend - Removes user2 from user1's friend list if user is there.
9. getMessage - Returns user's message history.
10. message - Enables user1 to message user2.
11. checkUser - Searches to see if user with given username exists.
12. checkBlocked - Searches to see if user2 is blocked by user1.
13. writeToFile - Uses print writer to write to file.
14. overwriteFile - Used to edit information already existing in text files.
15. handleRequests - Used to carry out all user functions.
16. Main method - Connects to client.

Test Client: 
Establishes connection with the server. 
1. sendRequest - requests to connect with server.
2. closeConnection - closes the connection with server.
3. Main method - Provides options to: create user, get user info, change user info, block user, get blocked users, unblock user, friend user, get friend list, unfriend user, send message, get messages and exit. Sets up each choice with subsequent prompts for user input.

Test Files:
1. blockedExample.txt - List of example users and their example blocked lists. 
2. friendsExample.txt - List of example users and their example friend lists.
3. userInfoExample.txt - List of example users with their user info.

Groupmember Contributions - Phase 1:

1. Aneesh: Authored ReadMe file, Group Report, and editMessage/deleteMessage functions.
2. Garv: Authored clientServer.java, server and home page GUI.
3. Lakshay: Authored socialServer.java, and messaging GUI.
4. Tasha: Co-authored group presentation
5. Amelia: Oversaw operations

Citations:

Stack overflow - https://docs.oracle.com/javase/6/docs/api/java/lang/StackOverflowError.html
JPass - https://docs.oracle.com/javase/tutorial/uiswing/components/passwordfield.html

README.file Phase 2 Team Project - by: Aneesh, Lakshay, Garv, Tasha, and Amelia

ALERT: We have changed many of the code from Phase 1, since we redesigned the way we want to move forward with the project. 

Database: We opt to store the data for our social media app using files. We decided it would be feasible to implement this as we could make use of buffered reader & writer to access user information conviniently.

Files:
1. blocked.txt - Consists of a list of every user and who they have blocked.
2. friends.txt - Consists of a list of every user and their list of friends.
3. msgs.txt - Consists of a list of every direct message chat history.
4. userInfo.txt - Conists of every user's profile information such as username as user bio.
5. userPasswords.txt - Consists of every user's current password used for login.
6. deafult.png - Consists of the default PFP if user doesn't provide a PFP image. 

Exceptions:
1. MessagesNotFoundException.java - Throws an exception when user calls getMessage method and has no message history.
2. OperationFailedException.java - Throws an exception when a user calls an unexecutable method.
3. UserAlreadFriendException.java - Throws an exception when user tries friending a user who they are already friends with.
4. UserAlreadyBlockedException.java - Throws an exception when user tries blocking a user who they've already blocked.
5. UserAlreadyExistsException.java - Throws an exception when a new user is attempted to be created based on an username which already exists in the database. 
6. UserNotBlockedException.java - Throws an exception when user tries unblocking a user who isn't blocked in the first place. 
7. UserNotFoundException.java - Throws an exception when user tries searching for a user or passes a user has a parameter who doesn't exist. 
8. UserNotFriendException.java - Throws an exception when user tries doing a frined-only-interaction with a user they aren't friends with.



# Vibe Social Network

A Java-based social media application with a graphical user interface that allows users to connect, message, and manage their social network. Built with Java Swing for the frontend and file-based storage for the backend.

## Features

- **User Authentication**: Login and registration system
- **Real-time Messaging**: Direct messaging between users with WhatsApp-style chat bubbles
- **Friend Management**: Add, remove, and view friends
- **Block System**: Block and unblock users
- **Profile Management**: Edit username, password, profile picture, and bio
- **Message Management**: Send, edit, and delete messages
- **Modern GUI**: Dark theme with intuitive user interface inspired by popular social media platforms

## Techn Stack

- **Backend**: Java (JDK 8+)
- **Frontend**: Java Swing (GUI)
- **Storage**: File-based database (text files)
- **Networking**: Java Socket programming
- **Testing**: JUnit 4

## Project Structure

```
VibeSocialMedia/
├── SocialClient.java          # Main client application with GUI
├── SocialServer.java          # Server application
├── SocialClientTest.java      # Client test cases
├── SocialServerTest.java      # Server test cases
├── Database/
│   ├── Data/
│   │   ├── userInfo.txt      # User profile information
│   │   ├── friends.txt       # Friend relationships
│   │   ├── blocked.txt       # Blocked user relationships
│   │   ├── msgs.txt          # Message history
│   │   └── userPassword.txt  # User authentication data
│   ├── ProfilePicture/
│   │   └── default.png       # Default profile picture
│   └── logo.jpg              # Application logo
├── ServerException/
│   ├── ClientDataException.java
│   ├── CustomException.java
│   ├── InvalidInputException.java
│   └── UserNotFoundException.java
└── lib/                      # JUnit testing libraries
```

## Getting Started

### Prerequisites

- Java Development Kit (JDK) 8 or higher
- Git (for cloning the repository)

### Installation

1. **Clone the repository**
   ```bash
   git clone <repository-url>
   cd VibeSocialMedia
   ```

2. **Compile the Java files**
   ```bash
   javac -cp "lib/*" *.java ServerException/*.java
   ```

## 🏃‍♂️ Running the Application

### Starting the Server

1. **Open a terminal/command prompt**
2. **Navigate to the project directory**
3. **Run the server**
   ```bash
   java SocialServer
   ```
   
   The server will start on port 4242 and display:
   ```
   Server running on port 4242...
   ```

### Starting the Client

1. **Open another terminal/command prompt**
2. **Navigate to the project directory**
3. **Run the client**
   ```bash
   java SocialClient
   ```
   
   The client will connect to `localhost:4242` and open the login GUI.

### Using the Application

1. **Login/Register**: 
   - Enter username and password
   - If the user doesn't exist, you'll be prompted to create an account
   - If the user exists, enter the correct password to login

2. **Main Features**:
   - **Chat**: Click on any user in the chat list to start messaging
   - **Friends**: Add and manage friends from the friends panel
   - **Block**: Block unwanted users from the blocked panel
   - **Profile**: Edit your profile information including picture and bio
   - **Search**: Search for users to add as friends

## Database Schema

The application uses text files for data storage:

- **userInfo.txt**: `username | password | profilePicture | bio`
- **friends.txt**: `username | friend1 | friend2 | ...`
- **blocked.txt**: `username | blockedUser1 | blockedUser2 | ...`
- **msgs.txt**: `sender | receiver | message1 ; message2 ; ...`
- **userPassword.txt**: `username | password`

## Configuration

### Server Configuration
- **Port**: 4242 (default)
- **Host**: localhost
- **Max Connections**: Unlimited (multi-threaded)

### Client Configuration
- **Server Address**: 127.0.0.1
- **Server Port**: 4242

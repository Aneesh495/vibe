import javax.swing.JOptionPane;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.util.*;
import java.awt.event.*;

public class SocialClient {
    public static void main(String[] args) throws InvalidCredentialsException, InvalidBioException {
        String username = JOptionPane.showInputDialog(null,
                "Enter Username", "Login", JOptionPane.QUESTION_MESSAGE);
        if (!(username.inDatabase())) {
            throw new InvalidCredentialsException("Invalid username");
        }
        String password = JOptionPane.showInputDialog(null,
                "Enter Password", "Login", JOptionPane.QUESTION_MESSAGE);
        if (!password.equals(username.getPassword())) {
            throw new InvalidCredentialsException("Incorrect password");
        }
        String pfp = JOptionPane.showInputDialog(null, "Enter your pfp in a file format",
                JOptionPane.QUESTION_MESSAGE);
        if (!(pfp.isValidFile())) {
            throw new InvalidCredentialsException("Invalid PFP file");
        }
        String bio = JOptionPane.showInputDialog(null, "Enter your bio",
                JOptionPane.QUESTION_MESSAGE);
        if (!(bio.isValid())) {
            throw new InvalidBioException ("Invalid bio");
        }
    }
}

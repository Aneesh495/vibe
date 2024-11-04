import Exceptions.ServerException.InvalidCredentialsException;
import org.junit.*;
import static org.junit.Assert.*;
import java.io.*;

public class SocialServerTest {
    private SocialServer server;
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @Before
    public void setUp() {
        server = new SocialServer();
        System.setOut(new PrintStream(outContent)); // Redirect System.out to capture outputs
    }

    @Test
    public void testCreateUserValid() throws Exception {
        User user = new User("validUser", "validPassword123");
        server.createUser(user);
        // This assumes createUser outputs to System.out or a similar side-effect
        assertTrue("Output should confirm creation", outContent.toString().contains("User created"));
    }

    @Test(expected = InvalidCredentialsException.class)
    public void testCreateUserInvalid() throws Exception {
        User user = new User("us", "pwd"); // intentionally short username and password
        server.createUser(user);
    }

    @Test
    public void testConfirmWithPassword() {
        ByteArrayInputStream in = new ByteArrayInputStream("validPassword123\n".getBytes());
        System.setIn(in);
        User user = new User("user", "validPassword123");
        assertTrue("Password should be confirmed correctly", SocialServer.confirmWithPassword(user));
        assertEquals("Output should confirm password acceptance", "Password confirmed\n", outContent.toString());
    }

    @After
    public void cleanUp() {
        System.setOut(originalOut); // Restore System.out
        System.setIn(System.in); // Restore System.in
    }
}

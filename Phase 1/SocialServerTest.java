import org.junit.*;
import static org.junit.Assert.*;
import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.io.PrintStream;
import java.io.ByteArrayOutputStream;

public class SocialServerTest {
    private final ByteArrayOutputStream outContent = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;
    private SocialServer server;
    private User testUser;

    @Before
    public void setUp() {
        server = new SocialServer();
        testUser = new User("testUser", "testPassword123");
        System.setOut(new PrintStream(outContent)); // Capture System.out output
    }

    @Test
    public void testConfirmWithPasswordCorrect() {
        String input = "testPassword123\n";
        InputStream in = new ByteArrayInputStream(input.getBytes());
        System.setIn(in); // Set System.in to our test input

        assertTrue("Password confirmation should succeed", SocialServer.confirmWithPassword(testUser));

        System.setIn(System.in); // Reset System.in after the test
    }

    @Test
    public void testConfirmWithPasswordIncorrect() {
        String input = "wrongPassword\n";
        InputStream in = new ByteArrayInputStream(input.getBytes());
        System.setIn(in); // Set System.in to our test input

        assertFalse("Password confirmation should fail", SocialServer.confirmWithPassword(testUser));
        assertTrue("Should print 'Incorrect Password'", outContent.toString().contains("Incorrect Password"));

        System.setIn(System.in); // Reset System.in after the test
    }

    @After
    public void restoreStreams() {
        System.setIn(System.in); // Restore System.in to prevent issues in other tests
        System.setOut(originalOut); // Restore System.out
        outContent.reset();
    }
}

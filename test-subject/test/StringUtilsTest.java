import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

public class StringUtilsTest {

    @Test
    public void testReverse() {
        assertEquals("olleh", StringUtils.reverse("hello"));  // PASS
    }

    @Test
    public void testPalindrome() {
        assertTrue(StringUtils.isPalindrome("racecar"));  // PASS
    }

    @Test
    public void testNullPalindrome() {
        assertFalse(StringUtils.isPalindrome(null));  // PASS (but covers bug)
    }
}

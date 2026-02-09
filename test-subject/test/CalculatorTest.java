import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.junit.jupiter.api.Test;

/**
 * Tests for Calculator class.
 * 
 * EXPECTED FAILURES (will expose bugs):
 *   - testSubtract: expects 2, gets 8 (BUG #1)
 *   - testDivideByZero: expects exception, gets 0 (BUG #2)
 *   - testModulo: expects 1, gets 3 (BUG #3)
 *   - testPower: expects 8, gets 16 (BUG #4)
 *   - testAbsoluteNegative: expects 5, gets -5 (BUG #5)
 */
public class CalculatorTest {
    
    private final Calculator calc = new Calculator();
    
    // ===== PASSING TESTS (correct methods) =====
    
    @Test
    public void testAdd() {
        assertEquals(5, calc.add(2, 3));
    }
    
    @Test
    public void testAddNegatives() {
        assertEquals(-5, calc.add(-2, -3));
    }
    
    @Test
    public void testMultiply() {
        assertEquals(15, calc.multiply(3, 5));
    }
    
    @Test
    public void testMultiplyByZero() {
        assertEquals(0, calc.multiply(5, 0));
    }
    
    @Test
    public void testDivide() {
        assertEquals(4, calc.divide(12, 3));
    }
    
    @Test
    public void testNegate() {
        assertEquals(-5, calc.negate(5));
    }
    
    @Test
    public void testNegateNegative() {
        assertEquals(5, calc.negate(-5));
    }
    
    @Test
    public void testSquare() {
        assertEquals(25, calc.square(5));
    }
    
    @Test
    public void testIsEven() {
        assertTrue(calc.isEven(4));
    }
    
    @Test
    public void testIsOdd() {
        assertFalse(calc.isEven(5));
    }
    
    @Test
    public void testMax() {
        assertEquals(10, calc.max(5, 10));
    }
    
    @Test
    public void testMin() {
        assertEquals(5, calc.min(5, 10));
    }
    
    @Test
    public void testAbsolutePositive() {
        assertEquals(5, calc.absolute(5));
    }
    
    @Test
    public void testPowerZeroExponent() {
        assertEquals(1, calc.power(5, 0));
    }
    
    // ===== FAILING TESTS (expose bugs) =====
    
    @Test
    public void testSubtract() {
        // WILL FAIL: BUG #1 - subtract uses + instead of -
        // Expected: 5 - 3 = 2, Actual: 5 + 3 = 8
        assertEquals(2, calc.subtract(5, 3));
    }
    
    @Test
    public void testDivideByZero() {
        // WILL FAIL: BUG #2 - returns 0 instead of throwing exception
        assertThrows(ArithmeticException.class, () -> {
            calc.divide(10, 0);
        });
    }
    
    @Test
    public void testModulo() {
        // WILL FAIL: BUG #3 - modulo uses / instead of %
        // Expected: 10 % 3 = 1, Actual: 10 / 3 = 3
        assertEquals(1, calc.modulo(10, 3));
    }
    
    @Test
    public void testPower() {
        // WILL FAIL: BUG #4 - off-by-one, result is doubled
        // Expected: 2^3 = 8, Actual: 16 (loops 4 times instead of 3)
        assertEquals(8, calc.power(2, 3));
    }
    
    @Test
    public void testAbsoluteNegative() {
        // WILL FAIL: BUG #5 - doesn't negate negative numbers
        // Expected: |-5| = 5, Actual: -5
        assertEquals(5, calc.absolute(-5));
    }
}


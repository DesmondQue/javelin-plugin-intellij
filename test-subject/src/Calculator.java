/**
 * Simple Calculator
 * 
 * BUGS IN THIS FILE:
 *   - Line 19: subtract() uses + instead of -
 *   - Line 29: divide() returns 0 instead of throwing exception
 *   - Line 36: modulo() uses / instead of %
 *   - Line 43: power() loop starts at 1 instead of 0 (off-by-one)
 *   - Line 52: absolute() missing negation for negative numbers
 */
public class Calculator {
    
    public int add(int a, int b) {
        return a + b;
    }
    
    // BUG #1: should be a - b, but returns a + b
    public int subtract(int a, int b) {
        return a + b;  // BUG: wrong operator
    }
    
    public int multiply(int a, int b) {
        return a * b;
    }
    
    // BUG #2: should throw ArithmeticException on divide by zero
    public int divide(int a, int b) {
        if (b == 0) {
            return 0;  // BUG: should throw ArithmeticException
        }
        return a / b;
    }
    
    // BUG #3: should use % operator, but uses /
    public int modulo(int a, int b) {
        return a / b;  // BUG: wrong operator, should be a % b
    }
    
    // BUG #4: off-by-one error, loop should start at 0
    public int power(int base, int exponent) {
        if (exponent == 0) return 1;
        int result = 1;
        for (int i = 1; i <= exponent; i++) {  // BUG: should be i = 0 (This test passes and is coincidentally correct)
            result *= base;
        }
        return result;
    }
    
    // BUG #5: doesn't negate negative numbers
    public int absolute(int a) {
        if (a < 0) {
            return a;  // BUG: should return -a
        }
        return a;
    }
    
    public int negate(int a) {
        return -a;
    }
    
    public int square(int a) {
        return a * a;
    }
    
    public boolean isEven(int a) {
        return a % 2 == 0;
    }
    
    public int max(int a, int b) {
        return (a > b) ? a : b;
    }
    
    public int min(int a, int b) {
        return (a < b) ? a : b;
    }
}


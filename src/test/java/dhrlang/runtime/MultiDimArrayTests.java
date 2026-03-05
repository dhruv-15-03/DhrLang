package dhrlang.runtime;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Comprehensive tests for multi-dimensional arrays in DhrLang.
 * 
 * Tests cover:
 * 1. 2D, 3D, and higher dimensional arrays
 * 2. Jagged arrays (variable length rows)
 * 3. Bounds checking and error handling
 * 4. Default value initialization
 * 5. Edge cases and corner cases
 */
@DisplayName("Multi-Dimensional Array Tests")
public class MultiDimArrayTests {
    
    @Nested
    @DisplayName("Basic 2D Array Operations")
    class Basic2DArrayTests {
        
        @Test
        @DisplayName("Create and index 2D array")
        void createAndIndex2D() {
            String src = "class A { static kaam main(){ num[][] m = new num[2][3]; m[0][1] = 7; printLine(m[0][1]); } }";
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, "compile errors: " + result.stderr);
            assertFalse(result.hadRuntimeError, "runtime error: " + result.runtimeErrorMessage);
            assertEquals("7", result.stdout.trim());
        }
        
        @Test
        @DisplayName("2D array with all elements accessed")
        void fullGridAccess() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[3][3]; 
                        for(num i = 0; i < 3; i++) {
                            for(num j = 0; j < 3; j++) {
                                m[i][j] = i * 3 + j;
                            }
                        }
                        printLine(m[2][2]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            assertEquals("8", result.stdout.trim());
        }
        
        @Test
        @DisplayName("2D string array")
        void string2DArray() {
            String src = """
                class A { 
                    static kaam main(){
                        sab[][] names = new sab[2][2]; 
                        names[0][0] = "Hello";
                        names[1][1] = "World";
                        printLine(names[0][0] + " " + names[1][1]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertEquals("Hello World", result.stdout.trim());
        }
    }
    
    @Nested
    @DisplayName("3D and Higher Dimensional Arrays")
    class HigherDimensionTests {

        @Test
        @DisplayName("3D array type checking and access")
        void typeChecking3D() {
            String src = "class A { static kaam main(){ num[][][] m = new num[2][2][2]; m[1][1][1] = 5; printLine(m[1][1][1]); } }";
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            assertEquals("5", result.stdout.trim());
        }
        
        @Test
        @DisplayName("4D array allocation and access")
        void array4D() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][][][] arr = new num[2][2][2][2]; 
                        arr[1][1][1][1] = 42;
                        printLine(arr[1][1][1][1]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            assertEquals("42", result.stdout.trim());
        }
    }
    
    @Nested
    @DisplayName("Jagged Array Tests")
    class JaggedArrayTests {
        
        @Test
        @DisplayName("Create jagged array with different row lengths")
        void jaggedArrayDifferentLengths() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] jagged = new num[3][]; 
                        jagged[0] = new num[1];
                        jagged[1] = new num[5];
                        jagged[2] = new num[3];
                        printLine(arrayLength(jagged[1]));
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            assertEquals("5", result.stdout.trim());
        }
        
        @Test
        @DisplayName("Assign and read from jagged array")
        void jaggedArrayAssignment() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] jagged = new num[2][]; 
                        jagged[0] = new num[3];
                        jagged[1] = new num[2];
                        jagged[0][2] = 99;
                        jagged[1][1] = 88;
                        printLine(jagged[0][2]);
                        printLine(jagged[1][1]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            String[] lines = result.stdout.trim().split("\\R");
            assertEquals("99", lines[0]);
            assertEquals("88", lines[1]);
        }
    }
    
    @Nested
    @DisplayName("Error Handling Tests")
    class ErrorHandlingTests {

        @Test
        @DisplayName("Negative size in any dimension is an error")
        void negativeSizeInAnyDimIsError() {
            String src = "class A { static kaam main(){ num[][] m = new num[2][-1]; } }";
            var result = RuntimeTestUtil.runSource(src);
            assertTrue(result.hadRuntimeError || result.hadCompileErrors);
            assertTrue(result.stderr.contains("negative") || result.stderr.contains("BOUNDS_VIOLATION") || 
                      result.stderr.contains("DHR-E"), result.stderr);
        }

        @Test
        @DisplayName("Non-numeric size causes compile error")
        void nonNumericSizeCompileError() {
            String src = "class A { static kaam main(){ num n = 2; sab s = \"x\"; num[][] m = new num[n][s]; } }";
            var result = RuntimeTestUtil.runSource(src);
            assertTrue(result.hadCompileErrors);
            assertTrue(result.stderr.contains("Array size must be numeric") || 
                      result.stderr.contains("DHR-E") || 
                      result.stderr.contains("BOUNDS_VIOLATION"), result.stderr);
        }
        
        @Test
        @DisplayName("Index out of bounds on first dimension")
        void indexOutOfBoundsFirstDim() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[2][3]; 
                        num x = m[5][0];
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertTrue(result.hadRuntimeError || result.hadCompileErrors, 
                "Should have error: " + result.stdout);
        }
        
        @Test
        @DisplayName("Index out of bounds on second dimension")
        void indexOutOfBoundsSecondDim() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[2][3]; 
                        num x = m[0][10];
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertTrue(result.hadRuntimeError || result.hadCompileErrors);
        }
        
        @Test
        @DisplayName("Negative index is an error")
        void negativeIndexError() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[2][3]; 
                        num x = m[-1][0];
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertTrue(result.hadRuntimeError || result.hadCompileErrors);
        }
    }
    
    @Nested
    @DisplayName("Default Value Tests")
    class DefaultValueTests {
        
        @Test
        @DisplayName("Num array elements default to 0")
        void numDefaultsToZero() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[2][2]; 
                        printLine(m[0][0]);
                        printLine(m[1][1]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            String[] lines = result.stdout.trim().split("\\R");
            assertEquals("0", lines[0]);
            assertEquals("0", lines[1]);
        }
        
        @Test
        @DisplayName("Duo array elements default to 0.0")
        void duoDefaultsToZero() {
            String src = """
                class A { 
                    static kaam main(){
                        duo[][] m = new duo[2][2]; 
                        printLine(m[0][0]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertTrue(result.stdout.trim().equals("0.0") || result.stdout.trim().equals("0"));
        }
        
        @Test
        @DisplayName("Kya array elements default to false")
        void kyaDefaultsToFalse() {
            String src = """
                class A { 
                    static kaam main(){
                        kya[][] m = new kya[2][2]; 
                        printLine(m[0][0]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertEquals("false", result.stdout.trim());
        }
    }
    
    @Nested
    @DisplayName("Edge Cases")
    class EdgeCaseTests {
        
        @Test
        @DisplayName("Single element 2D array")
        void singleElement2D() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[1][1]; 
                        m[0][0] = 42;
                        printLine(m[0][0]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertEquals("42", result.stdout.trim());
        }
        
        @Test
        @DisplayName("Large 2D array (stress test)")
        void large2DArray() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[100][100]; 
                        m[99][99] = 999;
                        printLine(m[99][99]);
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            assertEquals("999", result.stdout.trim());
        }
        
        @Test
        @DisplayName("Array length of inner array")
        void innerArrayLength() {
            String src = """
                class A { 
                    static kaam main(){
                        num[][] m = new num[3][5]; 
                        printLine(arrayLength(m));
                        printLine(arrayLength(m[0]));
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            String[] lines = result.stdout.trim().split("\\R");
            assertEquals("3", lines[0]);
            assertEquals("5", lines[1]);
        }
        
        @Test
        @DisplayName("Pass 2D array to function")
        void passArrayToFunction() {
            String src = """
                class A { 
                    static num sumMatrix(num[][] m) {
                        num total = 0;
                        for(num i = 0; i < arrayLength(m); i++) {
                            for(num j = 0; j < arrayLength(m[i]); j++) {
                                total = total + m[i][j];
                            }
                        }
                        return total;
                    }
                    
                    static kaam main(){
                        num[][] m = new num[2][2]; 
                        m[0][0] = 1; m[0][1] = 2;
                        m[1][0] = 3; m[1][1] = 4;
                        printLine(sumMatrix(m));
                    } 
                }
                """;
            var result = RuntimeTestUtil.runSource(src);
            assertFalse(result.hadCompileErrors, result.stderr);
            assertFalse(result.hadRuntimeError, result.runtimeErrorMessage);
            assertEquals("10", result.stdout.trim());
        }
    }
}


package dhrlang.runtime;

import org.junit.jupiter.api.DisplayName;import org.junit.jupiter.api.Test;import static org.junit.jupiter.api.Assertions.*;

public class GoldenProgramsTests {
    private String canon(String s){return s.replace("\r","") .lines().map(l->l.stripTrailing()).reduce((a,b)->a+"\n"+b).orElse("").trim();}
    private void runAndAssert(String file, String expected) throws Exception { var r = RuntimeTestUtil.runFile(file); assertFalse(r.hadCompileErrors, "Compile errors in "+file+"\n"+r.stderr); assertFalse(r.hadRuntimeError, "Runtime error in "+file+"\n"+r.stderr); assertEquals(canon(expected), canon(r.stdout), "Mismatch for "+file); }

    @Test @DisplayName("basic syntax program") void basicSyntax() throws Exception { runAndAssert("input/test_basic_syntax.dhr", String.join("\n","Integer: 42","Decimal: 3.14159","String: Hello DhrLang!","Boolean: true","Character: A","Sum: 52","Product: 6.28318","Flag is true","Loop iteration: 1","Loop iteration: 2","Loop iteration: 3","While loop: 0","While loop: 1")); }

    @Test @DisplayName("arrays program") void arrays() throws Exception { runAndAssert("input/test_arrays.dhr", String.join("\n","First number: 1","Last name: Charlie","Modified third number: 99","All numbers:","Index 0: 1","Index 1: 2","Index 2: 99","Index 3: 4","Index 4: 5","All names:","Name 0: Alice","Name 1: Bob","Name 2: Charlie","Dynamic array:","Value 0: 10","Value 1: 20","Value 2: 30","Sum of all numbers: 111")); }

    @Test @DisplayName("oop features program") void oop() throws Exception { runAndAssert("input/test_oop_features.dhr", String.join("\n","Generic Animal makes a sound","Buddy barks!","Animal name: Generic Animal","Dog name: Buddy","Dog age: 3","Breed: Golden Retriever")); }

    @Test @DisplayName("strings program") void strings() throws Exception { runAndAssert("input/test_strings.dhr", String.join("\n","Combined: Hello World","Original: Programming in DhrLang","Length: 22","Character at index 5: a","Index of 'gram': 3","Index of 'Lang': 18","Index of 'xyz': -1","Contains 'Program': true","Contains 'Java': false","After replace: Programming in Java","'apple' equals 'apple': true","'apple' equals 'banana': false","Alphabet: abcdefghijklmnopqrstuvwxyz","Alphabet length: 26","First 5 characters:","Index 0: a","Index 1: b","Index 2: c","Index 3: d","Index 4: e","Numbers: 1, 2, 3, 4, 5, 6, 7, 8, 9, 10","Original sentence: The quick brown fox jumps over the lazy dog","Number of spaces: 8","Number of words (approximately): 9","Occurrences of 'the': 1")); }

    @Test @DisplayName("exceptions program") void exceptions() throws Exception { runAndAssert("input/test_exceptions.dhr", String.join("\n","Result: 5.0","Caught division error: Division by zero error","Finally block executed","Outer try block","Inner catch: Something went wrong","Outer catch: Re-throwing from inner catch","10 / 2 = 5.0","10 / 1 = 10.0","Error in iteration 0: Division by zero error","10 / -1 = -10.0","Program completed successfully")); }

    @Test @DisplayName("static methods program") void staticMethods() throws Exception { runAndAssert("input/test_static_methods.dhr", String.join("\n","Addition: 40","Multiplication: 56","Factorial of 5: 120","1 is not prime","2 is prime","3 is prime","4 is not prime","5 is prime","6 is not prime","7 is prime","8 is not prime","9 is not prime","10 is not prime","Original: Hello World","Reversed: dlroW olleH","Vowel count: 3","Programming has 3 vowels","DhrLang has 1 vowels","Compiler has 3 vowels","Algorithm has 3 vowels")); }

    @Test @DisplayName("algorithms program") void algorithms() throws Exception { runAndAssert("input/test_algorithms.dhr", String.join("\n","Factorial of 1 = 1","Factorial of 2 = 2","Factorial of 3 = 6","Factorial of 4 = 24","Factorial of 5 = 120","Factorial of 6 = 720","GCD of 48 and 18: 6","GCD of 100 and 25: 25","Prime numbers from 1 to 20:","2 3 5 7 11 13 17 19","Algorithm tests completed successfully!")); }

    @Test @DisplayName("edge control flow program") void controlFlowEdge() throws Exception { runAndAssert("input/test_edge_control_flow.dhr", String.join("\n","i=0, j=0","i=0, j=1","i=0, j=2","i=1, j=0","i=1, j=2","k=0","k=1","k=2")); }
}

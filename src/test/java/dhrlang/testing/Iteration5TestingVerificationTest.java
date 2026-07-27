package dhrlang.testing;

import dhrlang.ast.*;
import dhrlang.interpreter.*;
import dhrlang.testing.ContractAssertions.AssertionError;
import dhrlang.testing.ContractFuzzer.FuzzOutcome;
import dhrlang.testing.ContractFuzzer.FuzzResult;
import dhrlang.testing.ContractFuzzer.FuzzStats;
import dhrlang.testing.ContractTestRunner.TestResult;
import dhrlang.testing.ContractTestRunner.TestStatus;
import dhrlang.testing.InvariantChecker.InvariantInfo;
import dhrlang.testing.InvariantChecker.InvariantViolation;
import dhrlang.testing.TestCoverageTracker.ContractCoverage;
import dhrlang.testing.TestCoverageTracker.FunctionCoverage;
import dhrlang.testing.TestReporter.OutputFormat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.BeforeEach;

import java.math.BigInteger;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Iteration 5 – Testing &amp; Verification
 *
 * <p>Tests cover all new classes in the {@code dhrlang.testing} package:
 * ContractTestRunner, ContractAssertions, TestCheatcodes, InvariantChecker,
 * ContractFuzzer, TestCoverageTracker, and TestReporter.</p>
 *
 * <p>Covers user stories SC-401 through SC-405.</p>
 */
class Iteration5TestingVerificationTest {

    // ═══════════════════════════════════════════════════
    //  Helpers: build AST nodes for testing
    // ═══════════════════════════════════════════════════

    /** Build a FunctionDecl with no annotations. */
    private static FunctionDecl makeFn(String name) {
        return new FunctionDecl("void", name, List.of(), new Block(List.of()));
    }

    /** Build a FunctionDecl with a body of N empty statements. */
    private static FunctionDecl makeFnWithBody(String name, int stmts) {
        List<Statement> body = new ArrayList<>();
        for (int i = 0; i < stmts; i++) {
            body.add(new Block(List.of())); // each Block counts as 1 statement
        }
        return new FunctionDecl("void", name, List.of(), new Block(body));
    }

    /** Build a FunctionDecl with parameters. */
    private static FunctionDecl makeFnWithParams(String name, String... paramTypes) {
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        return new FunctionDecl("void", name, params, new Block(List.of()));
    }

    /** Build a FunctionDecl with contract annotations. */
    private static FunctionDecl makeFnWithAnnotations(String name,
                                                       ContractAnnotation... annotations) {
        Set<ContractAnnotation> annSet = EnumSet.noneOf(ContractAnnotation.class);
        annSet.addAll(Arrays.asList(annotations));
        return new FunctionDecl("void", name, List.of(), new Block(List.of()),
                Set.of(), annSet);
    }

    /** Build a FunctionDecl with annotations and a body. */
    private static FunctionDecl makeFnAnnotatedWithBody(String name,
                                                         int stmts,
                                                         ContractAnnotation... annotations) {
        Set<ContractAnnotation> annSet = EnumSet.noneOf(ContractAnnotation.class);
        annSet.addAll(Arrays.asList(annotations));
        List<Statement> body = new ArrayList<>();
        for (int i = 0; i < stmts; i++) {
            body.add(new Block(List.of()));
        }
        return new FunctionDecl("void", name, List.of(), new Block(body),
                Set.of(), annSet);
    }

    /** Build a FunctionDecl with annotations AND parameters. */
    private static FunctionDecl makeFnAnnotatedWithParams(String name,
                                                           ContractAnnotation annotation,
                                                           String... paramTypes) {
        Set<ContractAnnotation> annSet = EnumSet.of(annotation);
        List<VarDecl> params = new ArrayList<>();
        for (int i = 0; i < paramTypes.length; i++) {
            params.add(new VarDecl(paramTypes[i], "p" + i, null));
        }
        return new FunctionDecl("void", name, params, new Block(List.of()),
                Set.of(), annSet);
    }

    /** Build a ClassDecl with functions. */
    private static ClassDecl makeClass(String name, FunctionDecl... fns) {
        return new ClassDecl(name, null, List.of(fns), List.of());
    }

    /** Build a Program. */
    private static Program makeProgram(ClassDecl... classes) {
        return new Program(List.of(classes));
    }

    // ═══════════════════════════════════════════════════
    //  1.  ContractAnnotation — new annotations
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("1. Contract Annotations – @test, @beforeEach, @afterEach")
    class AnnotationTests {

        @Test
        @DisplayName("TEST annotation exists and has correct syntax")
        void testAnnotationExists() {
            ContractAnnotation ann = ContractAnnotation.TEST;
            assertEquals("@test", ann.getSyntax());
            assertTrue(ann.appliesToMethod());
            assertFalse(ann.appliesToClass());
            assertFalse(ann.appliesToField());
        }

        @Test
        @DisplayName("BEFORE_EACH annotation exists and has correct syntax")
        void beforeEachAnnotationExists() {
            ContractAnnotation ann = ContractAnnotation.BEFORE_EACH;
            assertEquals("@beforeEach", ann.getSyntax());
            assertTrue(ann.appliesToMethod());
        }

        @Test
        @DisplayName("AFTER_EACH annotation exists and has correct syntax")
        void afterEachAnnotationExists() {
            ContractAnnotation ann = ContractAnnotation.AFTER_EACH;
            assertEquals("@afterEach", ann.getSyntax());
            assertTrue(ann.appliesToMethod());
        }

        @Test
        @DisplayName("INVARIANT annotation still present")
        void invariantStillPresent() {
            ContractAnnotation ann = ContractAnnotation.INVARIANT;
            assertEquals("@invariant", ann.getSyntax());
            assertTrue(ann.appliesToMethod());
        }

        @Test
        @DisplayName("FunctionDecl can hold @test annotation")
        void functionDeclHoldsTestAnnotation() {
            FunctionDecl fn = makeFnWithAnnotations("testTransfer",
                    ContractAnnotation.TEST);
            assertTrue(fn.hasContractAnnotation(ContractAnnotation.TEST));
            assertFalse(fn.hasContractAnnotation(ContractAnnotation.VIEW));
        }

        @Test
        @DisplayName("FunctionDecl can hold @beforeEach annotation")
        void functionDeclHoldsBeforeEach() {
            FunctionDecl fn = makeFnWithAnnotations("setup",
                    ContractAnnotation.BEFORE_EACH);
            assertTrue(fn.hasContractAnnotation(ContractAnnotation.BEFORE_EACH));
        }

        @Test
        @DisplayName("FunctionDecl can hold @afterEach annotation")
        void functionDeclHoldsAfterEach() {
            FunctionDecl fn = makeFnWithAnnotations("teardown",
                    ContractAnnotation.AFTER_EACH);
            assertTrue(fn.hasContractAnnotation(ContractAnnotation.AFTER_EACH));
        }

        @Test
        @DisplayName("New annotations do not forbid state modification")
        void newAnnotationsAllowStateMod() {
            assertFalse(ContractAnnotation.TEST.forbidsStateModification());
            assertFalse(ContractAnnotation.BEFORE_EACH.forbidsStateModification());
            assertFalse(ContractAnnotation.AFTER_EACH.forbidsStateModification());
        }
    }

    // ═══════════════════════════════════════════════════
    //  2.  ContractTestRunner (SC-401)
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("2. ContractTestRunner – SC-401: Built-in test framework")
    class TestRunnerTests {

        private Program programWithTests;
        private ContractTestRunner runner;

        @BeforeEach
        void setUp() {
            FunctionDecl beforeEach = makeFnAnnotatedWithBody("setup", 1,
                    ContractAnnotation.BEFORE_EACH);
            FunctionDecl afterEach = makeFnAnnotatedWithBody("teardown", 1,
                    ContractAnnotation.AFTER_EACH);
            FunctionDecl test1 = makeFnAnnotatedWithBody("testTransfer", 3,
                    ContractAnnotation.TEST);
            FunctionDecl test2 = makeFnAnnotatedWithBody("testBalance", 2,
                    ContractAnnotation.TEST);
            FunctionDecl transfer = makeFnWithBody("transfer", 5);
            FunctionDecl viewFn = makeFnWithAnnotations("getBalance",
                    ContractAnnotation.VIEW);

            ClassDecl tokenClass = makeClass("Token",
                    beforeEach, afterEach, test1, test2, transfer, viewFn);

            FunctionDecl test3 = makeFnAnnotatedWithBody("testMint", 2,
                    ContractAnnotation.TEST);
            ClassDecl nftClass = makeClass("NFT", test3);

            programWithTests = makeProgram(tokenClass, nftClass);
            runner = new ContractTestRunner(programWithTests);
        }

        @Test
        @DisplayName("Discovers test classes correctly")
        void discoversTestClasses() {
            List<ClassDecl> classes = runner.discoverTestClasses();
            assertEquals(2, classes.size());
            assertEquals("Token", classes.get(0).getName());
            assertEquals("NFT", classes.get(1).getName());
        }

        @Test
        @DisplayName("Discovers test methods within a class")
        void discoversTestMethods() {
            ClassDecl token = programWithTests.getClasses().get(0);
            List<FunctionDecl> tests = runner.discoverTests(token);
            assertEquals(2, tests.size());
            assertEquals("testTransfer", tests.get(0).getName());
            assertEquals("testBalance", tests.get(1).getName());
        }

        @Test
        @DisplayName("Finds @beforeEach hook")
        void findsBeforeEach() {
            ClassDecl token = programWithTests.getClasses().get(0);
            FunctionDecl before = runner.findBeforeEach(token);
            assertNotNull(before);
            assertEquals("setup", before.getName());
        }

        @Test
        @DisplayName("Finds @afterEach hook")
        void findsAfterEach() {
            ClassDecl token = programWithTests.getClasses().get(0);
            FunctionDecl after = runner.findAfterEach(token);
            assertNotNull(after);
            assertEquals("teardown", after.getName());
        }

        @Test
        @DisplayName("Returns null when no hooks exist")
        void returnsNullForMissingHooks() {
            ClassDecl nft = programWithTests.getClasses().get(1);
            assertNull(runner.findBeforeEach(nft));
            assertNull(runner.findAfterEach(nft));
        }

        @Test
        @DisplayName("runAll executes all tests")
        void runAllExecutesTests() {
            runner.runAll();
            assertEquals(3, runner.totalTests());
            assertEquals(3, runner.passedCount());
            assertEquals(0, runner.failedCount());
            assertEquals(0, runner.errorCount());
            assertTrue(runner.allPassed());
        }

        @Test
        @DisplayName("Single test returns PASSED for valid function")
        void singleTestPassed() {
            ClassDecl token = programWithTests.getClasses().get(0);
            FunctionDecl test = runner.discoverTests(token).get(0);
            TestResult result = runner.runSingleTest(token, test, null, null);
            assertEquals(TestStatus.PASSED, result.getStatus());
            assertEquals("Token", result.getContractName());
            assertEquals("testTransfer", result.getTestName());
        }

        @Test
        @DisplayName("formatSummary includes test info")
        void formatSummaryWorks() {
            runner.runAll();
            String summary = runner.formatSummary();
            assertTrue(summary.contains("DhrLang Contract Test Results"));
            assertTrue(summary.contains("Token::testTransfer"));
            assertTrue(summary.contains("PASSED"));
        }

        @Test
        @DisplayName("Empty program produces no test classes")
        void emptyProgramNoTests() {
            Program empty = makeProgram(makeClass("Empty", makeFn("notATest")));
            ContractTestRunner emptyRunner = new ContractTestRunner(empty);
            assertTrue(emptyRunner.discoverTestClasses().isEmpty());
            emptyRunner.runAll();
            assertEquals(0, emptyRunner.totalTests());
        }

        @Test
        @DisplayName("Coverage tracker receives data from test run")
        void coverageTrackerReceivesData() {
            runner.runAll();
            TestCoverageTracker tracker = runner.getCoverageTracker();
            assertTrue(tracker.getTotalCalls() > 0);
        }

        @Test
        @DisplayName("Test result toString includes icon")
        void testResultToString() {
            TestResult r = new TestResult("Token", "testX",
                    TestStatus.PASSED, "", 42);
            String s = r.toString();
            assertTrue(s.contains("✓"));
            assertTrue(s.contains("Token::testX"));
            assertTrue(s.contains("42ms"));
        }

        @Test
        @DisplayName("Failed test result toString includes X icon")
        void failedResultToString() {
            TestResult r = new TestResult("Token", "testY",
                    TestStatus.FAILED, "value mismatch", 10);
            String s = r.toString();
            assertTrue(s.contains("✗"));
            assertTrue(s.contains("value mismatch"));
        }

        @Test
        @DisplayName("Error test result toString includes ! icon")
        void errorResultToString() {
            TestResult r = new TestResult("NFT", "testZ",
                    TestStatus.ERROR, "NPE: null", 5);
            assertTrue(r.toString().contains("!"));
        }

        @Test
        @DisplayName("Skipped test result toString includes ○ icon")
        void skippedResultToString() {
            TestResult r = new TestResult("Dao", "testVote",
                    TestStatus.SKIPPED, "disabled", 0);
            assertTrue(r.toString().contains("○"));
        }

        @Test
        @DisplayName("Results list is unmodifiable")
        void resultsUnmodifiable() {
            runner.runAll();
            assertThrows(UnsupportedOperationException.class,
                    () -> runner.getResults().add(null));
        }
    }

    // ═══════════════════════════════════════════════════
    //  3.  ContractAssertions
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("3. ContractAssertions – Assertion functions")
    class AssertionTests {

        private final Interpreter interp = new Interpreter();

        @Test
        @DisplayName("assertEqual passes for equal values")
        void assertEqualPasses() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertEqual().call(interp, List.of(42, 42)));
        }

        @Test
        @DisplayName("assertEqual fails for different values")
        void assertEqualFails() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertEqual().call(interp, List.of(1, 2)));
        }

        @Test
        @DisplayName("assertEqual handles null values")
        void assertEqualNulls() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertEqual().call(interp, Arrays.asList(null, null)));
        }

        @Test
        @DisplayName("assertEqual compares numbers across types")
        void assertEqualMixedNumeric() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertEqual().call(interp, List.of(42, 42.0)));
        }

        @Test
        @DisplayName("assertNotEqual passes for different values")
        void assertNotEqualPasses() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertNotEqual().call(interp, List.of("a", "b")));
        }

        @Test
        @DisplayName("assertNotEqual fails for same values")
        void assertNotEqualFails() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertNotEqual().call(interp, List.of(5, 5)));
        }

        @Test
        @DisplayName("assertTrue passes for true")
        void assertTruePasses() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertTrue().call(interp, List.of(true)));
        }

        @Test
        @DisplayName("assertTrue fails for false")
        void assertTrueFails() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertTrue().call(interp, List.of(false)));
        }

        @Test
        @DisplayName("assertTrue treats non-zero as truthy")
        void assertTrueNonZero() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertTrue().call(interp, List.of(1)));
        }

        @Test
        @DisplayName("assertTrue treats null as falsy")
        void assertTrueNull() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertTrue().call(interp, Arrays.asList((Object) null)));
        }

        @Test
        @DisplayName("assertTrue treats non-empty string as truthy")
        void assertTrueString() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertTrue().call(interp, List.of("hello")));
        }

        @Test
        @DisplayName("assertFalse passes for false")
        void assertFalsePasses() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertFalse().call(interp, List.of(false)));
        }

        @Test
        @DisplayName("assertFalse fails for true")
        void assertFalseFails() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertFalse().call(interp, List.of(true)));
        }

        @Test
        @DisplayName("assertFalse treats zero as falsy")
        void assertFalseZero() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertFalse().call(interp, List.of(0)));
        }

        @Test
        @DisplayName("assertGreaterThan passes when a > b")
        void assertGreaterThanPasses() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertGreaterThan().call(interp, List.of(10, 5)));
        }

        @Test
        @DisplayName("assertGreaterThan fails when a <= b")
        void assertGreaterThanFails() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertGreaterThan().call(interp, List.of(5, 10)));
        }

        @Test
        @DisplayName("assertLessThan passes when a < b")
        void assertLessThanPasses() {
            assertDoesNotThrow(() ->
                    ContractAssertions.assertLessThan().call(interp, List.of(3, 7)));
        }

        @Test
        @DisplayName("assertLessThan fails when a >= b")
        void assertLessThanFails() {
            assertThrows(AssertionError.class, () ->
                    ContractAssertions.assertLessThan().call(interp, List.of(7, 3)));
        }

        @Test
        @DisplayName("assertReverts returns expect marker")
        void assertRevertsReturns() {
            Object result = ContractAssertions.assertReverts()
                    .call(interp, List.of("insufficientBalance"));
            assertTrue(result.toString().startsWith("EXPECT_REVERT:"));
        }

        @Test
        @DisplayName("assertGasBelow returns gas limit marker")
        void assertGasBelowReturns() {
            Object result = ContractAssertions.assertGasBelow()
                    .call(interp, List.of(50000));
            assertTrue(result.toString().startsWith("GAS_LIMIT:"));
        }

        @Test
        @DisplayName("allAssertions returns all 8 assertion functions")
        void allAssertionsSize() {
            Map<String, NativeFunction> all = ContractAssertions.allAssertions();
            assertEquals(8, all.size());
            assertTrue(all.containsKey("assertEqual"));
            assertTrue(all.containsKey("assertNotEqual"));
            assertTrue(all.containsKey("assertTrue"));
            assertTrue(all.containsKey("assertFalse"));
            assertTrue(all.containsKey("assertGreaterThan"));
            assertTrue(all.containsKey("assertLessThan"));
            assertTrue(all.containsKey("assertReverts"));
            assertTrue(all.containsKey("assertGasBelow"));
        }

        @Test
        @DisplayName("assertEqual arity is 2")
        void assertEqualArity() {
            assertEquals(2, ContractAssertions.assertEqual().arity());
        }

        @Test
        @DisplayName("assertTrue arity is 1")
        void assertTrueArity() {
            assertEquals(1, ContractAssertions.assertTrue().arity());
        }

        @Test
        @DisplayName("AssertionError stores expected and actual")
        void assertionErrorDetails() {
            AssertionError err = new AssertionError("msg", "expected", "actual");
            assertEquals("expected", err.getExpected());
            assertEquals("actual", err.getActual());
            assertEquals("msg", err.getMessage());
        }

        @Test
        @DisplayName("deepEquals handles null symmetrically")
        void deepEqualsNullSymmetry() {
            assertTrue(ContractAssertions.deepEquals(null, null));
            assertFalse(ContractAssertions.deepEquals(null, "x"));
            assertFalse(ContractAssertions.deepEquals("x", null));
        }

        @Test
        @DisplayName("isTruthy handles various types")
        void isTruthyVariousTypes() {
            assertFalse(ContractAssertions.isTruthy(null));
            assertFalse(ContractAssertions.isTruthy(false));
            assertFalse(ContractAssertions.isTruthy(0));
            assertFalse(ContractAssertions.isTruthy(""));
            assertTrue(ContractAssertions.isTruthy(true));
            assertTrue(ContractAssertions.isTruthy(1));
            assertTrue(ContractAssertions.isTruthy("hello"));
            assertTrue(ContractAssertions.isTruthy(new Object()));
        }

        @Test
        @DisplayName("stringify handles null")
        void stringifyNull() {
            assertEquals("null", ContractAssertions.stringify(null));
            assertEquals("42", ContractAssertions.stringify(42));
        }
    }

    // ═══════════════════════════════════════════════════
    //  4.  TestCheatcodes
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("4. TestCheatcodes – VM cheatcodes (prank, deal, warp, roll)")
    class CheatcodeTests {

        private TestCheatcodes cc;

        @BeforeEach
        void setUp() {
            cc = new TestCheatcodes();
        }

        // --- prank ---

        @Test
        @DisplayName("prank sets temporary sender")
        void prankSetsSender() {
            cc.prank("0xABCD");
            assertTrue(cc.isPrankActive());
            assertEquals("0xABCD", cc.getEffectiveSender());
        }

        @Test
        @DisplayName("prank is consumed after consumePrank")
        void prankConsumed() {
            cc.prank("0xABCD");
            cc.consumePrank();
            assertFalse(cc.isPrankActive());
            // Falls back to permanent sender
            assertNotEquals("0xABCD", cc.getEffectiveSender());
        }

        @Test
        @DisplayName("setCurrentSender changes permanent sender")
        void setCurrentSenderWorks() {
            cc.setCurrentSender("0x1234");
            assertEquals("0x1234", cc.getEffectiveSender());
        }

        @Test
        @DisplayName("prank overrides permanent sender")
        void prankOverrides() {
            cc.setCurrentSender("0x1111");
            cc.prank("0x2222");
            assertEquals("0x2222", cc.getEffectiveSender());
        }

        @Test
        @DisplayName("prank null throws NPE")
        void prankNullThrows() {
            assertThrows(NullPointerException.class, () -> cc.prank(null));
        }

        // --- deal ---

        @Test
        @DisplayName("deal sets balance")
        void dealSetsBalance() {
            cc.deal("0xAAA", BigInteger.valueOf(1_000_000));
            assertEquals(BigInteger.valueOf(1_000_000), cc.getBalance("0xAAA"));
        }

        @Test
        @DisplayName("deal convenience overload with long")
        void dealWithLong() {
            cc.deal("0xBBB", 500_000L);
            assertEquals(BigInteger.valueOf(500_000), cc.getBalance("0xBBB"));
        }

        @Test
        @DisplayName("Unknown address has zero balance")
        void unknownAddressZero() {
            assertEquals(BigInteger.ZERO, cc.getBalance("0xUNKNOWN"));
        }

        @Test
        @DisplayName("deal with negative amount throws")
        void dealNegativeThrows() {
            assertThrows(IllegalArgumentException.class,
                    () -> cc.deal("0xAAA", BigInteger.valueOf(-1)));
        }

        // --- warp ---

        @Test
        @DisplayName("warp sets block timestamp")
        void warpSetsTimestamp() {
            cc.warp(1_800_000_000L);
            assertEquals(1_800_000_000L, cc.getBlockTimestamp());
        }

        @Test
        @DisplayName("Default timestamp is reasonable")
        void defaultTimestamp() {
            assertTrue(cc.getBlockTimestamp() > 0);
        }

        @Test
        @DisplayName("warp with negative timestamp throws")
        void warpNegativeThrows() {
            assertThrows(IllegalArgumentException.class, () -> cc.warp(-1));
        }

        // --- roll ---

        @Test
        @DisplayName("roll sets block number")
        void rollSetsBlock() {
            cc.roll(42);
            assertEquals(42, cc.getBlockNumber());
        }

        @Test
        @DisplayName("Default block number is 1")
        void defaultBlock() {
            assertEquals(1, cc.getBlockNumber());
        }

        @Test
        @DisplayName("roll with negative number throws")
        void rollNegativeThrows() {
            assertThrows(IllegalArgumentException.class, () -> cc.roll(-1));
        }

        // --- store / load ---

        @Test
        @DisplayName("store and load work together")
        void storeLoadRoundTrip() {
            cc.store("0xCONTRACT", 0, BigInteger.valueOf(999));
            assertEquals(BigInteger.valueOf(999), cc.load("0xCONTRACT", 0));
        }

        @Test
        @DisplayName("load returns zero for empty slot")
        void loadEmpty() {
            assertEquals(BigInteger.ZERO, cc.load("0xNONE", 5));
        }

        @Test
        @DisplayName("Multiple slots per address")
        void multipleSlots() {
            cc.store("0xC", 0, BigInteger.ONE);
            cc.store("0xC", 1, BigInteger.TEN);
            assertEquals(BigInteger.ONE, cc.load("0xC", 0));
            assertEquals(BigInteger.TEN, cc.load("0xC", 1));
        }

        // --- expectRevert ---

        @Test
        @DisplayName("expectRevert sets flag")
        void expectRevertSetsFlag() {
            cc.expectRevert();
            assertTrue(cc.isRevertExpected());
            assertNull(cc.getExpectedRevertMessage());
        }

        @Test
        @DisplayName("expectRevert with message")
        void expectRevertWithMessage() {
            cc.expectRevert("overflow");
            assertTrue(cc.isRevertExpected());
            assertEquals("overflow", cc.getExpectedRevertMessage());
        }

        @Test
        @DisplayName("consumeExpectRevert clears flag")
        void consumeExpectRevert() {
            cc.expectRevert("err");
            cc.consumeExpectRevert();
            assertFalse(cc.isRevertExpected());
            assertNull(cc.getExpectedRevertMessage());
        }

        // --- gas tracking ---

        @Test
        @DisplayName("Gas tracking accumulates")
        void gasTracking() {
            cc.recordGas(100);
            cc.recordGas(200);
            assertEquals(300, cc.getGasUsed());
        }

        @Test
        @DisplayName("resetGas clears counter")
        void resetGasClears() {
            cc.recordGas(500);
            cc.resetGas();
            assertEquals(0, cc.getGasUsed());
        }

        // --- logging ---

        @Test
        @DisplayName("Cheatcode log records calls")
        void cheatcodeLogRecords() {
            cc.prank("0xA");
            cc.deal("0xB", 100L);
            cc.warp(1234L);
            cc.roll(5);
            List<String> log = cc.getCheatcodeLog();
            assertEquals(4, log.size());
            assertTrue(log.get(0).contains("prank"));
            assertTrue(log.get(1).contains("deal"));
            assertTrue(log.get(2).contains("warp"));
            assertTrue(log.get(3).contains("roll"));
        }

        // --- reset ---

        @Test
        @DisplayName("reset clears all state")
        void resetClearsAll() {
            cc.prank("0xA");
            cc.deal("0xB", 100L);
            cc.warp(9999L);
            cc.roll(999);
            cc.store("0xC", 0, BigInteger.TEN);
            cc.expectRevert();
            cc.recordGas(500);
            cc.reset();

            assertFalse(cc.isPrankActive());
            assertEquals(BigInteger.ZERO, cc.getBalance("0xB"));
            assertEquals(1_700_000_000L, cc.getBlockTimestamp());
            assertEquals(1, cc.getBlockNumber());
            assertEquals(BigInteger.ZERO, cc.load("0xC", 0));
            assertFalse(cc.isRevertExpected());
            assertEquals(0, cc.getGasUsed());
            assertTrue(cc.getCheatcodeLog().isEmpty());
        }

        @Test
        @DisplayName("formatState produces readable output")
        void formatStateWorks() {
            cc.prank("0xABC");
            String state = cc.formatState();
            assertTrue(state.contains("sender:"));
            assertTrue(state.contains("timestamp:"));
            assertTrue(state.contains("block:"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  5.  InvariantChecker (SC-402)
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("5. InvariantChecker – SC-402: @invariant formal properties")
    class InvariantCheckerTests {

        @Test
        @DisplayName("Discovers @invariant methods")
        void discoversInvariants() {
            FunctionDecl inv = makeFnAnnotatedWithBody("totalSupplyNonNeg", 2,
                    ContractAnnotation.INVARIANT);
            FunctionDecl transfer = makeFn("transfer");
            ClassDecl cls = makeClass("Token", inv, transfer);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            List<InvariantInfo> invs = checker.discoverInvariants();
            assertEquals(1, invs.size());
            assertEquals("totalSupplyNonNeg", invs.get(0).getMethodName());
            assertEquals("Token", invs.get(0).getContractName());
        }

        @Test
        @DisplayName("Discovers state-modifying functions")
        void discoversStateModifiers() {
            FunctionDecl inv = makeFnWithAnnotations("checkInv",
                    ContractAnnotation.INVARIANT);
            FunctionDecl viewFn = makeFnWithAnnotations("getX",
                    ContractAnnotation.VIEW);
            FunctionDecl pureFn = makeFnWithAnnotations("calc",
                    ContractAnnotation.PURE);
            FunctionDecl transfer = makeFn("transfer");
            FunctionDecl mint = makeFn("mint");
            ClassDecl cls = makeClass("Token", inv, viewFn, pureFn, transfer, mint);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            Map<String, List<String>> mods = checker.discoverStateModifyingFunctions();
            assertEquals(1, mods.size());
            assertTrue(mods.containsKey("Token"));
            assertEquals(2, mods.get("Token").size());
            assertTrue(mods.get("Token").contains("transfer"));
            assertTrue(mods.get("Token").contains("mint"));
        }

        @Test
        @DisplayName("analyzeAll detects empty-body invariants")
        void detectsEmptyInvariants() {
            FunctionDecl inv = makeFnWithAnnotations("mustHold",
                    ContractAnnotation.INVARIANT);
            FunctionDecl transfer = makeFn("transfer");
            ClassDecl cls = makeClass("Token", inv, transfer);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            checker.analyzeAll();

            assertTrue(checker.hasViolations());
            assertEquals(1, checker.getViolations().size());
            assertTrue(checker.getViolations().get(0).getDescription()
                    .contains("no body"));
        }

        @Test
        @DisplayName("Invariant with body passes static check")
        void invariantWithBodyPasses() {
            FunctionDecl inv = makeFnAnnotatedWithBody("supply >= 0", 3,
                    ContractAnnotation.INVARIANT);
            FunctionDecl viewFn = makeFnWithAnnotations("getSupply",
                    ContractAnnotation.VIEW);
            // Only view functions — no state modifiers
            ClassDecl cls = makeClass("Token", inv, viewFn);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            checker.analyzeAll();
            assertFalse(checker.hasViolations());
        }

        @Test
        @DisplayName("Custom evaluator is called for each invariant check")
        void customEvaluator() {
            FunctionDecl inv = makeFnAnnotatedWithBody("alwaysTrue", 2,
                    ContractAnnotation.INVARIANT);
            FunctionDecl transfer = makeFn("transfer");
            ClassDecl cls = makeClass("Token", inv, transfer);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            // Evaluator that always returns false (violation)
            checker.setInvariantEvaluator(info -> false);
            checker.analyzeAll();

            assertTrue(checker.hasViolations());
            InvariantViolation v = checker.getViolations().get(0);
            assertEquals("Token", v.getContractName());
            assertEquals("alwaysTrue", v.getInvariantName());
            assertEquals("transfer", v.getTriggerFunction());
        }

        @Test
        @DisplayName("Custom evaluator returns true -> no violations from evaluator")
        void customEvaluatorPasses() {
            FunctionDecl inv = makeFnAnnotatedWithBody("myInv", 2,
                    ContractAnnotation.INVARIANT);
            FunctionDecl transfer = makeFn("transfer");
            ClassDecl cls = makeClass("Token", inv, transfer);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            checker.setInvariantEvaluator(info -> true);
            checker.analyzeAll();

            // No violations from evaluator; the static check also won't fire
            // because the invariant has a body
            assertFalse(checker.hasViolations());
        }

        @Test
        @DisplayName("No contracts produces no violations")
        void noContractsNoViolations() {
            Program p = makeProgram();
            InvariantChecker checker = new InvariantChecker(p);
            checker.analyzeAll();
            assertFalse(checker.hasViolations());
            assertTrue(checker.getDiscoveredInvariants().isEmpty());
        }

        @Test
        @DisplayName("formatReport produces readable output")
        void formatReportWorks() {
            FunctionDecl inv = makeFnAnnotatedWithBody("inv1", 1,
                    ContractAnnotation.INVARIANT);
            FunctionDecl fn = makeFn("buy");
            ClassDecl cls = makeClass("Market", inv, fn);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            checker.analyzeAll();
            String report = checker.formatReport();
            assertTrue(report.contains("Invariant Analysis Report"));
            assertTrue(report.contains("Market"));
        }

        @Test
        @DisplayName("InvariantViolation toString is descriptive")
        void violationToString() {
            InvariantViolation v = new InvariantViolation(
                    "Token", "totalOk", "transfer", "broke it");
            String s = v.toString();
            assertTrue(s.contains("Token"));
            assertTrue(s.contains("totalOk"));
            assertTrue(s.contains("transfer"));
            assertTrue(s.contains("broke it"));
        }

        @Test
        @DisplayName("Excludes @test and @beforeEach from state-modifying set")
        void excludesLifecycleFunctions() {
            FunctionDecl testFn = makeFnWithAnnotations("testX",
                    ContractAnnotation.TEST);
            FunctionDecl beforeFn = makeFnWithAnnotations("setup",
                    ContractAnnotation.BEFORE_EACH);
            FunctionDecl transfer = makeFn("transfer");
            ClassDecl cls = makeClass("Token", testFn, beforeFn, transfer);
            Program p = makeProgram(cls);

            InvariantChecker checker = new InvariantChecker(p);
            Map<String, List<String>> mods = checker.discoverStateModifyingFunctions();
            assertEquals(List.of("transfer"), mods.get("Token"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  6.  ContractFuzzer (SC-403)
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("6. ContractFuzzer – SC-403: Fuzzing support")
    class FuzzerTests {

        @Test
        @DisplayName("Discovers fuzzable targets (excludes view/pure/test/lifecycle)")
        void discoversTargets() {
            FunctionDecl transfer = makeFn("transfer");
            FunctionDecl mint = makeFn("mint");
            FunctionDecl getX = makeFnWithAnnotations("getX", ContractAnnotation.VIEW);
            FunctionDecl calc = makeFnWithAnnotations("calc", ContractAnnotation.PURE);
            FunctionDecl inv = makeFnWithAnnotations("inv", ContractAnnotation.INVARIANT);
            FunctionDecl testFn = makeFnWithAnnotations("test1", ContractAnnotation.TEST);
            FunctionDecl setup = makeFnWithAnnotations("setup", ContractAnnotation.BEFORE_EACH);
            ClassDecl cls = makeClass("Token", transfer, mint, getX, calc, inv, testFn, setup);
            Program p = makeProgram(cls);

            ContractFuzzer fuzzer = new ContractFuzzer(p);
            List<FunctionDecl> targets = fuzzer.discoverFuzzTargets(cls);
            assertEquals(2, targets.size());
            assertEquals("transfer", targets.get(0).getName());
            assertEquals("mint", targets.get(1).getName());
        }

        @Test
        @DisplayName("fuzzAll runs and produces results")
        void fuzzAllRuns() {
            FunctionDecl transfer = makeFnWithParams("transfer", "Address", "uint256");
            ClassDecl cls = makeClass("Token", transfer);
            Program p = makeProgram(cls);

            ContractFuzzer fuzzer = new ContractFuzzer(p);
            fuzzer.setRuns(10);
            fuzzer.setSeed(42);
            fuzzer.fuzzAll();

            assertFalse(fuzzer.getResults().isEmpty());
            assertFalse(fuzzer.getStats().isEmpty());
        }

        @Test
        @DisplayName("Fuzzer generates correct number of runs")
        void correctRunCount() {
            FunctionDecl fn = makeFn("buy");
            ClassDecl cls = makeClass("Market", fn);
            Program p = makeProgram(cls);

            ContractFuzzer fuzzer = new ContractFuzzer(p);
            fuzzer.setRuns(50);
            fuzzer.setSeed(123);
            fuzzer.fuzzAll();

            FuzzStats stats = fuzzer.getStats().get("Market::buy");
            assertNotNull(stats);
            assertEquals(50, stats.getTotalRuns());
        }

        @Test
        @DisplayName("generateValue for different types")
        void generateValueTypes() {
            Program p = makeProgram();
            ContractFuzzer fuzzer = new ContractFuzzer(p);
            fuzzer.setSeed(0);

            assertInstanceOf(Integer.class, fuzzer.generateValue("num"));
            assertInstanceOf(Double.class, fuzzer.generateValue("duo"));
            assertInstanceOf(Boolean.class, fuzzer.generateValue("kya"));
            assertInstanceOf(String.class, fuzzer.generateValue("sab"));
            assertInstanceOf(Character.class, fuzzer.generateValue("ek"));
            assertInstanceOf(Long.class, fuzzer.generateValue("uint256"));
            assertInstanceOf(Long.class, fuzzer.generateValue("int256"));
            assertInstanceOf(String.class, fuzzer.generateValue("Address"));
            assertInstanceOf(String.class, fuzzer.generateValue("bytes32"));
        }

        @Test
        @DisplayName("Generated addresses have correct format")
        void addressFormat() {
            Program p = makeProgram();
            ContractFuzzer fuzzer = new ContractFuzzer(p);
            fuzzer.setSeed(1);
            String addr = (String) fuzzer.generateValue("Address");
            assertTrue(addr.startsWith("0x"));
            assertEquals(42, addr.length()); // 0x + 40 hex chars
        }

        @Test
        @DisplayName("FuzzStats tracks outcomes correctly")
        void fuzzStatsTracking() {
            FuzzStats stats = new FuzzStats("Token", "transfer");
            stats.record(FuzzOutcome.OK);
            stats.record(FuzzOutcome.OK);
            stats.record(FuzzOutcome.INVARIANT_VIOLATION);
            stats.record(FuzzOutcome.REVERT);
            stats.record(FuzzOutcome.EXCEPTION);

            assertEquals(5, stats.getTotalRuns());
            assertEquals(2, stats.getOkCount());
            assertEquals(1, stats.getInvariantViolations());
            assertEquals(1, stats.getReverts());
            assertEquals(1, stats.getExceptions());
            assertTrue(stats.hasFailures());
        }

        @Test
        @DisplayName("Empty class produces no targets")
        void emptyClassNoTargets() {
            ClassDecl cls = makeClass("Empty");
            Program p = makeProgram(cls);
            ContractFuzzer fuzzer = new ContractFuzzer(p);
            assertTrue(fuzzer.discoverFuzzTargets(cls).isEmpty());
        }

        @Test
        @DisplayName("setRuns rejects zero")
        void setRunsRejectsZero() {
            ContractFuzzer fuzzer = new ContractFuzzer(makeProgram());
            assertThrows(IllegalArgumentException.class, () -> fuzzer.setRuns(0));
        }

        @Test
        @DisplayName("setSeed produces reproducible results")
        void seedReproducibility() {
            FunctionDecl fn = makeFnWithParams("do_thing", "num");
            ClassDecl cls = makeClass("C", fn);
            Program p = makeProgram(cls);

            ContractFuzzer f1 = new ContractFuzzer(p);
            f1.setSeed(42);
            f1.setRuns(5);
            f1.fuzzAll();

            ContractFuzzer f2 = new ContractFuzzer(p);
            f2.setSeed(42);
            f2.setRuns(5);
            f2.fuzzAll();

            assertEquals(f1.getResults().size(), f2.getResults().size());
        }

        @Test
        @DisplayName("formatReport produces readable output")
        void formatReportWorks() {
            FunctionDecl fn = makeFn("stake");
            ClassDecl cls = makeClass("Vault", fn);
            Program p = makeProgram(cls);

            ContractFuzzer fuzzer = new ContractFuzzer(p);
            fuzzer.setRuns(5);
            fuzzer.setSeed(1);
            fuzzer.fuzzAll();
            String report = fuzzer.formatReport();
            assertTrue(report.contains("Fuzzer Report"));
            assertTrue(report.contains("Vault"));
        }

        @Test
        @DisplayName("FuzzResult toString is descriptive")
        void fuzzResultToString() {
            FuzzResult r = new FuzzResult("Token", "transfer",
                    List.of("0xABC", 100),
                    FuzzOutcome.OK, "completed");
            String s = r.toString();
            assertTrue(s.contains("Token"));
            assertTrue(s.contains("transfer"));
            assertTrue(s.contains("0xABC"));
        }

        @Test
        @DisplayName("generateArguments matches parameter count")
        void argumentCountMatches() {
            FunctionDecl fn = makeFnWithParams("send", "Address", "uint256", "kya");
            Program p = makeProgram();
            ContractFuzzer fuzzer = new ContractFuzzer(p);
            fuzzer.setSeed(1);
            List<Object> args = fuzzer.generateArguments(fn.getParameters());
            assertEquals(3, args.size());
        }
    }

    // ═══════════════════════════════════════════════════
    //  7.  TestCoverageTracker (SC-404)
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("7. TestCoverageTracker – SC-404: Coverage reports")
    class CoverageTests {

        private TestCoverageTracker tracker;

        @BeforeEach
        void setUp() {
            tracker = new TestCoverageTracker();
        }

        @Test
        @DisplayName("Records function entry and exit")
        void recordsFunctionEntryExit() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordFunctionExit("Token", "transfer");
            assertTrue(tracker.isFunctionCovered("Token", "transfer"));
        }

        @Test
        @DisplayName("Not-called function is not covered")
        void uncalledNotCovered() {
            assertFalse(tracker.isFunctionCovered("Token", "mint"));
        }

        @Test
        @DisplayName("Records statement coverage")
        void recordsStatements() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordStatements(5);
            tracker.recordFunctionExit("Token", "transfer");

            ContractCoverage cc = tracker.getContractCoverage("Token");
            assertNotNull(cc);
            FunctionCoverage fc = cc.getFunctions().get("transfer");
            assertEquals(5, fc.getTotalStatements());
            assertEquals(5, fc.getCoveredStatements());
        }

        @Test
        @DisplayName("Function coverage percentage correct")
        void functionCoveragePercent() {
            tracker.registerContract("Token", 4);
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordFunctionExit("Token", "transfer");
            tracker.recordFunctionEntry("Token", "mint");
            tracker.recordFunctionExit("Token", "mint");

            assertEquals(50.0, tracker.getCoverage("Token"), 0.01);
        }

        @Test
        @DisplayName("100% coverage when all functions called")
        void fullCoverage() {
            tracker.registerContract("Token", 2);
            tracker.recordFunctionEntry("Token", "a");
            tracker.recordFunctionExit("Token", "a");
            tracker.recordFunctionEntry("Token", "b");
            tracker.recordFunctionExit("Token", "b");

            assertEquals(100.0, tracker.getCoverage("Token"), 0.01);
        }

        @Test
        @DisplayName("0% coverage for unknown contract")
        void unknownContractZero() {
            assertEquals(0.0, tracker.getCoverage("Nonexistent"));
        }

        @Test
        @DisplayName("getTotalCalls counts across contracts")
        void totalCallsAcrossContracts() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordFunctionExit("Token", "transfer");
            tracker.recordFunctionEntry("Token", "mint");
            tracker.recordFunctionExit("Token", "mint");
            tracker.recordFunctionEntry("NFT", "mint");
            tracker.recordFunctionExit("NFT", "mint");

            assertEquals(3, tracker.getTotalCalls());
        }

        @Test
        @DisplayName("Multiple calls to same function counted")
        void multipleCalls() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordFunctionExit("Token", "transfer");
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordFunctionExit("Token", "transfer");

            FunctionCoverage fc = tracker.getContractCoverage("Token")
                    .getFunctions().get("transfer");
            assertEquals(2, fc.getCallCount());
        }

        @Test
        @DisplayName("getUncoveredFunctions works")
        void uncoveredFunctions() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordFunctionExit("Token", "transfer");

            List<String> uncovered = tracker.getUncoveredFunctions(
                    "Token", List.of("transfer", "mint", "burn"));
            assertEquals(2, uncovered.size());
            assertTrue(uncovered.contains("mint"));
            assertTrue(uncovered.contains("burn"));
        }

        @Test
        @DisplayName("reset clears all data")
        void resetClears() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordStatements(3);
            tracker.recordFunctionExit("Token", "transfer");
            tracker.reset();

            assertTrue(tracker.getAllCoverage().isEmpty());
            assertEquals(0, tracker.getTotalCalls());
        }

        @Test
        @DisplayName("formatReport produces readable output")
        void formatReportWorks() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordStatements(3);
            tracker.recordFunctionExit("Token", "transfer");

            String report = tracker.formatReport();
            assertTrue(report.contains("Coverage Report"));
            assertTrue(report.contains("Token"));
            assertTrue(report.contains("transfer"));
        }

        @Test
        @DisplayName("Statement coverage percentage for function")
        void statementCoverageForFunction() {
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordStatements(7);
            tracker.recordFunctionExit("Token", "transfer");

            FunctionCoverage fc = tracker.getContractCoverage("Token")
                    .getFunctions().get("transfer");
            assertEquals(100.0, fc.getCoveragePercentage(), 0.01);
        }

        @Test
        @DisplayName("Statements not recorded outside active function")
        void statementsOutsideFunction() {
            tracker.recordStatements(10);
            // No active function, so nothing should be recorded
            assertTrue(tracker.getAllCoverage().isEmpty());
        }

        @Test
        @DisplayName("getStatementCoverage returns 0 for unknown contract")
        void statementCoverageUnknown() {
            assertEquals(0.0, tracker.getStatementCoverage("X"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  8.  TestReporter
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("8. TestReporter – Unified test report")
    class ReporterTests {

        @Test
        @DisplayName("Builder creates reporter with all data")
        void builderWorks() {
            TestResult tr = new TestResult("Token", "test1",
                    TestStatus.PASSED, "", 10);
            InvariantViolation iv = new InvariantViolation(
                    "Token", "inv1", "transfer", "broke");
            FuzzResult fr = new FuzzResult("Token", "transfer",
                    List.of(), FuzzOutcome.OK, "ok");
            TestCoverageTracker cov = new TestCoverageTracker();

            TestReporter reporter = new TestReporter.Builder()
                    .testResults(List.of(tr))
                    .invariantViolations(List.of(iv))
                    .fuzzResults(List.of(fr))
                    .coverageTracker(cov)
                    .totalDurationMs(100)
                    .build();

            assertEquals(1, reporter.getTestResults().size());
            assertEquals(1, reporter.getInvariantViolations().size());
            assertEquals(1, reporter.getFuzzResults().size());
            assertNotNull(reporter.getCoverageTracker());
            assertEquals(100, reporter.getTotalDurationMs());
        }

        @Test
        @DisplayName("TEXT format produces full report")
        void textFormat() {
            TestResult tr = new TestResult("Token", "test1",
                    TestStatus.PASSED, "", 5);
            TestReporter reporter = new TestReporter.Builder()
                    .testResults(List.of(tr))
                    .totalDurationMs(50)
                    .build();

            String text = reporter.format(OutputFormat.TEXT);
            assertTrue(text.contains("DhrLang Contract Test Report"));
            assertTrue(text.contains("Test Results"));
            assertTrue(text.contains("Token"));
            assertTrue(text.contains("PASSED"));
        }

        @Test
        @DisplayName("COMPACT format produces one-line summary")
        void compactFormat() {
            TestResult tr = new TestResult("Token", "t1",
                    TestStatus.PASSED, "", 5);
            TestReporter reporter = new TestReporter.Builder()
                    .testResults(List.of(tr))
                    .totalDurationMs(30)
                    .build();

            String compact = reporter.format(OutputFormat.COMPACT);
            assertTrue(compact.contains("Tests: 1/1 passed"));
            assertTrue(compact.contains("Invariants: OK"));
        }

        @Test
        @DisplayName("JSON format has correct structure")
        void jsonFormat() {
            TestResult tr = new TestResult("Token", "t1",
                    TestStatus.PASSED, "", 10);
            TestReporter reporter = new TestReporter.Builder()
                    .testResults(List.of(tr))
                    .totalDurationMs(20)
                    .build();

            String json = reporter.format(OutputFormat.JSON);
            assertTrue(json.contains("\"tests\""));
            assertTrue(json.contains("\"passed\": 1"));
            assertTrue(json.contains("\"durationMs\": 20"));
        }

        @Test
        @DisplayName("Report with failures shows ISSUES FOUND")
        void failureReport() {
            TestResult tr = new TestResult("Token", "t1",
                    TestStatus.FAILED, "bad", 10);
            TestReporter reporter = new TestReporter.Builder()
                    .testResults(List.of(tr))
                    .totalDurationMs(10)
                    .build();

            String text = reporter.format(OutputFormat.TEXT);
            assertTrue(text.contains("ISSUES FOUND"));
        }

        @Test
        @DisplayName("Report with violations shows ISSUES FOUND")
        void violationReport() {
            TestResult tr = new TestResult("Token", "t1",
                    TestStatus.PASSED, "", 5);
            InvariantViolation v = new InvariantViolation(
                    "Token", "inv", "fn", "broken");
            TestReporter reporter = new TestReporter.Builder()
                    .testResults(List.of(tr))
                    .invariantViolations(List.of(v))
                    .totalDurationMs(10)
                    .build();

            String text = reporter.format(OutputFormat.TEXT);
            assertTrue(text.contains("ISSUES FOUND"));
        }

        @Test
        @DisplayName("Empty report still works")
        void emptyReport() {
            TestReporter reporter = new TestReporter.Builder()
                    .totalDurationMs(0)
                    .build();

            String text = reporter.format(OutputFormat.TEXT);
            assertTrue(text.contains("No tests executed"));
            assertTrue(text.contains("ALL CHECKS PASSED"));
        }

        @Test
        @DisplayName("COMPACT format with violations")
        void compactWithViolations() {
            InvariantViolation v = new InvariantViolation(
                    "Token", "inv", "fn", "x");
            TestReporter reporter = new TestReporter.Builder()
                    .invariantViolations(List.of(v))
                    .totalDurationMs(0)
                    .build();

            String compact = reporter.format(OutputFormat.COMPACT);
            assertTrue(compact.contains("1 violations"));
        }

        @Test
        @DisplayName("Coverage section in full report")
        void coverageInReport() {
            TestCoverageTracker tracker = new TestCoverageTracker();
            tracker.recordFunctionEntry("Token", "transfer");
            tracker.recordStatements(3);
            tracker.recordFunctionExit("Token", "transfer");

            TestReporter reporter = new TestReporter.Builder()
                    .coverageTracker(tracker)
                    .totalDurationMs(5)
                    .build();

            String text = reporter.format(OutputFormat.TEXT);
            assertTrue(text.contains("Coverage"));
            assertTrue(text.contains("Token"));
        }

        @Test
        @DisplayName("Fuzz section in full report")
        void fuzzInReport() {
            FuzzResult fr = new FuzzResult("Token", "transfer",
                    List.of("0xA", 100), FuzzOutcome.INVARIANT_VIOLATION, "broke inv");
            TestReporter reporter = new TestReporter.Builder()
                    .fuzzResults(List.of(fr))
                    .totalDurationMs(5)
                    .build();

            String text = reporter.format(OutputFormat.TEXT);
            assertTrue(text.contains("Fuzzer Results"));
            assertTrue(text.contains("Failures: 1"));
        }
    }

    // ═══════════════════════════════════════════════════
    //  9.  Integration – Full pipeline
    // ═══════════════════════════════════════════════════

    @Nested
    @DisplayName("9. Integration – Full testing pipeline")
    class IntegrationTests {

        @Test
        @DisplayName("Full pipeline: test + invariant + fuzz + coverage + report")
        void fullPipeline() {
            // Build a contract with tests, invariants, and functions
            FunctionDecl setup = makeFnAnnotatedWithBody("setup", 1,
                    ContractAnnotation.BEFORE_EACH);
            FunctionDecl test1 = makeFnAnnotatedWithBody("testTransfer", 3,
                    ContractAnnotation.TEST);
            FunctionDecl test2 = makeFnAnnotatedWithBody("testMint", 2,
                    ContractAnnotation.TEST);
            FunctionDecl inv = makeFnAnnotatedWithBody("balanceInvariant", 2,
                    ContractAnnotation.INVARIANT);
            FunctionDecl transfer = makeFnWithParams("transfer", "Address", "uint256");
            FunctionDecl mint = makeFnWithParams("mint", "uint256");
            FunctionDecl getBalance = makeFnWithAnnotations("getBalance",
                    ContractAnnotation.VIEW);

            ClassDecl tokenClass = makeClass("ERC20",
                    setup, test1, test2, inv, transfer, mint, getBalance);
            Program program = makeProgram(tokenClass);

            // 1. Run tests
            ContractTestRunner runner = new ContractTestRunner(program);
            runner.runAll();
            assertEquals(2, runner.totalTests());
            assertTrue(runner.allPassed());

            // 2. Check invariants
            InvariantChecker checker = new InvariantChecker(program);
            checker.setInvariantEvaluator(info -> true);  // All pass
            checker.analyzeAll();
            assertFalse(checker.hasViolations());

            // 3. Fuzz
            ContractFuzzer fuzzer = new ContractFuzzer(program);
            fuzzer.setRuns(10);
            fuzzer.setSeed(42);
            fuzzer.fuzzAll();
            assertFalse(fuzzer.hasFailures());

            // 4. Build report
            TestReporter reporter = new TestReporter.Builder()
                    .testResults(runner.getResults())
                    .invariantViolations(checker.getViolations())
                    .fuzzResults(fuzzer.getResults())
                    .coverageTracker(runner.getCoverageTracker())
                    .totalDurationMs(100)
                    .build();

            // 5. All output formats
            String text = reporter.format(OutputFormat.TEXT);
            String compact = reporter.format(OutputFormat.COMPACT);
            String json = reporter.format(OutputFormat.JSON);

            assertTrue(text.contains("ALL CHECKS PASSED"));
            assertTrue(compact.contains("2/2 passed"));
            assertTrue(json.contains("\"passed\": 2"));
        }

        @Test
        @DisplayName("Pipeline with cheatcodes for test setup")
        void pipelineWithCheatcodes() {
            TestCheatcodes cc = new TestCheatcodes();
            cc.prank("0xDeployer");
            cc.deal("0xDeployer", 1_000_000L);
            cc.warp(1_700_000_000L);
            cc.roll(100);

            assertEquals("0xDeployer", cc.getEffectiveSender());
            assertEquals(BigInteger.valueOf(1_000_000), cc.getBalance("0xDeployer"));
            assertEquals(1_700_000_000L, cc.getBlockTimestamp());
            assertEquals(100, cc.getBlockNumber());

            // After call
            cc.consumePrank();
            assertFalse(cc.isPrankActive());
        }

        @Test
        @DisplayName("Test runner + coverage tracker integration")
        void runnerCoverageIntegration() {
            FunctionDecl test1 = makeFnAnnotatedWithBody("test1", 5,
                    ContractAnnotation.TEST);
            FunctionDecl setup = makeFnAnnotatedWithBody("setup", 2,
                    ContractAnnotation.BEFORE_EACH);
            ClassDecl cls = makeClass("Vault", test1, setup);
            Program p = makeProgram(cls);

            ContractTestRunner runner = new ContractTestRunner(p);
            runner.runAll();

            TestCoverageTracker tracker = runner.getCoverageTracker();
            assertTrue(tracker.isFunctionCovered("Vault", "test1"));
            assertTrue(tracker.isFunctionCovered("Vault", "setup"));
        }

        @Test
        @DisplayName("Fuzzer respects seed for reproducibility")
        void fuzzerDeterminism() {
            FunctionDecl fn = makeFnWithParams("swap", "uint256", "Address");
            ClassDecl cls = makeClass("Dex", fn);
            Program p = makeProgram(cls);

            ContractFuzzer f1 = new ContractFuzzer(p);
            f1.setSeed(999);
            f1.setRuns(20);
            List<Object> args1 = f1.generateArguments(fn.getParameters());

            ContractFuzzer f2 = new ContractFuzzer(p);
            f2.setSeed(999);
            f2.setRuns(20);
            List<Object> args2 = f2.generateArguments(fn.getParameters());

            assertEquals(args1, args2);
        }

        @Test
        @DisplayName("Multiple contracts in one program")
        void multipleContracts() {
            FunctionDecl t1 = makeFnAnnotatedWithBody("testA", 2,
                    ContractAnnotation.TEST);
            FunctionDecl t2 = makeFnAnnotatedWithBody("testB", 3,
                    ContractAnnotation.TEST);
            ClassDecl c1 = makeClass("Alpha", t1);
            ClassDecl c2 = makeClass("Beta", t2);
            Program p = makeProgram(c1, c2);

            ContractTestRunner runner = new ContractTestRunner(p);
            runner.runAll();
            assertEquals(2, runner.totalTests());
            assertTrue(runner.allPassed());

            // Coverage
            TestCoverageTracker tracker = runner.getCoverageTracker();
            assertTrue(tracker.isFunctionCovered("Alpha", "testA"));
            assertTrue(tracker.isFunctionCovered("Beta", "testB"));
        }

        @Test
        @DisplayName("Cheatcodes + assertions work together")
        void cheatcodesAndAssertions() {
            TestCheatcodes cc = new TestCheatcodes();
            cc.deal("0xAlice", 1_000_000L);

            Interpreter interp = new Interpreter();
            assertDoesNotThrow(() ->
                    ContractAssertions.assertTrue().call(interp,
                            List.of(cc.getBalance("0xAlice").compareTo(BigInteger.ZERO) > 0)));
        }
    }
}

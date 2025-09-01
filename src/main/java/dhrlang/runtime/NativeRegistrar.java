package dhrlang.runtime;

import dhrlang.interpreter.Environment;
import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.stdlib.*;

/**
 * Central place for installing native/global functions into the root environment.
 * Pure extraction from the previous Interpreter.initGlobals() for modularity.
 */
public final class NativeRegistrar {
    private NativeRegistrar() {}

    public static void registerAll(Interpreter interpreter, Environment globals) {
        java.util.Set<String> installedNatives = new java.util.HashSet<>();

        globals.define("clock", new NativeFunction() {
            @Override public int arity() { return 0; }
            @Override public Object call(Interpreter i, java.util.List<Object> args) { return (double) System.currentTimeMillis(); }
            @Override public String toString() { return "<native fn clock>"; }
        });

        globals.define("printLine", new NativeFunction() {
            @Override public int arity() { return 1; }
            @Override public Object call(Interpreter i, java.util.List<Object> a) { System.out.println(RuntimeFormatting.formatForPrint(a.get(0))); return null; }
            @Override public String toString() { return "<native fn printLine>"; }
        });
        globals.define("print", new NativeFunction() {
            @Override public int arity() { return 1; }
            @Override public Object call(Interpreter i, java.util.List<Object> a) { System.out.print(RuntimeFormatting.formatForPrint(a.get(0))); return null; }
            @Override public String toString() { return "<native fn print>"; }
        });

        // Math
        globals.define("abs", MathFunctions.abs());
        globals.define("sqrt", MathFunctions.sqrt());
        globals.define("pow", MathFunctions.pow());
        globals.define("min", MathFunctions.min());
        globals.define("max", MathFunctions.max());
        globals.define("floor", MathFunctions.floor());
        globals.define("ceil", MathFunctions.ceil());
        globals.define("round", MathFunctions.round());
        globals.define("random", MathFunctions.random());
        globals.define("sin", MathFunctions.sin());
        globals.define("cos", MathFunctions.cos());
        globals.define("tan", MathFunctions.tan());
        globals.define("log", MathFunctions.log());
        globals.define("log10", MathFunctions.log10());
        globals.define("exp", MathFunctions.exp());
        globals.define("randomRange", MathFunctions.randomRange());
        globals.define("clamp", MathFunctions.clamp());

        // Strings
        globals.define("length", StringFunctions.length());
        globals.define("substring", StringFunctions.substring());
        globals.define("charAt", StringFunctions.charAt());
        globals.define("toUpperCase", StringFunctions.toUpperCase());
        globals.define("toLowerCase", StringFunctions.toLowerCase());
        globals.define("indexOf", StringFunctions.indexOf());
        globals.define("replace", StringFunctions.replace());
        globals.define("startsWith", StringFunctions.startsWith());
        globals.define("endsWith", StringFunctions.endsWith());
        globals.define("trim", StringFunctions.trim());
        globals.define("split", StringFunctions.split());
        globals.define("join", StringFunctions.join());
        globals.define("repeat", StringFunctions.repeat());
        globals.define("reverse", StringFunctions.reverse());
        globals.define("padLeft", StringFunctions.padLeft());
        globals.define("padRight", StringFunctions.padRight());

        // IO & conversion
        globals.define("readLine", IOFunctions.readLine());
        globals.define("readLineWithPrompt", IOFunctions.readLineWithPrompt());
        globals.define("toNum", IOFunctions.toNum());
        globals.define("toDuo", IOFunctions.toDuo());
        globals.define("toString", IOFunctions.toStringFunc());

        // Array functions
        globals.define("arrayLength", ArrayFunctions.arrayLength());
        globals.define("arrayContains", ArrayFunctions.arrayContains());
        globals.define("arrayIndexOf", ArrayFunctions.arrayIndexOf());
        globals.define("arrayCopy", ArrayFunctions.arrayCopy());
        globals.define("arrayReverse", ArrayFunctions.arrayReverse());
        globals.define("arraySlice", ArrayFunctions.arraySlice());
        globals.define("arraySort", ArrayFunctions.arraySort());
        globals.define("arrayConcat", ArrayFunctions.arrayConcat());
        globals.define("arrayFill", ArrayFunctions.arrayFill());
        globals.define("arraySum", ArrayFunctions.arraySum());
        globals.define("arrayAverage", ArrayFunctions.arrayAverage());
        globals.define("arrayPush", ArrayFunctions.arrayPush());
        globals.define("arrayPop", ArrayFunctions.arrayPop());
        globals.define("arrayInsert", ArrayFunctions.arrayInsert());

        // Utilities
        globals.define("isNum", UtilityFunctions.isNum());
        globals.define("isDuo", UtilityFunctions.isDuo());
        globals.define("isSab", UtilityFunctions.isSab());
        globals.define("isKya", UtilityFunctions.isKya());
        globals.define("isArray", UtilityFunctions.isArray());
        globals.define("typeOf", UtilityFunctions.typeOf());
        globals.define("range", UtilityFunctions.range());
        globals.define("sleep", UtilityFunctions.sleep());

        // Exception constructors
        globals.define("DhrException", NativeExceptions.dhrException());
    // 'Error' handled specially in Evaluator.newExpr; no synthetic class needed.
        globals.define("ArithmeticException", NativeExceptions.arithmetic());
        globals.define("IndexOutOfBoundsException", NativeExceptions.index());
        globals.define("TypeException", NativeExceptions.type());
        globals.define("NullPointerException", NativeExceptions.nullPtr());

        // Record installed names, warn for any missing expected signatures
        for (String name : dhrlang.stdlib.NativeSignatures.all()) {
            try { globals.get(name); installedNatives.add(name);} catch (Exception ignored) {}
        }
        for (String required : dhrlang.stdlib.NativeSignatures.all()) {
            if (!installedNatives.contains(required)) {
                System.err.println("[WARN] Native not installed yet: " + required);
            }
        }
    }
}

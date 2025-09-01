package dhrlang.stdlib;
import java.util.*;
public final class NativeSignatures {
    public static final class Signature { public final List<String> params; public final String returns; public Signature(List<String> p,String r){this.params=p;this.returns=r;} }
    private static final Map<String, Signature> SIGS = new HashMap<>();
    static {
        // Printing / IO
        add("print", List.of("any"), "kaam");
        add("printLine", List.of("any"), "kaam");
        add("readLine", List.of(), "sab");
        add("readLineWithPrompt", List.of("sab"), "sab");
        // Conversion
        add("toNum", List.of("sab"), "num");
        add("toDuo", List.of("sab"), "duo");
        add("toString", List.of("any"), "sab");
        // Time / util
        add("clock", List.of(), "duo");
        add("sleep", List.of("num"), "kaam");
        // Math
        for(String m : List.of("abs","sqrt","floor","ceil","round","sin","cos","tan","log","log10","exp")) add(m, List.of("num"), m.equals("sqrt")||m.equals("sin")||m.equals("cos")||m.equals("tan")||m.equals("log")||m.equals("log10")||m.equals("exp")?"duo":"num");
        add("pow", List.of("num","num"), "duo");
        add("min", List.of("num","num"), "numOrDuo");
        add("max", List.of("num","num"), "numOrDuo");
        add("random", List.of(), "duo");
        add("randomRange", List.of("num","num"), "num");
        add("clamp", List.of("num","num","num"), "numOrDuo");
        // Arrays
        add("arrayLength", List.of("array"), "num");
        add("arrayContains", List.of("array","any"), "kya");
        add("arrayIndexOf", List.of("array","any"), "num");
        add("arrayCopy", List.of("array"), "array");
        add("arrayReverse", List.of("array"), "array");
        add("arraySlice", List.of("array","num","num"), "array");
        add("arraySort", List.of("array"), "array");
        add("arrayConcat", List.of("array","array"), "array");
        add("arrayFill", List.of("any","num"), "array");
        add("arraySum", List.of("array"), "numOrDuo");
        add("arrayAverage", List.of("array"), "duo");
        add("arrayPush", List.of("array","any"), "array");
        add("arrayPop", List.of("array"), "array");
        add("arrayInsert", List.of("array","num","any"), "array");
        // String free functions
        for(String s: List.of("length","substring","charAt","toUpperCase","toLowerCase","indexOf","replace","startsWith","endsWith","trim","split","join","repeat","reverse","padLeft","padRight")) {
            // Return types vary; left as special-case in type checker; registry mainly for existence & arity.
            add(s, List.of(), "dynamic");
        }
        // Type predicates
        for(String s: List.of("isNum","isDuo","isSab","isKya","isArray")) add(s, List.of("any"), "kya");
        add("typeOf", List.of("any"), "sab");
        add("range", List.of("num","num"), "num[]");
    }
    private static void add(String name, List<String> params, String returns){ SIGS.put(name, new Signature(params, returns)); }
    public static boolean exists(String name){ return SIGS.containsKey(name); }
    public static Signature get(String name){ return SIGS.get(name); }
    public static Set<String> all(){ return Collections.unmodifiableSet(SIGS.keySet()); }
    // Utility to ensure interpreter installed all natives declared here.
    public static void assertInstalled(Set<String> installed){
        for(String n: SIGS.keySet()) if(!installed.contains(n)) throw new IllegalStateException("Native function missing implementation: "+n);
    }
}

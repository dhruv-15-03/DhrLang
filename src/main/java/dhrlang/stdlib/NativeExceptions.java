package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;

/** Factory for exception native constructors (extracted from Interpreter). */
public final class NativeExceptions {
    private NativeExceptions() {}

    public static NativeFunction dhrException() {
        return new NativeFunction() {
            @Override public int arity() { return 0; }
            @Override public Object call(Interpreter i, java.util.List<Object> a) { return new dhrlang.stdlib.exceptions.DhrException("Error"); }
            public String toString(){ return "<native exception DhrException>"; }
        }; }
    public static NativeFunction arithmetic() {
        return new NativeFunction() { @Override public int arity(){ return 1; } @Override public Object call(Interpreter i, java.util.List<Object> a){ return new dhrlang.stdlib.exceptions.ArithmeticException(a.get(0).toString()); } public String toString(){ return "<native exception ArithmeticException>"; } }; }
    public static NativeFunction index() {
        return new NativeFunction() { @Override public int arity(){ return 1; } @Override public Object call(Interpreter i, java.util.List<Object> a){ return new dhrlang.stdlib.exceptions.IndexOutOfBoundsException(a.get(0).toString()); } public String toString(){ return "<native exception IndexOutOfBoundsException>"; } }; }
    public static NativeFunction type() {
        return new NativeFunction() { @Override public int arity(){ return 1; } @Override public Object call(Interpreter i, java.util.List<Object> a){ return new dhrlang.stdlib.exceptions.TypeException(a.get(0).toString()); } public String toString(){ return "<native exception TypeException>"; } }; }
    public static NativeFunction nullPtr() {
        return new NativeFunction() { @Override public int arity(){ return 1; } @Override public Object call(Interpreter i, java.util.List<Object> a){ return new dhrlang.stdlib.exceptions.NullPointerException(a.get(0).toString()); } public String toString(){ return "<native exception NullPointerException>"; } }; }
}

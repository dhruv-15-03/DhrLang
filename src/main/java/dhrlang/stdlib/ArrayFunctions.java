package dhrlang.stdlib;

import dhrlang.interpreter.Interpreter;
import dhrlang.interpreter.NativeFunction;
import dhrlang.error.ErrorFactory;
import dhrlang.error.SourceLocation;

import java.util.List;

public class ArrayFunctions {

    public static NativeFunction arrayLength() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arg = arguments.get(0);
                if (!(arg instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayLength() requires an array argument", interpreter.getCurrentCallLocation());
                }
                return (long) ((Object[]) arg).length;
            }

            @Override
            public String toString() {
                return "<native fn arrayLength>";
            }
        };
    }

    public static NativeFunction arrayContains() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);
                Object value = arguments.get(1);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayContains() first argument must be an array", (SourceLocation) null);
                }

                Object[] array = (Object[]) arr;
                for (Object element : array) {
                    if (isEqual(element, value)) {
                        return true;
                    }
                }
                return false;
            }

            @Override
            public String toString() {
                return "<native fn arrayContains>";
            }
        };
    }

    public static NativeFunction arrayIndexOf() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);
                Object value = arguments.get(1);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayIndexOf() first argument must be an array", (SourceLocation) null);
                }

                Object[] array = (Object[]) arr;
                for (int i = 0; i < array.length; i++) {
                    if (isEqual(array[i], value)) {
                        return (long) i;
                    }
                }
                return -1L;
            }

            @Override
            public String toString() {
                return "<native fn arrayIndexOf>";
            }
        };
    }

    public static NativeFunction arrayCopy() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayCopy() requires an array argument", (SourceLocation) null);
                }

                Object[] original = (Object[]) arr;
                Object[] copy = new Object[original.length];
                System.arraycopy(original, 0, copy, 0, original.length);
                return copy;
            }

            @Override
            public String toString() {
                return "<native fn arrayCopy>";
            }
        };
    }

    public static NativeFunction arrayReverse() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayReverse() requires an array argument", (SourceLocation) null);
                }

                Object[] original = (Object[]) arr;
                Object[] reversed = new Object[original.length];
                for (int i = 0; i < original.length; i++) {
                    reversed[i] = original[original.length - 1 - i];
                }
                return reversed;
            }

            @Override
            public String toString() {
                return "<native fn arrayReverse>";
            }
        };
    }

    public static NativeFunction arraySlice() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);
                Object start = arguments.get(1);
                Object end = arguments.get(2);

                if (!(arr instanceof Object[]) || !(start instanceof Long) || !(end instanceof Long)) {
                    throw ErrorFactory.typeError("arraySlice() requires array, number, number arguments", (SourceLocation) null);
                }

                Object[] array = (Object[]) arr;
                int startIdx = ((Long) start).intValue();
                int endIdx = ((Long) end).intValue();

                if (startIdx < 0 || endIdx > array.length || startIdx > endIdx) {
                    throw ErrorFactory.validationError("arraySlice() indices out of bounds", interpreter.getCurrentCallLocation());
                }

                Object[] slice = new Object[endIdx - startIdx];
                System.arraycopy(array, startIdx, slice, 0, endIdx - startIdx);
                return slice;
            }

            @Override
            public String toString() {
                return "<native fn arraySlice>";
            }
        };
    }

    public static NativeFunction arraySort() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arraySort() requires an array argument", (SourceLocation) null);
                }

                Object[] array = (Object[]) arr;
                Object[] sorted = new Object[array.length];
                System.arraycopy(array, 0, sorted, 0, array.length);

                quickSort(sorted, 0, sorted.length - 1);
                return sorted;
            }

            @Override
            public String toString() {
                return "<native fn arraySort>";
            }
        };
    }

    public static NativeFunction arrayConcat() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr1 = arguments.get(0);
                Object arr2 = arguments.get(1);

                if (!(arr1 instanceof Object[]) || !(arr2 instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayConcat() requires two array arguments", (SourceLocation) null);
                }

                Object[] array1 = (Object[]) arr1;
                Object[] array2 = (Object[]) arr2;
                Object[] result = new Object[array1.length + array2.length];

                System.arraycopy(array1, 0, result, 0, array1.length);
                System.arraycopy(array2, 0, result, array1.length, array2.length);

                return result;
            }

            @Override
            public String toString() {
                return "<native fn arrayConcat>";
            }
        };
    }

    public static NativeFunction arrayFill() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object size = arguments.get(0);
                Object value = arguments.get(1);

                if (!(size instanceof Long)) {
                    throw ErrorFactory.typeError("arrayFill() first argument must be a number", (SourceLocation) null);
                }

                int arraySize = ((Long) size).intValue();
                if (arraySize < 0) {
                    throw ErrorFactory.validationError("arrayFill() size cannot be negative", (SourceLocation) null);
                }

                Object[] array = new Object[arraySize];
                for (int i = 0; i < arraySize; i++) {
                    array[i] = value;
                }
                return array;
            }

            @Override
            public String toString() {
                return "<native fn arrayFill>";
            }
        };
    }

    public static NativeFunction arraySum() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arraySum() requires an array argument", (SourceLocation) null);
                }

                Object[] array = (Object[]) arr;
                double sum = 0.0;
                boolean hasDouble = false;

                for (Object item : array) {
                    if (item instanceof Long) {
                        sum += ((Long) item).doubleValue();
                    } else if (item instanceof Double) {
                        sum += (Double) item;
                        hasDouble = true;
                    } else {
                        throw ErrorFactory.typeError("arraySum() requires an array of numbers", (SourceLocation) null);
                    }
                }

                return hasDouble ? sum : (long) sum;
            }

            @Override
            public String toString() {
                return "<native fn arraySum>";
            }
        };
    }

    public static NativeFunction arrayAverage() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayAverage() requires an array argument", (SourceLocation) null);
                }

                Object[] array = (Object[]) arr;
                if (array.length == 0) {
                    throw ErrorFactory.validationError("arrayAverage() cannot calculate average of empty array", (SourceLocation) null);
                }

                double sum = 0.0;
                for (Object item : array) {
                    if (item instanceof Long) {
                        sum += ((Long) item).doubleValue();
                    } else if (item instanceof Double) {
                        sum += (Double) item;
                    } else {
                        throw ErrorFactory.typeError("arrayAverage() requires an array of numbers", (SourceLocation) null);
                    }
                }

                return sum / array.length;
            }

            @Override
            public String toString() {
                return "<native fn arrayAverage>";
            }
        };
    }

    private static void quickSort(Object[] arr, int low, int high) {
        if (low < high) {
            int pivotIndex = partition(arr, low, high);
            
            quickSort(arr, low, pivotIndex - 1);
            quickSort(arr, pivotIndex + 1, high);
        }
    }
    
    private static int partition(Object[] arr, int low, int high) {
        Object pivot = arr[high];
        
        int i = (low - 1);
        
        for (int j = low; j < high; j++) {
            if (compareValues(arr[j], pivot) <= 0) {
                i++;
                swap(arr, i, j);
            }
        }
        swap(arr, i + 1, high);
        return i + 1;
    }
    
    private static void swap(Object[] arr, int i, int j) {
        Object temp = arr[i];
        arr[i] = arr[j];
        arr[j] = temp;
    }

    private static int compareValues(Object a, Object b) {
        if (a instanceof Long && b instanceof Long) {
            return Long.compare((Long) a, (Long) b);
        } else if (a instanceof Double && b instanceof Double) {
            return Double.compare((Double) a, (Double) b);
        } else if (a instanceof Number && b instanceof Number) {
            return Double.compare(((Number) a).doubleValue(), ((Number) b).doubleValue());
        } else if (a instanceof String && b instanceof String) {
            return ((String) a).compareTo((String) b);
        } else {
            return a.toString().compareTo(b.toString());
        }
    }

    public static NativeFunction arrayPush() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 2;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);
                Object newElement = arguments.get(1);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayPush() first argument must be an array", (SourceLocation) null);
                }

                Object[] original = (Object[]) arr;
                Object[] newArray = new Object[original.length + 1];
                
                System.arraycopy(original, 0, newArray, 0, original.length);
                newArray[original.length] = newElement;
                
                return newArray;
            }

            @Override
            public String toString() {
                return "<native fn arrayPush>";
            }
        };
    }

    public static NativeFunction arrayPop() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 1;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);

                if (!(arr instanceof Object[])) {
                    throw ErrorFactory.typeError("arrayPop() requires an array argument", interpreter.getCurrentCallLocation());
                }

                Object[] original = (Object[]) arr;
                if (original.length == 0) {
                    throw ErrorFactory.validationError("arrayPop() cannot pop from empty array", interpreter.getCurrentCallLocation());
                }

                Object[] newArray = new Object[original.length - 1];
                System.arraycopy(original, 0, newArray, 0, original.length - 1);
                
                return newArray;
            }

            @Override
            public String toString() {
                return "<native fn arrayPop>";
            }
        };
    }

    public static NativeFunction arrayInsert() {
        return new NativeFunction() {
            @Override
            public int arity() {
                return 3;
            }

            @Override
            public Object call(Interpreter interpreter, List<Object> arguments) {
                Object arr = arguments.get(0);
                Object index = arguments.get(1);
                Object element = arguments.get(2);

                if (!(arr instanceof Object[]) || !(index instanceof Long)) {
                    throw ErrorFactory.typeError("arrayInsert() requires array, number, value arguments", (SourceLocation) null);
                }

                Object[] original = (Object[]) arr;
                int insertIndex = ((Long) index).intValue();
                
                if (insertIndex < 0 || insertIndex > original.length) {
                    throw ErrorFactory.validationError("arrayInsert() index out of bounds", interpreter.getCurrentCallLocation());
                }

                Object[] newArray = new Object[original.length + 1];
                
                System.arraycopy(original, 0, newArray, 0, insertIndex);
                
                newArray[insertIndex] = element;
                System.arraycopy(original, insertIndex, newArray, insertIndex + 1, original.length - insertIndex);
                
                return newArray;
            }

            @Override
            public String toString() {
                return "<native fn arrayInsert>";
            }
        };
    }

    private static boolean isEqual(Object a, Object b) {
        if (a == null && b == null) return true;
        if (a == null || b == null) return false;
        return a.equals(b);
    }
}

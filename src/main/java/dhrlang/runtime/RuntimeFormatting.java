package dhrlang.runtime;

/** Formatting helpers extracted from Interpreter to reduce size and allow reuse in natives. */
public final class RuntimeFormatting {
    private RuntimeFormatting() {}

    public static String formatForPrint(Object obj) {
        if (obj == null) return "null";
        if (obj instanceof Object[]) {
            Object[] array = (Object[]) obj;
            StringBuilder sb = new StringBuilder("[");
            for (int i = 0; i < array.length; i++) {
                if (i > 0) sb.append(", ");
                Object el = array[i];
                if (el == null) sb.append("null");
                else if (el instanceof String) sb.append("\"").append(el).append("\"");
                else sb.append(el.toString());
            }
            return sb.append("]").toString();
        }
        return obj.toString();
    }
}

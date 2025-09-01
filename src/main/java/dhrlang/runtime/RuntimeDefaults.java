package dhrlang.runtime;


public final class RuntimeDefaults {
    private RuntimeDefaults() {}

    public static Object getDefaultValue(String type) {
        if (type == null) return null;
        return switch (type) {
            case "num" -> 0L;
            case "duo" -> 0.0;
            case "kya" -> false;
            case "ek" -> '\0';
            case "sab" -> "";
            default -> null; // object / reference / generic types
        };
    }
}

package dhrlang.typechecker;

import java.util.List;

public class FunctionSignature {
    private final List<String> parameterTypes;
    private final String returnType;

    public FunctionSignature(List<String> parameterTypes, String returnType) {
        this.parameterTypes = parameterTypes;
        this.returnType = returnType;
    }

    public List<String> getParameterTypes() {
        return parameterTypes;
    }

    public String getReturnType() {
        return returnType;
    }
}
